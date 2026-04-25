package com.javelin.core.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects known Java agent dependencies on the classpath that would conflict with
 * JaCoCo's online instrumentation agent.
 *
 * <p>Conflicting agents register their own {@code ClassFileTransformer} during
 * {@code premain()}, and when JaCoCo's transformer also modifies the same classes
 * the combined bytecode is often invalid, causing {@code ClassFormatError} or
 * {@code LinkageError}.
 *
 * <p>Detection is based on JAR file naming patterns (offline heuristic): the
 * presence of a JAR on the classpath does not guarantee the agent is actually
 * activated, but it is a reliable signal in Gradle/Maven project layouts where
 * agent-mode libraries are only added when the feature is in use.
 */
public final class AgentConflictDetector {

    /**
     * A detected conflict: the conflicting JAR file name and a human-readable reason.
     */
    public static final class Conflict {
        private final String jarName;
        private final String reason;

        public Conflict(String jarName, String reason) {
            this.jarName = jarName;
            this.reason = reason;
        }

        public String jarName() { return jarName; }
        public String reason() { return reason; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Conflict that = (Conflict) o;
            return java.util.Objects.equals(jarName, that.jarName)
                    && java.util.Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(jarName, reason);
        }

        @Override
        public String toString() {
            return "Conflict[jarName=" + jarName + ", reason=" + reason + "]";
        }
    }

    /**
     * JAR name patterns (matched case-insensitively against the file name of each
     * classpath entry) that indicate an agent likely to conflict with JaCoCo online mode.
     */
    private static final List<Pattern> CONFLICT_PATTERNS = List.of(
            // Mockito-inline uses ByteBuddy agent instrumentation internally
            Pattern.compile("mockito-inline[\\-_]?(\\d.*)?\\.(jar)$", Pattern.CASE_INSENSITIVE),
            // ByteBuddy agent itself
            Pattern.compile("byte-buddy-agent[\\-_]?(\\d.*)?\\.(jar)$", Pattern.CASE_INSENSITIVE),
            // PowerMock agent (JUnit 4 + 5 variants)
            Pattern.compile("powermock-agent[\\-_]?(\\d.*)?\\.(jar)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("powermock-module-junit.*agent.*\\.(jar)$", Pattern.CASE_INSENSITIVE),
            // JMockit attaches a Java agent via -javaagent at JVM start
            Pattern.compile("jmockit[\\-_]?(\\d.*)?\\.(jar)$", Pattern.CASE_INSENSITIVE),
            // AspectJ weaving agent
            Pattern.compile("aspectjweaver[\\-_]?(\\d.*)?\\.(jar)$", Pattern.CASE_INSENSITIVE)
    );

    private AgentConflictDetector() {}

    /**
     * Scans every entry in {@code classpath} (colon- or semicolon-separated) and
     * returns a list of detected agent conflicts.
     *
     * @param classpath the additional classpath string provided to Javelin (may be null or blank)
     * @return list of detected conflicts; empty if none found
     */
    public static List<Conflict> detect(String classpath) {
        List<Conflict> conflicts = new ArrayList<>();
        if (classpath == null || classpath.isBlank()) {
            return conflicts;
        }

        String separator = System.getProperty("path.separator", ":");
        for (String entry : classpath.split(java.util.regex.Pattern.quote(separator), -1)) {
            if (entry.isBlank()) {
                continue;
            }
            Path p = Path.of(entry);
            // Only inspect regular files — skip directories and non-existent entries
            if (!Files.isRegularFile(p)) {
                continue;
            }
            String fileName = p.getFileName().toString();
            for (Pattern pattern : CONFLICT_PATTERNS) {
                if (pattern.matcher(fileName).find()) {
                    conflicts.add(new Conflict(fileName, describeConflict(fileName)));
                    break; // one match per entry is enough
                }
            }
        }
        return conflicts;
    }

    /**
     * Returns {@code true} if any conflicting agents are detected.
     */
    public static boolean hasConflicts(String classpath) {
        return !detect(classpath).isEmpty();
    }

    private static String describeConflict(String jarName) {
        String lower = jarName.toLowerCase();
        if (lower.contains("mockito-inline")) {
            return "Mockito-inline uses ByteBuddy for bytecode rewriting and conflicts with JaCoCo's ClassFileTransformer";
        }
        if (lower.contains("byte-buddy-agent")) {
            return "ByteBuddy agent registers its own ClassFileTransformer, conflicting with JaCoCo's online agent";
        }
        if (lower.contains("powermock")) {
            return "PowerMock rewrites class bytecode via its own agent, conflicting with JaCoCo's ClassFileTransformer";
        }
        if (lower.contains("jmockit")) {
            return "JMockit attaches a Java agent at JVM start, conflicting with JaCoCo's online agent";
        }
        if (lower.contains("aspectjweaver")) {
            return "AspectJ load-time weaving uses a ClassFileTransformer that may conflict with JaCoCo's online agent";
        }
        return "Known agent-based library that may conflict with JaCoCo's ClassFileTransformer";
    }
}
