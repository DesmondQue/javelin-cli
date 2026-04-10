package com.javelin.core.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;

/**
  Ochiai Calculator
  
  Responsibilities:
  - pure math logic for Standard Ochiai SBFL
  - calculates suspiciousness scores for each line of code
  
  formula:

  for each line s:
    Score(s) = a11(s) / sqrt((a11(s) + a01(s)) × (a11(s) + a10(s)))
  
  where:
    a11(s) = Failed tests that covered line s
    a10(s) = Passed tests that covered line s
    a01(s) = Failed tests that did NOT cover line s = TotalFailed - a11(s)
    a00(s) = Passed tests that did NOT cover line s (not used)
  
  optimization:
    (a11 + a01) = TotalFailed (constant)
    Score = a11 / sqrt(TotalFailed × (a11 + a10))
  
  rules:
    - if denominator is 0, score is 0.0
    - range: score is always between 0.0 (safe) and 1.0 (highly suspicious)
    - use double for floating point precision
 */
public class OchiaiCalculator {

    /**
     * calculates Ochiai suspiciousness scores for all lines in the spectrum matrix.
     *
     * @param matrix The spectrum matrix containing coverage data
     * @return List of SuspiciousnessResult, sorted by score descending, with ranks assigned
     */
    public List<SuspiciousnessResult> calculate(SpectrumMatrix matrix) {
        Map<String, int[]> lineCounts = matrix.lineCounts();
        int totalFailed = matrix.totalFailed();

        List<SuspiciousnessResult> results = new ArrayList<>();

        for (Map.Entry<String, int[]> entry : lineCounts.entrySet()) {
            String lineKey = entry.getKey();
            int[] counts = entry.getValue();
            
            int a11 = counts[0]; // failed & covered
            int a10 = counts[1]; // passed & covered

            double score = calculateOchiaiScore(a11, a10, totalFailed);

            int separatorIndex = lineKey.lastIndexOf(':');
            String className = lineKey.substring(0, separatorIndex);
            int lineNumber = Integer.parseInt(lineKey.substring(separatorIndex + 1));

            results.add(new SuspiciousnessResult(className, lineNumber, score, 0));
        }

        results.sort(Comparator.comparingDouble(SuspiciousnessResult::score).reversed()); //sort descending

        results = assignDenseRanks(results);

        return results;
    }

    /**
     calculates the ochiai suspiciousness score for a single line.
     formula: Score = a11 / sqrt(TotalFailed × (a11 + a10))
     
      @param a11         Number of failed tests that covered this line
      @param a10         Number of passed tests that covered this line
      @param totalFailed Total number of failed tests
      @return Suspiciousness score between 0.0 and 1.0
     */
    public double calculateOchiaiScore(int a11, int a10, int totalFailed) {

        if (a11 == 0) {
            return 0.0;
        }

        if (totalFailed == 0) {
            return 0.0;
        }

        int coveredBy = a11 + a10;
        if (coveredBy == 0) {
            return 0.0;
        }

        double denominator = Math.sqrt((double) totalFailed * coveredBy);
        
        if (denominator == 0.0) {
            return 0.0;
        }

        double score = (double) a11 / denominator;

        //clamp to [0.0, 1.0] for safety NOTE: further testing required
        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * Assigns Dense Ranking to the sorted results.
     * Dense Ranking: 1, 2, 2, 3 (ties get same rank, next rank is not skipped)
     *
     * @param sortedResults Results sorted by score descending
     * @return New list with ranks assigned
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

    /**
     * calculates full ochiai score using all 4 variables (for verification)
     * this is the non optimized formula for testing purposes
     *
     * @param a11 Failed & Covered
     * @param a10 Passed & Covered
     * @param a01 Failed & Uncovered
     * @param a00 Passed & Uncovered (not used in formula)
     * @return Suspiciousness score
     */
    public double calculateOchiaiFull(int a11, int a10, int a01, int a00) {
        if (a11 == 0) {
            return 0.0;
        }

        int totalFailed = a11 + a01;
        int totalCovered = a11 + a10;

        double denominator = Math.sqrt((double) totalFailed * totalCovered);
        
        if (denominator == 0.0) {
            return 0.0;
        }

        return (double) a11 / denominator;
    }
}
