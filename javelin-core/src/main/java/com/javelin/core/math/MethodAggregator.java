package com.javelin.core.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.javelin.core.model.MethodInfo;
import com.javelin.core.model.MethodSuspiciousnessResult;
import com.javelin.core.model.SuspiciousnessResult;

/**
 * Aggregates line-level suspiciousness scores to method-level.
 *
 * For each method, the maximum score among its lines is used (standard in SBFL literature).
 * Lines not belonging to any method are grouped under a synthetic "<class-level>" entry.
 */
public class MethodAggregator {

    /**
     * Aggregates line-level results to method-level.
     *
     * @param lineResults    line-level suspiciousness results
     * @param methodMapping  className to list of MethodInfo (from CoverageData)
     * @param useAverageRank if true, use average (MID) ranking; if false, use dense ranking
     * @return method-level results sorted by score descending with ranks assigned
     */
    public List<MethodSuspiciousnessResult> aggregate(
            List<SuspiciousnessResult> lineResults,
            Map<String, List<MethodInfo>> methodMapping,
            boolean useAverageRank) {

        Map<String, Double> maxScores = new HashMap<>();
        Map<String, MethodInfo> methodInfoLookup = new HashMap<>();

        for (SuspiciousnessResult lineResult : lineResults) {
            MethodInfo method = findContainingMethod(
                    lineResult.fullyQualifiedClass(), lineResult.lineNumber(), methodMapping);

            String methodId;
            if (method != null) {
                methodId = method.getMethodId();
                methodInfoLookup.putIfAbsent(methodId, method);
            } else {
                methodId = lineResult.fullyQualifiedClass() + "#<class-level>";
                methodInfoLookup.putIfAbsent(methodId, new MethodInfo(
                        lineResult.fullyQualifiedClass(), "<class-level>", "",
                        lineResult.lineNumber(), lineResult.lineNumber()));
            }

            maxScores.merge(methodId, lineResult.score(), Math::max);

            if (method == null) {
                MethodInfo existing = methodInfoLookup.get(methodId);
                int newFirst = Math.min(existing.firstLine(), lineResult.lineNumber());
                int newLast = Math.max(existing.lastLine(), lineResult.lineNumber());
                if (newFirst != existing.firstLine() || newLast != existing.lastLine()) {
                    methodInfoLookup.put(methodId, new MethodInfo(
                            existing.className(), existing.methodName(), existing.descriptor(),
                            newFirst, newLast));
                }
            }
        }

        List<MethodSuspiciousnessResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : maxScores.entrySet()) {
            MethodInfo info = methodInfoLookup.get(entry.getKey());
            results.add(new MethodSuspiciousnessResult(
                    info.className(), info.methodName(), info.descriptor(),
                    entry.getValue(), 0.0, info.firstLine(), info.lastLine()));
        }

        results.sort(Comparator.comparingDouble(MethodSuspiciousnessResult::score).reversed());

        if (useAverageRank) {
            return assignAverageRanks(results);
        } else {
            return assignDenseRanks(results);
        }
    }

    private MethodInfo findContainingMethod(String className, int lineNumber,
                                            Map<String, List<MethodInfo>> methodMapping) {
        List<MethodInfo> methods = methodMapping.get(className);
        if (methods == null) {
            return null;
        }
        for (MethodInfo method : methods) {
            if (method.containsLine(lineNumber)) {
                return method;
            }
        }
        return null;
    }

    private List<MethodSuspiciousnessResult> assignDenseRanks(
            List<MethodSuspiciousnessResult> sorted) {
        if (sorted.isEmpty()) return sorted;

        List<MethodSuspiciousnessResult> ranked = new ArrayList<>(sorted.size());
        int currentRank = 1;
        double previousScore = sorted.get(0).score();

        for (MethodSuspiciousnessResult r : sorted) {
            if (Double.compare(r.score(), previousScore) != 0) {
                currentRank++;
                previousScore = r.score();
            }
            ranked.add(new MethodSuspiciousnessResult(
                    r.fullyQualifiedClass(), r.methodName(), r.descriptor(),
                    r.score(), currentRank, r.firstLine(), r.lastLine()));
        }
        return ranked;
    }

    /**
     * Average (MID) ranking: MID = S + (E - 1) / 2.0
     * where S = 1-based start position of tie group, E = number of tied elements.
     */
    private List<MethodSuspiciousnessResult> assignAverageRanks(
            List<MethodSuspiciousnessResult> sorted) {
        if (sorted.isEmpty()) return sorted;

        List<MethodSuspiciousnessResult> ranked = new ArrayList<>(sorted.size());
        int i = 0;
        int n = sorted.size();

        while (i < n) {
            double score = sorted.get(i).score();
            int start = i;
            while (i < n && Double.compare(sorted.get(i).score(), score) == 0) {
                i++;
            }
            int groupSize = i - start;
            double midRank = (start + 1) + (groupSize - 1) / 2.0;

            for (int j = start; j < i; j++) {
                MethodSuspiciousnessResult r = sorted.get(j);
                ranked.add(new MethodSuspiciousnessResult(
                        r.fullyQualifiedClass(), r.methodName(), r.descriptor(),
                        r.score(), midRank, r.firstLine(), r.lastLine()));
            }
        }
        return ranked;
    }
}
