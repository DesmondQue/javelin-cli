package com.javelin.core.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
  Process Executor (Utility)
  
  Responsibilities:
  - low level logic that abstracts OS differences
  - Windows: cmd.exe /c java ...
  - Linux: java ... 
 */
public class ProcessExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    private final Path overrideJavaHome; // null = use current JVM

    public ProcessExecutor() {
        this.overrideJavaHome = null;
    }

    public ProcessExecutor(Path jvmHome) {
        this.overrideJavaHome = jvmHome;
    }

    /**
      result of a process execution
     */
    public static final class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;

        public ExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }

        public int exitCode() { return exitCode; }
        public String stdout() { return stdout; }
        public String stderr() { return stderr; }
        public boolean timedOut() { return timedOut; }

        public boolean isSuccess() {
            return exitCode == 0 && !timedOut;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExecutionResult that = (ExecutionResult) o;
            return exitCode == that.exitCode && timedOut == that.timedOut
                    && java.util.Objects.equals(stdout, that.stdout)
                    && java.util.Objects.equals(stderr, that.stderr);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(exitCode, stdout, stderr, timedOut);
        }

        @Override
        public String toString() {
            return "ExecutionResult[exitCode=" + exitCode + ", stdout=" + stdout
                    + ", stderr=" + stderr + ", timedOut=" + timedOut + "]";
        }
    }

    /**
      executes a Java command in a new process.
     
      @param javaArgs     Arguments to pass to the java command (after 'java')
      @param workingDir   Working directory for the process
      @param environment  Additional environment variables (can be null)
      @return ExecutionResult containing exit code, stdout, stderr
     */
    public ExecutionResult executeJava(List<String> javaArgs, Path workingDir,
                                        Map<String, String> environment) {
        return executeJava(javaArgs, workingDir, environment, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
      executes a Java command in a new process with custom timeout.
     
      @param javaArgs       Arguments to pass to the java command (after 'java')
      @param workingDir     Working directory for the process
      @param environment    Additional environment variables (can be null)
      @param timeoutSeconds Maximum time to wait for process completion
      @return ExecutionResult containing exit code, stdout, stderr
     */
    public ExecutionResult executeJava(List<String> javaArgs, Path workingDir,
                                        Map<String, String> environment,
                                        long timeoutSeconds) {
        List<String> command = buildCommand(javaArgs);
        return execute(command, workingDir, environment, timeoutSeconds);
    }

    /**
      executes a generic command in a new process.
     
      @param command        The command and arguments to execute
      @param workingDir     Working directory for the process
      @param environment    Additional environment variables (can be null)
      @param timeoutSeconds Maximum time to wait for process completion
      @return ExecutionResult containing exit code, stdout, stderr
     */
    public ExecutionResult execute(List<String> command, Path workingDir,
                                    Map<String, String> environment,
                                    long timeoutSeconds) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }

        if (environment != null && !environment.isEmpty()) {
            processBuilder.environment().putAll(environment);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        boolean timedOut = false;
        int exitCode = -1;

        try {
            Process process = processBuilder.start();
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append(System.lineSeparator());
                    }
                } catch (IOException e) {
                    stderr.append("Error reading stdout: ").append(e.getMessage())
                          .append(System.lineSeparator());
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append(System.lineSeparator());
                    }
                } catch (IOException e) {
                    stderr.append("Error reading stderr: ").append(e.getMessage())
                          .append(System.lineSeparator());
                }
            });

            stdoutThread.start();
            stderrThread.start();

            timedOut = !process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (timedOut) {
                process.destroyForcibly();
                stderr.append("Process timed out after ")
                      .append(timeoutSeconds).append(" seconds")
                      .append(System.lineSeparator());
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

        } catch (IOException e) {
            stderr.append("Failed to start process: ").append(e.getMessage())
                  .append(System.lineSeparator());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stderr.append("Process interrupted: ").append(e.getMessage())
                  .append(System.lineSeparator());
        }

        return new ExecutionResult(exitCode, stdout.toString(), stderr.toString(), timedOut);
    }

    /**
      builds the OS specific command list
      Note: ProcessBuilder handles paths with spaces correctly, so we don't need cmd.exe wrapper
     */
    private List<String> buildCommand(List<String> javaArgs) {
        List<String> command = new ArrayList<>();
        
        command.add(getJavaExecutable());
        command.addAll(javaArgs);
        
        return command;
    }

    /**
      gets path to java executable.
      Uses overrideJavaHome if set, otherwise falls back to java.home system property.
     */
    private String getJavaExecutable() {
        Path homeToUse = overrideJavaHome;
        if (homeToUse == null) {
            String javaHome = System.getProperty("java.home");
            if (javaHome != null && !javaHome.isEmpty()) {
                homeToUse = Path.of(javaHome);
            }
        }
        if (homeToUse != null) {
            Path javaBin = homeToUse.resolve(Path.of("bin", IS_WINDOWS ? "java.exe" : "java"));
            if (javaBin.toFile().exists()) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    /**
      returns OS specific path separator
     */
    public static String getPathSeparator() {
        return System.getProperty("path.separator");
    }

    /**
      returns whether if process is running on Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    private static final int MAX_COMMAND_LENGTH = 8_000;

    /**
     * On Windows, wraps a long classpath into a temporary manifest JAR to avoid
     * CreateProcess error=206. On other OSes or for short classpaths, returns
     * the original classpath unchanged.
     *
     * @param classpath the full classpath string (OS path-separator delimited)
     * @param tempDir   directory in which to create the manifest JAR
     * @return the original classpath, or the path to the manifest JAR
     */
    public static String shortenClasspathIfNeeded(String classpath, Path tempDir) {
        if (!IS_WINDOWS || classpath.length() < MAX_COMMAND_LENGTH) {
            return classpath;
        }
        try {
            String separator = getPathSeparator();
            StringBuilder cpAttribute = new StringBuilder();
            for (String entry : classpath.split(java.util.regex.Pattern.quote(separator), -1)) {
                if (entry.isBlank()) continue;
                if (cpAttribute.length() > 0) cpAttribute.append(' ');
                URI uri = Path.of(entry).toAbsolutePath().toUri();
                cpAttribute.append(uri);
            }

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, cpAttribute.toString());

            Path jarFile = Files.createTempFile(tempDir, "javelin-cp-", ".jar");
            jarFile.toFile().deleteOnExit();
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
                // empty JAR -- manifest is all we need
            }
            return jarFile.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("      WARNING: Could not create classpath JAR, using long classpath: " + e.getMessage());
            return classpath;
        }
    }
}
