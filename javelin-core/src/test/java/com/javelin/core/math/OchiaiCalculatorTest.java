package com.javelin.core.math;

import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OchiaiCalculatorTest {

    private final OchiaiCalculator calc = new OchiaiCalculator();

    // ── calculateOchiaiScore ──────────────────────────────────────────────────

    @Test
    void score_perfectFault_isOne() {
        // a11=1, a10=0, nf=1 → 1/sqrt(1*1) = 1.0
        assertEquals(1.0, calc.calculateOchiaiScore(1, 0, 1), 1e-9);
    }

    @Test
    void score_noFailuresCoverLine_isZero() {
        assertEquals(0.0, calc.calculateOchiaiScore(0, 3, 2), 1e-9);
    }

    @Test
    void score_zeroTotalFailed_isZero() {
        assertEquals(0.0, calc.calculateOchiaiScore(0, 0, 0), 1e-9);
    }

    @Test
    void score_knownValue() {
        // a11=1, a10=1, nf=1 → 1/sqrt(1*2) ≈ 0.7071
        double expected = 1.0 / Math.sqrt(2.0);
        assertEquals(expected, calc.calculateOchiaiScore(1, 1, 1), 1e-9);
    }

    @Test
    void score_optimizedMatchesFourVariableFormula() {
        // a11=2, a10=1, a01=1 (nf=3)
        double optimized = calc.calculateOchiaiScore(2, 1, 3);
        double full      = calc.calculateOchiaiFull(2, 1, 1, 5);
        assertEquals(full, optimized, 1e-9);
    }

    @Test
    void score_clampedToOneMax() {
        // Large a11 relative to nf and a10=0 should stay ≤ 1.0
        assertTrue(calc.calculateOchiaiScore(100, 0, 100) <= 1.0);
    }

    // ── calculate (full matrix) ───────────────────────────────────────────────

    @Test
    void calculate_emptyMatrix_returnsEmpty() {
        SpectrumMatrix matrix = new SpectrumMatrix(new HashMap<>(), 0, 0);
        assertTrue(calc.calculate(matrix).isEmpty());
    }

    @Test
    void calculate_sortedDescending() {
        Map<String, int[]> lines = new HashMap<>();
        lines.put("com.example.Foo:1", new int[]{1, 1}); // lower score
        lines.put("com.example.Foo:2", new int[]{1, 0}); // higher score
        SpectrumMatrix matrix = new SpectrumMatrix(lines, 1, 1);

        List<SuspiciousnessResult> results = calc.calculate(matrix);

        assertTrue(results.get(0).score() >= results.get(1).score());
    }

    @Test
    void calculate_denseRankingTies() {
        Map<String, int[]> lines = new HashMap<>();
        lines.put("com.example.Foo:10", new int[]{1, 0}); // score 1.0
        lines.put("com.example.Foo:20", new int[]{1, 0}); // score 1.0
        lines.put("com.example.Foo:30", new int[]{0, 1}); // score 0.0
        SpectrumMatrix matrix = new SpectrumMatrix(lines, 1, 1);

        List<SuspiciousnessResult> results = calc.calculate(matrix);

        long rank1Count = results.stream().filter(r -> r.rank() == 1).count();
        long rank2Count = results.stream().filter(r -> r.rank() == 2).count();
        assertEquals(2, rank1Count, "both score-1.0 lines should be rank 1");
        assertEquals(1, rank2Count, "score-0.0 line should be rank 2");
    }

    @Test
    void calculate_ranksStartAtOne() {
        Map<String, int[]> lines = new HashMap<>();
        lines.put("com.example.Foo:1", new int[]{1, 0});
        SpectrumMatrix matrix = new SpectrumMatrix(lines, 1, 0);

        List<SuspiciousnessResult> results = calc.calculate(matrix);

        assertEquals(1, results.get(0).rank());
    }

    @Test
    void calculate_classAndLineNumberParsedCorrectly() {
        Map<String, int[]> lines = new HashMap<>();
        lines.put("com.example.Calculator:42", new int[]{1, 0});
        SpectrumMatrix matrix = new SpectrumMatrix(lines, 1, 0);

        SuspiciousnessResult r = calc.calculate(matrix).get(0);

        assertEquals("com.example.Calculator", r.fullyQualifiedClass());
        assertEquals(42, r.lineNumber());
    }
}
