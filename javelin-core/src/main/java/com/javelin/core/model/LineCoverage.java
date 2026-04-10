package com.javelin.core.model;

/**
  Line Coverage Model
  represents a line of code with its coverage status
  
  @param className  Fully qualified class name
  @param lineNumber Line number (1-based)
  @param covered    Whether this line was executed by at least one test
 */
public record LineCoverage(
        String className,
        int lineNumber,
        boolean covered
) {
    /*
     returns a unique identifier for this line
     format: "className:lineNumber"
     */
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
}
