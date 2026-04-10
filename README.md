# Javelin CLI

Automated **Spectrum-Based Fault Localization (SBFL)** for Java projects. Javelin analyzes test pass/fail data and code coverage to rank lines of code by suspiciousness, helping you find bugs faster.

Javelin implements the standard **Ochiai** SBFL algorithm alongside **Ochiai-MS**, an **experimental** algorithm that integrates mutation testing into the SBFL pipeline. Ochiai-MS is a novel contribution of the research study overseeing the development of Javelin, exploring whether mutation score–weighted test spectra can improve fault localization accuracy.

> **⚠️ Experimental:** The Ochiai-MS algorithm (`--algorithm ochiai-ms`) is an active area of research and should be considered experimental. Results and behavior may change in future releases.

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
- [License](#license)

---

## Overview

Javelin instruments your Java test suite with [JaCoCo](https://www.jacoco.org/) coverage, builds a spectrum hit matrix from the results, and applies fault localization algorithms to produce a ranked list of suspicious lines. Two algorithms are supported:

- **Ochiai** — Standard SBFL ranking using pass/fail spectrum data.
- **Ochiai-MS** — Enhanced Ochiai that weights passing tests by their mutation-killing strength via [PITest](https://pitest.org/) analysis.

---

## Prerequisites

| Requirement | Version | Install Guide |
|---|---|---|
| **Java JDK** | 21 or later | [Eclipse Temurin](https://adoptium.net/), [Oracle JDK](https://www.oracle.com/java/technologies/downloads/), [Amazon Corretto](https://aws.amazon.com/corretto/), or any OpenJDK 21+ distribution |
| **Gradle** | 8.5+ *(optional — wrapper included)* | [gradle.org/install](https://gradle.org/install/) |

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

**Standard Ochiai analysis** (default):

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv
```

**Ochiai-MS with mutation scoring** (requires source path):

```bash
javelin -a ochiai-ms \
  -t build/classes/java/main \
  -T build/classes/java/test \
  -s src/main/java \
  -o results.csv
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
|---|---|---|---|---|
| `-a` | `--algorithm` | No | `ochiai` | Fault localization algorithm: `ochiai` or `ochiai-ms` |
| `-t` | `--target` | **Yes** | — | Path to compiled application classes (e.g., `build/classes/java/main`) |
| `-T` | `--test` | **Yes** | — | Path to compiled test classes (e.g., `build/classes/java/test`) |
| `-o` | `--output` | **Yes** | — | Output CSV file path for the suspiciousness report |
| `-s` | `--source` | Only for `ochiai-ms` | — | Path to Java source files (needed by PITest for mutation analysis) |
| `-c` | `--classpath` | No | — | Additional classpath entries (JARs or directories) |
| `-j` | `--threads` | No | CPU core count | Number of parallel threads for test execution |
| `-h` | `--help` | — | — | Show help message and exit |
| `-V` | `--version` | — | — | Print version information and exit |

### Requirements

- **At least one failing test** is required. Javelin exits with an error if all tests pass (there is nothing to localize).
- **Zero passing tests** is allowed, but the suspiciousness ranking will be less informative.

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

### Ochiai-MS (Mutation Score weighted) — ⚠️ Experimental

> **This algorithm is experimental.** It is a novel research contribution exploring the integration of mutation testing into SBFL. Results may differ from standard Ochiai and the approach is under active evaluation.

Enhanced variant that runs scoped [PITest](https://pitest.org/) mutation analysis on the fault region (lines covered by failing tests), then weights each passing test by its mutation-killing strength. This penalizes weak passing tests and rewards strong ones.

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

> **Note:** `ochiai-ms` requires the `-s/--source` flag and takes longer due to mutation analysis.

---

## Output Format

Javelin outputs a CSV file with the following columns:

| Column | Description |
|---|---|
| `rank` | Suspiciousness rank (1 = most suspicious) |
| `class` | Fully qualified Java class name |
| `line` | Line number in the source file |
| `score` | Suspiciousness score (0.0 – 1.0) |

Example output:

```csv
rank,class,line,score
1,com.example.Calculator,42,1.0000
2,com.example.Calculator,38,0.7071
3,com.example.MathHelper,15,0.5000
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
CLI Input → Coverage Collection → Data Parsing → Matrix Building → SBFL Scoring → CSV Export
```

| Layer | Components | Responsibility |
|---|---|---|
| **Controller** | `Main.java` | CLI parsing (Picocli), input validation, pipeline orchestration |
| **Execution** | `CoverageRunner` | JaCoCo-instrumented test execution with parallel thread support |
| **Data Processing** | `DataParser`, `MatrixBuilder` | Parse `.exec` coverage files, build spectrum hit matrix |
| **Math** | `OchiaiCalculator`, `OchiaiMSCalculator` | Compute suspiciousness scores |
| **Mutation** *(ochiai-ms only)* | `MutationRunner`, `MutationScoreCalculator` | Scoped PITest analysis and per-test mutation scoring |
| **Export** | `CsvExporter` | Write ranked results to CSV |

---

## License

Javelin CLI is open-source software licensed under the [MIT License](LICENSE).

You are free to use, modify, and distribute this software. See the [LICENSE](LICENSE) file for details.
