package com.javelin.core.model;

import java.util.Map;
import java.util.Set;

/**
  Coverage Data Model
  
  holds the parsed coverage information from JaCoCo execution
  
  components:
  - Test Results: map of test ID to TestResult (pass/fail)
  - Coverage Per Test: map of test ID to class to lines coverage
  - All Line Coverage: set of all tracked lines with coverage status
 */
public record CoverageData(
        /**
         map of test identifier to its result (pass/fail)
         key: test class name or method identifier
         value: TestResult containing pass/fail status and optional failure message
         */
        Map<String, TestResult> testResults,

        /**
         per test coverage mapping
         key: test identifier
         value: map of class name to set of covered line numbers
         */
        Map<String, Map<String, Set<Integer>>> coveragePerTest,

        /**
         set of all lines tracked during coverage analysis
         includes both covered and uncovered executable lines
         */
        Set<LineCoverage> allLineCoverage
) {
    /**
     returns total number of tests executed
     */
    public int getTestCount() {
        return testResults.size();
    }

    /**
     returns number of passed tests
     */
    public int getPassedCount() {
        return (int) testResults.values().stream()
                .filter(TestResult::passed)
                .count();
    }

    /**
     returns number of failed tests
     */
    public int getFailedCount() {
        return (int) testResults.values().stream()
                .filter(r -> !r.passed())
                .count();
    }

    /**
     returns total number of unique lines tracked
     */
    public int getTotalLinesTracked() {
        return allLineCoverage.size();
    }

    /**
     returns number of lines that were covered by at least one test
     */
    public int getCoveredLineCount() {
        return (int) allLineCoverage.stream()
                .filter(LineCoverage::covered)
                .count();
    }

    /**
     gets test results map
     */
    public Map<String, TestResult> getTestResults() {
        return testResults;
    }

    /**
     gets per test coverage map
     */
    public Map<String, Map<String, Set<Integer>>> getCoveragePerTest() {
        return coveragePerTest;
    }

    /**
     gets set of all line coverage data
     */
    public Set<LineCoverage> getAllLineCoverage() {
        return allLineCoverage;
    }
}
