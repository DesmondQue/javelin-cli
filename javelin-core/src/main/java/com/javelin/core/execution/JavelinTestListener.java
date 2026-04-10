package com.javelin.core.execution;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** JaCoCo IAgent instance obtained reflectively */
    private Object jacocoAgent;

    /** Method handles for JaCoCo agent operations */
    private Method resetMethod;
    private Method getExecutionDataMethod;

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
     * This avoids compile-time dependency on jacoco-agent-rt.
     */
    private void initializeJacocoAgent() {
        try {
            //access JaCoCo RT class which provides the agent instance
            Class<?> rtClass = Class.forName("org.jacoco.agent.rt.RT");
            
            //get agent instance via RT.getAgent()
            Method getAgentMethod = rtClass.getMethod("getAgent");
            jacocoAgent = getAgentMethod.invoke(null);
            
            //get method handles for reset() and getExecutionData(boolean)
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
                resetMethod.invoke(jacocoAgent);
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
                byte[] data = (byte[]) getExecutionDataMethod.invoke(jacocoAgent, false);
                
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
        String legacyName = testIdentifier.getLegacyReportingName();
        return legacyName != null ? legacyName : testIdentifier.getDisplayName();
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
        String safeFileName = testId.replace("#", "_").replace(".", "_");
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
