package com.javelin.core.model;

import java.util.Objects;

public final class MutantInfo {
    private final String mutantId;
    private final String mutatedClass;
    private final int lineNumber;
    private final String status;

    public MutantInfo(String mutantId, String mutatedClass, int lineNumber, String status) {
        this.mutantId = mutantId;
        this.mutatedClass = mutatedClass;
        this.lineNumber = lineNumber;
        this.status = status;
    }

    public String mutantId() { return mutantId; }
    public String mutatedClass() { return mutatedClass; }
    public int lineNumber() { return lineNumber; }
    public String status() { return status; }

    public String getLineKey() {
        return mutatedClass + ":" + lineNumber;
    }

    public boolean isKilled() {
        return "KILLED".equals(status) || "TIMED_OUT".equals(status);
    }

    public boolean isSurvived() {
        return "SURVIVED".equals(status);
    }

    public boolean isNoCoverage() {
        return "NO_COVERAGE".equals(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutantInfo that = (MutantInfo) o;
        return lineNumber == that.lineNumber
                && Objects.equals(mutantId, that.mutantId)
                && Objects.equals(mutatedClass, that.mutatedClass)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mutantId, mutatedClass, lineNumber, status);
    }

    @Override
    public String toString() {
        return "MutantInfo[mutantId=" + mutantId + ", mutatedClass=" + mutatedClass
                + ", lineNumber=" + lineNumber + ", status=" + status + "]";
    }
}
