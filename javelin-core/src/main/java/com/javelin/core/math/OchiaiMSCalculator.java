package com.javelin.core.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;
import com.javelin.core.model.TestResult;

/**
 * Ochiai-MS Calculator
 *
 * Computes Ochiai-MS suspiciousness scores using discounted_passed, where passing
 * tests are weighted by their mutation score MS(t) rather than counted as 1.
 *
 * Formula for each line s:
 *
 *   discounted_passed(s) = Σ MS(t) for each passing test t that covers line s
 *
 *   Score(s) = a11(s) / sqrt(totalFailed × (a11(s) + discounted_passed(s)))
 *
 * where:
 *   a11(s)             = failed tests that covered line s
 *   discounted_passed(s) = sum of MS(t) for passing tests covering s
 *   totalFailed        = total number of failing tests
 *
 * Lines with weak passing tests (low MS) get a higher discounted_passed
 * contribution and thus a higher suspiciousness score compared to standard Ochiai.
 */
public class OchiaiMSCalculator {

    /**
     * Calculates Ochiai-MS suspiciousness scores for all lines in the spectrum matrix.
     *
     * @param matrix         the spectrum matrix from MatrixBuilder
     * @param coverageData   Phase 1 coverage data (per-test line coverage + test results)
     * @param mutationScores testId → MS(t) from MutationScoreCalculator
     * @return list of SuspiciousnessResult, sorted by score descending, with dense ranks assigned
     */
    public List<SuspiciousnessResult> calculate(
            SpectrumMatrix matrix,
            CoverageData coverageData,
            Map<String, Double> mutationScores) {

        Map<String, int[]> lineCounts = matrix.lineCounts();
        int totalFailed = matrix.totalFailed();
        List<SuspiciousnessResult> results = new ArrayList<>();

        for (Map.Entry<String, int[]> entry : lineCounts.entrySet()) {
            String lineKey = entry.getKey();
            int[] counts = entry.getValue();
            int a11 = counts[0]; // failed & covered

            double discountedPassed = computeDiscountedPassed(lineKey, coverageData, mutationScores);

            double score = calculateOchiaiMSScore(a11, discountedPassed, totalFailed);

            int separatorIndex = lineKey.lastIndexOf(':');
            String className = lineKey.substring(0, separatorIndex);
            int lineNumber = Integer.parseInt(lineKey.substring(separatorIndex + 1));

            results.add(new SuspiciousnessResult(className, lineNumber, score, 0));
        }

        results.sort(Comparator.comparingDouble(SuspiciousnessResult::score).reversed());
        return assignDenseRanks(results);
    }

    /**
     * Computes the sum of mutation scores for all passing tests that cover the given line.
     *
     * @param lineKey        "className:lineNumber" key from SpectrumMatrix
     * @param coverageData   Phase 1 per-test coverage data
     * @param mutationScores testId → MS(t)
     * @return discounted_passed value for this line
     */
    private double computeDiscountedPassed(
            String lineKey,
            CoverageData coverageData,
            Map<String, Double> mutationScores) {

        int sep = lineKey.lastIndexOf(':');
        String className = lineKey.substring(0, sep);
        int lineNumber = Integer.parseInt(lineKey.substring(sep + 1));

        double discounted = 0.0;

        for (Map.Entry<String, TestResult> testEntry : coverageData.testResults().entrySet()) {
            String testId = testEntry.getKey();
            TestResult result = testEntry.getValue();

            if (!result.passed()) continue; // only count passing tests

            // Check if this passing test covers line s
            Map<String, Set<Integer>> testCov = coverageData.coveragePerTest().get(testId);
            if (testCov == null) continue;
            Set<Integer> lines = testCov.get(className);
            if (lines == null || !lines.contains(lineNumber)) continue;

            // Add this passing test's mutation score
            double ms = mutationScores.getOrDefault(testId, 0.0);
            discounted += ms;
        }

        return discounted;
    }

    /**
     * Calculates the Ochiai-MS score for a single line.
     *
     * Formula: Score = a11 / sqrt(totalFailed × (a11 + discountedPassed))
     *
     * @param a11              failed tests that covered this line
     * @param discountedPassed sum of MS(t) for passing tests covering this line
     * @param totalFailed      total number of failing tests
     * @return suspiciousness score in [0.0, 1.0]
     */
    public double calculateOchiaiMSScore(int a11, double discountedPassed, int totalFailed) {
        if (a11 == 0) return 0.0;
        if (totalFailed == 0) return 0.0;

        double coveredBy = a11 + discountedPassed;
        if (coveredBy == 0.0) return 0.0;

        double denominator = Math.sqrt((double) totalFailed * coveredBy);
        if (denominator == 0.0) return 0.0;

        double score = (double) a11 / denominator;
        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * Assigns dense ranking to the sorted results.
     * Dense ranking: 1, 2, 2, 3 (ties get the same rank; next rank is not skipped).
     *
     * @param sortedResults results sorted by score descending
     * @return new list with ranks assigned
     */
    private List<SuspiciousnessResult> assignDenseRanks(List<SuspiciousnessResult> sortedResults) {
        if (sortedResults.isEmpty()) {
            return sortedResults;
        }

        List<SuspiciousnessResult> rankedResults = new ArrayList<>(sortedResults.size());
        int currentRank = 1;
        double previousScore = sortedResults.get(0).score();

        for (SuspiciousnessResult result : sortedResults) {
            if (Double.compare(result.score(), previousScore) != 0) {
                currentRank++;
                previousScore = result.score();
            }
            rankedResults.add(new SuspiciousnessResult(
                    result.fullyQualifiedClass(),
                    result.lineNumber(),
                    result.score(),
                    currentRank
            ));
        }

        return rankedResults;
    }
}
