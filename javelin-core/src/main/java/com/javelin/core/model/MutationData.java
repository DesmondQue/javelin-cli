package com.javelin.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MutationData {
    private final List<MutantInfo> mutants;
    private final Map<String, Set<String>> killMatrix;
    private final Set<String> examinedTests;

    public MutationData(List<MutantInfo> mutants, Map<String, Set<String>> killMatrix, Set<String> examinedTests) {
        this.mutants = mutants;
        this.killMatrix = killMatrix;
        this.examinedTests = examinedTests;
    }

    public List<MutantInfo> mutants() { return mutants; }
    public Map<String, Set<String>> killMatrix() { return killMatrix; }
    public Set<String> examinedTests() { return examinedTests; }

    public int getMutantCount() {
        return mutants.size();
    }

    public int getKilledCount() {
        return (int) mutants.stream().filter(MutantInfo::isKilled).count();
    }

    public int getSurvivedCount() {
        return (int) mutants.stream().filter(MutantInfo::isSurvived).count();
    }

    public int getNoCoverageCount() {
        return (int) mutants.stream().filter(MutantInfo::isNoCoverage).count();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutationData that = (MutationData) o;
        return Objects.equals(mutants, that.mutants)
                && Objects.equals(killMatrix, that.killMatrix)
                && Objects.equals(examinedTests, that.examinedTests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mutants, killMatrix, examinedTests);
    }

    @Override
    public String toString() {
        return "MutationData[mutants=" + mutants + ", killMatrix=" + killMatrix
                + ", examinedTests=" + examinedTests + "]";
    }
}
