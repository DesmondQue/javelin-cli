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
     *
     * @param targetClassNames Fully qualified class names to pass to PITest targetClasses
     * @param targetLines      className → set of line numbers in the fault region
     */
    public record FaultRegion(
            Set<String> targetClassNames,
            Map<String, Set<Integer>> targetLines
    ) {}

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
