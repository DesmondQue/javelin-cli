package com.javelin.core.parsing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.LineCoverage;
import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.TestResult;

/*
  Matrix Builder

  Responsibilities:
  - converts parsed CoverageData into a boolean spectrum matrix (Test × Line)
  - maps every test case to every line of code it executed

  Notes:
  for each line
    - a11: failed tests that covered this line
    - a10: passed tests that covered this line
    - a01: failed tests that did NOT cover this line (calculated as TotalFailed - a11)
    - a00: passed tests that did NOT cover this line (not used in formula)
  
 */
public class MatrixBuilder {

    /**
      builds a SpectrumMatrix from the parsed coverage data

      @param coverageData The parsed coverage data from DataParser
      @return SpectrumMatrix containing the boolean coverage matrix and test results
     */
    public SpectrumMatrix build(CoverageData coverageData) {
        Map<String, TestResult> testResults = coverageData.getTestResults();
        Map<String, Map<String, Set<Integer>>> coveragePerTest = coverageData.getCoveragePerTest();
        Set<LineCoverage> allLines = coverageData.getAllLineCoverage();

        int totalFailed = 0;
        int totalPassed = 0;

        for (TestResult result : testResults.values()) {
            if (result.passed()) {
                totalPassed++;
            } else {
                totalFailed++;
            }
        }

        //key: "className:lineNumber", value: int[2] where [0]=a11 (failed&covered), [1]=a10 (passed&covered)
        Map<String, int[]> lineCounts = new HashMap<>();

        for (LineCoverage line : allLines) {
            String lineKey = line.className() + ":" + line.lineNumber();
            lineCounts.put(lineKey, new int[2]);
        }

        for (Map.Entry<String, TestResult> testEntry : testResults.entrySet()) {
            String testId = testEntry.getKey();
            TestResult result = testEntry.getValue();
            
            Map<String, Set<Integer>> testCoverage = coveragePerTest.get(testId);
            if (testCoverage == null) {
                continue;
            }

            Set<String> coveredLineKeys = new HashSet<>();
            for (Map.Entry<String, Set<Integer>> classEntry : testCoverage.entrySet()) {
                String className = classEntry.getKey();
                for (Integer lineNum : classEntry.getValue()) {
                    coveredLineKeys.add(className + ":" + lineNum);
                }
            }

            for (String lineKey : coveredLineKeys) {
                int[] counts = lineCounts.get(lineKey);
                if (counts == null) {
                    counts = new int[2];
                    lineCounts.put(lineKey, counts);
                }

                if (result.passed()) {
                    counts[1]++; //a10: passed and covered
                } else {
                    counts[0]++; //a11: failed and covered
                }
            }
        }

        return new SpectrumMatrix(lineCounts, totalFailed, totalPassed);
    }

    /**
      alternative builder for raw test-to-coverage mapping (not tested / to be implemented)
      for per-test coverage data
     
      @param testResults    map of test ID to pass/fail result
      @param testCoverage   map of test ID to set of covered line identifiers
      @param allLineIds     set of all line identifiers in the project
      @return SpectrumMatrix
     */
    public SpectrumMatrix buildFromRaw(
            Map<String, Boolean> testResults,
            Map<String, Set<String>> testCoverage,
            Set<String> allLineIds) {

        int totalFailed = 0;
        int totalPassed = 0;
        for (Boolean passed : testResults.values()) {
            if (passed) {
                totalPassed++;
            } else {
                totalFailed++;
            }
        }

        Map<String, int[]> lineCounts = new HashMap<>();
        
        for (String lineId : allLineIds) {
            lineCounts.put(lineId, new int[2]);
        }

        for (Map.Entry<String, Boolean> testEntry : testResults.entrySet()) {
            String testId = testEntry.getKey();
            boolean passed = testEntry.getValue();
            Set<String> coveredLines = testCoverage.getOrDefault(testId, Set.of());

            for (String lineId : coveredLines) {
                int[] counts = lineCounts.computeIfAbsent(lineId, k -> new int[2]);
                if (passed) {
                    counts[1]++; //a10
                } else {
                    counts[0]++; //a11
                }
            }
        }

        return new SpectrumMatrix(lineCounts, totalFailed, totalPassed);
    }
}
