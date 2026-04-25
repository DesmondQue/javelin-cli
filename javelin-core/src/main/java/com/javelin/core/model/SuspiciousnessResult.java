package com.javelin.core.model;

import java.util.Objects;

public final class SuspiciousnessResult {
    private final String fullyQualifiedClass;
    private final int lineNumber;
    private final double score;
    private final int rank;

    public SuspiciousnessResult(String fullyQualifiedClass, int lineNumber, double score, int rank) {
        this.fullyQualifiedClass = fullyQualifiedClass;
        this.lineNumber = lineNumber;
        this.score = score;
        this.rank = rank;
    }

    public String fullyQualifiedClass() { return fullyQualifiedClass; }
    public int lineNumber() { return lineNumber; }
    public double score() { return score; }
    public int rank() { return rank; }

    public String getLineId() {
        return fullyQualifiedClass + ":" + lineNumber;
    }

    public String getScoreAsPercentage() {
        return String.format("%.2f%%", score * 100);
    }

    public String toDisplayString() {
        return String.format("Rank %d: %s:%d (Score: %.4f)", rank, fullyQualifiedClass, lineNumber, score);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SuspiciousnessResult that = (SuspiciousnessResult) o;
        return lineNumber == that.lineNumber && Double.compare(that.score, score) == 0
                && rank == that.rank && Objects.equals(fullyQualifiedClass, that.fullyQualifiedClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedClass, lineNumber, score, rank);
    }

    @Override
    public String toString() {
        return "SuspiciousnessResult[fullyQualifiedClass=" + fullyQualifiedClass
                + ", lineNumber=" + lineNumber + ", score=" + score + ", rank=" + rank + "]";
    }
}
