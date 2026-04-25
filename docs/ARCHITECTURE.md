# Architecture

Javelin follows a layered pipeline architecture:

```
CLI Input → Coverage Collection → Data Parsing → Matrix Building → SBFL Scoring → [Method Aggregation] → CSV Export
```

The method aggregation step is optional, activated by `-g method`.

| Layer | Components | Responsibility |
|---|---|---|
| **Controller** | `Main.java` | CLI parsing (Picocli), input validation, pipeline orchestration |
| **Execution** | `CoverageRunner`, `OfflineInstrumenter` | JaCoCo-instrumented test execution (online agent or offline pre-instrumentation) |
| **Validation** | `SbflPreconditions`, `AgentConflictDetector` | SBFL precondition checks and agent conflict auto-detection |
| **Data Processing** | `DataParser`, `MatrixBuilder` | Parse `.exec` coverage files, build spectrum hit matrix; extract method boundaries from JaCoCo |
| **Math** | `OchiaiCalculator`, `OchiaiMSCalculator` | Compute line-level suspiciousness scores |
| **Aggregation** | `MethodAggregator` | Aggregate line scores to method-level (max score per method), apply dense or average ranking |
| **Mutation** *(ochiai-ms only)* | `MutationRunner`, `MutationScoreCalculator` | Scoped PITest analysis and per-test mutation scoring |
| **Export** | `CsvExporter`, `ConsoleReporter` | Write ranked results to CSV; print terminal summary tables |
| **Models** | `SuspiciousnessResult`, `MethodSuspiciousnessResult`, `MethodInfo`, `CoverageData` | Data records for line-level results, method-level results, method boundaries, and coverage data |
