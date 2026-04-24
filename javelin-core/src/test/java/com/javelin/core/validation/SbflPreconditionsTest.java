package com.javelin.core.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SbflPreconditionsTest {

    @Test
    void zeroFailedTests_cannotProceed() {
        var result = SbflPreconditions.evaluate(5, 0);

        assertFalse(result.canProceed());
        assertFalse(result.warning());
        assertFalse(result.message().isBlank());
    }

    @Test
    void negativeFailedTests_cannotProceed() {
        var result = SbflPreconditions.evaluate(3, -1);

        assertFalse(result.canProceed());
    }

    @Test
    void oneFailingTest_noPassing_canProceedWithWarning() {
        var result = SbflPreconditions.evaluate(0, 1);

        assertTrue(result.canProceed());
        assertTrue(result.warning());
        assertFalse(result.message().isBlank());
    }

    @Test
    void typicalMix_canProceedNoWarning() {
        var result = SbflPreconditions.evaluate(8, 2);

        assertTrue(result.canProceed());
        assertFalse(result.warning());
        assertTrue(result.message().isBlank());
    }

    @Test
    void allFailing_canProceedWithWarning() {
        var result = SbflPreconditions.evaluate(0, 5);

        assertTrue(result.canProceed());
        assertTrue(result.warning());
    }

    @Test
    void zeroFailedZeroPassed_cannotProceed() {
        var result = SbflPreconditions.evaluate(0, 0);

        assertFalse(result.canProceed());
    }
}
