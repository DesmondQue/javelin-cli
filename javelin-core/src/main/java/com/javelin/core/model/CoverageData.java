package com.javelin.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CoverageData {
    private final Map<String, TestResult> testResults;
    private final Map<String, Map<String, Set<Integer>>> coveragePerTest;
    private final Set<LineCoverage> allLineCoverage;
    private final Map<String, List<MethodInfo>> methodMapping;

    public CoverageData(Map<String, TestResult> testResults,
                        Map<String, Map<String, Set<Integer>>> coveragePerTest,
                        Set<LineCoverage> allLineCoverage,
                        Map<String, List<MethodInfo>> methodMapping) {
        this.testResults = testResults;
        this.coveragePerTest = coveragePerTest;
        this.allLineCoverage = allLineCoverage;
        this.methodMapping = methodMapping;
    }

    public CoverageData(Map<String, TestResult> testResults,
                        Map<String, Map<String, Set<Integer>>> coveragePerTest,
                        Set<LineCoverage> allLineCoverage) {
        this(testResults, coveragePerTest, allLineCoverage, Map.of());
    }

    public Map<String, TestResult> testResults() { return testResults; }
    public Map<String, Map<String, Set<Integer>>> coveragePerTest() { return coveragePerTest; }
    public Set<LineCoverage> allLineCoverage() { return allLineCoverage; }
    public Map<String, List<MethodInfo>> methodMapping() { return methodMapping; }

    public boolean hasMethodMapping() {
        return methodMapping != null && !methodMapping.isEmpty();
    }

    public int getTestCount() {
        return testResults.size();
    }

    public int getPassedCount() {
        return (int) testResults.values().stream()
                .filter(TestResult::passed)
                .count();
    }

    public int getFailedCount() {
        return (int) testResults.values().stream()
                .filter(r -> !r.passed())
                .count();
    }

    public int getTotalLinesTracked() {
        return allLineCoverage.size();
    }

    public int getCoveredLineCount() {
        return (int) allLineCoverage.stream()
                .filter(LineCoverage::covered)
                .count();
    }

    public Map<String, TestResult> getTestResults() {
        return testResults;
    }

    public Map<String, Map<String, Set<Integer>>> getCoveragePerTest() {
        return coveragePerTest;
    }

    public Set<LineCoverage> getAllLineCoverage() {
        return allLineCoverage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoverageData that = (CoverageData) o;
        return Objects.equals(testResults, that.testResults)
                && Objects.equals(coveragePerTest, that.coveragePerTest)
                && Objects.equals(allLineCoverage, that.allLineCoverage)
                && Objects.equals(methodMapping, that.methodMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testResults, coveragePerTest, allLineCoverage, methodMapping);
    }

    @Override
    public String toString() {
        return "CoverageData[testResults=" + testResults + ", coveragePerTest=" + coveragePerTest
                + ", allLineCoverage=" + allLineCoverage + ", methodMapping=" + methodMapping + "]";
    }
}
