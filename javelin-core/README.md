# Javelin CLI

Automated **Spectrum-Based Fault Localization (SBFL)** for Java projects. Javelin analyzes test pass/fail data and code coverage to rank code elements by suspiciousness, helping you find bugs faster. Supports both **statement-level** and **method-level** output granularity with **dense** and **average (MID)** ranking strategies.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Command Reference](#command-reference)
- [Algorithms](#algorithms)
- [Output Format](#output-format)
- [Development](#development)
- [Troubleshooting](#troubleshooting)
- [Architecture](#architecture)

---

## Overview

Javelin instruments your Java test suite with [JaCoCo](https://www.jacoco.org/) coverage, builds a spectrum hit matrix from the results, and applies fault localization algorithms to produce a ranked list of suspicious lines. Two algorithms are supported:

- **Ochiai:** Standard SBFL ranking using pass/fail spectrum data.
- **Ochiai-MS:** Enhanced Ochiai that weights passing tests by their mutation-killing strength via [PITest](https://pitest.org/) analysis.
- **Method-level aggregation:** Post-scoring aggregation from line to method granularity, comparable to GZoltar and SBFL evaluation literature.
- **Average (MID) ranking:** Fractional ranking for tied scores, standard in SBFL evaluation (Sarhan & Beszedes 2020).

---

## Prerequisites

| Requirement | Version | Install Guide |
|---|---|:---|
| **Java JDK** | 21 or later | [Eclipse Temurin](https://adoptium.net/), [Oracle JDK](https://www.oracle.com/java/technologies/downloads/), [Amazon Corretto](https://aws.amazon.com/corretto/), or any OpenJDK 21+ distribution |
| **Gradle** | 8.5+ *(optional, wrapper included)* | [gradle.org/install](https://gradle.org/install/) |

> **Note:** Any Java 21+ JDK works (Temurin, Oracle, Corretto, GraalVM, etc.). Ensure `java -version` reports 21 or higher, or set `JAVA_HOME` to your JDK 21 installation.

---

## Installation

### Homebrew (macOS / Linux)

Requires [Homebrew](https://brew.sh/).

```bash
brew tap DesmondQue/javelin-cli https://github.com/DesmondQue/javelin-cli.git
brew install javelin-cli
```

### Chocolatey (Windows)

Requires [Chocolatey](https://chocolatey.org/install).

```powershell
choco install javelin-cli
```

### From Source (All Platforms)

```bash
# 1. Clone the repository
git clone https://github.com/DesmondQue/javelin-cli.git
cd javelin-cli/javelin-core

# 2. Build and install
./gradlew installDist          # macOS/Linux
.\gradlew.bat installDist      # Windows

# 3. Verify
./build/install/javelin/bin/javelin --version        # macOS/Linux
.\build\install\javelin\bin\javelin.bat --version     # Windows
```

The built distribution is at `build/install/javelin/`. Add `build/install/javelin/bin` to your `PATH` to use `javelin` globally.

---

## Usage

### Basic Syntax

```
javelin [-a <algorithm>] -t <target-classes> -T <test-classes> -o <output.csv> [options]
```

### Quick Start Examples

**Standard Ochiai analysis** (statement-level, default):

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv
```

**Method-level output** (for SBFL evaluation):

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method
```

**Method-level with average ranking** (matches SBFL literature conventions):

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method --ranking average
```

**Ochiai-MS with mutation scoring** (requires source path):

```bash
javelin -a ochiai-ms \
  -t build/classes/java/main \
  -T build/classes/java/test \
  -s src/main/java \
  -o results.csv
```

**Full evaluation setup** (Ochiai-MS, method-level, average ranking):

```bash
javelin -a ochiai-ms \
  -t build/classes/java/main \
  -T build/classes/java/test \
  -s src/main/java \
  -o results.csv -g method --ranking average
```

**With additional classpath and thread control**:

```bash
javelin -t build/classes/java/main \
  -T build/classes/java/test \
  -c "libs/dependency.jar" \
  -j 4 \
  -o report.csv
```

---

## Command Reference

### Options

| Flag | Long Form | Required | Default | Description |
|---|---|---|---|:---|
| `-a` | `--algorithm` | No | `ochiai` | Fault localization algorithm: `ochiai` or `ochiai-ms` |
| `-t` | `--target` | **Yes** | - | Path to compiled application classes (e.g., `build/classes/java/main`) |
| `-T` | `--test` | **Yes** | - | Path to compiled test classes (e.g., `build/classes/java/test`) |
| `-o` | `--output` | **Yes** | - | Output CSV file path for the suspiciousness report |
| `-s` | `--source` | Only for `ochiai-ms` | - | Path to Java source files (needed by PITest for mutation analysis) |
| `-c` | `--classpath` | No | - | Additional classpath entries (JARs or directories) |
| `-j` | `--threads` | No | CPU core count | Number of parallel threads for test execution |
| `-g` | `--granularity` | No | `statement` | Output granularity: `statement` or `method` |
| | `--ranking` | No | `dense` | Ranking strategy: `dense` (recommended) or `average` (for evaluation) |
| | `--offline` | No | `false` | Offline bytecode instrumentation (avoids agent conflicts) |
| | `--pitest-threads` | No | CPU core count | Parallel threads for PITest mutation analysis (ochiai-ms only) |
| | `--jvm-home` | No | - | JVM home directory for test subprocesses |
| `-q` | `--quiet` | No | `false` | Suppress progress output |
| `-h` | `--help` | - | - | Show help message and exit |
| `-V` | `--version` | - | - | Print version information and exit |

### Requirements

- **At least one failing test** is required. Javelin exits with an error if all tests pass (there is nothing to localize).
- **Zero passing tests** is allowed, but the suspiciousness ranking will be less informative.
- `--ranking average` produces fractional MID ranks (e.g., 2.5) for EXAM score evaluation. Dense ranking is recommended for interactive debugging.

---

## Algorithms

### Ochiai (default)

Standard SBFL algorithm. Computes a suspiciousness score for each executable line based on how frequently it is covered by failing vs. passing tests.

$$\text{Ochiai}(s) = \frac{e_f(s)}{\sqrt{n_f \cdot (e_f(s) + e_p(s))}}$$

Where:
- $e_f(s)$ = number of failing tests that execute statement $s$
- $e_p(s)$ = number of passing tests that execute statement $s$
- $n_f$ = total number of failing tests

```bash
javelin -a ochiai -t build/classes/java/main -T build/classes/java/test -o report.csv
```

### Ochiai-MS (Mutation Score weighted)

Enhanced variant that runs scoped [PITest](https://pitest.org/) mutation analysis on the fault region (lines covered by failing tests), then weights each passing test by its mutation-killing strength. This penalizes weak passing tests and rewards strong ones.

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

> **Note:** `ochiai-ms` requires the `-s/--source` flag and takes longer due to mutation analysis.

### Method-Level Aggregation

When `-g method` is specified, line-level scores are aggregated to method-level as a post-scoring step. Each method's score is the maximum score among its lines. Method boundaries are extracted from JaCoCo coverage data.

### Ranking Strategies

- **Dense** (default): tied scores share the same rank. `[1, 2, 2, 3]`
- **Average (MID)**: tied scores receive the mean of their ordinal positions. `[1.0, 2.5, 2.5, 4.0]` (standard in SBFL evaluation literature).

---

## Output Format

### Statement-Level (default)

| Column | Description |
|---|:---|
| `FullyQualifiedClass` | Fully qualified Java class name |
| `LineNumber` | Line number in the source file |
| `OchiaiScore` | Suspiciousness score (0.0 – 1.0) |
| `Rank` | Dense rank (1 = most suspicious) |

```csv
FullyQualifiedClass,LineNumber,OchiaiScore,Rank
com.example.Calculator,42,1.000000,1
com.example.Calculator,38,0.707107,2
com.example.MathHelper,15,0.500000,3
```

### Method-Level (`-g method`)

| Column | Description |
|---|:---|
| `FullyQualifiedClass` | Fully qualified Java class name |
| `MethodName` | Method name (`<init>` for constructors) |
| `Descriptor` | JVM method descriptor for overload disambiguation |
| `MaxScore` | Maximum suspiciousness score among the method's lines |
| `Rank` | Dense or average rank (formatted as decimal) |
| `FirstLine` | First line of the method |
| `LastLine` | Last line of the method |

```csv
FullyQualifiedClass,MethodName,Descriptor,MaxScore,Rank,FirstLine,LastLine
com.example.Calculator,compute,(II)I,1.000000,1.0,10,25
com.example.Calculator,validate,(I)Z,0.707107,2.0,30,45
com.example.MathHelper,sqrt,(D)D,0.500000,3.0,5,15
```

---

## Development

```bash
# Build the distribution (creates build/install/javelin/)
./gradlew installDist

# Build a fat JAR (single executable JAR with all dependencies)
./gradlew fatJar

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Build distribution archives (zip + tar)
./gradlew distZip distTar

# Build Chocolatey package
./gradlew chocopack

# Update Homebrew formula version and SHA
./gradlew updateHomebrew

# Update Scoop manifest version and SHA
./gradlew updateScoop
```

---

## Troubleshooting

### `UnsupportedClassVersionError` or `class file version 65.0`

You're running Javelin with a Java version older than 21. Check with:

```bash
java -version
```

If the reported version is below 21, install Java 21+ and either add it to your `PATH` or set `JAVA_HOME`:

```bash
# macOS/Linux
export JAVA_HOME=/path/to/jdk-21

# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
```

### `The system cannot find the path specified`

The distribution hasn't been built yet. Run:

```bash
./gradlew installDist       # macOS/Linux
.\gradlew.bat installDist   # Windows
```

### No failing tests detected

Javelin requires at least one failing test to perform fault localization. If all tests pass, there is no fault to localize.

### Ochiai-MS errors about missing source path

The `ochiai-ms` algorithm requires the `-s/--source` flag pointing to your Java source files:

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

---

## Architecture

Javelin follows a layered pipeline architecture:

```
CLI Input → Coverage Collection → Data Parsing → Matrix Building → SBFL Scoring → [Method Aggregation] → CSV Export
```

The method aggregation step is optional, activated by `-g method`.

| Layer | Components | Responsibility |
|---|---|:---|
| **Controller** | `Main`, `VersionProvider` | CLI parsing (Picocli), input validation, pipeline orchestration |
| **Execution** | `CoverageRunner`, `OfflineInstrumenter`, `SingleJvmTestRunner`, `ProcessExecutor`, `JavelinTestListener` | JaCoCo-instrumented test execution (online agent or offline pre-instrumentation), subprocess management |
| **Validation** | `SbflPreconditions`, `AgentConflictDetector` | SBFL precondition checks and agent conflict auto-detection |
| **Data Processing** | `DataParser`, `MatrixBuilder` | Parse `.exec` coverage files, build spectrum hit matrix; extract method boundaries |
| **Math** | `OchiaiCalculator`, `OchiaiMSCalculator` | Compute line-level suspiciousness scores |
| **Aggregation** | `MethodAggregator` | Aggregate line scores to method-level, apply dense or average ranking |
| **Mutation** *(ochiai-ms only)* | `MutationRunner`, `MutationScoreCalculator`, `FaultRegionIdentifier`, `MutationDataParser` | Scoped PITest analysis, fault region identification, mutation data parsing, and per-test mutation scoring |
| **Export** | `CsvExporter`, `ConsoleReporter` | Write ranked results to CSV; print terminal summary tables |
| **Models** | `SuspiciousnessResult`, `MethodSuspiciousnessResult`, `MethodInfo`, `CoverageData`, `SpectrumMatrix`, `ExitCode`, `LineCoverage`, `MutantInfo`, `MutationData`, `TestExecResult`, `TestResult` | Data records for line-level results, method-level results, method boundaries, coverage data, spectrum matrix, exit codes, and test execution results |
