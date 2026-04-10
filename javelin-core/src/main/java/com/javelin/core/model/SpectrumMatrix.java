package com.javelin.core.model;

import java.util.Map;

/**
  Spectrum Matrix Model
  
  represents the Boolean Spectrum Matrix (Test × Line) used for SBFL
  
  Design Notes:
  - key: "className:lineNumber"
  - value: int[2] where [0]=a11 (failed&covered), [1]=a10 (passed&covered)
  
  @param lineCounts  Map of line identifier to [a11, a10] counts
  @param totalFailed Total number of failed tests
  @param totalPassed Total number of passed tests
 */
public record SpectrumMatrix(
        Map<String, int[]> lineCounts,
        int totalFailed,
        int totalPassed
) {
    /**
      gets the count data for a specific line.
      
      @param lineKey Line identifier in format "className:lineNumber"
      @return int[2] where [0]=a11, [1]=a10, or null if line not found
     */
    public int[] getCountsForLine(String lineKey) {
        return lineCounts.get(lineKey);
    }

    /**
      gets a11 (failed tests that covered this line) for a specific line
     */
    public int getA11(String lineKey) {
        int[] counts = lineCounts.get(lineKey);
        return counts != null ? counts[0] : 0;
    }

    /**
      gets a10 (passed tests that covered this line) for a specific line
     */
    public int getA10(String lineKey) {
        int[] counts = lineCounts.get(lineKey);
        return counts != null ? counts[1] : 0;
    }

    /**
      gets a01 (failed tests that did NOT cover this line) for a specific line
     */
    public int getA01(String lineKey) {
        return totalFailed - getA11(lineKey);
    }

    /**
      gets total number of tests
     */
    public int getTotalTests() {
        return totalFailed + totalPassed;
    }

    /**
      gets number of unique lines in the matrix
     */
    public int getLineCount() {
        return lineCounts.size();
    }

    /**
      checks if matrix has any failed tests
      NOTE: SBFL requires at least one failing test to be meaningful
     */
    public boolean hasFailedTests() {
        return totalFailed > 0;
    }

    /**
      gets line counts map
     */
    public Map<String, int[]> lineCounts() {
        return lineCounts;
    }
}
