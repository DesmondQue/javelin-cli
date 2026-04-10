package com.javelin.core.execution;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * - jacoci agent is attached via -javaagent argument
 * - supports both JUnit 4 (Vintage) and JUnit 5 (Jupiter) tests
 */
public class CoverageRunner {

    private final Path targetPath;
    private final Path testPath;
    private final String additionalClasspath;
    private final ProcessExecutor processExecutor;

    private Path tempDir;
    private Path jacocoAgentJar;

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath) {
        this.targetPath = targetPath;
        this.testPath = testPath;
        this.additionalClasspath = additionalClasspath;
        this.processExecutor = new ProcessExecutor();
    }

    /**
     * @deprecated threadCount is no longer used since all tests run in a single JVM.
     *             Use {@link #CoverageRunner(Path, Path, String)} instead.
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
                if (fileName.endsWith("Test.class") || fileName.endsWith("Tests.class")) {
                    // Convert file path to fully qualified class name
                    Path relativePath = dir.relativize(file);
                    String className = relativePath.toString()
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
     * Discovers test methods within a test class using ASM bytecode analysis.
     * Looks for methods annotated with @Test (JUnit 4 or JUnit 5).
     *
     * @param testDir   the directory containing test classes
     * @param className the fully qualified class name
     * @return List of method names annotated with @Test
     * @throws IOException if class file cannot be read
     */
    private List<String> findTestMethods(Path testDir, String className) throws IOException {
        List<String> testMethods = new ArrayList<>();
        String classFilePath = className.replace('.', java.io.File.separatorChar) + ".class";
        Path classFile = testDir.resolve(classFilePath);
        
        if (!Files.exists(classFile)) {
            classFile = testDir.resolve(className + ".class");
        }
        
        if (!Files.exists(classFile)) {
            System.err.println("      WARNING: Could not find class file for " + className);
            return testMethods;
        }
        
        try (FileInputStream fis = new FileInputStream(classFile.toFile())) {
            ClassReader classReader = new ClassReader(fis);
            
            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    // Return a MethodVisitor that checks for @Test annotation
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if (annotationDescriptor.equals("Lorg/junit/jupiter/api/Test;") ||
                                annotationDescriptor.equals("Lorg/junit/Test;")) {
                                testMethods.add(name);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        
        return testMethods;
    }

    /**
     * Runs all tests with JaCoCo coverage instrumentation in a SINGLE JVM process.
     * Uses JavelinTestListener to capture per-test coverage data via JaCoCo Runtime API.
     * 
     * This is significantly faster than the previous approach of forking a new JVM per test,
     * following the GZoltar approach of in-process coverage collection.
     *
     * @return List of TestExecResult containing coverage files and pass/fail status
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
        
        System.out.println("      Found " + testClasses.size() + " test class(es) with " 
                + testSpecifiers.size() + " test method(s)");
        
        if (testSpecifiers.isEmpty()) {
            System.err.println("      WARNING: No test methods found");
            return new ArrayList<>();
        }
        
        System.out.println("      Executing all tests in single JVM fork...");
        
        // Build arguments for SingleJvmTestRunner
        List<String> javaArgs = buildSingleJvmRunnerArgs(classpath, testSpecifiers);
        
        // Execute single JVM process with all tests
        ProcessExecutor.ExecutionResult result = processExecutor.executeJava(
                javaArgs, 
                tempDir, 
                null,
                600 // 10 minute timeout for all tests
        );
        
        // Print output
        if (!result.stdout().isBlank()) {
            System.out.println(result.stdout());
        }
        if (!result.stderr().isBlank() && !result.stderr().contains("WARNING: Delegated")) {
            System.err.println(result.stderr());
        }
        
        // Collect results from output directory
        List<TestExecResult> results = collectResults(testSpecifiers);
        
        long passedCount = results.stream().filter(TestExecResult::passed).count();
        long failedCount = results.size() - passedCount;
        System.out.println("      Total: " + results.size() + " test(s) - " + passedCount + " passed, " + failedCount + " failed");
        
        return results;
    }

    /**
     * Builds Java arguments for the SingleJvmTestRunner.
     */
    private List<String> buildSingleJvmRunnerArgs(String classpath, List<String> testSpecifiers) {
        List<String> args = new ArrayList<>();

        // JaCoCo agent - note: coverage is collected per-test via JavelinTestListener
        // The agent destfile is not used directly; per-test .exec files are written by the listener
        String jacocoAgent = String.format(
                "-javaagent:%s=destfile=%s,includes=*,excludes=org.junit.*:org.jacoco.*",
                jacocoAgentJar.toAbsolutePath(),
                tempDir.resolve("jacoco-all.exec").toAbsolutePath()
        );
        args.add(jacocoAgent);
        
        args.add("-cp");
        args.add(classpath);
        
        // Main class: SingleJvmTestRunner
        args.add("com.javelin.core.execution.SingleJvmTestRunner");
        
        // Output directory argument
        args.add("--output");
        args.add(tempDir.toAbsolutePath().toString());
        
        // Test specifiers
        args.add("--tests");
        args.addAll(testSpecifiers);

        return args;
    }

    /**
     * Collects test results and maps them to TestExecResult objects.
     */
    private List<TestExecResult> collectResults(List<String> testSpecifiers) {
        List<TestExecResult> results = new ArrayList<>();
        
        // Try to read the serialized results file
        Map<String, Boolean> testResults = null;
        try {
            testResults = SingleJvmTestRunner.readResultsFile(tempDir);
        } catch (Exception e) {
            System.err.println("      WARNING: Could not read test results file: " + e.getMessage());
        }
        
        for (String specifier : testSpecifiers) {
            // Parse className#methodName
            String[] parts = specifier.split("#");
            String className = parts[0];
            String methodName = parts.length > 1 ? parts[1] : "";
            
            String simpleClassName = className.contains(".") 
                    ? className.substring(className.lastIndexOf('.') + 1) 
                    : className;
            String testId = simpleClassName + "#" + methodName;
            
            // Find the corresponding .exec file
            String safeFileName = testId.replace("#", "_").replace(".", "_");
            Path execFile = tempDir.resolve("jacoco-" + safeFileName + ".exec");
            
            if (Files.exists(execFile)) {
                boolean passed = testResults != null && testResults.getOrDefault(testId, false);
                results.add(new TestExecResult(testId, execFile, passed, passed ? 0 : 1));
                System.out.println("        " + testId + ": " + (passed ? "PASSED" : "FAILED"));
            } else {
                // Try alternate naming patterns
                Path altExecFile = tempDir.resolve("jacoco-" + simpleClassName + "_" + methodName + ".exec");
                if (Files.exists(altExecFile)) {
                    boolean passed = testResults != null && testResults.getOrDefault(testId, false);
                    results.add(new TestExecResult(testId, altExecFile, passed, passed ? 0 : 1));
                    System.out.println("        " + testId + ": " + (passed ? "PASSED" : "FAILED"));
                } else {
                    System.err.println("      WARNING: No coverage file found for " + testId);
                }
            }
        }
        
        return results;
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
            System.out.println("      Using JaCoCo agent: " + agentPath);
            return;
        }

        //strategy 2: extract from org.jacoco.agent JAR's internal jacocoagent.jar
        URL agentUrl = findJacocoAgentFromResources();
        if (agentUrl != null) {
            try (InputStream is = agentUrl.openStream()) {
                Files.copy(is, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("      Extracted JaCoCo agent from resources");
                return;
            }
        }

        //strategy 3: extract from fat JAR (jacocoagent.jar at root level)
        URL fatJarAgentUrl = getClass().getClassLoader().getResource("jacocoagent.jar");
        if (fatJarAgentUrl != null) {
            try (InputStream is = fatJarAgentUrl.openStream()) {
                Files.copy(is, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("      Extracted JaCoCo agent from fat JAR");
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
     * returns path to the temp directory (for testing)
     */
    public Path getTempDir() {
        return tempDir;
    }
}