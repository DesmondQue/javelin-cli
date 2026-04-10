package com.javelin.core.validation;

/**
 * Validates preconditions required for meaningful SBFL analysis.
 */
public final class SbflPreconditions {

    private SbflPreconditions() {
    }

    /**
     * Evaluates whether SBFL should proceed based on pass/fail distribution.
     *
     * @param passedCount number of passing tests
     * @param failedCount number of failing tests
     * @return validation result indicating whether to proceed and what to report
     */
    public static ValidationResult evaluate(int passedCount, int failedCount) {
        if (failedCount <= 0) {
            return new ValidationResult(
                    false,
                    false,
                    "SBFL requires at least one failing test. Re-run after adding or selecting failing tests."
            );
        }

        if (passedCount <= 0) {
            return new ValidationResult(
                    true,
                    true,
                    "No passing tests were detected. Ochiai can still run, but ranking quality may be reduced."
            );
        }

        return new ValidationResult(
                true,
                false,
                ""
        );
    }

    public record ValidationResult(boolean canProceed, boolean warning, String message) {
    }
}
