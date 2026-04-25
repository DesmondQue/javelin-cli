package com.javelin.core.model;

/**
 * Method-level suspiciousness result, produced by aggregating line-level scores.
 *
 * @param fullyQualifiedClass Fully qualified class name
 * @param methodName          Method name (e.g., "calculate", "<init>")
 * @param descriptor          JVM method descriptor for overload disambiguation
 * @param score               Max Ochiai score among all lines in this method
 * @param rank                Rank (double to support average/MID ranking, e.g., 2.5)
 * @param firstLine           First source line of method
 * @param lastLine            Last source line of method
 */
public record MethodSuspiciousnessResult(
        String fullyQualifiedClass,
        String methodName,
        String descriptor,
        double score,
        double rank,
        int firstLine,
        int lastLine
) {
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
}
