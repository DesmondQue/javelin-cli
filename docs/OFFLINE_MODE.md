# Offline Instrumentation Mode

Offline mode pre-instruments compiled `.class` files using JaCoCo's offline API before running tests, eliminating the need for the `-javaagent` flag entirely. This avoids conflicts with other Java agents.

## How It Works

1. Javelin copies target classes to a temporary directory
2. Each `.class` file is instrumented with JaCoCo probes via the `Instrumenter` API
3. Tests run against the pre-instrumented classes without a Java agent attached
4. Per-test coverage data is collected via JaCoCo's `Offline` runtime class
5. The temporary directory is cleaned up automatically after execution

The original target classes directory is **never modified**.

## Auto-detection

Javelin automatically scans the classpath (`-c`) for JARs that indicate agent conflicts:

| JAR Pattern | Library |
|---|:---|
| `mockito-inline-*.jar` | Mockito inline mocking (uses ByteBuddy internally) |
| `byte-buddy-agent-*.jar` | ByteBuddy agent |
| `powermock-agent-*.jar` | PowerMock |
| `jmockit-*.jar` | JMockit |
| `aspectjweaver-*.jar` | AspectJ load-time weaving |

When conflicts are detected, Javelin prints a message and switches to offline mode:

```
[AUTO] Agent conflict(s) detected on classpath. Switching to offline instrumentation mode:
       • mockito-inline-5.2.0.jar: Mockito-inline uses ByteBuddy for bytecode rewriting...
       • byte-buddy-agent-1.14.1.jar: ByteBuddy agent registers its own ClassFileTransformer...
       (use --offline explicitly to suppress this message)
```

## Manual Activation

Force offline mode with the `--offline` flag:

```bash
javelin --offline -t target/classes -T target/test-classes -c "$(cat /tmp/cp.txt)" -o report.csv
```

## Correctness

Offline mode produces **identical** fault localization results to online mode. The same JaCoCo probes are injected; only the injection timing differs (build-time vs. load-time).
