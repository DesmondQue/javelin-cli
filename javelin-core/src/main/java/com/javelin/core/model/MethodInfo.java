package com.javelin.core.model;

/**
 * Represents a method's identity and source line boundaries, extracted from JaCoCo's IMethodCoverage.
 *
 * @param className   Fully qualified class name (e.g., "com.example.Calculator")
 * @param methodName  Method name (e.g., "calculate", "<init>", "<clinit>")
 * @param descriptor  JVM method descriptor (e.g., "(II)D")
 * @param firstLine   First source line of the method body (1-based)
 * @param lastLine    Last source line of the method body (1-based)
 */
public record MethodInfo(
        String className,
        String methodName,
        String descriptor,
        int firstLine,
        int lastLine
) {
    public String getMethodId() {
        return className + "#" + methodName + descriptor;
    }

    public String getDisplayName() {
        return className + "#" + methodName;
    }

    public boolean containsLine(int lineNumber) {
        return lineNumber >= firstLine && lineNumber <= lastLine;
    }
}
