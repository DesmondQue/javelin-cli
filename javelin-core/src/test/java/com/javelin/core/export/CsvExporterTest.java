package com.javelin.core.export;

import com.javelin.core.model.SuspiciousnessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {

    private final CsvExporter exporter = new CsvExporter();

    @Test
    void exportToString_containsHeader() {
        String csv = exporter.exportToString(List.of());
        assertTrue(csv.startsWith("FullyQualifiedClass,LineNumber,OchiaiScore,Rank"));
    }

    @Test
    void exportToString_singleResult_correctFormat() {
        SuspiciousnessResult r = new SuspiciousnessResult("com.example.Foo", 42, 1.0, 1);
        String csv = exporter.exportToString(List.of(r));

        assertTrue(csv.contains("com.example.Foo,42,1.000000,1"));
    }

    @Test
    void exportToString_scoreFormattedToSixDecimals() {
        SuspiciousnessResult r = new SuspiciousnessResult("com.A", 1, 1.0 / Math.sqrt(2), 1);
        String csv = exporter.exportToString(List.of(r));

        // 1/sqrt(2) ≈ 0.707107
        assertTrue(csv.contains("0.707107"), "score should be formatted to 6 decimal places");
    }

    @Test
    void exportToString_classNameWithComma_quoted() {
        // Commas in class names (unlikely but must be escaped)
        SuspiciousnessResult r = new SuspiciousnessResult("com.a,b", 1, 0.5, 1);
        String csv = exporter.exportToString(List.of(r));

        assertTrue(csv.contains("\"com.a,b\""), "class name with comma should be quoted");
    }

    @Test
    void exportToString_multipleResults_allPresent() {
        List<SuspiciousnessResult> results = List.of(
                new SuspiciousnessResult("com.A", 1, 1.0, 1),
                new SuspiciousnessResult("com.A", 2, 0.5, 2),
                new SuspiciousnessResult("com.B", 10, 0.0, 3)
        );
        String csv = exporter.exportToString(results);
        String[] lines = csv.split(System.lineSeparator());

        assertEquals(4, lines.length, "header + 3 data rows");
    }

    @Test
    void export_writesFileCorrectly(@TempDir Path dir) throws IOException {
        Path output = dir.resolve("results.csv");
        SuspiciousnessResult r = new SuspiciousnessResult("com.example.Bar", 7, 0.75, 1);

        exporter.export(List.of(r), output);

        assertTrue(Files.exists(output));
        String content = Files.readString(output);
        assertTrue(content.contains("com.example.Bar,7,0.750000,1"));
    }

    @Test
    void export_createsParentDirectories(@TempDir Path dir) throws IOException {
        Path output = dir.resolve("nested/dir/results.csv");
        exporter.export(List.of(), output);
        assertTrue(Files.exists(output));
    }

    @Test
    void exportToString_emptyList_onlyHeader() {
        String csv = exporter.exportToString(List.of());
        String[] lines = csv.split(System.lineSeparator());
        assertEquals(1, lines.length, "only the header line should be present");
    }
}
