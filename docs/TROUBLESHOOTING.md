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
