package com.javelin.core.model;

/**
 * Suspiciousness Result Model
 * 
 * represents the final Ochiai suspiciousness score for a single line of code
 * 
 * @param fullyQualifiedClass Fully qualified class name (e.g., "com.example.OrderService")
 * @param lineNumber          Line number (1-based)
 * @param score               Ochiai suspiciousness score [0.0, 1.0]
 * @param rank                Dense rank (1 = most suspicious)
 */
public record SuspiciousnessResult(
        String fullyQualifiedClass,
        int lineNumber,
        double score,
        int rank
) {
    /**
     returns a unique identifier for this line
     */
    public String getLineId() {
        return fullyQualifiedClass + ":" + lineNumber;
    }

    /**
     formats the score as a percentage string
     */
    public String getScoreAsPercentage() {
        return String.format("%.2f%%", score * 100);
    }

    /**
     creates a display-friendly string for this result
     */
    public String toDisplayString() {
        return String.format("Rank %d: %s:%d (Score: %.4f)", 
                rank, fullyQualifiedClass, lineNumber, score);
    }
}
