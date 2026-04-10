package com.javelin.core.model;

/**
 Test Result Model
 
 represents the outcome of a single test execution
  
 @param testId         Unique identifier for the test (typically class.method)
 @param passed         Whether the test passed (true) or failed (false)
 @param failureMessage Optional failure message if the test failed
 */
public record TestResult(
        String testId,
        boolean passed,
        String failureMessage
) {
    /**
     * creates a passed test result
     */
    public static TestResult passed(String testId) {
        return new TestResult(testId, true, null);
    }

    /**
     * creates a failed test result
     */
    public static TestResult failed(String testId, String failureMessage) {
        return new TestResult(testId, false, failureMessage);
    }

    /**
     * creates a failed test result from an exception
     */
    public static TestResult failed(String testId, Throwable exception) {
        String message = exception.getClass().getName();
        if (exception.getMessage() != null) {
            message += ": " + exception.getMessage();
        }
        return new TestResult(testId, false, message);
    }
}
