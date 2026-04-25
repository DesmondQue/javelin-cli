# Troubleshooting

## `UnsupportedClassVersionError` or `class file version 65.0`

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

## `The system cannot find the path specified`

The distribution hasn't been built yet. Run:

```bash
./gradlew installDist       # macOS/Linux
.\gradlew.bat installDist   # Windows
```

## No failing tests detected

Javelin requires at least one failing test to perform fault localization. If all tests pass, there is no fault to localize.

## Ochiai-MS errors about missing source path

The `ochiai-ms` algorithm requires the `-s/--source` flag pointing to your Java source files:

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

## Warning about `--ranking average` without `-g method`

Average (MID) ranking produces fractional ranks (e.g., 2.5) which are only supported in method-level output. If you use `--ranking average` without `-g method`, Javelin prints a warning and falls back to dense ranking:

```bash
# Correct — average ranking with method-level output
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method --ranking average

# Will produce a warning — average ranking without method-level
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv --ranking average
```

## Method-level output shows `<class-level>` entries

Lines that don't fall within any method (field initializers, static blocks, etc.) are grouped under a synthetic `<class-level>` entry. This is expected behavior — these lines are still ranked but cannot be attributed to a specific method.
