package com.javelin.core.execution;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

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
 *   - Individual .exec files per test: jacoco-ClassName_methodName.exec
 *   - Results file: test-results.dat (serialized Map of testId -> passed)
 */
public class SingleJvmTestRunner {

    private static final String OUTPUT_ARG = "--output";
    private static final String TESTS_ARG = "--tests";
    private static final String RESULTS_FILE = "test-results.dat";

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
    private static RunnerConfig parseArgs(String[] args) {
        Path outputDir = null;
        List<String> tests = new ArrayList<>();
        
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            
            if (OUTPUT_ARG.equals(arg) && i + 1 < args.length) {
                outputDir = Path.of(args[++i]);
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
            throw new IllegalArgumentException("No tests specified. Use --tests <test1> <test2> ...");
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
        JavelinTestListener coverageListener = new JavelinTestListener(config.outputDir);
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        
        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
        
        for (String testSpec : config.tests) {
            if (testSpec.contains("#")) {
                //maethod specifier: ClassName#methodName
                requestBuilder.selectors(DiscoverySelectors.selectMethod(testSpec));
            } else {
                //class specifier: just ClassName
                requestBuilder.selectors(DiscoverySelectors.selectClass(testSpec));
            }
        }
        requestBuilder.configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
        LauncherDiscoveryRequest request = requestBuilder.build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(coverageListener, summaryListener);

        System.out.println("Running " + config.tests.size() + " test(s) in single JVM...");
        launcher.execute(request);

        Map<String, Boolean> testResults = coverageListener.getTestResults();
        Map<String, byte[]> coverageData = coverageListener.getCoverageData();

        writeResultsFile(config.outputDir, testResults);

        TestExecutionSummary summary = summaryListener.getSummary();
        long passed = summary.getTestsSucceededCount();
        long failed = summary.getTestsFailedCount();
        long skipped = summary.getTestsSkippedCount();
        
        System.out.println("Test execution completed:");
        System.out.println("  Passed:  " + passed);
        System.out.println("  Failed:  " + failed);
        System.out.println("  Skipped: " + skipped);
        System.out.println("  Coverage files generated: " + coverageData.size());

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
     * Writes test results to a serialized file.
     */
    private static void writeResultsFile(Path outputDir, Map<String, Boolean> results) throws IOException {
        Path resultsFile = outputDir.resolve(RESULTS_FILE);
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(resultsFile.toFile()))) {
            oos.writeObject(new java.util.HashMap<>(results));
        }
    }

    /**
     * Reads test results from the serialized file.
     * Used by CoverageRunner to retrieve results after process completes.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Boolean> readResultsFile(Path outputDir) throws IOException, ClassNotFoundException {
        Path resultsFile = outputDir.resolve(RESULTS_FILE);
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(resultsFile.toFile()))) {
            return (Map<String, Boolean>) ois.readObject();
        }
    }

    /**
     * Configuration record for the test runner.
     */
    private record RunnerConfig(Path outputDir, List<String> tests) {}
}
