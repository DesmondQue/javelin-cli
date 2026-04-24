package com.javelin.core.model;

public final class ExitCode {
    private ExitCode() {}

    public static final int SUCCESS          = 0;
    public static final int GENERAL_ERROR    = 1;
    public static final int NO_FAILING_TESTS = 2;
    public static final int TARGET_NOT_FOUND = 3;
    public static final int TEST_NOT_FOUND   = 4;
    public static final int COVERAGE_FAILED  = 5;
    public static final int MUTATION_FAILED  = 6;
    public static final int OUTPUT_WRITE_ERROR = 7;
}
