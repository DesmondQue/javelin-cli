# Javelin CLI

Automated **Spectrum-Based Fault Localization (SBFL)** for Java projects. Javelin analyzes test pass/fail data and code coverage to rank lines of code by suspiciousness, helping you find bugs faster.

Javelin supports both **statement-level** (line) and **method-level** output granularity, each with **dense** and **average (MID)** ranking strategies. Statement-level with dense ranking is the default and recommended for interactive debugging (e.g., IntelliJ plugin). Average ranking at either granularity level is intended for SBFL evaluation -- computing EXAM scores and Top-N metrics per Pearson et al. (ICSE 2017) and Sarhan & Beszedes (2023).

Javelin implements the standard **Ochiai** SBFL algorithm alongside **Ochiai-MS**, an **experimental** algorithm that integrates mutation testing into the SBFL pipeline. Ochiai-MS is a novel contribution of the research study overseeing the development of Javelin, exploring whether mutation score–weighted test spectra can improve fault localization accuracy.

> **⚠️ Experimental:** The Ochiai-MS algorithm (`--algorithm ochiai-ms`) is an active area of research and should be considered experimental. Results and behavior may change in future releases.

---

## Prerequisites

| Requirement | Version |
|---|---|
| **Java JDK** | 21 or later ([Eclipse Temurin](https://adoptium.net/), [Oracle JDK](https://www.oracle.com/java/technologies/downloads/), [Amazon Corretto](https://aws.amazon.com/corretto/), or any OpenJDK 21+) |

---

## Installation

### Homebrew (macOS / Linux)

On **Linux/WSL**, install Java 21+ first (`sudo apt install openjdk-21-jdk`).

```bash
brew tap DesmondQue/javelin-cli https://github.com/DesmondQue/javelin-cli.git
brew install javelin-cli
```

### Scoop (Windows)

Requires Java 21+ on your `PATH`.

```powershell
scoop bucket add javelin-cli https://github.com/DesmondQue/javelin-cli
scoop install javelin-cli
```

### From Source

```bash
git clone https://github.com/DesmondQue/javelin-cli.git
cd javelin-cli/javelin-core
./gradlew installDist
./build/install/javelin/bin/javelin --version
```

---

## Usage

```
javelin [-a <algorithm>] -t <target-classes> -T <test-classes> -o <output.csv> [options]
```

**Standard Ochiai analysis (statement-level, default):**

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv
```

**Method-level output (for SBFL evaluation):**

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method
```

**Average ranking for EXAM score evaluation (works with both granularity levels):**

```bash
# Statement-level average ranking (Pearson et al., ICSE 2017)
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv --ranking average

# Method-level average ranking (Sarhan & Beszedes 2023)
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method --ranking average
```

**Ochiai-MS with mutation scoring:**

```bash
javelin -a ochiai-ms \
  -t build/classes/java/main \
  -T build/classes/java/test \
  -s src/main/java \
  -o results.csv
```

**With additional classpath:**

```bash
javelin -t build/classes/java/main \
  -T build/classes/java/test \
  -c "libs/dependency.jar" \
  -o report.csv
```

---

## Command Reference

| Flag | Long Form | Required | Default | Description |
|---|---|---|---|---|
| `-a` | `--algorithm` | No | `ochiai` | `ochiai` or `ochiai-ms` |
| `-t` | `--target` | **Yes** | — | Path to compiled application classes |
| `-T` | `--test` | **Yes** | — | Path to compiled test classes |
| `-o` | `--output` | **Yes** | — | Output CSV file path |
| `-s` | `--source` | Only for `ochiai-ms` | — | Path to Java source files (for PITest) |
| `-c` | `--classpath` | No | — | Additional classpath entries |
| `-j` | `--threads` | No | CPU cores | Parallel threads for test execution |
| `-g` | `--granularity` | No | `statement` | Output granularity: `statement` (recommended) or `method` (for evaluation) |
| | `--ranking` | No | `dense` | Ranking strategy: `dense` (recommended) or `average` (for evaluation) |
| | `--offline` | No | `false` | Offline bytecode instrumentation (auto-enabled on agent conflicts) |
| | `--pitest-threads` | No | CPU cores | Parallel threads for PITest mutation analysis (ochiai-ms only) |
| | `--jvm-home` | No | — | JVM home directory for test subprocesses |
| `-q` | `--quiet` | No | `false` | Suppress progress output |
| `-h` | `--help` | — | — | Show help message |
| `-V` | `--version` | — | — | Print version |

- **At least one failing test** is required (nothing to localize if all pass).
- `--ranking average` produces fractional MID ranks (e.g., 2.5) for EXAM score evaluation. Dense ranking is recommended for interactive debugging.

---

## Known Limitations

**Project must be compiled first.** Javelin operates on compiled `.class` files and does not invoke the build tool. Run `mvn compile test-compile -DskipTests` or `./gradlew classes testClasses` before using Javelin.

**Build-tool orchestrated infrastructure tests.** Javelin runs tests directly via JUnit Platform. Most tests work — including Spring Boot `@SpringBootTest` and Testcontainers. Tests that require the build tool to manage external infrastructure (e.g., Arquillian server lifecycle, Maven Failsafe phase orchestration) are not supported.

---

## Further Reading

| Topic | Link |
|---|---|
| Algorithm details and formulas | [docs/ALGORITHMS.md](docs/ALGORITHMS.md) |
| Output format (CSV and terminal) | [docs/OUTPUT_FORMAT.md](docs/OUTPUT_FORMAT.md) |
| Offline instrumentation mode | [docs/OFFLINE_MODE.md](docs/OFFLINE_MODE.md) |
| Development and build commands | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Troubleshooting | [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) |
| Architecture | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |

---

## License

[MIT License](LICENSE)
