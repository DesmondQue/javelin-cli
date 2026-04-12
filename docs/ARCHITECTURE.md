# Architecture

Javelin follows a layered pipeline architecture:

```
CLI Input → Coverage Collection → Data Parsing → Matrix Building → SBFL Scoring → CSV Export
```

| Layer | Components | Responsibility |
|---|---|---|
| **Controller** | `Main.java` | CLI parsing (Picocli), input validation, pipeline orchestration |
| **Execution** | `CoverageRunner`, `OfflineInstrumenter` | JaCoCo-instrumented test execution (online agent or offline pre-instrumentation) |
| **Validation** | `SbflPreconditions`, `AgentConflictDetector` | SBFL precondition checks and agent conflict auto-detection |
| **Data Processing** | `DataParser`, `MatrixBuilder` | Parse `.exec` coverage files, build spectrum hit matrix |
| **Math** | `OchiaiCalculator`, `OchiaiMSCalculator` | Compute suspiciousness scores |
| **Mutation** *(ochiai-ms only)* | `MutationRunner`, `MutationScoreCalculator` | Scoped PITest analysis and per-test mutation scoring |
| **Export** | `CsvExporter` | Write ranked results to CSV |
