package com.javelin.core.model;

public final class LineCoverage {
    private final String className;
    private final int lineNumber;
    private final boolean covered;

    public LineCoverage(String className, int lineNumber, boolean covered) {
        this.className = className;
        this.lineNumber = lineNumber;
        this.covered = covered;
    }

    public String className() { return className; }
    public int lineNumber() { return lineNumber; }
    public boolean covered() { return covered; }

    public String getLineId() {
        return className + ":" + lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineCoverage that = (LineCoverage) o;
        return lineNumber == that.lineNumber && className.equals(that.className);
    }

    @Override
    public int hashCode() {
        return 31 * className.hashCode() + lineNumber;
    }

    @Override
    public String toString() {
        return "LineCoverage[className=" + className + ", lineNumber=" + lineNumber + ", covered=" + covered + "]";
    }
}
