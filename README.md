# Javelin CLI

Automated **Spectrum-Based Fault Localization (SBFL)** for Java projects. Javelin analyzes test pass/fail data and code coverage to rank lines of code by suspiciousness, helping you find bugs faster.

Javelin supports both **statement-level** (line) and **method-level** output granularity, each with **dense** and **average (MID)** ranking strategies. Statement-level with dense ranking is the default and recommended for interactive debugging (e.g., IntelliJ plugin). Average ranking at either granularity level is intended for SBFL evaluation, computing EXAM scores and Top-N metrics per Pearson et al. (ICSE 2017) and Sarhan & Beszedes (2023).

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
| `-t` | `--target` | **Yes** | - | Path to compiled application classes |
| `-T` | `--test` | **Yes** | - | Path to compiled test classes |
| `-o` | `--output` | **Yes** | - | Output CSV file path |
| `-s` | `--source` | Only for `ochiai-ms` | - | Path to Java source files (for PITest) |
| `-c` | `--classpath` | No | - | Additional classpath entries |
| `-j` | `--threads` | No | CPU cores | Parallel threads for test execution |
| `-g` | `--granularity` | No | `statement` | Output granularity: `statement` (recommended) or `method` (for evaluation) |
| | `--ranking` | No | `dense` | Ranking strategy: `dense` (recommended) or `average` (for evaluation) |
| | `--offline` | No | `false` | Offline bytecode instrumentation (auto-enabled on agent conflicts) |
| | `--pitest-threads` | No | CPU cores | Parallel threads for PITest mutation analysis (ochiai-ms only) |
| | `--jvm-home` | No | - | JVM home directory for test subprocesses |
| `-q` | `--quiet` | No | `false` | Suppress progress output |
| | `--timeout` | No | `0` | Maximum time for the entire analysis in minutes (0 = no limit) |
| `-h` | `--help` | - | - | Show help message |
| `-V` | `--version` | - | - | Print version |

- **At least one failing test** is required (nothing to localize if all pass).
- `--ranking average` produces fractional MID ranks (e.g., 2.5) for EXAM score evaluation. Dense ranking is recommended for interactive debugging.
- `--timeout` caps the entire analysis (coverage, mutation testing, and scoring). Each phase receives the remaining time budget, so a 60-minute timeout that spends 15 minutes on coverage leaves 45 minutes for mutation analysis. When set to 0 (default), there is no time limit. Individual mutants that cause infinite loops are still killed by PITest's per-mutation timeout regardless of this setting. For large projects, consider setting a limit (e.g., `--timeout 60`) to cap long-running analyses.

---

## Known Limitations

**Project must be compiled first.** Javelin operates on compiled `.class` files and does not invoke the build tool. Run `mvn compile test-compile -DskipTests` or `./gradlew classes testClasses` before using Javelin.

**Build-tool orchestrated infrastructure tests.** Javelin runs tests directly via JUnit Platform. Most tests work, including Spring Boot `@SpringBootTest` and Testcontainers. Tests that require the build tool to manage external infrastructure (e.g., Arquillian server lifecycle, Maven Failsafe phase orchestration) are not supported.

---

## JVM Compatibility

Javelin is compiled to Java 11 bytecode and requires Java 21+ to run. The `--jvm-home` flag controls which JVM executes test subprocesses. When omitted, tests run on the same JVM as javelin-cli itself.

### Dependency Runtime Requirements

| Component | Minimum JVM to Run | Bytecode It Can Analyze |
|---|---|---|
| **javelin-cli** | Java 21+ (enforced at startup) | N/A |
| **JaCoCo 0.8.12** | Java 8+ | Java 5+ |
| **PITest 1.17.4** | Java 11+ | Any bytecode loadable by the host JVM |

### Target Projects Using Java 8+

Projects compiled for **Java 8 through Java 21** are fully supported and have been tested. When the test execution JVM (controlled by `--jvm-home` or the default runtime) is newer than the project's target version, Javelin prints a warning at startup suggesting `--jvm-home` for correct test behavior. In practice, this mismatch is harmless for most projects.

### Target Projects Using Java 7 and Below

Java bytecode is forward-compatible: classes compiled for older JDKs will load and execute on newer JVMs. However, tests may behave differently when the execution JVM is significantly newer than the project's target version:

- **Removed APIs.** Internal APIs such as `sun.misc.BASE64Encoder`, `com.sun.image.codec.jpeg`, and `sun.security.*` were removed in later JDK releases. Tests that depend on these will fail with `NoClassDefFoundError` or `ClassNotFoundException`.
- **Module access restrictions.** Java 9 introduced the module system, which restricts reflective access to JDK internals by default. Older frameworks that use `setAccessible(true)` on JDK-internal classes will fail with `InaccessibleObjectException` unless `--add-opens` flags are passed to the JVM.
- **Changed runtime defaults.** String hash codes became randomized in Java 7, `SecurityManager` was deprecated for removal in Java 17, TLS protocol defaults changed across versions, and garbage collector defaults differ between major releases. Tests that assert specific runtime behavior may produce different results.
- **Framework compatibility.** Older versions of testing frameworks (e.g., JUnit 3, early Mockito, PowerMock) may not function correctly on newer JVMs due to bytecode-level assumptions or reliance on removed internals.

To avoid behavioral mismatches, point `--jvm-home` at a JDK matching the project's target version. The minimum `--jvm-home` version depends on the algorithm: **Java 8+** for Ochiai (JaCoCo only), or **Java 11+** for Ochiai-MS (PITest requires 11+).

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
