package com.javelin.core.mutation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.javelin.core.model.SpectrumMatrix;

/**
 * Fault Region Identifier
 *
 * Extracts the set of classes and line numbers from a SpectrumMatrix that are
 * covered by at least one failing test (a11 > 0). This defines the scoped
 * mutation target for PITest — only lines in the fault region are mutated.
 */
public class FaultRegionIdentifier {

    /**
     * Result of fault region identification.
     */
    public static final class FaultRegion {
        private final Set<String> targetClassNames;
        private final Map<String, Set<Integer>> targetLines;

        public FaultRegion(Set<String> targetClassNames, Map<String, Set<Integer>> targetLines) {
            this.targetClassNames = targetClassNames;
            this.targetLines = targetLines;
        }

        public Set<String> targetClassNames() { return targetClassNames; }
        public Map<String, Set<Integer>> targetLines() { return targetLines; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FaultRegion that = (FaultRegion) o;
            return java.util.Objects.equals(targetClassNames, that.targetClassNames)
                    && java.util.Objects.equals(targetLines, that.targetLines);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(targetClassNames, targetLines);
        }

        @Override
        public String toString() {
            return "FaultRegion[targetClassNames=" + targetClassNames + ", targetLines=" + targetLines + "]";
        }
    }

    /**
     * Identifies the fault region from the given SpectrumMatrix.
     *
     * Only lines where a11 > 0 (covered by at least one failing test) are included.
     *
     * @param matrix the spectrum matrix produced by MatrixBuilder
     * @return FaultRegion containing target class names and line sets
     */
    public FaultRegion identify(SpectrumMatrix matrix) {
        Set<String> targetClassNames = new HashSet<>();
        Map<String, Set<Integer>> targetLines = new HashMap<>();

        for (Map.Entry<String, int[]> entry : matrix.lineCounts().entrySet()) {
            String lineKey = entry.getKey();
            int[] counts = entry.getValue();
            int a11 = counts[0]; // failed & covered

            if (a11 > 0) {
                int separatorIndex = lineKey.lastIndexOf(':');
                String className = lineKey.substring(0, separatorIndex);
                int lineNumber = Integer.parseInt(lineKey.substring(separatorIndex + 1));

                targetClassNames.add(className);
                targetLines.computeIfAbsent(className, k -> new HashSet<>()).add(lineNumber);
            }
        }

        return new FaultRegion(targetClassNames, targetLines);
    }
}
