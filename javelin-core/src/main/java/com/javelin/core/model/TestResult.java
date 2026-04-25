package com.javelin.core.model;

import java.util.Objects;

public final class TestResult {
    private final String testId;
    private final boolean passed;
    private final String failureMessage;

    public TestResult(String testId, boolean passed, String failureMessage) {
        this.testId = testId;
        this.passed = passed;
        this.failureMessage = failureMessage;
    }

    public String testId() { return testId; }
    public boolean passed() { return passed; }
    public String failureMessage() { return failureMessage; }

    public static TestResult passed(String testId) {
        return new TestResult(testId, true, null);
    }

    public static TestResult failed(String testId, String failureMessage) {
        return new TestResult(testId, false, failureMessage);
    }

    public static TestResult failed(String testId, Throwable exception) {
        String message = exception.getClass().getName();
        if (exception.getMessage() != null) {
            message += ": " + exception.getMessage();
        }
        return new TestResult(testId, false, message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestResult that = (TestResult) o;
        return passed == that.passed
                && Objects.equals(testId, that.testId)
                && Objects.equals(failureMessage, that.failureMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testId, passed, failureMessage);
    }

    @Override
    public String toString() {
        return "TestResult[testId=" + testId + ", passed=" + passed + ", failureMessage=" + failureMessage + "]";
    }
}
