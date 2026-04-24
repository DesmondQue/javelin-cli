package com.javelin.core.parsing;

import com.javelin.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatrixBuilderTest {

    private final MatrixBuilder builder = new MatrixBuilder();

    @Test
    void build_failedTestCoveringLine_incrementsA11() {
        Map<String, TestResult> testResults = Map.of("T#fail", TestResult.failed("T#fail", "err"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#fail", Map.of("com.Foo", Set.of(10)));
        CoverageData cd = new CoverageData(testResults, cov,
                Set.of(new LineCoverage("com.Foo", 10, true)));

        SpectrumMatrix matrix = builder.build(cd);

        assertEquals(1, matrix.getA11("com.Foo:10"));
        assertEquals(0, matrix.getA10("com.Foo:10"));
    }

    @Test
    void build_passedTestCoveringLine_incrementsA10() {
        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(20)));
        CoverageData cd = new CoverageData(testResults, cov,
                Set.of(new LineCoverage("com.Foo", 20, true)));

        SpectrumMatrix matrix = builder.build(cd);

        assertEquals(0, matrix.getA11("com.Foo:20"));
        assertEquals(1, matrix.getA10("com.Foo:20"));
    }

    @Test
    void build_totalFailedAndPassedCounted() {
        Map<String, TestResult> testResults = new HashMap<>();
        testResults.put("T#f1", TestResult.failed("T#f1", "e"));
        testResults.put("T#f2", TestResult.failed("T#f2", "e"));
        testResults.put("T#p1", TestResult.passed("T#p1"));
        CoverageData cd = new CoverageData(testResults, Map.of(), Set.of());

        SpectrumMatrix matrix = builder.build(cd);

        assertEquals(2, matrix.totalFailed());
        assertEquals(1, matrix.totalPassed());
    }

    @Test
    void build_uncoveredLine_inMatrix_withZeroCounts() {
        // allLineCoverage includes a line, but no test covers it
        Map<String, TestResult> testResults = Map.of("T#f", TestResult.failed("T#f", "e"));
        CoverageData cd = new CoverageData(testResults, Map.of(),
                Set.of(new LineCoverage("com.Bar", 5, false)));

        SpectrumMatrix matrix = builder.build(cd);

        int[] counts = matrix.getCountsForLine("com.Bar:5");
        assertNotNull(counts);
        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
    }

    @Test
    void build_emptyData_emptyMatrix() {
        CoverageData cd = new CoverageData(Map.of(), Map.of(), Set.of());

        SpectrumMatrix matrix = builder.build(cd);

        assertEquals(0, matrix.getLineCount());
        assertEquals(0, matrix.totalFailed());
    }

    @Test
    void build_multipleTestsMixedCoverage() {
        Map<String, TestResult> testResults = new HashMap<>();
        testResults.put("T#fail", TestResult.failed("T#fail", "e"));
        testResults.put("T#pass", TestResult.passed("T#pass"));

        Map<String, Map<String, Set<Integer>>> cov = new HashMap<>();
        cov.put("T#fail", Map.of("com.Foo", Set.of(1)));
        cov.put("T#pass", Map.of("com.Foo", Set.of(1)));

        CoverageData cd = new CoverageData(testResults, cov,
                Set.of(new LineCoverage("com.Foo", 1, true)));

        SpectrumMatrix matrix = builder.build(cd);

        assertEquals(1, matrix.getA11("com.Foo:1"), "one failing test covered line 1");
        assertEquals(1, matrix.getA10("com.Foo:1"), "one passing test covered line 1");
    }

    @Test
    void buildFromRaw_correctCounts() {
        Map<String, Boolean> testResults = Map.of("T#f", false, "T#p", true);
        Map<String, Set<String>> testCov = Map.of(
                "T#f", Set.of("L:1"),
                "T#p", Set.of("L:1", "L:2")
        );
        Set<String> allLines = Set.of("L:1", "L:2");

        SpectrumMatrix matrix = builder.buildFromRaw(testResults, testCov, allLines);

        assertEquals(1, matrix.getA11("L:1"));
        assertEquals(1, matrix.getA10("L:1"));
        assertEquals(0, matrix.getA11("L:2"));
        assertEquals(1, matrix.getA10("L:2"));
    }
}
