package com.javelin.core.math;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.MutantInfo;
import com.javelin.core.model.MutationData;
import com.javelin.core.model.TestResult;

/**
 * Mutation Score Calculator
 *
 * Computes MS(t) — the mutation score — for each passing test.
 *
 * For a passing test t:
 *   1. Reachable mutants = mutants on lines that t covers (excluding NO_COVERAGE mutants)
 *   2. Killed mutants    = reachable mutants that t actually killed (from kill matrix)
 *   3. MS(t)             = |killed| / |reachable|
 *
 * If a passing test has no reachable mutants, MS(t) = 0.0 (conservative assumption).
 *
 * The mutation score reflects how effective a passing test is at detecting faults.
 * A high MS(t) means the test killed most reachable mutants — it is a strong test.
 * A low MS(t) means the test covers code but fails to detect injected faults — it is weak.
 */
public class MutationScoreCalculator {

    /**
     * Computes mutation scores for all passing tests.
     *
     * @param mutationData parsed PITest results (mutants + kill matrix)
     * @param coverageData Phase 1 coverage data (per-test line coverage + test results)
     * @return map of testId → MS(t) for each passing test
     */
    public Map<String, Double> calculate(MutationData mutationData, CoverageData coverageData) {
        Map<String, Double> mutationScores = new HashMap<>();

        // Pre-index: build a lookup from lineKey → set of mutantIds for fast reachability checks
        Map<String, Set<String>> lineToMutants = buildLineToMutantIndex(mutationData);

        for (Map.Entry<String, TestResult> testEntry : coverageData.testResults().entrySet()) {
            String testId = testEntry.getKey();
            TestResult result = testEntry.getValue();

            // Only compute MS for passing tests
            if (!result.passed()) {
                continue;
            }

            // Step 1: Find reachable mutants — mutants on lines this test covers
            Set<String> reachableMutantIds = findReachableMutants(testId, coverageData, lineToMutants);

            if (reachableMutantIds.isEmpty()) {
                mutationScores.put(testId, 0.0);
                continue;
            }

            // Step 2: Find killed mutants — reachable mutants that this test killed
            Set<String> killedByTest = mutationData.killMatrix().getOrDefault(testId, Set.of());
            long killedReachable = reachableMutantIds.stream()
                    .filter(killedByTest::contains)
                    .count();

            // Step 3: MS(t) = |killed ∩ reachable| / |reachable|
            double ms = (double) killedReachable / reachableMutantIds.size();
            mutationScores.put(testId, ms);
        }

        return mutationScores;
    }

    /**
     * Builds an index from lineKey ("className:lineNumber") to the set of mutant IDs
     * located on that line. Excludes NO_COVERAGE mutants since they have no test interaction.
     */
    private Map<String, Set<String>> buildLineToMutantIndex(MutationData mutationData) {
        Map<String, Set<String>> index = new HashMap<>();

        for (MutantInfo mutant : mutationData.mutants()) {
            // Exclude NO_COVERAGE mutants — PITest already skipped them
            if (mutant.isNoCoverage()) {
                continue;
            }
            String lineKey = mutant.getLineKey();
            index.computeIfAbsent(lineKey, k -> new HashSet<>()).add(mutant.mutantId());
        }

        return index;
    }

    /**
     * Finds all mutant IDs that are reachable by a given test.
     * A mutant is reachable if it is on a line that the test covers.
     *
     * @param testId         Javelin test ID (e.g. "CalculatorTest#testAdd")
     * @param coverageData   Phase 1 coverage data
     * @param lineToMutants  pre-computed lineKey → mutantIds index
     * @return set of reachable mutant IDs
     */
    private Set<String> findReachableMutants(String testId, CoverageData coverageData,
                                              Map<String, Set<String>> lineToMutants) {
        Set<String> reachable = new HashSet<>();

        Map<String, Set<Integer>> testCoverage = coverageData.coveragePerTest().get(testId);
        if (testCoverage == null) {
            return reachable;
        }

        for (Map.Entry<String, Set<Integer>> classCov : testCoverage.entrySet()) {
            String className = classCov.getKey();
            for (int lineNumber : classCov.getValue()) {
                String lineKey = className + ":" + lineNumber;
                Set<String> mutantsOnLine = lineToMutants.get(lineKey);
                if (mutantsOnLine != null) {
                    reachable.addAll(mutantsOnLine);
                }
            }
        }

        return reachable;
    }
}
