package com.javelin.core.model;

import java.util.Map;
import java.util.Objects;

public final class SpectrumMatrix {
    private final Map<String, int[]> lineCounts;
    private final int totalFailed;
    private final int totalPassed;

    public SpectrumMatrix(Map<String, int[]> lineCounts, int totalFailed, int totalPassed) {
        this.lineCounts = lineCounts;
        this.totalFailed = totalFailed;
        this.totalPassed = totalPassed;
    }

    public Map<String, int[]> lineCounts() { return lineCounts; }
    public int totalFailed() { return totalFailed; }
    public int totalPassed() { return totalPassed; }

    public int[] getCountsForLine(String lineKey) {
        return lineCounts.get(lineKey);
    }

    public int getA11(String lineKey) {
        int[] counts = lineCounts.get(lineKey);
        return counts != null ? counts[0] : 0;
    }

    public int getA10(String lineKey) {
        int[] counts = lineCounts.get(lineKey);
        return counts != null ? counts[1] : 0;
    }

    public int getA01(String lineKey) {
        return totalFailed - getA11(lineKey);
    }

    public int getTotalTests() {
        return totalFailed + totalPassed;
    }

    public int getLineCount() {
        return lineCounts.size();
    }

    public boolean hasFailedTests() {
        return totalFailed > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpectrumMatrix that = (SpectrumMatrix) o;
        return totalFailed == that.totalFailed && totalPassed == that.totalPassed
                && Objects.equals(lineCounts, that.lineCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineCounts, totalFailed, totalPassed);
    }

    @Override
    public String toString() {
        return "SpectrumMatrix[lineCounts=" + lineCounts + ", totalFailed=" + totalFailed
                + ", totalPassed=" + totalPassed + "]";
    }
}
