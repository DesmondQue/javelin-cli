package com.javelin.core.execution;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.javelin.core.model.TestExecResult;

/**
 * Single JVM Test Runner for JaCoCo coverage collection.
 *
 * This class is designed to be executed in a forked JVM process with the JaCoCo agent attached.
 * It runs all specified tests using the JUnit Platform Launcher while capturing per-test
 * coverage data via JavelinTestListener.
 *
 * Usage:
 *   java -javaagent:jacocoagent.jar -cp <classpath> com.javelin.core.execution.SingleJvmTestRunner
 *        --output <outputDir> --tests <test1> <test2> ...
 *
 * Test format: ClassName#methodName or just ClassName for all methods in a class
 *
 * Output:
 *   - Single binary file: javelin-results.bin (test results + coverage data)
 */
public class SingleJvmTestRunner {

    private static final String OUTPUT_ARG = "--output";
    private static final String TESTS_ARG = "--tests";
    private static final String TESTS_FILE_ARG = "--tests-file";
    private static final String RESULTS_FILE = "javelin-results.bin";
    private static final int MAGIC = 0x4A415645; // "JAVE"
    private static final int VERSION = 1;

    public static void main(String[] args) {
        try {
            RunnerConfig config = parseArgs(args);
            int exitCode = runTests(config);
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parses command line arguments.
     */
    static RunnerConfig parseArgs(String[] args) {
        Path outputDir = null;
        List<String> tests = new ArrayList<>();

        int i = 0;
        while (i < args.length) {
            String arg = args[i];

            if (OUTPUT_ARG.equals(arg) && i + 1 < args.length) {
                outputDir = Path.of(args[++i]);
            } else if (TESTS_FILE_ARG.equals(arg) && i + 1 < args.length) {
                Path testsFilePath = Path.of(args[++i]);
                try {
                    for (String line : Files.readAllLines(testsFilePath, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            tests.add(trimmed);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            "Failed to read tests file: " + testsFilePath + " - " + e.getMessage(), e);
                }
            } else if (TESTS_ARG.equals(arg)) {
                i++;
                while (i < args.length && !args[i].startsWith("--")) {
                    tests.add(args[i]);
                    i++;
                }
                continue;
            }
            i++;
        }

        if (outputDir == null) {
            throw new IllegalArgumentException("Missing required --output argument");
        }
        if (tests.isEmpty()) {
            throw new IllegalArgumentException(
                    "No tests specified. Use --tests <test1> <test2> ... or --tests-file <filepath>");
        }

        return new RunnerConfig(outputDir, tests);
    }

    /**
     * Runs all specified tests with coverage collection.
     *
     * @return 0 if all tests passed, 1 otherwise
     */
    private static int runTests(RunnerConfig config) throws IOException {

        Files.createDirectories(config.outputDir);
        System.out.println("      Output directory: " + config.outputDir.toAbsolutePath());
        JavelinTestListener coverageListener = new JavelinTestListener();
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();

        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();

        // Use class-level selectors to let JUnit handle method discovery.
        // This avoids failures with methods that have injected parameters
        // (e.g., WireMock's WireMockRuntimeInfo) where selectMethod() can't
        // resolve the no-arg signature.
        Set<String> classNames = new LinkedHashSet<>();
        for (String testSpec : config.tests) {
            if (testSpec.contains("#")) {
                classNames.add(testSpec.substring(0, testSpec.indexOf('#')));
            } else {
                classNames.add(testSpec);
            }
        }
        for (String className : classNames) {
            requestBuilder.selectors(DiscoverySelectors.selectClass(className));
        }
        requestBuilder.configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
        LauncherDiscoveryRequest request = requestBuilder.build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(coverageListener, summaryListener);

        System.out.println("Running " + config.tests.size() + " test(s) in single JVM...");
        launcher.execute(request);

        Map<String, Boolean> testResults = coverageListener.getTestResults();
        Map<String, byte[]> coverageData = coverageListener.getCoverageData();

        writeCombinedResultsFile(config.outputDir, testResults, coverageData);

        TestExecutionSummary summary = summaryListener.getSummary();
        long passed = summary.getTestsSucceededCount();
        long failed = summary.getTestsFailedCount();
        long skipped = summary.getTestsSkippedCount();

        System.out.println("Test execution completed:");
        System.out.println("  Passed:  " + passed);
        System.out.println("  Failed:  " + failed);
        System.out.println("  Skipped: " + skipped);
        System.out.println("  Coverage entries: " + coverageData.size());

        if (failed > 0) {
            System.out.println("\nFailures:");
            summary.getFailures().forEach(failure -> {
                System.out.println("  - " + failure.getTestIdentifier().getDisplayName());
                if (failure.getException() != null) {
                    System.out.println("    " + failure.getException().getMessage());
                }
            });
        }

        return failed > 0 ? 1 : 0;
    }

    /**
     * Writes test results and coverage data to a single binary file.
     * Format: magic (int) + version (int) + count (int) + entries
     * Each entry: testId (UTF) + passed (boolean) + dataLength (int) + coverage bytes
     */
    private static void writeCombinedResultsFile(Path outputDir,
                                                  Map<String, Boolean> testResults,
                                                  Map<String, byte[]> coverageData) throws IOException {
        Path resultsFile = outputDir.resolve(RESULTS_FILE);

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(resultsFile)))) {
            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            dos.writeInt(testResults.size());

            for (Map.Entry<String, Boolean> entry : testResults.entrySet()) {
                String testId = entry.getKey();
                boolean passed = entry.getValue();
                byte[] data = coverageData.get(testId);

                dos.writeUTF(testId);
                dos.writeBoolean(passed);
                if (data != null) {
                    dos.writeInt(data.length);
                    dos.write(data);
                } else {
                    dos.writeInt(0);
                }
            }
        }
        System.out.println("      Wrote " + testResults.size() + " result(s) to " + resultsFile.toAbsolutePath());
    }

    /**
     * Reads test results and coverage data from the consolidated binary file.
     * Used by CoverageRunner to retrieve results after process completes.
     */
    public static List<TestExecResult> readCombinedResultsFile(Path outputDir) throws IOException {
        Path resultsFile = outputDir.resolve(RESULTS_FILE);
        List<TestExecResult> results = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(resultsFile)))) {
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid results file: bad magic number 0x" + Integer.toHexString(magic));
            }
            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported results file version: " + version);
            }
            int count = dis.readInt();

            for (int i = 0; i < count; i++) {
                String testId = dis.readUTF();
                boolean passed = dis.readBoolean();
                int dataLength = dis.readInt();
                byte[] data = null;
                if (dataLength > 0) {
                    data = new byte[dataLength];
                    dis.readFully(data);
                }
                results.add(new TestExecResult(testId, data, passed));
            }
        }
        return results;
    }

    /**
     * Configuration for the test runner.
     */
    static final class RunnerConfig {
        private final Path outputDir;
        private final List<String> tests;

        RunnerConfig(Path outputDir, List<String> tests) {
            this.outputDir = outputDir;
            this.tests = tests;
        }

        Path outputDir() { return outputDir; }
        List<String> tests() { return tests; }
    }
}
