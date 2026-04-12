package com.javelin.core.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestExecutionResult.Status;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform TestExecutionListener that captures per-test JaCoCo coverage data.
 * 
 * This listener uses the JaCoCo Runtime API (accessed reflectively) to:
 * 1. Reset coverage data before each test method starts
 * 2. Dump coverage data after each test method finishes
 * 3. Store coverage bytes in a map keyed by test identifier
 * 
 * This approach mimics GZoltar's per-test coverage collection strategy,
 * allowing all tests to run in a single JVM while capturing isolated coverage.
 * 
 * Usage:
 * - The JaCoCo agent must be attached to the JVM via -javaagent
 * - Register this listener with the JUnit Platform Launcher
 * - After test execution, retrieve coverage data via getCoverageData()
 */
public class JavelinTestListener implements TestExecutionListener {

    /** Coverage data keyed by test identifier (ClassName#methodName) */
    private final Map<String, byte[]> coverageData = new ConcurrentHashMap<>();

    /** Test pass/fail status keyed by test identifier */
    private final Map<String, Boolean> testResults = new ConcurrentHashMap<>();

    /** Directory to write .exec files (optional - can be null for in-memory only) */
    private Path outputDirectory;

    /** JaCoCo IAgent instance obtained reflectively (online mode) */
    private Object jacocoAgent;

    /** Method handles for JaCoCo agent operations (online mode) */
    private Method resetMethod;
    private Method getExecutionDataMethod;

    /**
     * Offline mode: shaded RuntimeData instance and reflected method/field handles.
     * JaCoCo shades its internals with a version-specific hash in the package name
     * (e.g. org.jacoco.agent.rt.internal_aeaf9ab.*), so we cannot import these types
     * directly. All access is via pure reflection.
     */
    private Object offlineShadedRuntime;     // shaded core.runtime.RuntimeData instance
    private Method offlineResetMethod;        // RuntimeData.reset()
    private Field  offlineStoreField;         // RuntimeData.store (ExecutionDataStore)
    private Method offlineGetContentsMethod;  // ExecutionDataStore.getContents()
    private Method offlineGetIdMethod;        // ExecutionData.getId()
    private Method offlineGetNameMethod;      // ExecutionData.getName()
    private Method offlineGetProbesMethod;    // ExecutionData.getProbes()

    /** Flag indicating whether offline instrumentation mode is active */
    private boolean offlineDataMode = false;

    /** Flag indicating whether JaCoCo agent is available */
    private boolean agentAvailable = false;

    /**
     * Creates a new listener that stores coverage data in memory only
     */
    public JavelinTestListener() {
        this(null);
    }

    /**
     * Creates a new listener that writes .exec files to the specified directory.
     * 
     * @param outputDirectory directory for .exec files (null for in-memory only)
     */
    public JavelinTestListener(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
        initializeJacocoAgent();
    }

    /**
     * Initializes the JaCoCo agent connection using reflection.
     * Detects online vs offline mode via the javelin.offline system property.
     * - Online mode:  RT.getAgent() — standard JaCoCo agent attached via -javaagent
     * - Offline mode: Offline.RUNTIME (RuntimeData) — accessed via the javelin.offline.class property
     */
    private void initializeJacocoAgent() {
        if (Boolean.getBoolean("javelin.offline")) {
            initializeOfflineRuntime();
        } else {
            initializeOnlineAgent();
        }
    }

    /**
     * Online mode: access JaCoCo through the attached agent (RT.getAgent()).
     */
    private void initializeOnlineAgent() {
        try {
            Class<?> rtClass = Class.forName("org.jacoco.agent.rt.RT");
            Method getAgentMethod = rtClass.getMethod("getAgent");
            jacocoAgent = getAgentMethod.invoke(null);
            Class<?> agentClass = jacocoAgent.getClass();
            resetMethod = agentClass.getMethod("reset");
            getExecutionDataMethod = agentClass.getMethod("getExecutionData", boolean.class);
            agentAvailable = true;
            System.out.println("      JaCoCo agent connected successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("      WARNING: JaCoCo agent not found. Ensure JVM is started with -javaagent:jacocoagent.jar");
            agentAvailable = false;
        } catch (Exception e) {
            System.err.println("      WARNING: Failed to initialize JaCoCo agent: " + e.getMessage());
            agentAvailable = false;
        }
    }

    /**
     * Offline mode: access coverage data from the shaded JaCoCo Offline class.
     *
     * The jacocoagent.jar shades all internals under a hash-suffixed package, e.g.
     *   org.jacoco.agent.rt.internal_aeaf9ab.Offline
     *   org.jacoco.agent.rt.internal_aeaf9ab.core.runtime.RuntimeData
     *   org.jacoco.agent.rt.internal_aeaf9ab.core.data.ExecutionData
     *
     * CoverageRunner scans jacocoagent.jar to find the exact class name and passes it
     * as the javelin.offline.class system property. All accesses are via reflection.
     *
     * Data flow per test:
     *   executionStarted  → RuntimeData.reset() clears accumulated probe bits
     *   test runs         → instrumented classes flip probe bits via Offline.getProbes()
     *   executionFinished → iterate RuntimeData.store.getContents(), translate to public
     *                        org.jacoco.core.data.ExecutionData, serialise with
     *                        ExecutionDataWriter to standard JaCoCo binary format
     */
    private void initializeOfflineRuntime() {
        String offlineClassName = System.getProperty("javelin.offline.class");
        if (offlineClassName == null || offlineClassName.isBlank()) {
            System.err.println("      WARNING: javelin.offline.class property not set; offline coverage unavailable");
            agentAvailable = false;
            return;
        }
        try {
            // e.g. offlineClassName = "org.jacoco.agent.rt.internal_aeaf9ab.Offline"
            // internalPkg           = "org.jacoco.agent.rt.internal_aeaf9ab"
            String internalPkg = offlineClassName.substring(0, offlineClassName.lastIndexOf('.'));

            // Get the Offline class and call its private getRuntimeData() to ensure the
            // static RuntimeData instance is initialised before any tests run.
            Class<?> offlineClass = Class.forName(offlineClassName);
            Method getRTDataMethod = offlineClass.getDeclaredMethod("getRuntimeData");
            getRTDataMethod.setAccessible(true);
            offlineShadedRuntime = getRTDataMethod.invoke(null);

            // RuntimeData.reset() — clears all probe bits
            offlineResetMethod = offlineShadedRuntime.getClass().getMethod("reset");

            // RuntimeData.store (protected final ExecutionDataStore) — holds probe data
            offlineStoreField = offlineShadedRuntime.getClass().getDeclaredField("store");
            offlineStoreField.setAccessible(true);

            // ExecutionDataStore.getContents() — returns Collection<ExecutionData>
            Class<?> storeClass = Class.forName(internalPkg + ".core.data.ExecutionDataStore");
            offlineGetContentsMethod = storeClass.getMethod("getContents");

            // ExecutionData accessors — same semantics as public JaCoCo API
            Class<?> execDataClass = Class.forName(internalPkg + ".core.data.ExecutionData");
            offlineGetIdMethod     = execDataClass.getMethod("getId");
            offlineGetNameMethod   = execDataClass.getMethod("getName");
            offlineGetProbesMethod = execDataClass.getMethod("getProbes");

            offlineDataMode = true;
            agentAvailable  = true;
            System.out.println("      JaCoCo offline runtime connected successfully (" + offlineClassName + ")");
        } catch (Exception e) {
            System.err.println("      WARNING: Failed to initialize JaCoCo offline runtime: " + e.getMessage());
            agentAvailable = false;
        }
    }

    /**
     * Sets the output directory for .exec files
     * 
     * @param outputDirectory directory for .exec files
     */
    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Called when the test plan execution starts
     */
    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        coverageData.clear();
        testResults.clear();
        
        if (outputDirectory != null) {
            try {
                Files.createDirectories(outputDirectory);
            } catch (IOException e) {
                System.err.println("      WARNING: Could not create output directory: " + e.getMessage());
            }
        }
    }

    /**
     * Called when a test execution starts.
     * For test methods, resets JaCoCo coverage data to capture only this test's coverage
     */
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!testIdentifier.isTest()) {
            return;
        }

        if (agentAvailable) {
            try {
                if (offlineDataMode) {
                    offlineResetMethod.invoke(offlineShadedRuntime);
                } else {
                    resetMethod.invoke(jacocoAgent);
                }
            } catch (Exception e) {
                System.err.println("      WARNING: Failed to reset JaCoCo coverage: " + e.getMessage());
            }
        }
    }

    /**
     * Called when a test execution finishes
     * For test methods, dumps JaCoCo coverage data and stores it
     */
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest()) {
            return;
        }

        String testId = buildTestId(testIdentifier);
        boolean passed = testExecutionResult.getStatus() == Status.SUCCESSFUL;
        testResults.put(testId, passed);

        if (agentAvailable) {
            try {
                byte[] data;
                if (offlineDataMode) {
                    // Translate shaded ExecutionData entries to public equivalents, then
                    // serialise to standard JaCoCo binary format (same as online getExecutionData).
                    Object shadedStore = offlineStoreField.get(offlineShadedRuntime);
                    @SuppressWarnings("unchecked")
                    java.util.Collection<Object> contents =
                            (java.util.Collection<Object>) offlineGetContentsMethod.invoke(shadedStore);
                    ExecutionDataStore publicStore = new ExecutionDataStore();
                    for (Object entry : contents) {
                        long    id     = (long)    offlineGetIdMethod.invoke(entry);
                        String  name   = (String)  offlineGetNameMethod.invoke(entry);
                        boolean[] probes = (boolean[]) offlineGetProbesMethod.invoke(entry);
                        publicStore.put(new org.jacoco.core.data.ExecutionData(id, name, probes));
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    publicStore.accept(new ExecutionDataWriter(bos));
                    data = bos.toByteArray();
                } else {
                    data = (byte[]) getExecutionDataMethod.invoke(jacocoAgent, false);
                }
                
                if (data != null && data.length > 0) {
                    coverageData.put(testId, data);
                    if (outputDirectory != null) {
                        writeExecFile(testId, data);
                    }
                }
            } catch (Exception e) {
                System.err.println("      WARNING: Failed to dump JaCoCo coverage for " + testId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Builds a test identifier string from the TestIdentifier.
     * Format: SimpleClassName#methodName
     * 
     * Uses the uniqueId to extract bytecode-level class and method names,
     * ensuring consistency with the parent process which discovers tests via ASM.
     * 
     * Supports both JUnit Jupiter and JUnit Vintage (JUnit 4) uniqueId formats:
     *   Jupiter: [engine:junit-jupiter]/[class:com.example.Test]/[method:testMethod()]
     *   Vintage: [engine:junit-vintage]/[runner:com.example.Test]/[test:testMethod(com.example.Test)]
     */
    private String buildTestId(TestIdentifier testIdentifier) {
        String uniqueId = testIdentifier.getUniqueId();
        
        String className = extractClassName(uniqueId);
        String methodName = extractMethodName(uniqueId);
        
        if (className != null && methodName != null) {
            int lastDot = className.lastIndexOf('.');
            String simpleClassName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
            return simpleClassName + "#" + methodName;
        }
        
        // Last resort fallback: use legacy reporting name
        // Normalize spaces to underscores — @DisplayNameGeneration(ReplaceUnderscores.class)
        // causes displayName/legacyReportingName to have spaces instead of underscores,
        // which breaks filename matching with the parent process (ASM uses bytecode names).
        String legacyName = testIdentifier.getLegacyReportingName();
        String fallback = legacyName != null ? legacyName : testIdentifier.getDisplayName();
        return fallback.replace(" ", "_");
    }

    /**
     * Extracts the fully qualified class name from a JUnit unique ID.
     * Handles Jupiter [class:...], Jupiter [nested-class:...], and Vintage [runner:...] formats.
     * 
     * For nested classes, appends inner class names with '$' to match bytecode naming:
     *   [class:com.example.OuterTest]/[nested-class:InnerTest] -> com.example.OuterTest$InnerTest
     */
    private String extractClassName(String uniqueId) {
        // JUnit Jupiter: [engine:junit-jupiter]/[class:com.example.TestClass]/[method:testMethod()]
        //   or nested:   [engine:junit-jupiter]/[class:com.example.Outer]/[nested-class:Inner]/[method:test()]
        int classStart = uniqueId.indexOf("[class:");
        if (classStart >= 0) {
            int classEnd = uniqueId.indexOf("]", classStart);
            if (classEnd > classStart) {
                String className = uniqueId.substring(classStart + 7, classEnd);
                
                // Append any [nested-class:...] segments with '$' separator
                int searchFrom = classEnd;
                while (true) {
                    int nestedStart = uniqueId.indexOf("[nested-class:", searchFrom);
                    if (nestedStart < 0) break;
                    int nestedEnd = uniqueId.indexOf("]", nestedStart);
                    if (nestedEnd <= nestedStart) break;
                    className += "$" + uniqueId.substring(nestedStart + 14, nestedEnd);
                    searchFrom = nestedEnd;
                }
                
                return className;
            }
        }
        // JUnit Vintage: [engine:junit-vintage]/[runner:com.example.TestClass]/[test:testMethod(...)]
        int runnerStart = uniqueId.indexOf("[runner:");
        if (runnerStart >= 0) {
            int runnerEnd = uniqueId.indexOf("]", runnerStart);
            if (runnerEnd > runnerStart) {
                return uniqueId.substring(runnerStart + 8, runnerEnd);
            }
        }
        return null;
    }

    /**
     * Extracts the bytecode method name from the JUnit unique ID.
     * This is more reliable than using displayName (which can be customized via @DisplayName)
     * or legacyReportingName (which has different formats per engine).
     * 
     * Jupiter: [method:testMethod()] -> testMethod
     * Vintage: [test:testMethod(com.example.TestClass)] -> testMethod
     */
    private String extractMethodName(String uniqueId) {
        // JUnit Jupiter: [method:testMethod()] or [method:testMethod(Type)]
        int methodStart = uniqueId.indexOf("[method:");
        if (methodStart >= 0) {
            int parenOrEnd = uniqueId.indexOf("(", methodStart);
            int bracketEnd = uniqueId.indexOf("]", methodStart);
            int end = (parenOrEnd >= 0 && parenOrEnd < bracketEnd) ? parenOrEnd : bracketEnd;
            if (end > methodStart + 8) {
                return uniqueId.substring(methodStart + 8, end);
            }
        }
        // JUnit Vintage: [test:testMethod(com.example.TestClass)]
        int testStart = uniqueId.indexOf("[test:");
        if (testStart >= 0) {
            int parenOrEnd = uniqueId.indexOf("(", testStart);
            int bracketEnd = uniqueId.indexOf("]", testStart);
            int end = (parenOrEnd >= 0 && parenOrEnd < bracketEnd) ? parenOrEnd : bracketEnd;
            if (end > testStart + 6) {
                return uniqueId.substring(testStart + 6, end);
            }
        }
        return null;
    }

    /**
     * Writes coverage data to an .exec file
     */
    private void writeExecFile(String testId, byte[] data) {
        String safeFileName = testId.replace("#", "_").replace(".", "_").replace(" ", "_");
        Path execFile = outputDirectory.resolve("jacoco-" + safeFileName + ".exec");
        
        try {
            Files.write(execFile, data);
        } catch (IOException e) {
            System.err.println("      WARNING: Failed to write exec file for " + testId + ": " + e.getMessage());
        }
    }

    /**
     * Returns the collected coverage data map
     * 
     * @return Map of test identifiers to coverage byte arrays
     */
    public Map<String, byte[]> getCoverageData() {
        return new ConcurrentHashMap<>(coverageData);
    }

    /**
     * Returns the test results map
     * 
     * @return Map of test identifiers to pass/fail status
     */
    public Map<String, Boolean> getTestResults() {
        return new ConcurrentHashMap<>(testResults);
    }

    /**
     * Returns whether the JaCoCo agent is available
     * 
     * @return true if JaCoCo agent is connected
     */
    public boolean isAgentAvailable() {
        return agentAvailable;
    }

    /**
     * Returns the path to the .exec file for a specific test
     * 
     * @param testId the test identifier
     * @return Path to the .exec file, or null if not written
     */
    public Path getExecFilePath(String testId) {
        if (outputDirectory == null) {
            return null;
        }
        String safeFileName = testId.replace("#", "_").replace(".", "_");
        return outputDirectory.resolve("jacoco-" + safeFileName + ".exec");
    }
}
