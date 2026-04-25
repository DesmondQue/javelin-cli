package com.javelin.core.model;

import java.nio.file.Path;
import java.util.Objects;

public final class TestExecResult {
    private final String testClassName;
    private final Path execFile;
    private final boolean passed;
    private final int exitCode;

    public TestExecResult(String testClassName, Path execFile, boolean passed, int exitCode) {
        this.testClassName = testClassName;
        this.execFile = execFile;
        this.passed = passed;
        this.exitCode = exitCode;
    }

    public String testClassName() { return testClassName; }
    public Path execFile() { return execFile; }
    public boolean passed() { return passed; }
    public int exitCode() { return exitCode; }

    public static TestExecResult fromExitCode(String testClassName, Path execFile, int exitCode) {
        return new TestExecResult(testClassName, execFile, exitCode == 0, exitCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestExecResult that = (TestExecResult) o;
        return passed == that.passed && exitCode == that.exitCode
                && Objects.equals(testClassName, that.testClassName)
                && Objects.equals(execFile, that.execFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testClassName, execFile, passed, exitCode);
    }

    @Override
    public String toString() {
        return "TestExecResult[testClassName=" + testClassName + ", execFile=" + execFile
                + ", passed=" + passed + ", exitCode=" + exitCode + "]";
    }
}
