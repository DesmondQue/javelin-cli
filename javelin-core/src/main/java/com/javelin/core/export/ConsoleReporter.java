package com.javelin.core.export;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.MethodSuspiciousnessResult;
import com.javelin.core.model.SuspiciousnessResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formats and prints analysis results to stdout.
 * Extracted from Main to keep pipeline orchestration separate from display logic.
 */
public final class ConsoleReporter {

    private ConsoleReporter() {}

    public static void printInputSummary(int totalSteps,
                                         String targetPath, String testPath,
                                         String outputPath, String classpath) {
        System.out.printf("[1/%d] Input validation complete.%n%n", totalSteps);
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Configuration | Path                                                    |%n");
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Target Classes| %-56s |%n", truncate(targetPath, 56));
        System.out.printf("| Test Classes  | %-56s |%n", truncate(testPath, 56));
        System.out.printf("| Output File   | %-56s |%n", truncate(outputPath, 56));
        if (classpath != null && !classpath.isBlank()) {
            System.out.printf("| Classpath     | %-56s |%n", truncate(classpath, 56));
        }
        System.out.printf("+---------------+---------------------------------------------------------+%n%n");
    }

    public static void printCoverageSummary(CoverageData coverageData) {
        System.out.printf("%n+---------------------------------+----------+%n");
        System.out.printf("| Coverage Metric                 | Count    |%n");
        System.out.printf("+---------------------------------+----------+%n");
        System.out.printf("| Total Tests                     | %8d |%n", coverageData.getTestCount());
        System.out.printf("| Passed Tests                    | %8d |%n", coverageData.getPassedCount());
        System.out.printf("| Failed Tests                    | %8d |%n", coverageData.getFailedCount());
        System.out.printf("| Unique Lines Tracked            | %8d |%n", coverageData.getTotalLinesTracked());
        System.out.printf("| Lines Covered                   | %8d |%n", coverageData.getCoveredLineCount());
        System.out.printf("+---------------------------------+----------+%n%n");
    }

    public static void printResultsSummary(List<SuspiciousnessResult> results) {
        System.out.printf("+===============================================================+%n");
        System.out.printf("|  Analysis Complete                                            |%n");
        System.out.printf("+===============================================================+%n%n");

        if (results.isEmpty()) {
            System.out.printf("No suspicious lines found.%n");
            return;
        }

        LinkedHashMap<Double, List<SuspiciousnessResult>> rankGroups = new LinkedHashMap<>();
        for (SuspiciousnessResult r : results) {
            rankGroups.computeIfAbsent(r.rank(), k -> new ArrayList<>()).add(r);
        }

        int totalRanks = rankGroups.size();
        long nonZeroLines = results.stream().filter(r -> r.score() > 0.0).count();
        double uniqueness = (double) totalRanks / results.size();

        System.out.printf("Ranking Overview:%n%n");
        System.out.printf("  Total lines tracked:    %d%n", results.size());
        System.out.printf("  Lines with score > 0:   %d%n", nonZeroLines);
        System.out.printf("  Distinct score groups:  %d%n", totalRanks);
        System.out.printf("  Uniqueness (groups/lines): %.2f%%%n%n", uniqueness * 100);

        System.out.printf("Suspiciousness Ranking (all groups with score > 0):%n%n");
        System.out.printf("+--------+------------+-------+---------+----------------------------------------------+%n");
        System.out.printf("|   Rank | Score      | Lines | Top-N   | Top Classes                                  |%n");
        System.out.printf("+--------+------------+-------+---------+----------------------------------------------+%n");

        int cumulativeLines = 0;
        for (var entry : rankGroups.entrySet()) {
            double rank = entry.getKey();
            List<SuspiciousnessResult> group = entry.getValue();
            double score = group.get(0).score();
            if (score <= 0.0) break;

            cumulativeLines += group.size();

            LinkedHashMap<String, Integer> classLineCounts = new LinkedHashMap<>();
            for (SuspiciousnessResult r : group) {
                classLineCounts.merge(r.fullyQualifiedClass(), 1, Integer::sum);
            }
            List<Map.Entry<String, Integer>> sortedClasses = new ArrayList<>(classLineCounts.entrySet());
            sortedClasses.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            StringBuilder classSummary = new StringBuilder();
            int classesShown = 0;
            int totalClasses = sortedClasses.size();
            for (var ce : sortedClasses) {
                String simpleName = ce.getKey().contains(".")
                        ? ce.getKey().substring(ce.getKey().lastIndexOf('.') + 1)
                        : ce.getKey();
                String fragment = simpleName;
                if (totalClasses > 1 || ce.getValue() > 1) {
                    fragment += " (" + ce.getValue() + ")";
                }
                int remaining = totalClasses - classesShown - 1;
                String suffix = remaining > 0 ? ", +" + remaining + " more" : "";
                int projected = classSummary.length() + (classesShown > 0 ? 2 : 0) + fragment.length() + suffix.length();
                if (classesShown > 0 && projected > 44) {
                    classSummary.append(", +").append(remaining + 1).append(" more");
                    break;
                }
                if (classesShown > 0) classSummary.append(", ");
                classSummary.append(fragment);
                classesShown++;
            }

            System.out.printf("| %6.1f | %10.4f | %5d | %7d | %-44s |%n",
                    rank, score, group.size(), cumulativeLines, truncate(classSummary.toString(), 44));
        }
        System.out.printf("+--------+------------+-------+---------+----------------------------------------------+%n");
        System.out.printf("%n  * Top-N = cumulative lines to inspect at each rank (for Top-N evaluation).%n");
    }

    public static void printMethodResultsSummary(List<MethodSuspiciousnessResult> results) {
        System.out.printf("+===============================================================+%n");
        System.out.printf("|  Analysis Complete (Method-Level)                              |%n");
        System.out.printf("+===============================================================+%n%n");

        if (results.isEmpty()) {
            System.out.printf("No suspicious methods found.%n");
            return;
        }

        LinkedHashMap<Double, List<MethodSuspiciousnessResult>> rankGroups = new LinkedHashMap<>();
        for (MethodSuspiciousnessResult r : results) {
            rankGroups.computeIfAbsent(r.rank(), k -> new ArrayList<>()).add(r);
        }

        long nonZeroMethods = results.stream().filter(r -> r.score() > 0.0).count();

        System.out.printf("Ranking Overview:%n%n");
        System.out.printf("  Total methods ranked:     %d%n", results.size());
        System.out.printf("  Methods with score > 0:   %d%n", nonZeroMethods);
        System.out.printf("  Distinct rank groups:     %d%n%n", rankGroups.size());

        System.out.printf("Suspiciousness Ranking (all groups with score > 0):%n%n");
        System.out.printf("+--------+------------+---------+---------+----------------------------------------------+%n");
        System.out.printf("|   Rank | Score      | Methods | Top-N   | Top Methods                                  |%n");
        System.out.printf("+--------+------------+---------+---------+----------------------------------------------+%n");

        int cumulativeMethods = 0;
        for (var entry : rankGroups.entrySet()) {
            double rank = entry.getKey();
            List<MethodSuspiciousnessResult> group = entry.getValue();
            double score = group.get(0).score();
            if (score <= 0.0) break;

            cumulativeMethods += group.size();

            StringBuilder methodSummary = new StringBuilder();
            int shown = 0;
            for (MethodSuspiciousnessResult r : group) {
                String simpleName = r.fullyQualifiedClass().contains(".")
                        ? r.fullyQualifiedClass().substring(r.fullyQualifiedClass().lastIndexOf('.') + 1)
                        : r.fullyQualifiedClass();
                String fragment = simpleName + "#" + r.methodName();
                int remaining = group.size() - shown - 1;
                String suffix = remaining > 0 ? ", +" + remaining + " more" : "";
                int projected = methodSummary.length() + (shown > 0 ? 2 : 0) + fragment.length() + suffix.length();
                if (shown > 0 && projected > 44) {
                    methodSummary.append(", +").append(remaining + 1).append(" more");
                    break;
                }
                if (shown > 0) methodSummary.append(", ");
                methodSummary.append(fragment);
                shown++;
            }

            System.out.printf("| %6.1f | %10.4f | %7d | %7d | %-44s |%n",
                    rank, score, group.size(), cumulativeMethods, truncate(methodSummary.toString(), 44));
        }
        System.out.printf("+--------+------------+---------+---------+----------------------------------------------+%n");
        System.out.printf("%n  * Top-N = cumulative methods to inspect at each rank.%n");
    }

    public static void printTimingSummary(long testExecTimeMs, long ochiaiTimeMs) {
        System.out.printf("%nTiming:%n");
        System.out.printf("  Test execution:      %s%n", formatDuration(testExecTimeMs));
        System.out.printf("  Ochiai calculation:  %s%n", formatDuration(ochiaiTimeMs));
        System.out.printf("  Total:               %s%n", formatDuration(testExecTimeMs + ochiaiTimeMs));
    }

    public static void printTimingSummaryMS(long testExecTimeMs, long mutationTimeMs, long ochiaiMSTimeMs) {
        System.out.printf("%nTiming:%n");
        System.out.printf("  Test execution:      %s%n", formatDuration(testExecTimeMs));
        System.out.printf("  Mutation analysis:   %s%n", formatDuration(mutationTimeMs));
        System.out.printf("  Ochiai-MS scoring:   %s%n", formatDuration(ochiaiMSTimeMs));
        System.out.printf("  Total:               %s%n", formatDuration(testExecTimeMs + mutationTimeMs + ochiaiMSTimeMs));
    }

    public static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.2fs", ms / 1000.0);
    }

    public static String truncate(String str, int maxWidth) {
        if (str == null) return "";
        if (str.length() <= maxWidth) return str;
        return "..." + str.substring(str.length() - maxWidth + 3);
    }
}
