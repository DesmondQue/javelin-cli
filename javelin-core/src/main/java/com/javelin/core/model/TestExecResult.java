package com.javelin.core.model;

import java.nio.file.Path;

/**
 * Represents the result of executing a single test class with coverage instrumentation.
 * Captures both the coverage data file path and the test pass/fail status.
 *
 * @param testClassName the simple name of the test class (e.g., "CalculatorTest")
 * @param execFile      path to the generated jacoco-<ClassName>.exec coverage file
 * @param passed        true if all tests in the class passed (exit code 0), false otherwise
 * @param exitCode      the raw exit code from JUnit execution
 */
public record TestExecResult(
        String testClassName,
        Path execFile,
        boolean passed,
        int exitCode
) {
    /**
     * Creates a TestExecResult from execution data.
     * JUnit Console Launcher returns exit code 0 for success, non-zero for failures.
     *
     * @param testClassName the test class name
     * @param execFile      the coverage file path
     * @param exitCode      the JUnit exit code
     * @return a new TestExecResult with passed derived from exit code
     */
    public static TestExecResult fromExitCode(String testClassName, Path execFile, int exitCode) {
        return new TestExecResult(testClassName, execFile, exitCode == 0, exitCode);
    }
}
