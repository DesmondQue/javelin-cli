package com.javelin.core.model;

import java.util.Objects;

public final class MethodSuspiciousnessResult {
    private final String fullyQualifiedClass;
    private final String methodName;
    private final String descriptor;
    private final double score;
    private final double rank;
    private final int firstLine;
    private final int lastLine;

    public MethodSuspiciousnessResult(String fullyQualifiedClass, String methodName, String descriptor,
                                      double score, double rank, int firstLine, int lastLine) {
        this.fullyQualifiedClass = fullyQualifiedClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.score = score;
        this.rank = rank;
        this.firstLine = firstLine;
        this.lastLine = lastLine;
    }

    public String fullyQualifiedClass() { return fullyQualifiedClass; }
    public String methodName() { return methodName; }
    public String descriptor() { return descriptor; }
    public double score() { return score; }
    public double rank() { return rank; }
    public int firstLine() { return firstLine; }
    public int lastLine() { return lastLine; }

    public String getMethodId() {
        return fullyQualifiedClass + "#" + methodName + descriptor;
    }

    public String getDisplayName() {
        return fullyQualifiedClass + "#" + methodName;
    }

    public String toDisplayString() {
        return String.format("Rank %.1f: %s#%s (Score: %.4f, Lines: %d-%d)",
                rank, fullyQualifiedClass, methodName, score, firstLine, lastLine);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSuspiciousnessResult that = (MethodSuspiciousnessResult) o;
        return Double.compare(that.score, score) == 0 && Double.compare(that.rank, rank) == 0
                && firstLine == that.firstLine && lastLine == that.lastLine
                && Objects.equals(fullyQualifiedClass, that.fullyQualifiedClass)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedClass, methodName, descriptor, score, rank, firstLine, lastLine);
    }

    @Override
    public String toString() {
        return "MethodSuspiciousnessResult[fullyQualifiedClass=" + fullyQualifiedClass
                + ", methodName=" + methodName + ", descriptor=" + descriptor
                + ", score=" + score + ", rank=" + rank
                + ", firstLine=" + firstLine + ", lastLine=" + lastLine + "]";
    }
}
