package com.javelin.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.javelin.core.execution.CoverageRunner;
import com.javelin.core.export.CsvExporter;
import com.javelin.core.math.MutationScoreCalculator;
import com.javelin.core.math.OchiaiCalculator;
import com.javelin.core.math.OchiaiMSCalculator;
import com.javelin.core.model.CoverageData;
import com.javelin.core.model.MutationData;
import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;
import com.javelin.core.model.TestExecResult;
import com.javelin.core.mutation.FaultRegionIdentifier;
import com.javelin.core.mutation.MutationDataParser;
import com.javelin.core.mutation.MutationRunner;
import com.javelin.core.parsing.DataParser;
import com.javelin.core.parsing.MatrixBuilder;
import com.javelin.core.validation.SbflPreconditions;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
  CLI Controller (Main)
  
  Responsibilities:
   - uses Picocli to parse command-line arguments
   - validate input paths
   - orchestrate main pipeline: CoverageRunner -> DataParser -> MatrixBuilder -> OchiaiCalculator -> CsvExporter
 */
@Command(
    name = "javelin",
    mixinStandardHelpOptions = true,
    version = "javelin-core 1.0.0",
    description = "Automated Spectrum-Based Fault Localization for Java",
    header = {
        "",
        "+===============================================================+",
        "|                      Javelin Core v1.0.0                      |",
        "+===============================================================+",
        ""
    },
    descriptionHeading = "%n",
    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Algorithms:",
        "  ochiai      Standard Ochiai SBFL (default). Ranks lines by suspiciousness",
        "              using pass/fail test spectrum data.",
        "  ochiai-ms   Ochiai with Mutation Score weighting. Weights passing tests",
        "              by their mutation-killing strength using scoped PITest analysis.",
        "",
        "Examples:",
        "  javelin -a ochiai -t build/classes/java/main -T build/classes/java/test -o report.csv",
        "  javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv",
        "",
        "SBFL: requires >=1 failing test; 0 passing tests is allowed (lower ranking quality).",
        ""
    }
)
public class Main implements Callable<Integer> {

    @Option(names = {"-a", "--algorithm"}, required = false, paramLabel = "<name>", order = 0,
            description = "Fault localization algorithm: ochiai (default) or ochiai-ms")
    private String algorithm = "ochiai";

    @Option(names = {"-t", "--target"}, required = true, paramLabel = "<dir>", order = 1,
            description = "Path to compiled classes")
    private Path targetPath;

    @Option(names = {"-T", "--test"}, required = true, paramLabel = "<dir>", order = 2,
            description = "Path to test classes")
    private Path testPath;

    @Option(names = {"-o", "--output"}, required = true, paramLabel = "<file>", order = 3,
            description = "Output CSV file path")
    private Path outputPath;

    @Option(names = {"-s", "--source"}, required = false, paramLabel = "<dir>", order = 4,
            description = "Source files path (required for ochiai-ms)")
    private Path sourcePath;

    @Option(names = {"-c", "--classpath"}, required = false, paramLabel = "<path>", order = 5,
            description = "Additional classpath")
    private String additionalClasspath;

    @Option(names = {"-j", "--threads"}, required = false, paramLabel = "<count>", order = 6,
            description = "Number of parallel threads for test execution (default: CPU core count)")
    private int threadCount = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setUsageHelpWidth(300);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.printf("%n+===============================================================+%n");
        System.out.printf("|                          Javelin Core                           |%n");
        System.out.printf("+===============================================================+%n%n");

        //step 0: validate algorithm selection
        String algo = algorithm.toLowerCase().trim();
        if (!algo.equals("ochiai") && !algo.equals("ochiai-ms")) {
            System.err.printf("ERROR: Unknown algorithm '%s'. Valid options: ochiai, ochiai-ms%n", algorithm);
            return 1;
        }

        if (algo.equals("ochiai-ms")) {
            System.out.printf("  Algorithm: Ochiai-MS (Mutation Score weighted)%n%n");
            if (sourcePath == null) {
                System.err.printf("ERROR: --source/-s is required for ochiai-ms (PITest needs source dirs).%n");
                return 1;
            }
        } else {
            System.out.printf("  Algorithm: Ochiai SBFL%n%n");
        }

        //step 1: validate input paths
        if (!validatePaths()) {
            return 1;
        }

        boolean isOchiaiMS = algo.equals("ochiai-ms");
        int totalSteps = isOchiaiMS ? 8 : 5;
        printInputSummary(totalSteps);

        //step 2: run tests with JaCoCo coverage
        System.out.printf("[2/%d] Running tests with coverage instrumentation...%n", totalSteps);
        long testExecStart = System.nanoTime();
        CoverageRunner coverageRunner = new CoverageRunner(targetPath, testPath, additionalClasspath, threadCount);
        List<TestExecResult> testExecResults = coverageRunner.run();
        long testExecTimeMs = (System.nanoTime() - testExecStart) / 1_000_000;
        
        if (testExecResults == null || testExecResults.isEmpty()) {
            System.err.printf("ERROR: Coverage execution failed. No .exec files generated.%n");
            return 1;
        }
        System.out.printf("      Generated %d coverage file(s):%n", testExecResults.size());
        for (TestExecResult execResult : testExecResults) {
            String status = execResult.passed() ? "PASSED" : "FAILED";
            System.out.printf("        - %s [%s]%n", execResult.execFile().getFileName(), status);
        }
        System.out.println();

        //step 3: parse JaCoCo execution data (per-test coverage)
        System.out.printf("[3/%d] Parsing coverage data...%n", totalSteps);
        DataParser dataParser = new DataParser();
        CoverageData coverageData = dataParser.parseMultiple(testExecResults, targetPath);
        
        printCoverageSummary(coverageData);

        SbflPreconditions.ValidationResult validation = SbflPreconditions.evaluate(
                coverageData.getPassedCount(),
                coverageData.getFailedCount()
        );
        if (!validation.canProceed()) {
            System.err.printf("ERROR: %s%n", validation.message());
            return 2;
        }
        if (validation.warning()) {
            System.err.printf("WARNING: %s%n%n", validation.message());
        }

        //step 4: build spectrum hit matrix
        System.out.printf("[4/%d] Building spectrum hit matrix...%n", totalSteps);
        MatrixBuilder matrixBuilder = new MatrixBuilder();
        SpectrumMatrix matrix = matrixBuilder.build(coverageData);

        List<SuspiciousnessResult> results;
        long ochiaiTimeMs = 0;
        long mutationTimeMs = 0;

        if (isOchiaiMS) {
            // Phase 2: Scoped Mutation Analysis

            // Identify fault region (lines covered by failing tests)
            FaultRegionIdentifier regionIdentifier = new FaultRegionIdentifier();
            FaultRegionIdentifier.FaultRegion faultRegion = regionIdentifier.identify(matrix);

            if (faultRegion.targetClassNames().isEmpty()) {
                System.err.printf("ERROR: No lines covered by failing tests. Cannot run mutation analysis.%n");
                return 2;
            }

            System.out.printf("      Fault region: %d class(es), %d unique line(s).%n%n",
                    faultRegion.targetClassNames().size(),
                    faultRegion.targetLines().values().stream().mapToInt(Set::size).sum());

            long mutationStart = System.nanoTime();

            // Run scoped PITest
            System.out.printf("[5/8] Running scoped mutation analysis (PITest)...%n");
            MutationRunner mutationRunner = new MutationRunner(
                    targetPath, testPath, sourcePath, additionalClasspath, threadCount, coverageData);
            Path reportDir = mutationRunner.run(faultRegion.targetClassNames());

            // Parse mutation data
            System.out.printf("[6/8] Parsing mutation results...%n");
            MutationDataParser mutationDataParser = new MutationDataParser();
            MutationData mutationData = mutationDataParser.parse(reportDir);

            System.out.printf("      Mutants: %d total (%d killed, %d survived, %d no coverage).%n",
                    mutationData.mutants().size(),
                    mutationData.getKilledCount(),
                    mutationData.getSurvivedCount(),
                    mutationData.getNoCoverageCount());

            // Compute MS per passing test
            System.out.printf("[7/8] Computing mutation scores per passing test...%n");
            MutationScoreCalculator msCalculator = new MutationScoreCalculator();
            Map<String, Double> mutationScores = msCalculator.calculate(mutationData, coverageData);

            mutationTimeMs = (System.nanoTime() - mutationStart) / 1_000_000;

            // Print mutation score summary
            if (!mutationScores.isEmpty()) {
                double avgMS = mutationScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                long testsWithKills = mutationScores.values().stream().filter(ms -> ms > 0.0).count();
                System.out.printf("      Passing tests with mutation scores: %d (avg MS: %.4f, %d with kills).%n%n",
                        mutationScores.size(), avgMS, testsWithKills);
            } else {
                System.out.printf("      WARNING: No passing tests had mutation scores computed.%n%n");
            }

            // Compute Ochiai-MS suspiciousness scores
            System.out.printf("[8/8] Calculating Ochiai-MS suspiciousness scores...%n");
            long ochiaiMSStart = System.nanoTime();
            OchiaiMSCalculator ochiaiMSCalc = new OchiaiMSCalculator();
            results = ochiaiMSCalc.calculate(matrix, coverageData, mutationScores);
            ochiaiTimeMs = (System.nanoTime() - ochiaiMSStart) / 1_000_000;
            System.out.printf("      Calculated Ochiai-MS suspiciousness for %d line(s).%n%n", results.size());

        } else {
            // Standard Ochiai
            long ochiaiStart = System.nanoTime();
            OchiaiCalculator calculator = new OchiaiCalculator();
            results = calculator.calculate(matrix);
            ochiaiTimeMs = (System.nanoTime() - ochiaiStart) / 1_000_000;
            System.out.printf("      Calculated suspiciousness for %d line(s).%n%n", results.size());
        }

        //export to CSV
        System.out.printf("[%d/%d] Exporting results to CSV...%n", totalSteps, totalSteps);
        CsvExporter exporter = new CsvExporter();
        exporter.export(results, outputPath);
        System.out.printf("      Report saved to: %s%n%n", outputPath.toAbsolutePath());

        printResultsSummary(results);
        if (isOchiaiMS) {
            printTimingSummaryMS(testExecTimeMs, mutationTimeMs, ochiaiTimeMs);
        } else {
            printTimingSummary(testExecTimeMs, ochiaiTimeMs);
        }

        return 0;
    }
    //input summary
    private void printInputSummary(int totalSteps) {
        System.out.printf("[1/%d] Input validation complete.%n%n", totalSteps);
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Configuration | Path                                                    |%n");
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Target Classes| %-56s |%n", truncate(targetPath.toAbsolutePath().toString(), 56));
        System.out.printf("| Test Classes  | %-56s |%n", truncate(testPath.toAbsolutePath().toString(), 56));
        System.out.printf("| Output File   | %-56s |%n", truncate(outputPath.toAbsolutePath().toString(), 56));
        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            System.out.printf("| Classpath     | %-56s |%n", truncate(additionalClasspath, 56));
        }
        System.out.printf("+---------------+---------------------------------------------------------+%n%n");
    }


    //coverage analysis summary
    private void printCoverageSummary(CoverageData coverageData) {
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


    //results summary
    private void printResultsSummary(List<SuspiciousnessResult> results) {
        System.out.printf("+===============================================================+%n");
        System.out.printf("|  Analysis Complete                                            |%n");
        System.out.printf("+===============================================================+%n%n");
        
        if (results.isEmpty()) {
            System.out.printf("No suspicious lines found.%n");
            return;
        }

        System.out.printf("Top 5 Most Suspicious Lines:%n%n");
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
        System.out.printf("| Rank | Class                                      | Line | Score      |%n");
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
        
        int limit = Math.min(5, results.size());
        for (int i = 0; i < limit; i++) {
            SuspiciousnessResult result = results.get(i);
            System.out.printf("| %4d | %-42s | %4d | %10.4f |%n",
                    result.rank(),
                    truncate(result.fullyQualifiedClass(), 42),
                    result.lineNumber(),
                    result.score());
        }
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
    }

    //timing summary
    private void printTimingSummary(long testExecTimeMs, long ochiaiTimeMs) {
        System.out.printf("%nTiming:%n");
        System.out.printf("  Test execution:      %s%n", formatDuration(testExecTimeMs));
        System.out.printf("  Ochiai calculation:  %s%n", formatDuration(ochiaiTimeMs));
        System.out.printf("  Total:               %s%n", formatDuration(testExecTimeMs + ochiaiTimeMs));
    }

    //timing summary for ochiai-ms (includes mutation analysis phase)
    private void printTimingSummaryMS(long testExecTimeMs, long mutationTimeMs, long ochiaiMSTimeMs) {
        System.out.printf("%nTiming:%n");
        System.out.printf("  Test execution:      %s%n", formatDuration(testExecTimeMs));
        System.out.printf("  Mutation analysis:   %s%n", formatDuration(mutationTimeMs));
        System.out.printf("  Ochiai-MS scoring:   %s%n", formatDuration(ochiaiMSTimeMs));
        System.out.printf("  Total:               %s%n", formatDuration(testExecTimeMs + mutationTimeMs + ochiaiMSTimeMs));
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.2fs", ms / 1000.0);
    }

    //truncates a string to fit a specified width
    private String truncate(String str, int maxWidth) { //untested on other commandlines
        if (str.length() <= maxWidth) {
            return str;
        }
        return "..." + str.substring(str.length() - maxWidth + 3);
    }

    /**
      validates that all required input paths exist.
     */
    private boolean validatePaths() {
        boolean valid = true;

        if (!Files.exists(targetPath)) {
            System.err.printf("ERROR: Target path does not exist: %s%n", targetPath);
            valid = false;
        } else if (!Files.isDirectory(targetPath)) {
            System.err.printf("ERROR: Target path is not a directory: %s%n", targetPath);
            valid = false;
        }

        if (!Files.exists(testPath)) {
            System.err.printf("ERROR: Test path does not exist: %s%n", testPath);
            valid = false;
        } else if (!Files.isDirectory(testPath)) {
            System.err.printf("ERROR: Test path is not a directory: %s%n", testPath);
            valid = false;
        }

        Path outputDir = outputPath.getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (Exception e) {
                System.err.printf("ERROR: Cannot create output directory: %s%n", outputDir);
                valid = false;
            }
        }

        return valid;
    }
}
