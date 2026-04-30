package com.javelin.core.execution;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.javelin.core.model.TestExecResult;

/**
 * Coverage Runner
 * 
 * Responsibilities:
 * - Orchestrates the JaCoCo agent
 * - input: Target Class path, Test Class path
 * - process: calls ProcessExecutor to run tests with -javaagent:jacoco.jar
 * - output: a binary jacoco.exec file
 * 
 * Design Notes:
 * - uses ProcessBuilder (via ProcessExecutor) to launch tests in a new JVM
 * - online mode: JaCoCo agent attached via -javaagent argument
 * - offline mode: classes pre-instrumented by OfflineInstrumenter; no -javaagent needed
 * - supports both JUnit 4 (Vintage) and JUnit 5 (Jupiter) tests
 */
public class CoverageRunner {

    private final Path targetPath;
    private final Path testPath;
    private final String additionalClasspath;
    private final ProcessExecutor processExecutor;
    private final boolean offlineMode;
    private final boolean quiet;

    private Path tempDir;
    private Path jacocoAgentJar;

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath) {
        this(targetPath, testPath, additionalClasspath, false, false, null);
    }

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath, boolean offlineMode) {
        this(targetPath, testPath, additionalClasspath, offlineMode, false, null);
    }

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath, boolean offlineMode, boolean quiet) {
        this(targetPath, testPath, additionalClasspath, offlineMode, quiet, null);
    }

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath,
                          boolean offlineMode, boolean quiet, Path jvmHome) {
        this.targetPath = targetPath;
        this.testPath = testPath;
        this.additionalClasspath = additionalClasspath;
        this.offlineMode = offlineMode;
        this.quiet = quiet;
        this.processExecutor = new ProcessExecutor(jvmHome);
    }

    /**
     * @deprecated threadCount is no longer used since all tests run in a single JVM.
     *             Use {@link #CoverageRunner(Path, Path, String, boolean)} instead.
     */
    @Deprecated
    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath, int threadCount) {
        this(targetPath, testPath, additionalClasspath);
    }

    /**
     * Discovers test classes by walking the test path directory structure.
     * Finds all files ending in Test.class or Tests.class.
     *
     * @param dir the directory to search for test classes
     * @return List of fully qualified class names (e.g., com.example.CalculatorTest)
     * @throws IOException if directory traversal fails
     */
    private List<String> findTestClasses(Path dir) throws IOException {
        List<String> testClasses = new ArrayList<>();
        
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith("Test.class") || fileName.endsWith("Tests.class")
                        || fileName.endsWith("TestCase.class")) {
                    if (!isAbstractClass(file)) {
                        Path relativePath = dir.relativize(file);
                        String className = relativePath.toString()
                                .replace(".class", "")
                                .replace(java.io.File.separatorChar, '.')
                                .replace('/', '.');
                        testClasses.add(className);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return testClasses;
    }

    private boolean isAbstractClass(Path classFile) {
        try (var is = Files.newInputStream(classFile)) {
            ClassReader cr = new ClassReader(is);
            return (cr.getAccess() & Opcodes.ACC_ABSTRACT) != 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Discovers test methods within a test class using ASM bytecode analysis.
     * Looks for methods annotated with @Test (JUnit 4 or JUnit 5).
     *
     * @param testDir   the directory containing test classes
     * @param className the fully qualified class name
     * @return List of method names annotated with @Test
     * @throws IOException if class file cannot be read
     */
    private List<String> findTestMethods(Path testDir, String className) throws IOException {
        String classFilePath = className.replace('.', java.io.File.separatorChar) + ".class";
        Path classFile = testDir.resolve(classFilePath);

        if (!Files.exists(classFile)) {
            classFile = testDir.resolve(className + ".class");
        }

        if (!Files.exists(classFile)) {
            System.err.println("      WARNING: Could not find class file for " + className);
            return new ArrayList<>();
        }

        List<String> annotatedMethods = new ArrayList<>();
        List<String> junit3Candidates = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(classFile.toFile())) {
            ClassReader classReader = new ClassReader(fis);

            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    if (name.startsWith("test") && descriptor.equals("()V")
                            && (access & Opcodes.ACC_PUBLIC) != 0) {
                        junit3Candidates.add(name);
                    }

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (desc.equals("Lorg/junit/jupiter/api/Test;")
                                    || desc.equals("Lorg/junit/Test;")) {
                                annotatedMethods.add(name);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        return annotatedMethods.isEmpty() ? junit3Candidates : annotatedMethods;
    }

    /**
     * Runs all tests with JaCoCo coverage instrumentation in a SINGLE JVM process.
     * Uses JavelinTestListener to capture per-test coverage data via JaCoCo Runtime API.
     * 
     * This is significantly faster than the previous approach of forking a new JVM per test,
     * following the GZoltar approach of in-process coverage collection.
     *
     * @return List of TestExecResult containing in-memory coverage data and pass/fail status
     * @throws IOException if temp files cannot be created
     */
    public List<TestExecResult> run() throws IOException {
        setupTempDirectory();
        extractJacocoAgent();

        String classpath = buildClasspath();
        
        // Discover all test classes
        List<String> testClasses = findTestClasses(testPath);
        
        if (testClasses.isEmpty()) {
            System.err.println("      WARNING: No test classes found in " + testPath);
            return new ArrayList<>();
        }
        
        // Build list of fully qualified test method specifiers
        List<String> testSpecifiers = new ArrayList<>();
        for (String className : testClasses) {
            List<String> methods = findTestMethods(testPath, className);
            for (String method : methods) {
                testSpecifiers.add(className + "#" + method);
            }
        }
        
        if (!quiet) System.out.println("      Found " + testClasses.size() + " test class(es) with "
                + testSpecifiers.size() + " test method(s)");
        if (!quiet) System.err.printf("[javelin] Discovered %d test method(s), executing...%n", testSpecifiers.size());

        if (testSpecifiers.isEmpty()) {
            System.err.println("      WARNING: No test methods found");
            return new ArrayList<>();
        }

        if (!quiet) System.out.println("      Executing all tests in single JVM fork" + (offlineMode ? " (offline instrumentation)" : "") + "...");

        // In offline mode, pre-instrument a copy of the target classes into a temp dir
        // and substitute it on the classpath so no -javaagent is needed.
        // 4.2: The original targetPath is NEVER modified — instrumentation is done in a
        // separate temp directory, so no backup/restore is required.
        Path instrumentedTempDir = null;
        Path effectiveTargetPath = targetPath;
        try {
            if (offlineMode) {
                if (!quiet) System.out.println("      Instrumenting classes offline...");
                OfflineInstrumenter offlineInstrumenter = new OfflineInstrumenter();
                instrumentedTempDir = offlineInstrumenter.instrumentIntoTempDir(targetPath);
                effectiveTargetPath = instrumentedTempDir;
                if (!quiet) System.out.println("      Offline-instrumented classes written to: " + effectiveTargetPath);
            }

            // Build arguments for SingleJvmTestRunner
            List<String> javaArgs = buildSingleJvmRunnerArgs(classpath, testSpecifiers, effectiveTargetPath);

            // Execute single JVM process with all tests
            ProcessExecutor.ExecutionResult result = processExecutor.executeJava(
                    javaArgs,
                    Path.of(System.getProperty("user.dir")),
                    null,
                    600 // 10 minute timeout for all tests
            );

            // Print output
            if (!quiet && !result.stdout().isBlank()) {
                System.out.println(result.stdout());
            }
            if (!result.stderr().isBlank()) {
                // Filter line-by-line: only suppress JVM delegation warnings, not all errors
                for (String line : result.stderr().split("\\R")) {
                    if (!line.isBlank() && !line.contains("WARNING: Delegated")) {
                        System.err.println(line);
                    }
                }
            }

            // Check subprocess exit code
            if (result.timedOut()) {
                System.err.println("      ERROR: Subprocess timed out after 600 seconds");
            } else if (result.exitCode() != 0 && result.exitCode() != 1) {
                // 0 = all tests passed, 1 = some tests failed (both are valid)
                System.err.println("      WARNING: Subprocess exited with unexpected code: " + result.exitCode());
            }

            // Read consolidated results file
            List<TestExecResult> results;
            try {
                results = SingleJvmTestRunner.readCombinedResultsFile(tempDir);
                if (!quiet) System.out.println("      Read " + results.size() + " result(s) from javelin-results.bin");
            } catch (IOException e) {
                System.err.println("      ERROR: Could not read results file: " + e.getMessage());
                results = new ArrayList<>();
            }

            long passedCount = results.stream().filter(TestExecResult::passed).count();
            long failedCount = results.size() - passedCount;
            if (!quiet) System.out.println("      Total: " + results.size() + " test(s) - " + passedCount + " passed, " + failedCount + " failed");
            System.err.printf("[javelin] Tests complete: %d passed, %d failed.%n", passedCount, failedCount);

            return results;
        } finally {
            // 4.1: Proactively clean up the instrumented temp dir on both normal exit and
            // exceptions (e.g., IOException from processExecutor). The shutdown hook
            // registered in instrumentIntoTempDir() still covers hard JVM crashes.
            deleteInstrumentedTempDir(instrumentedTempDir);
        }
    }

    /**
     * Builds Java arguments for the SingleJvmTestRunner.
     *
     * In online mode, prepends the JaCoCo -javaagent flag.
     * In offline mode, the target classes are already instrumented; the JaCoCo runtime
     * JAR is already on the classpath (via buildClasspath), so no -javaagent is needed.
     *
     * @param classpath       the base classpath (built from the original targetPath)
     * @param testSpecifiers  list of className#methodName test specifiers
     * @param effectiveTarget the directory containing (possibly instrumented) target classes;
     *                        in offline mode this differs from targetPath
     */
    private List<String> buildSingleJvmRunnerArgs(String classpath, List<String> testSpecifiers, Path effectiveTarget) {
        List<String> args = new ArrayList<>();

        if (offlineMode) {
            // Rebuild classpath: replace original targetPath entry with the instrumented dir.
            // Do not use String.replace() — it produces a double separator when targetPath is
            // at the start of the classpath (replaces to empty string, leaving "::").
            String separator = ProcessExecutor.getPathSeparator();
            String targetAbsolute = targetPath.toAbsolutePath().toString();
            StringBuilder instrumentedCp = new StringBuilder(effectiveTarget.toAbsolutePath().toString());
            for (String entry : classpath.split(java.util.regex.Pattern.quote(separator), -1)) {
                if (!entry.isBlank() && !entry.equals(targetAbsolute)) {
                    instrumentedCp.append(separator).append(entry);
                }
            }
            // 4.3: Explicitly add the JaCoCo runtime JAR so the Offline class is loadable
            // regardless of whether Javelin is invoked from a fat JAR or from source.
            // The shutdown hook registered by setupTempDirectory() keeps jacocoAgentJar alive
            // until after the forked JVM process exits.
            if (jacocoAgentJar != null && Files.exists(jacocoAgentJar)) {
                instrumentedCp.append(separator).append(jacocoAgentJar.toAbsolutePath());
            }
            // Signal offline mode and provide the obfuscated Offline class name to the forked JVM
            args.add("-Djavelin.offline=true");
            args.add("-Djacoco-agent.destfile=" + tempDir.resolve("jacoco-all.exec").toAbsolutePath());
            String offlineClass = findOfflineRuntimeClassName();
            if (offlineClass != null) {
                args.add("-Djavelin.offline.class=" + offlineClass);
            }
            args.add("-cp");
            args.add(ProcessExecutor.shortenClasspathIfNeeded(instrumentedCp.toString(), tempDir));
        } else {
            // Online mode: attach JaCoCo agent
            // The agent destfile is not used directly; per-test .exec files are written by the listener
            String jacocoAgent = String.format(
                    "-javaagent:%s=destfile=%s,includes=*,excludes=org.junit.*:org.jacoco.*",
                    jacocoAgentJar.toAbsolutePath(),
                    tempDir.resolve("jacoco-all.exec").toAbsolutePath()
            );
            args.add(jacocoAgent);
            args.add("-cp");
            args.add(ProcessExecutor.shortenClasspathIfNeeded(classpath, tempDir));
        }
        
        // Main class: SingleJvmTestRunner
        args.add("com.javelin.core.execution.SingleJvmTestRunner");
        
        // Output directory argument
        args.add("--output");
        args.add(tempDir.toAbsolutePath().toString());
        
        // Write test specifiers to a file and pass via --tests-file (avoids
        // Windows CreateProcess error=206 on large projects like JFreeChart).
        Path testsFile = tempDir.resolve("test-specifiers.txt");
        try {
            Files.write(testsFile, testSpecifiers, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(
                    "Failed to write test specifiers file: " + testsFile, e);
        }
        args.add("--tests-file");
        args.add(testsFile.toAbsolutePath().toString());

        return args;
    }

    /**
     * Scans the extracted jacocoagent.jar to find the obfuscated internal Offline class name.
     * JaCoCo shades its runtime with a version-specific hash in the package name, e.g.:
     *   org/jacoco/agent/rt/internal_3a46b200/Offline.class
     * The class name is needed so JavelinTestListener can access the RuntimeData in offline mode.
     *
     * @return dotted class name (e.g. org.jacoco.agent.rt.internal_3a46b200.Offline), or null if not found
     */
    private String findOfflineRuntimeClassName() {
        if (jacocoAgentJar == null || !Files.exists(jacocoAgentJar)) {
            return null;
        }
        try (JarFile jar = new JarFile(jacocoAgentJar.toFile())) {
            return jar.stream()
                    .map(java.util.jar.JarEntry::getName)
                    .filter(n -> n.endsWith("/Offline.class") && n.startsWith("org/jacoco/agent/rt/"))
                    .findFirst()
                    .map(n -> n.replace('/', '.').replace(".class", ""))
                    .orElse(null);
        } catch (IOException e) {
            System.err.println("      WARNING: Could not scan jacocoagent.jar for Offline class: " + e.getMessage());
            return null;
        }
    }


    /**
      sets up a temporary directory for execution artifacts
     */
    private void setupTempDirectory() throws IOException {
        tempDir = Files.createTempDirectory("javelin-coverage-");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (tempDir != null && Files.exists(tempDir)) {
                    Files.walk(tempDir)
                         .sorted((a, b) -> -a.compareTo(b)) //reverse order for deletion
                         .forEach(path -> {
                             try {
                                 Files.deleteIfExists(path);
                             } catch (IOException ignored) {
                             }
                         });
                }
            } catch (IOException ignored) {
            }
        }));
    }

    /**
      extracts the jacoco agent JAR from the classpath to the temp directory
      agent is provided by the org.jacoco:org.jacoco.agent:runtime dependency
     */
    private void extractJacocoAgent() throws IOException {
        jacocoAgentJar = tempDir.resolve("jacocoagent.jar");
        
        //strategy 1: search for the runtime agent jar in classpath
        Path agentPath = findJacocoAgentRuntimeJar();
        if (agentPath != null) {
            Files.copy(agentPath, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
            if (!quiet) System.out.println("      Using JaCoCo agent: " + agentPath);
            return;
        }

        //strategy 2: extract from org.jacoco.agent JAR's internal jacocoagent.jar
        URL agentUrl = findJacocoAgentFromResources();
        if (agentUrl != null) {
            try (InputStream is = agentUrl.openStream()) {
                Files.copy(is, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
                if (!quiet) System.out.println("      Extracted JaCoCo agent from resources");
                return;
            }
        }

        //strategy 3: extract from fat JAR (jacocoagent.jar at root level)
        URL fatJarAgentUrl = getClass().getClassLoader().getResource("jacocoagent.jar");
        if (fatJarAgentUrl != null) {
            try (InputStream is = fatJarAgentUrl.openStream()) {
                Files.copy(is, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
                if (!quiet) System.out.println("      Extracted JaCoCo agent from fat JAR");
                return;
            }
        }

        throw new IOException(
            "JaCoCo agent JAR not found. Ensure 'org.jacoco:org.jacoco.agent:0.8.12:runtime' is in dependencies."
        );
    }

    /**
      finds jacoco agent runtime jar from gradles resolved dependencies
      looks for files matching: "org.jacoco.agent-*-runtime.jar"
     */
    private Path findJacocoAgentRuntimeJar() {
        String classpath = System.getProperty("java.class.path");
        String separator = ProcessExecutor.getPathSeparator();
        
        for (String entry : classpath.split(separator)) {
            if (entry.contains("org.jacoco.agent") && entry.contains("-runtime.jar")) { //org.jacoco.agent-0.8.12-runtime.jar
                Path jarPath = Path.of(entry);
                if (Files.exists(jarPath)) {
                    return jarPath;
                }
            }
        }
        return null;
    }

    /**
      extracts jacocoagent.jar embedded inside org.jacoco.agent jar
      the agent dependency packages the actual agent as an internal resource
     */
    private URL findJacocoAgentFromResources() {
        try {
            String classpath = System.getProperty("java.class.path");
            String separator = ProcessExecutor.getPathSeparator();
            
            for (String entry : classpath.split(separator)) {
                if (entry.contains("org.jacoco.agent") && entry.endsWith(".jar")) {
                    Path jarPath = Path.of(entry);
                    if (Files.exists(jarPath)) {
                        try (JarFile jar = new JarFile(jarPath.toFile())) {
                            if (jar.getEntry("jacocoagent.jar") != null) {
                                URI jarUri = URI.create("jar:" + jarPath.toUri() + "!/jacocoagent.jar");
                                return jarUri.toURL();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("      Warning: Could not extract agent from resources: " + e.getMessage());
        }
        return null;
    }

    /**
     * builds the classpath string including all necessary jars
     */
    private String buildClasspath() {
        String separator = ProcessExecutor.getPathSeparator();
        StringBuilder cp = new StringBuilder();

        cp.append(targetPath.toAbsolutePath());
        cp.append(separator).append(testPath.toAbsolutePath());

        //includes JUnit Platform - convert to absolute paths for subprocess compatibility
        String javelinClasspath = System.getProperty("java.class.path");
        if (javelinClasspath != null && !javelinClasspath.isBlank()) {
            for (String entry : javelinClasspath.split(separator)) {
                if (!entry.isBlank()) {
                    Path entryPath = Path.of(entry);
                    cp.append(separator).append(entryPath.toAbsolutePath());
                }
            }
        }

        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            cp.append(separator).append(additionalClasspath);
        }

        return cp.toString();
    }

    /**
     * 4.1: Proactively deletes the instrumented temp directory created by
     * {@link OfflineInstrumenter#instrumentIntoTempDir}. Called from a finally block in
     * {@link #run()} so cleanup happens immediately on normal exit or exception, without
     * having to wait for JVM shutdown (the shutdown hook in OfflineInstrumenter is the
     * last-resort safety net for hard crashes / SIGKILL).
     *
     * @param dir the instrumented temp directory to delete, or null (no-op)
     */
    private void deleteInstrumentedTempDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("      WARNING: Could not clean up instrumented temp dir " + dir + ": " + e.getMessage());
        }
    }

    /**
     * returns path to the temp directory (for testing)
     */
    public Path getTempDir() {
        return tempDir;
    }
}