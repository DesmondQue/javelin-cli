package com.javelin.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.javelin.core.execution.CoverageRunner;
import com.javelin.core.execution.ProcessExecutor;
import com.javelin.core.export.ConsoleReporter;
import com.javelin.core.export.CsvExporter;
import com.javelin.core.math.MethodAggregator;
import com.javelin.core.math.MutationScoreCalculator;
import com.javelin.core.math.OchiaiCalculator;
import com.javelin.core.math.OchiaiMSCalculator;
import com.javelin.core.model.MethodSuspiciousnessResult;
import com.javelin.core.model.CoverageData;
import com.javelin.core.model.ExitCode;
import com.javelin.core.model.MutationData;
import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;
import com.javelin.core.model.TestExecResult;
import com.javelin.core.mutation.FaultRegionIdentifier;
import com.javelin.core.mutation.MutationDataParser;
import com.javelin.core.mutation.MutationRunner;
import com.javelin.core.parsing.DataParser;
import com.javelin.core.parsing.MatrixBuilder;
import com.javelin.core.validation.AgentConflictDetector;
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
    sortOptions = false,
    versionProvider = VersionProvider.class,
    customSynopsis = {
        "javelin -t <dir> -T <dir> -o <file> [OPTIONS]"
    },
    description = "Automated Spectrum-Based Fault Localization for Java",
    header = {
        "",
        "+==================================================+",
        "|                   Javelin Core                    |",
        "+==================================================+",
        ""
    },
    descriptionHeading = "%n",
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Algorithms:",
        "  ochiai      Ochiai SBFL (default)",
        "  ochiai-ms   Ochiai weighted by mutation score (needs -s)",
        "",
        "Granularity (-g) and ranking:",
        "  statement   Line-level output (default)",
        "  method      Method-level output; --ranking: dense (default) or average",
        "",
        "Examples:",
        "  javelin -t classes/main -T classes/test -o report.csv",
        "  javelin -a ochiai-ms -t classes/main -T classes/test",
        "          -s src/main/java -o results.csv -g method --ranking average",
        "",
        "Requires >=1 failing test.",
        ""
    }
)
public class Main implements Callable<Integer> {

    @Option(names = {"-t", "--target"}, required = true, paramLabel = "<dir>", order = 0,
            description = "Compiled classes directory")
    private Path targetPath;

    @Option(names = {"-T", "--test"}, required = true, paramLabel = "<dir>", order = 1,
            description = "Test classes directory")
    private Path testPath;

    @Option(names = {"-o", "--output"}, required = true, paramLabel = "<file>", order = 2,
            description = "Output CSV file path")
    private Path outputPath;

    @Option(names = {"-a", "--algorithm"}, required = false, paramLabel = "<name>", order = 3,
            description = "ochiai (default) or ochiai-ms")
    private String algorithm = "ochiai";

    @Option(names = {"-s", "--source"}, required = false, paramLabel = "<dir>", order = 4,
            description = "Source directory (required for ochiai-ms)")
    private Path sourcePath;

    @Option(names = {"-c", "--classpath"}, required = false, paramLabel = "<path>", order = 5,
            description = "Additional classpath entries")
    private String additionalClasspath;

    @Option(names = {"-j", "--threads"}, required = false, paramLabel = "<count>", order = 6,
            description = "Parallel threads (default: CPU cores)")
    private int threadCount = Runtime.getRuntime().availableProcessors();

    @Option(names = {"--pitest-threads"}, required = false, paramLabel = "<count>", order = 7,
            description = "PITest threads (default: CPU cores)")
    private int pitestThreadCount = Runtime.getRuntime().availableProcessors();

    @Option(names = {"--offline"}, required = false, order = 8,
            description = "Offline instrumentation (avoids agent conflicts)")
    private boolean offlineMode = false;

    @Option(names = {"-q", "--quiet"}, required = false, order = 9,
            description = "Suppress progress output")
    private boolean quiet = false;

    @Option(names = {"-g", "--granularity"}, required = false, paramLabel = "<level>", order = 10,
            description = "Output granularity: statement (default) or method")
    private String granularity = "statement";

    @Option(names = {"--ranking"}, required = false, paramLabel = "<strategy>", order = 11,
            description = "Ranking strategy: dense (default) or average")
    private String rankingStrategy = "dense";

    @Option(names = {"--jvm-home"}, required = false, paramLabel = "<path>", order = 12,
            description = "JVM home to use for test execution subprocesses (default: current JVM)")
    private Path jvmHome = null;

    private void progress(String msg) {
        if (!quiet) System.err.printf("[javelin] %s%n", msg);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setUsageHelpAutoWidth(true);
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
            return ExitCode.GENERAL_ERROR;
        }

        if (algo.equals("ochiai-ms")) {
            System.out.printf("  Algorithm: Ochiai-MS (Mutation Score weighted)%n%n");
            if (sourcePath == null) {
                System.err.printf("ERROR: --source/-s is required for ochiai-ms (PITest needs source dirs).%n");
                return ExitCode.GENERAL_ERROR;
            }
        } else {
            System.out.printf("  Algorithm: Ochiai SBFL%n%n");
        }

        //step 0b: validate granularity and ranking
        String gran = granularity.toLowerCase().trim();
        if (!gran.equals("statement") && !gran.equals("method")) {
            System.err.printf("ERROR: Unknown granularity '%s'. Valid options: statement, method%n", granularity);
            return ExitCode.GENERAL_ERROR;
        }
        String ranking = rankingStrategy.toLowerCase().trim();
        if (!ranking.equals("dense") && !ranking.equals("average")) {
            System.err.printf("ERROR: Unknown ranking strategy '%s'. Valid options: dense, average%n", rankingStrategy);
            return ExitCode.GENERAL_ERROR;
        }
        boolean isMethodLevel = gran.equals("method");
        boolean useAverageRank = ranking.equals("average");

        if (useAverageRank && !isMethodLevel) {
            System.err.printf("WARNING: --ranking average is only supported with -g method. Using dense ranking.%n%n");
            useAverageRank = false;
        }

        if (isMethodLevel) {
            System.out.printf("  Granularity: Method-level (max-score aggregation)%n");
            System.out.printf("  Ranking: %s%n%n", useAverageRank ? "Average (MID)" : "Dense");
        }

        //step 0c: validate --jvm-home if provided
        if (jvmHome != null) {
            Path javaBin = jvmHome.resolve(ProcessExecutor.isWindows() ? "bin/java.exe" : "bin/java");
            if (!Files.exists(javaBin)) {
                System.err.printf("ERROR: --jvm-home '%s' does not contain bin/java%n", jvmHome);
                return ExitCode.GENERAL_ERROR;
            }
            System.out.printf("  Test JVM: %s%n%n", javaBin.toAbsolutePath());
        }

        //step 1: validate input paths
        int pathValidation = validatePaths();
        if (pathValidation != ExitCode.SUCCESS) {
            return pathValidation;
        }

        boolean isOchiaiMS = algo.equals("ochiai-ms");
        int totalSteps = isOchiaiMS ? 8 : 5;
        ConsoleReporter.printInputSummary(totalSteps,
                targetPath.toAbsolutePath().toString(),
                testPath.toAbsolutePath().toString(),
                outputPath.toAbsolutePath().toString(),
                additionalClasspath);

        //step 2: run tests with JaCoCo coverage
        // Auto-detect agent conflicts and switch to offline mode if needed
        if (!offlineMode) {
            List<AgentConflictDetector.Conflict> conflicts =
                    AgentConflictDetector.detect(additionalClasspath);
            if (!conflicts.isEmpty()) {
                System.out.printf("  [AUTO] Agent conflict(s) detected on classpath — switching to offline instrumentation mode:%n");
                for (AgentConflictDetector.Conflict c : conflicts) {
                    System.out.printf("         • %s: %s%n", c.jarName(), c.reason());
                }
                System.out.printf("         (use --offline explicitly to suppress this message)%n%n");
                offlineMode = true;
            }
        }
        System.out.printf("[2/%d] Running tests with coverage instrumentation%s...%n",
                totalSteps, offlineMode ? " (offline mode)" : "");
        progress("Running tests with coverage instrumentation" + (offlineMode ? " (offline mode)" : "") + "...");
        long testExecStart = System.nanoTime();
        CoverageRunner coverageRunner = new CoverageRunner(targetPath, testPath, additionalClasspath, offlineMode, quiet, jvmHome);
        List<TestExecResult> testExecResults = coverageRunner.run();
        long testExecTimeMs = (System.nanoTime() - testExecStart) / 1_000_000;
        
        if (testExecResults == null || testExecResults.isEmpty()) {
            System.err.printf("ERROR: Coverage execution failed. No .exec files generated.%n");
            return ExitCode.COVERAGE_FAILED;
        }
        System.out.printf("      Generated %d coverage file(s):%n", testExecResults.size());
        for (TestExecResult execResult : testExecResults) {
            String status = execResult.passed() ? "PASSED" : "FAILED";
            System.out.printf("        - %s [%s]%n", execResult.execFile().getFileName(), status);
        }
        System.out.println();

        //step 3: parse JaCoCo execution data (per-test coverage)
        progress("Parsing coverage data...");
        System.out.printf("[3/%d] Parsing coverage data...%n", totalSteps);
        DataParser dataParser = new DataParser();
        CoverageData coverageData = dataParser.parseMultiple(testExecResults, targetPath);
        
        ConsoleReporter.printCoverageSummary(coverageData);

        SbflPreconditions.ValidationResult validation = SbflPreconditions.evaluate(
                coverageData.getPassedCount(),
                coverageData.getFailedCount()
        );
        if (!validation.canProceed()) {
            System.err.printf("ERROR: %s%n", validation.message());
            return ExitCode.NO_FAILING_TESTS;
        }
        if (validation.warning()) {
            System.err.printf("WARNING: %s%n%n", validation.message());
        }

        //step 4: build spectrum hit matrix
        progress("Building spectrum matrix...");
        System.out.printf("[4/%d] Building spectrum hit matrix...%n", totalSteps);
        MatrixBuilder matrixBuilder = new MatrixBuilder();
        SpectrumMatrix matrix = matrixBuilder.build(coverageData);

        List<SuspiciousnessResult> results;
        long ochiaiTimeMs = 0;
        long mutationTimeMs = 0;

        if (isOchiaiMS) {
            // Phase 2: Scoped Mutation Analysis

            // Identify fault region (lines covered by failing tests)
            progress("Identifying fault region...");
            FaultRegionIdentifier regionIdentifier = new FaultRegionIdentifier();
            FaultRegionIdentifier.FaultRegion faultRegion = regionIdentifier.identify(matrix);

            if (faultRegion.targetClassNames().isEmpty()) {
                System.err.printf("ERROR: No lines covered by failing tests. Cannot run mutation analysis.%n");
                return ExitCode.NO_FAILING_TESTS;
            }

            System.out.printf("      Fault region: %d class(es), %d unique line(s).%n%n",
                    faultRegion.targetClassNames().size(),
                    faultRegion.targetLines().values().stream().mapToInt(Set::size).sum());

            long mutationStart = System.nanoTime();

            // Run scoped PITest
            progress("Running PITest mutation analysis...");
            System.out.printf("[5/8] Running scoped mutation analysis (PITest)...%n");
            MutationRunner mutationRunner = new MutationRunner(
                    targetPath, testPath, sourcePath, additionalClasspath, pitestThreadCount, coverageData, quiet, jvmHome);
            Path reportDir;
            try {
                reportDir = mutationRunner.run(faultRegion.targetClassNames());
            } catch (IOException e) {
                System.err.printf("ERROR: PITest mutation analysis failed: %s%n", e.getMessage());
                return ExitCode.MUTATION_FAILED;
            }

            // Parse mutation data
            progress("Parsing mutation results...");
            System.out.printf("[6/8] Parsing mutation results...%n");
            MutationDataParser mutationDataParser = new MutationDataParser();
            MutationData mutationData = mutationDataParser.parse(reportDir);

            System.out.printf("      Mutants: %d total (%d killed, %d survived, %d no coverage).%n",
                    mutationData.mutants().size(),
                    mutationData.getKilledCount(),
                    mutationData.getSurvivedCount(),
                    mutationData.getNoCoverageCount());

            // Compute MS per passing test
            progress("Computing mutation scores per test...");
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
            progress("Calculating Ochiai-MS suspiciousness scores...");
            System.out.printf("[8/8] Calculating Ochiai-MS suspiciousness scores...%n");
            long ochiaiMSStart = System.nanoTime();
            OchiaiMSCalculator ochiaiMSCalc = new OchiaiMSCalculator();
            results = ochiaiMSCalc.calculate(matrix, coverageData, mutationScores);
            ochiaiTimeMs = (System.nanoTime() - ochiaiMSStart) / 1_000_000;
            System.out.printf("      Calculated Ochiai-MS suspiciousness for %d line(s).%n%n", results.size());

        } else {
            // Standard Ochiai
            progress("Calculating Ochiai suspiciousness scores...");
            long ochiaiStart = System.nanoTime();
            OchiaiCalculator calculator = new OchiaiCalculator();
            results = calculator.calculate(matrix);
            ochiaiTimeMs = (System.nanoTime() - ochiaiStart) / 1_000_000;
            System.out.printf("      Calculated suspiciousness for %d line(s).%n%n", results.size());
        }

        //export to CSV
        progress("Writing results to " + outputPath + "...");
        System.out.printf("[%d/%d] Exporting results to CSV...%n", totalSteps, totalSteps);
        CsvExporter exporter = new CsvExporter();
        int rankedCount;

        if (isMethodLevel) {
            if (!coverageData.hasMethodMapping()) {
                System.err.printf("ERROR: Method mapping not available. Cannot aggregate to method level.%n");
                return ExitCode.GENERAL_ERROR;
            }
            MethodAggregator aggregator = new MethodAggregator();
            List<MethodSuspiciousnessResult> methodResults = aggregator.aggregate(
                    results, coverageData.methodMapping(), useAverageRank);
            rankedCount = methodResults.size();
            System.out.printf("      Aggregated %d line(s) into %d method(s).%n", results.size(), rankedCount);

            try {
                exporter.exportMethods(methodResults, outputPath);
            } catch (IOException e) {
                System.err.printf("ERROR: Could not write output CSV: %s%n", e.getMessage());
                return ExitCode.OUTPUT_WRITE_ERROR;
            }
            System.out.printf("      Report saved to: %s%n%n", outputPath.toAbsolutePath());

            ConsoleReporter.printMethodResultsSummary(methodResults);
        } else {
            rankedCount = results.size();
            try {
                exporter.export(results, outputPath);
            } catch (IOException e) {
                System.err.printf("ERROR: Could not write output CSV: %s%n", e.getMessage());
                return ExitCode.OUTPUT_WRITE_ERROR;
            }
            System.out.printf("      Report saved to: %s%n%n", outputPath.toAbsolutePath());

            ConsoleReporter.printResultsSummary(results);
        }

        if (isOchiaiMS) {
            ConsoleReporter.printTimingSummaryMS(testExecTimeMs, mutationTimeMs, ochiaiTimeMs);
        } else {
            ConsoleReporter.printTimingSummary(testExecTimeMs, ochiaiTimeMs);
        }

        long totalMs = testExecTimeMs + mutationTimeMs + ochiaiTimeMs;
        progress(String.format("Done. %d %s ranked in %s.", rankedCount,
                isMethodLevel ? "method(s)" : "line(s)",
                ConsoleReporter.formatDuration(totalMs)));

        return ExitCode.SUCCESS;
    }
    /**
      validates that all required input paths exist.
      Returns ExitCode.SUCCESS (0) on success, or a specific ExitCode on failure.
     */
    private int validatePaths() {
        if (!Files.exists(targetPath)) {
            System.err.printf("ERROR: Target path does not exist: %s%n", targetPath);
            return ExitCode.TARGET_NOT_FOUND;
        } else if (!Files.isDirectory(targetPath)) {
            System.err.printf("ERROR: Target path is not a directory: %s%n", targetPath);
            return ExitCode.TARGET_NOT_FOUND;
        }

        if (!Files.exists(testPath)) {
            System.err.printf("ERROR: Test path does not exist: %s%n", testPath);
            return ExitCode.TEST_NOT_FOUND;
        } else if (!Files.isDirectory(testPath)) {
            System.err.printf("ERROR: Test path is not a directory: %s%n", testPath);
            return ExitCode.TEST_NOT_FOUND;
        }

        Path outputDir = outputPath.getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (Exception e) {
                System.err.printf("ERROR: Cannot create output directory: %s%n", outputDir);
                return ExitCode.OUTPUT_WRITE_ERROR;
            }
        }

        return ExitCode.SUCCESS;
    }
}
