package com.javelin.core.math;

import com.javelin.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MutationScoreCalculatorTest {

    private final MutationScoreCalculator calc = new MutationScoreCalculator();

    @Test
    void passingTest_killsAllReachable_scoreIsOne() {
        MutantInfo m = new MutantInfo("com.Foo:10:NEG:0", "com.Foo", 10, "KILLED");
        MutationData mutationData = new MutationData(List.of(m), Map.of("T#pass", Set.of(m.mutantId())));

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(10)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertEquals(1.0, scores.get("T#pass"), 1e-9);
    }

    @Test
    void passingTest_killsNone_scoreIsZero() {
        MutantInfo m = new MutantInfo("com.Foo:10:NEG:0", "com.Foo", 10, "SURVIVED");
        MutationData mutationData = new MutationData(List.of(m), Map.of());

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(10)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertEquals(0.0, scores.get("T#pass"), 1e-9);
    }

    @Test
    void passingTest_noReachableMutants_scoreIsZero() {
        // Test covers a line with no mutants
        MutationData mutationData = new MutationData(List.of(), Map.of());

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(99)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertEquals(0.0, scores.get("T#pass"), 1e-9);
    }

    @Test
    void failingTest_notIncludedInScores() {
        MutationData mutationData = new MutationData(List.of(), Map.of());

        Map<String, TestResult> testResults = Map.of("T#fail", TestResult.failed("T#fail", "err"));
        CoverageData cd = new CoverageData(testResults, Map.of(), Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertFalse(scores.containsKey("T#fail"), "failing tests should not have mutation scores");
    }

    @Test
    void noCoverageStatus_excluded_fromReachable() {
        // NO_COVERAGE mutants should not count as reachable
        MutantInfo noCov = new MutantInfo("com.Foo:10:NEG:0", "com.Foo", 10, "NO_COVERAGE");
        MutationData mutationData = new MutationData(List.of(noCov), Map.of());

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(10)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        // No reachable mutants (NO_COVERAGE excluded) → score = 0.0
        assertEquals(0.0, scores.get("T#pass"), 1e-9);
    }

    @Test
    void partialKills_scoreIsRatio() {
        MutantInfo m1 = new MutantInfo("com.Foo:10:NEG:0", "com.Foo", 10, "KILLED");
        MutantInfo m2 = new MutantInfo("com.Foo:10:AOR:1", "com.Foo", 10, "SURVIVED");
        MutationData mutationData = new MutationData(List.of(m1, m2), Map.of("T#pass", Set.of(m1.mutantId())));

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(10)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertEquals(0.5, scores.get("T#pass"), 1e-9, "1 killed out of 2 reachable = 0.5");
    }

    @Test
    void timedOutMutant_countedAsKilled() {
        MutantInfo m = new MutantInfo("com.Foo:5:NEG:0", "com.Foo", 5, "TIMED_OUT");
        MutationData mutationData = new MutationData(List.of(m), Map.of("T#pass", Set.of(m.mutantId())));

        Map<String, TestResult> testResults = Map.of("T#pass", TestResult.passed("T#pass"));
        Map<String, Map<String, Set<Integer>>> cov = Map.of("T#pass", Map.of("com.Foo", Set.of(5)));
        CoverageData cd = new CoverageData(testResults, cov, Set.of());

        Map<String, Double> scores = calc.calculate(mutationData, cd);

        assertEquals(1.0, scores.get("T#pass"), 1e-9, "TIMED_OUT should count as killed");
    }
}
