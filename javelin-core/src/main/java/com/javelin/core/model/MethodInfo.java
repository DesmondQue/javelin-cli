package com.javelin.core.model;

import java.util.Objects;

public final class MethodInfo {
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int firstLine;
    private final int lastLine;

    public MethodInfo(String className, String methodName, String descriptor, int firstLine, int lastLine) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.firstLine = firstLine;
        this.lastLine = lastLine;
    }

    public String className() { return className; }
    public String methodName() { return methodName; }
    public String descriptor() { return descriptor; }
    public int firstLine() { return firstLine; }
    public int lastLine() { return lastLine; }

    public String getMethodId() {
        return className + "#" + methodName + descriptor;
    }

    public String getDisplayName() {
        return className + "#" + methodName;
    }

    public boolean containsLine(int lineNumber) {
        return lineNumber >= firstLine && lineNumber <= lastLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        return firstLine == that.firstLine && lastLine == that.lastLine
                && Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, descriptor, firstLine, lastLine);
    }

    @Override
    public String toString() {
        return "MethodInfo[className=" + className + ", methodName=" + methodName
                + ", descriptor=" + descriptor + ", firstLine=" + firstLine + ", lastLine=" + lastLine + "]";
    }
}
