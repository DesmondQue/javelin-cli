package com.javelin.core.math;

import com.javelin.core.model.MethodInfo;
import com.javelin.core.model.MethodSuspiciousnessResult;
import com.javelin.core.model.SuspiciousnessResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MethodAggregatorTest {

    private final MethodAggregator aggregator = new MethodAggregator();

    @Test
    void singleMethod_maxScoreUsed() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 10, 0.8, 1),
                new SuspiciousnessResult("com.Foo", 11, 0.5, 2),
                new SuspiciousnessResult("com.Foo", 12, 0.3, 3)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(new MethodInfo("com.Foo", "doIt", "()V", 10, 15))
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(1, results.size());
        assertEquals(0.8, results.get(0).score(), 1e-9);
        assertEquals("doIt", results.get(0).methodName());
    }

    @Test
    void multipleMethodsDifferentScores_rankedCorrectly() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 5, 0.9, 1),
                new SuspiciousnessResult("com.Foo", 15, 0.4, 2)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(
                        new MethodInfo("com.Foo", "high", "()V", 1, 10),
                        new MethodInfo("com.Foo", "low", "()V", 11, 20)
                )
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(2, results.size());
        assertEquals("high", results.get(0).methodName());
        assertEquals(1.0, results.get(0).rank(), 1e-9);
        assertEquals("low", results.get(1).methodName());
        assertEquals(2.0, results.get(1).rank(), 1e-9);
    }

    @Test
    void tiedScores_denseRanking() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 5, 0.7, 1),
                new SuspiciousnessResult("com.Foo", 15, 0.7, 1)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(
                        new MethodInfo("com.Foo", "a", "()V", 1, 10),
                        new MethodInfo("com.Foo", "b", "()V", 11, 20)
                )
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.rank() == 1.0));
    }

    @Test
    void tiedScores_averageRanking_midFormula() {
        // 4 methods: scores [1.0, 0.7, 0.7, 0.3] → ranks [1.0, 2.5, 2.5, 4.0]
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 1, 1.0, 1),
                new SuspiciousnessResult("com.Foo", 11, 0.7, 2),
                new SuspiciousnessResult("com.Foo", 21, 0.7, 2),
                new SuspiciousnessResult("com.Foo", 31, 0.3, 3)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(
                        new MethodInfo("com.Foo", "a", "()V", 1, 9),
                        new MethodInfo("com.Foo", "b", "()V", 10, 19),
                        new MethodInfo("com.Foo", "c", "()V", 20, 29),
                        new MethodInfo("com.Foo", "d", "()V", 30, 39)
                )
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, true);

        assertEquals(4, results.size());
        assertEquals(1.0, results.get(0).rank(), 1e-9);
        assertEquals(2.5, results.get(1).rank(), 1e-9);
        assertEquals(2.5, results.get(2).rank(), 1e-9);
        assertEquals(4.0, results.get(3).rank(), 1e-9);
    }

    @Test
    void lineNotInAnyMethod_groupedAsClassLevel() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 50, 0.9, 1)
        );
        // No method contains line 50
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(new MethodInfo("com.Foo", "doIt", "()V", 1, 10))
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(1, results.size());
        assertEquals("<class-level>", results.get(0).methodName());
    }

    @Test
    void emptyLineResults_returnsEmpty() {
        List<MethodSuspiciousnessResult> results = aggregator.aggregate(
                List.of(), Map.of(), false);
        assertTrue(results.isEmpty());
    }

    @Test
    void constructorMethod_included() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 5, 0.6, 1)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(new MethodInfo("com.Foo", "<init>", "()V", 1, 10))
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(1, results.size());
        assertEquals("<init>", results.get(0).methodName());
    }

    @Test
    void overloadedMethods_distinguishedByDescriptor() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 5, 0.8, 1),
                new SuspiciousnessResult("com.Foo", 15, 0.4, 2)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(
                        new MethodInfo("com.Foo", "add", "(II)I", 1, 10),
                        new MethodInfo("com.Foo", "add", "(DD)D", 11, 20)
                )
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(2, results.size());
        assertEquals("(II)I", results.get(0).descriptor());
        assertEquals("(DD)D", results.get(1).descriptor());
    }

    @Test
    void multipleClasses_aggregatedPerMethod() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.A", 5, 0.9, 1),
                new SuspiciousnessResult("com.B", 5, 0.3, 2)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.A", List.of(new MethodInfo("com.A", "foo", "()V", 1, 10)),
                "com.B", List.of(new MethodInfo("com.B", "bar", "()V", 1, 10))
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, false);

        assertEquals(2, results.size());
        assertEquals("com.A", results.get(0).fullyQualifiedClass());
        assertEquals("com.B", results.get(1).fullyQualifiedClass());
    }

    @Test
    void averageRank_singleElement_rankIsOne() {
        List<SuspiciousnessResult> lines = List.of(
                new SuspiciousnessResult("com.Foo", 5, 0.5, 1)
        );
        Map<String, List<MethodInfo>> mapping = Map.of(
                "com.Foo", List.of(new MethodInfo("com.Foo", "m", "()V", 1, 10))
        );

        List<MethodSuspiciousnessResult> results = aggregator.aggregate(lines, mapping, true);

        assertEquals(1, results.size());
        assertEquals(1.0, results.get(0).rank(), 1e-9);
    }
}
