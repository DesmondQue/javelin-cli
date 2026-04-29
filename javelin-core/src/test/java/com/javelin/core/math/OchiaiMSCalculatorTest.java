package com.javelin.core.math;

import com.javelin.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OchiaiMSCalculatorTest {

    private final OchiaiMSCalculator calc = new OchiaiMSCalculator();

    // ── calculateOchiaiMSScore ────────────────────────────────────────────────

    @Test
    void score_zeroDiscounted_equalsStandardOchiai() {
        // discountedPassed=0 → denominator same as standard Ochiai
        OchiaiCalculator standard = new OchiaiCalculator();
        double ms      = calc.calculateOchiaiMSScore(1, 0.0, 1);
        double ochiai  = standard.calculateOchiaiScore(1, 0, 1);
        assertEquals(ochiai, ms, 1e-9);
    }

    @Test
    void score_higherDiscount_givesLowerScore() {
        double highMS = calc.calculateOchiaiMSScore(1, 1.0, 1);
        double lowMS  = calc.calculateOchiaiMSScore(1, 0.0, 1);
        assertTrue(highMS < lowMS, "stronger passing test should reduce suspiciousness");
    }

    @Test
    void score_zeroA11_isZero() {
        assertEquals(0.0, calc.calculateOchiaiMSScore(0, 0.5, 2), 1e-9);
    }

    @Test
    void score_zeroTotalFailed_isZero() {
        assertEquals(0.0, calc.calculateOchiaiMSScore(1, 0.5, 0), 1e-9);
    }

    @Test
    void score_clampedToOneMax() {
        assertTrue(calc.calculateOchiaiMSScore(100, 0.0, 100) <= 1.0);
    }

    // ── calculate (full matrix) ───────────────────────────────────────────────

    @Test
    void calculate_faultLineScoredHigher() {
        // Failing test covers line 10; passing test (weak, MS=0) covers line 20
        Map<String, TestResult> testResults = new HashMap<>();
        testResults.put("T#fail", TestResult.failed("T#fail", "err"));
        testResults.put("T#pass", TestResult.passed("T#pass"));

        Map<String, Map<String, Set<Integer>>> cov = new HashMap<>();
        cov.put("T#fail", Map.of("com.Foo", Set.of(10)));
        cov.put("T#pass", Map.of("com.Foo", Set.of(20)));

        CoverageData cd = new CoverageData(testResults, cov, Set.of(
                new LineCoverage("com.Foo", 10, true),
                new LineCoverage("com.Foo", 20, true)
        ));

        Map<String, int[]> lineCounts = new HashMap<>();
        lineCounts.put("com.Foo:10", new int[]{1, 0});
        lineCounts.put("com.Foo:20", new int[]{0, 1});
        SpectrumMatrix matrix = new SpectrumMatrix(lineCounts, 1, 1);

        List<SuspiciousnessResult> results = calc.calculate(matrix, cd, Map.of("T#pass", 0.0));

        assertEquals(10, results.get(0).lineNumber(), "fault line should be ranked first");
        assertEquals(1.0, results.get(0).rank(), 1e-9);
    }

    @Test
    void calculate_emptyMatrix_returnsEmpty() {
        CoverageData cd = new CoverageData(Map.of(), Map.of(), Set.of());
        List<SuspiciousnessResult> results = calc.calculate(
                new SpectrumMatrix(new HashMap<>(), 0, 0), cd, Map.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void calculate_denseRanksAssigned() {
        // Two lines with identical coverage pattern → same score → same rank
        Map<String, TestResult> testResults = Map.of("T#f", TestResult.failed("T#f", "e"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of(
                "T#f", Map.of("com.A", Set.of(1, 2)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of(
                new LineCoverage("com.A", 1, true),
                new LineCoverage("com.A", 2, true)
        ));

        Map<String, int[]> lines = new HashMap<>();
        lines.put("com.A:1", new int[]{1, 0});
        lines.put("com.A:2", new int[]{1, 0});
        SpectrumMatrix matrix = new SpectrumMatrix(lines, 1, 0);

        List<SuspiciousnessResult> results = calc.calculate(matrix, cd, Map.of());

        assertTrue(results.stream().allMatch(r -> r.rank() == 1.0), "tied scores should share rank 1");
    }
}
