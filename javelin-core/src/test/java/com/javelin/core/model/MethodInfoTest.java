package com.javelin.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodInfoTest {

    @Test
    void getMethodId_formatsCorrectly() {
        MethodInfo m = new MethodInfo("com.example.Foo", "bar", "(II)D", 10, 20);
        assertEquals("com.example.Foo#bar(II)D", m.getMethodId());
    }

    @Test
    void containsLine_withinRange_true() {
        MethodInfo m = new MethodInfo("com.Foo", "doIt", "()V", 5, 15);
        assertTrue(m.containsLine(5));
        assertTrue(m.containsLine(10));
        assertTrue(m.containsLine(15));
    }

    @Test
    void containsLine_outsideRange_false() {
        MethodInfo m = new MethodInfo("com.Foo", "doIt", "()V", 5, 15);
        assertFalse(m.containsLine(4));
        assertFalse(m.containsLine(16));
    }

    @Test
    void getDisplayName_noDescriptor() {
        MethodInfo m = new MethodInfo("com.example.Bar", "calc", "(I)I", 1, 10);
        assertEquals("com.example.Bar#calc", m.getDisplayName());
    }
}
