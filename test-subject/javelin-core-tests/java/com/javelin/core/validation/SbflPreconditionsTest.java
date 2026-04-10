package com.javelin.core.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SbflPreconditionsTest {

    @Test
    void evaluate_shouldProceedWithoutWarning_whenPassingAndFailingExist() {
        SbflPreconditions.ValidationResult result = SbflPreconditions.evaluate(3, 1);

        assertTrue(result.canProceed());
        assertFalse(result.warning());
    }

    @Test
    void evaluate_shouldBlock_whenNoFailingTests() {
        SbflPreconditions.ValidationResult result = SbflPreconditions.evaluate(4, 0);

        assertFalse(result.canProceed());
        assertFalse(result.warning());
        assertTrue(result.message().contains("at least one failing test"));
    }

    @Test
    void evaluate_shouldWarnButProceed_whenNoPassingTests() {
        SbflPreconditions.ValidationResult result = SbflPreconditions.evaluate(0, 5);

        assertTrue(result.canProceed());
        assertTrue(result.warning());
        assertTrue(result.message().contains("No passing tests"));
    }

    @Test
    void evaluate_shouldBlock_whenNoTestsWereExecuted() {
        SbflPreconditions.ValidationResult result = SbflPreconditions.evaluate(0, 0);

        assertFalse(result.canProceed());
        assertFalse(result.warning());
        assertTrue(result.message().contains("at least one failing test"));
    }
}
