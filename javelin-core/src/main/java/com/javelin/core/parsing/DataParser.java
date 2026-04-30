package com.javelin.core.parsing;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.LineCoverage;
import com.javelin.core.model.MethodInfo;
import com.javelin.core.model.TestExecResult;
import com.javelin.core.model.TestResult;

/*
 Data Parser
 
 Responsibilities:
 - reads raw jacoco.exec binary file
 - converts binary execution data to structured CoverageData
  
 Design Notes:
 - uses jacoco's ExecutionDataReader to parse the binary format
 - maps class names to line level coverage information
 */
public class DataParser {

    /**
     * @deprecated Use {@link #parseFromEntries(List, Path)} instead for per-test coverage.
     * This method uses aggregated coverage which produces inaccurate SBFL results.
     * 
     * Parses a single jacoco execution data file (aggregated coverage).
     * 
     * @param execFile   path to the jacoco.exec file
     * @param classesDir path to the directory containing the compiled .class files
     * @return CoverageData containing parsed coverage information
     * @throws IOException if the file cannot be read
     */
    @Deprecated
    public CoverageData parse(Path execFile, Path classesDir) throws IOException {

        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        SessionInfoStore sessionInfoStore = new SessionInfoStore();

        try (FileInputStream fis = new FileInputStream(execFile.toFile())) {
            ExecutionDataReader reader = new ExecutionDataReader(fis);
            reader.setExecutionDataVisitor(executionDataStore);
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.read();
        }

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
        
        analyzeDirectory(analyzer, classesDir);

        return buildCoverageData(coverageBuilder, executionDataStore);
    }

    
    private void analyzeDirectory(Analyzer analyzer, Path directory) throws IOException {
        if (!Files.exists(directory)) {
            throw new IOException("Classes directory does not exist: " + directory);
        }

        Files.walk(directory)
             .filter(path -> path.toString().endsWith(".class"))
             .forEach(classFile -> {
                 try (FileInputStream fis = new FileInputStream(classFile.toFile())) {
                     analyzer.analyzeClass(fis, classFile.toString());
                 } catch (IOException e) {
                     System.err.println("Warning: Could not analyze class file: " + classFile);
                 }
             });
    }

    private CoverageData buildCoverageData(CoverageBuilder coverageBuilder,
                                            ExecutionDataStore executionDataStore) {
        Map<String, Set<Integer>> coveredLinesByClass = new HashMap<>();
        Set<LineCoverage> allLineCoverage = new HashSet<>();

        for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
            String className = classCoverage.getName().replace('/', '.');
            Set<Integer> coveredLines = new HashSet<>();

            int firstLine = classCoverage.getFirstLine();
            int lastLine = classCoverage.getLastLine();
            
            for (int lineNum = firstLine; lineNum <= lastLine; lineNum++) {
                ILine line = classCoverage.getLine(lineNum);
                int status = line.getStatus();
                
                if (status != ICounter.EMPTY) {
                    boolean covered = (status == ICounter.FULLY_COVERED || 
                                       status == ICounter.PARTLY_COVERED);
                    
                    allLineCoverage.add(new LineCoverage(className, lineNum, covered));
                    
                    if (covered) {
                        coveredLines.add(lineNum);
                    }
                }
            }

            if (!coveredLines.isEmpty()) {
                coveredLinesByClass.put(className, coveredLines);
            }
        }

        //LEGACY: uses aggregated coverage (not per-test)
        Map<String, TestResult> testResults = extractTestResults(executionDataStore);
        Map<String, Map<String, Set<Integer>>> coveragePerTest = buildCoveragePerTest(
                testResults, coveredLinesByClass);

        return new CoverageData(testResults, coveragePerTest, allLineCoverage);
    }

    /**
     * LEGACY: Used by deprecated parse() method.
     * Extracts test results from execution data.
     * Note: jacoco's exec file doesn't contain pass/fail information.
     */
    private Map<String, TestResult> extractTestResults(ExecutionDataStore executionDataStore) {
        Map<String, TestResult> results = new HashMap<>();

        for (ExecutionData data : executionDataStore.getContents()) {
            String className = data.getName().replace('/', '.');
            
            if (className.endsWith("Test") || className.endsWith("Tests") ||
                className.contains("Test$") || className.contains("Tests$")) {
                // placeholder test result
                // this would come from JUnit execution in real impl
                results.put(className, new TestResult(className, true, null));
            }
        }
        
        return results;
    }

    /**
     * LEGACY: Used by deprecated parse() method
     * with aggregated exec data, all tests share the same coverage
     * use parseFromEntries() for true per-test coverage
     */
    private Map<String, Map<String, Set<Integer>>> buildCoveragePerTest(
            Map<String, TestResult> testResults,
            Map<String, Set<Integer>> aggregatedCoverage) {
        
        Map<String, Map<String, Set<Integer>>> coveragePerTest = new HashMap<>();
        
        for (String testId : testResults.keySet()) {
            coveragePerTest.put(testId, new HashMap<>(aggregatedCoverage));
        }
        
        return coveragePerTest;
    }

    /**
     * Parses in-memory coverage data from TestExecResult entries (no .exec file I/O).
     * This is the GZoltar-style approach: coverage bytes are passed directly from the
     * subprocess via a single binary file, avoiding per-test disk reads.
     *
     * @param entries    list of TestExecResult containing in-memory coverage bytes
     * @param classesDir path to the directory containing the compiled .class files
     * @return CoverageData containing per-test coverage information
     * @throws IOException if class files cannot be read
     */
    public CoverageData parseFromEntries(List<TestExecResult> entries,
                                          Path classesDir) throws IOException {
        Map<String, TestResult> testResults = new HashMap<>();
        Map<String, Map<String, Set<Integer>>> coveragePerTest = new HashMap<>();
        Set<LineCoverage> allLineCoverage = new HashSet<>();
        Map<String, List<MethodInfo>> methodMapping = new HashMap<>();

        // Phase 1: Load all .class bytes once
        Map<String, byte[]> classCache = loadClassCache(classesDir);

        // Phase 2: Discover all executable lines and method boundaries
        ExecutionDataStore emptyStore = new ExecutionDataStore();
        CoverageBuilder discoveryBuilder = new CoverageBuilder();
        Analyzer discoveryAnalyzer = new Analyzer(emptyStore, discoveryBuilder);
        for (Map.Entry<String, byte[]> entry : classCache.entrySet()) {
            try {
                discoveryAnalyzer.analyzeClass(entry.getValue(), entry.getKey());
            } catch (IOException e) {
                System.err.println("Warning: Could not analyze class: " + entry.getKey());
            }
        }
        for (IClassCoverage classCov : discoveryBuilder.getClasses()) {
            String className = classCov.getName().replace('/', '.');
            for (int ln = classCov.getFirstLine(); ln <= classCov.getLastLine(); ln++) {
                if (classCov.getLine(ln).getStatus() != ICounter.EMPTY) {
                    allLineCoverage.add(new LineCoverage(className, ln, false));
                }
            }
            List<MethodInfo> methods = extractMethodBoundaries(classCov, className);
            if (!methods.isEmpty()) {
                methodMapping.put(className, methods);
            }
        }

        // Phase 3: Per-test coverage from in-memory bytes
        for (TestExecResult testEntry : entries) {
            String testClassName = testEntry.testClassName();
            boolean passed = testEntry.passed();
            byte[] coverageBytes = testEntry.coverageData();

            if (coverageBytes == null || coverageBytes.length == 0) {
                coveragePerTest.put(testClassName, new HashMap<>());
                testResults.put(testClassName, new TestResult(testClassName, passed, null));
                continue;
            }

            ExecutionDataStore executionDataStore = new ExecutionDataStore();
            SessionInfoStore sessionInfoStore = new SessionInfoStore();

            try (ByteArrayInputStream bis = new ByteArrayInputStream(coverageBytes)) {
                ExecutionDataReader reader = new ExecutionDataReader(bis);
                reader.setExecutionDataVisitor(executionDataStore);
                reader.setSessionInfoVisitor(sessionInfoStore);
                reader.read();
            }

            // Only analyze classes that this test actually touched
            Set<String> touchedClasses = new HashSet<>();
            for (ExecutionData ed : executionDataStore.getContents()) {
                touchedClasses.add(ed.getName());
            }

            CoverageBuilder coverageBuilder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
            for (String internalName : touchedClasses) {
                byte[] bytes = classCache.get(internalName);
                if (bytes != null) {
                    try {
                        analyzer.analyzeClass(bytes, internalName);
                    } catch (IOException e) {
                        // skip
                    }
                }
            }

            Map<String, Set<Integer>> testCoverage = new HashMap<>();
            for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
                String className = classCoverage.getName().replace('/', '.');
                Set<Integer> coveredLines = new HashSet<>();

                for (int lineNum = classCoverage.getFirstLine(); lineNum <= classCoverage.getLastLine(); lineNum++) {
                    int status = classCoverage.getLine(lineNum).getStatus();
                    if (status == ICounter.FULLY_COVERED || status == ICounter.PARTLY_COVERED) {
                        coveredLines.add(lineNum);
                        LineCoverage uncovered = new LineCoverage(className, lineNum, false);
                        allLineCoverage.remove(uncovered);
                        allLineCoverage.add(new LineCoverage(className, lineNum, true));
                    }
                }

                if (!coveredLines.isEmpty()) {
                    testCoverage.put(className, coveredLines);
                }
            }

            coveragePerTest.put(testClassName, testCoverage);
            testResults.put(testClassName, new TestResult(testClassName, passed, null));
        }

        return new CoverageData(testResults, coveragePerTest, allLineCoverage, methodMapping);
    }

    private Map<String, byte[]> loadClassCache(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            throw new IOException("Classes directory does not exist: " + directory);
        }
        Map<String, byte[]> cache = new HashMap<>();
        Path absDir = directory.toAbsolutePath();
        Files.walk(absDir)
             .filter(path -> path.toString().endsWith(".class"))
             .forEach(classFile -> {
                 try {
                     // Derive internal class name from relative path (e.g. org/jfree/chart/Foo)
                     String relative = absDir.relativize(classFile).toString();
                     String internalName = relative.replace('\\', '/');
                     if (internalName.endsWith(".class")) {
                         internalName = internalName.substring(0, internalName.length() - 6);
                     }
                     cache.put(internalName, Files.readAllBytes(classFile));
                 } catch (IOException e) {
                     System.err.println("Warning: Could not read class file: " + classFile);
                 }
             });
        return cache;
    }

    private List<MethodInfo> extractMethodBoundaries(IClassCoverage classCoverage, String className) {
        List<MethodInfo> methods = new ArrayList<>();
        for (IMethodCoverage methodCov : classCoverage.getMethods()) {
            int mFirst = methodCov.getFirstLine();
            int mLast = methodCov.getLastLine();
            if (mFirst <= 0 || mLast <= 0) {
                continue;
            }
            String mName = methodCov.getName();
            if (mName.contains("$")) {
                continue;
            }
            methods.add(new MethodInfo(className, mName, methodCov.getDesc(), mFirst, mLast));
        }
        return methods;
    }
}
