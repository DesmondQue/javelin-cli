package com.javelin.core.model;

import java.util.Arrays;
import java.util.Objects;

public final class TestExecResult {
    private final String testClassName;
    private final byte[] coverageData;
    private final boolean passed;

    public TestExecResult(String testClassName, byte[] coverageData, boolean passed) {
        this.testClassName = testClassName;
        this.coverageData = coverageData;
        this.passed = passed;
    }

    public String testClassName() { return testClassName; }
    public byte[] coverageData() { return coverageData; }
    public boolean passed() { return passed; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestExecResult that = (TestExecResult) o;
        return passed == that.passed
                && Objects.equals(testClassName, that.testClassName)
                && Arrays.equals(coverageData, that.coverageData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(testClassName, passed);
        result = 31 * result + Arrays.hashCode(coverageData);
        return result;
    }

    @Override
    public String toString() {
        return "TestExecResult[testClassName=" + testClassName
                + ", coverageData=" + (coverageData != null ? coverageData.length + " bytes" : "null")
                + ", passed=" + passed + "]";
    }
}
