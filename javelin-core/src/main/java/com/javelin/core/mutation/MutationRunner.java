package com.javelin.core.mutation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.javelin.core.execution.ProcessExecutor;
import com.javelin.core.model.CoverageData;

/**
 * Mutation Runner
 *
 * Executes PITest programmatically (via CLI subprocess) with scoped targetClasses
 * derived from the fault region, and collects per-test kill results.
 *
 * Uses the same ProcessExecutor pattern as CoverageRunner: PITest is launched in
 * a separate JVM process using the Javelin fat JAR on its classpath (which bundles
 * PITest). This avoids classpath isolation issues between PITest and the target project.
 *
 * Output is written to a temporary directory containing:
 *   - mutations.xml           (standard PITest XML)
 *   - mutations.csv           (FULL_MUTATION_MATRIX, one row per test × mutant)
 */
public class MutationRunner {

    private final Path targetPath;
    private final Path testPath;
    private final Path sourcePath;
    private final String additionalClasspath;
    private final int threadCount;
    private final CoverageData coverageData;
    private final ProcessExecutor processExecutor;

    /**
     * @param targetPath          compiled source classes directory (e.g. build/classes/java/main)
     * @param testPath            compiled test classes directory  (e.g. build/classes/java/test)
     * @param sourcePath          source directory for PITest reports (e.g. src/main/java)
     * @param additionalClasspath extra classpath entries (from CLI --classpath arg), may be null
     * @param threadCount         number of threads for PITest parallel mutation testing
     * @param coverageData        per-test coverage data for test scoping (may be null to skip filtering)
     */
    public MutationRunner(Path targetPath, Path testPath, Path sourcePath,
                          String additionalClasspath, int threadCount,
                          CoverageData coverageData) {
        this.targetPath = targetPath;
        this.testPath = testPath;
        this.sourcePath = sourcePath;
        this.additionalClasspath = additionalClasspath;
        this.threadCount = threadCount;
        this.coverageData = coverageData;
        this.processExecutor = new ProcessExecutor();
    }

    /**
     * Runs PITest on the given target classes and returns the path to the report directory
     * that contains {@code mutations.xml} and the FULL_MUTATION_MATRIX CSV file.
     *
     * @param targetClassNames fully qualified class names to mutate (from FaultRegionIdentifier)
     * @return path to the temporary report directory written by PITest
     * @throws IOException if a temp directory cannot be created or PITest fails to produce output
     */
    public Path run(Set<String> targetClassNames) throws IOException {
        if (targetClassNames.isEmpty()) {
            throw new IllegalArgumentException("targetClassNames must not be empty");
        }

        Path reportDir = Files.createTempDirectory("javelin-pitest-");

        List<String> testClasses = filterTestsByFaultRegion(targetClassNames);
        if (testClasses.isEmpty()) {
            // Fallback: if coverage-based filtering yields nothing, use all test classes
            testClasses = findTestClasses(testPath);
            System.out.println("      Test scoping: fallback to all " + testClasses.size() + " test class(es).");
        } else {
            System.out.println("      Test scoping: " + testClasses.size() + " test class(es) cover the fault region.");
        }

        String classpath = buildClasspath();

        List<String> javaArgs = buildPitestArgs(classpath, targetClassNames, testClasses, reportDir);

        System.out.println("      Running PITest on " + targetClassNames.size() + " class(es)...");

        ProcessExecutor.ExecutionResult result = processExecutor.executeJava(
                javaArgs,
                targetPath,
                null,
                600 // 10-minute timeout
        );

        if (!result.stdout().isBlank()) {
            System.out.println(result.stdout());
        }
        if (!result.stderr().isBlank()) {
            System.err.println(result.stderr());
        }

        if (result.timedOut()) {
            throw new IOException("PITest timed out after 10 minutes");
        }

        // PITest exits non-zero when all mutants are covered but no kill happened — treat as OK
        // Exit code 0 = success, 1 = no mutations found. Treat both as non-fatal.
        if (result.exitCode() > 1) {
            throw new IOException("PITest exited with code " + result.exitCode()
                    + ". stderr: " + result.stderr());
        }

        System.out.println("      PITest report written to: " + reportDir);
        return reportDir;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the Java arguments for the PITest CLI subprocess.
     *
     * PITest's entry point when run via fat-JAR CLI:
     *   org.pitest.mutationtest.commandline.MutationCoverageReport
     *
     * Key flags:
     *   --targetClasses   — comma-separated FQ class names to mutate (scoped)
     *   --targetTests     — comma-separated FQ test class names
     *   --sourceDirs      — source directory (for report generation)
     *   --reportDir       — output directory
     *   --classPath       — project classpath (source + test classes)
     *   --outputFormats   — XML for structured mutation data
     *   --fullMutationMatrix — true, records all killing tests per mutant
     *   --mutators        — DEFAULT operator set
     *   --verbose         — false to reduce noise
     */
    private List<String> buildPitestArgs(String classpath, Set<String> targetClassNames,
                                          List<String> testClasses, Path reportDir) {
        List<String> args = new ArrayList<>();

        // Use -cp with the full Javelin classpath (which bundles PITest in the fat JAR)
        args.add("-cp");
        args.add(classpath);

        // PITest entry point
        args.add("org.pitest.mutationtest.commandline.MutationCoverageReport");

        // Scoped mutation targets (comma-separated)
        args.add("--targetClasses");
        args.add(String.join(",", targetClassNames));

        // Test classes (comma-separated)
        args.add("--targetTests");
        args.add(String.join(",", testClasses));

        // Source dirs (for HTML report source display; required by PITest even for XML-only output)
        args.add("--sourceDirs");
        args.add(sourcePath.toAbsolutePath().toString());

        // Report output directory
        args.add("--reportDir");
        args.add(reportDir.toAbsolutePath().toString());

        // Project classpath that PITest uses when running the tests under mutation
        String projectClasspath = buildProjectClasspath();
        args.add("--classPath");
        args.add(projectClasspath);

        // Output: XML with full mutation matrix (all killing tests per mutant)
        args.add("--outputFormats");
        args.add("XML");

        // Enable full mutation matrix — records all killing tests, not just the first
        args.add("--fullMutationMatrix");
        args.add("true");

        // Skip failing tests — Ochiai-MS only needs mutation scores for passing tests.
        // The target program has known bugs so some tests are expected to fail.
        args.add("--skipFailingTests");
        args.add("true");

        // Use the DEFAULT mutator set
        args.add("--mutators");
        args.add("DEFAULTS");

        // Reduce console noise; errors still printed via stderr
        args.add("--verbose");
        args.add("false");

        // Parallel mutation testing threads
        args.add("--threads");
        args.add(String.valueOf(threadCount));

        return args;
    }

    /**
     * Builds the full classpath used for the PITest subprocess (-cp argument).
     * Includes the Javelin fat JAR itself (which bundles PITest), and project classes.
     */
    private String buildClasspath() {
        String separator = ProcessExecutor.getPathSeparator();
        StringBuilder cp = new StringBuilder();

        // Javelin fat JAR provides PITest and all its dependencies
        String javelinClasspath = System.getProperty("java.class.path");
        if (javelinClasspath != null && !javelinClasspath.isBlank()) {
            boolean first = true;
            for (String entry : javelinClasspath.split(separator)) {
                if (!entry.isBlank()) {
                    if (!first) cp.append(separator);
                    cp.append(Path.of(entry).toAbsolutePath());
                    first = false;
                }
            }
        }

        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            cp.append(separator).append(additionalClasspath);
        }

        return cp.toString();
    }

    /**
     * Builds the project classpath passed to PITest via --classPath.
     * PITest CLI expects comma-separated paths (not OS path-separator).
     */
    private String buildProjectClasspath() {
        String osSeparator = ProcessExecutor.getPathSeparator();
        // PITest --classPath uses comma as delimiter
        String pitestSeparator = ",";
        StringBuilder cp = new StringBuilder();

        cp.append(targetPath.toAbsolutePath());
        cp.append(pitestSeparator).append(testPath.toAbsolutePath());

        String javelinClasspath = System.getProperty("java.class.path");
        if (javelinClasspath != null && !javelinClasspath.isBlank()) {
            for (String entry : javelinClasspath.split(java.util.regex.Pattern.quote(osSeparator))) {
                if (!entry.isBlank()) {
                    cp.append(pitestSeparator).append(Path.of(entry).toAbsolutePath());
                }
            }
        }

        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            for (String entry : additionalClasspath.split(java.util.regex.Pattern.quote(osSeparator))) {
                if (!entry.isBlank()) {
                    cp.append(pitestSeparator).append(entry);
                }
            }
        }

        return cp.toString();
    }

    /**
     * Discovers test classes by walking the test path directory.
     * Returns fully qualified class names for classes ending in Test or Tests.
     */
    private List<String> findTestClasses(Path dir) throws IOException {
        List<String> testClasses = new ArrayList<>();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith("Test.class") || fileName.endsWith("Tests.class")) {
                    Path relative = dir.relativize(file);
                    String className = relative.toString()
                            .replace(".class", "")
                            .replace(java.io.File.separatorChar, '.')
                            .replace('/', '.');
                    testClasses.add(className);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return testClasses;
    }

    /**
     * Filters tests to only those that cover at least one class in the fault region.
     * Uses the per-test coverage data from the JaCoCo coverage phase.
     *
     * CoverageData keys are per-method test IDs (e.g. "CalculatorTest#testAdd"),
     * but PITest --targetTests expects class names (e.g. "CalculatorTest").
     * This method extracts class names from the test IDs.
     *
     * Falls back to all test classes if coverageData is null.
     */
    private List<String> filterTestsByFaultRegion(Set<String> targetClassNames) throws IOException {
        if (coverageData == null) {
            return findTestClasses(testPath);
        }

        Set<String> relevantTestClasses = new HashSet<>();
        Map<String, Map<String, Set<Integer>>> perTestCoverage = coverageData.getCoveragePerTest();

        for (Map.Entry<String, Map<String, Set<Integer>>> entry : perTestCoverage.entrySet()) {
            String testId = entry.getKey();
            Map<String, Set<Integer>> classesCovered = entry.getValue();

            for (String targetClass : targetClassNames) {
                if (classesCovered.containsKey(targetClass)) {
                    // Extract class name from test ID (e.g. "CalculatorTest#testAdd" → "CalculatorTest")
                    String testClassName = testId.contains("#")
                            ? testId.substring(0, testId.indexOf('#'))
                            : testId;
                    relevantTestClasses.add(testClassName);
                    break;
                }
            }
        }

        return new ArrayList<>(relevantTestClasses);
    }

    /**
     * Returns the temp report directory for the last run (for testing / inspection).
     * NOTE: each call to {@link #run} creates a new temp directory.
     */
    public Path getReportDir() {
        // Provided for testing; callers should use the Path returned by run().
        throw new UnsupportedOperationException(
                "Use the Path returned by run() to access the report directory.");
    }
}
