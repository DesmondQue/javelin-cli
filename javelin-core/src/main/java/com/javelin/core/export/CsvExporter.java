package com.javelin.core.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.javelin.core.model.SuspiciousnessResult;

/**
  CSV Exporter
  
  Responsibilities:
  - formats and writes final suspiciousness output to CSV
  - format: FullyQualifiedClass, LineNumber, OchiaiScore, Rank
  
  Design Notes:
  - required for both UI highlighting and Python statistical analysis (Phase 2 and 3)
  - uses standard CSV format with header row
  - scores are formatted to 6 decimal places
 */
public class CsvExporter {

    private static final String CSV_HEADER = "FullyQualifiedClass,LineNumber,OchiaiScore,Rank";
    private static final String CSV_FORMAT = "%s,%d,%.6f,%d";

    /**
      exports suspiciousness results to a CSV file.
     
      @param results    List of SuspiciousnessResult to export
      @param outputPath Path to the output CSV file
      @throws IOException if the file cannot be written
     */
    public void export(List<SuspiciousnessResult> results, Path outputPath) throws IOException {
        Path parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (SuspiciousnessResult result : results) {
                String line = String.format(CSV_FORMAT,
                        escapeForCsv(result.fullyQualifiedClass()),
                        result.lineNumber(),
                        result.score(),
                        result.rank());
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
      exports results to a CSV string (useful for testing or in-memory operations)
     
      @param results List of SuspiciousnessResult to export
      @return CSV content as a string
     */
    public String exportToString(List<SuspiciousnessResult> results) {
        StringBuilder sb = new StringBuilder();
        
        // Write header
        sb.append(CSV_HEADER).append(System.lineSeparator());

        // Write each result
        for (SuspiciousnessResult result : results) {
            sb.append(String.format(CSV_FORMAT,
                    escapeForCsv(result.fullyQualifiedClass()),
                    result.lineNumber(),
                    result.score(),
                    result.rank()));
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
      escapes a string for CSV format
      if string contains commas, quotes, or newlines, its wrapped in quotes - existing quotes are doubled
     
      @param value The string to escape
      @return Escaped string safe for CSV
     */
    private String escapeForCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuoting = value.contains(",") || 
                               value.contains("\"") || 
                               value.contains("\n") ||
                               value.contains("\r");

        if (needsQuoting) {
            String escaped = value.replace("\"", "\"\""); //quotes safety
            return "\"" + escaped + "\"";
        }

        return value;
    }

    /**
      rxports results filtered by a minimum score threshold
     
      @param results       List of SuspiciousnessResult to export
      @param outputPath    Path to the output CSV file
      @param minScore      Minimum score threshold (inclusive)
      @throws IOException if the file cannot be written
     */
    public void exportFiltered(List<SuspiciousnessResult> results, 
                                Path outputPath, 
                                double minScore) throws IOException {
        List<SuspiciousnessResult> filtered = results.stream()
                .filter(r -> r.score() >= minScore)
                .toList();
        
        export(filtered, outputPath);
    }

    /**
      exports results limited to top N entries
     
      @param results    List of SuspiciousnessResult to export
      @param outputPath Path to the output CSV file
      @param topN       Number of top results to include
      @throws IOException if the file cannot be written
     */
    public void exportTopN(List<SuspiciousnessResult> results, 
                           Path outputPath, 
                           int topN) throws IOException {
        List<SuspiciousnessResult> top = results.stream()
                .limit(topN)
                .toList();
        
        export(top, outputPath);
    }
}
