package com.javelin.core.mutation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.javelin.core.model.MutantInfo;
import com.javelin.core.model.MutationData;

/**
 * Mutation Data Parser
 *
 * Parses PITest's XML output (mutations.xml) into structured per-test kill data.
 *
 * Supports two XML variants:
 *   - FULL_MUTATION_MATRIX mode: {@code <killingTests>} with pipe-separated test names
 *   - Standard mode (fallback): {@code <killingTest>} with a single test name
 *
 * PITest test name format: "com.example.TestClass.testMethod(com.example.TestClass)"
 * Javelin test ID format:  "SimpleClassName#methodName"
 *
 * A normalization step converts PITest test names to Javelin test IDs so the
 * kill matrix keys align with CoverageData.testResults().
 */
public class MutationDataParser {

    /** Separator used by PITest in FULL_MUTATION_MATRIX mode for multiple test names */
    private static final String PITEST_TEST_SEPARATOR = "\\|";

    /**
     * Parses the PITest report directory and returns structured MutationData.
     *
     * @param reportDir the directory produced by MutationRunner (contains mutations.xml)
     * @return MutationData with mutant list and kill matrix
     * @throws IOException if the report directory or mutations.xml cannot be read
     */
    public MutationData parse(Path reportDir) throws IOException {
        Path xmlPath = reportDir.resolve("mutations.xml");
        if (!Files.exists(xmlPath)) {
            throw new IOException("mutations.xml not found in: " + reportDir);
        }

        return parseXml(xmlPath);
    }

    /**
     * Parses a PITest mutations.xml file.
     */
    private MutationData parseXml(Path xmlPath) throws IOException {
        Document doc = loadXmlDocument(xmlPath);

        List<MutantInfo> mutants = new ArrayList<>();
        Map<String, Set<String>> killMatrix = new HashMap<>();

        NodeList mutationNodes = doc.getElementsByTagName("mutation");
        Map<String, Integer> mutantIndexTracker = new HashMap<>();

        for (int i = 0; i < mutationNodes.getLength(); i++) {
            Element mutationEl = (Element) mutationNodes.item(i);

            String mutatedClass = getElementText(mutationEl, "mutatedClass");
            String lineNumberStr = getElementText(mutationEl, "lineNumber");
            String mutator = getElementText(mutationEl, "mutator");
            String status = mutationEl.getAttribute("status");

            if (mutatedClass == null || lineNumberStr == null || status == null) {
                System.err.println("WARNING: Skipping malformed mutation element at index " + i);
                continue;
            }

            int lineNumber = Integer.parseInt(lineNumberStr);

            // Generate unique mutant ID: "class:line:mutatorSuffix:index"
            String mutatorSuffix = extractMutatorSuffix(mutator);
            String baseKey = mutatedClass + ":" + lineNumber + ":" + mutatorSuffix;
            int index = mutantIndexTracker.merge(baseKey, 0, (old, v) -> old + 1);
            String mutantId = baseKey + ":" + index;

            mutants.add(new MutantInfo(mutantId, mutatedClass, lineNumber, status));

            // Extract killing tests and build kill matrix
            List<String> killingTests = extractKillingTests(mutationEl);
            for (String pitestTestName : killingTests) {
                String javelinTestId = normalizeTestId(pitestTestName);
                killMatrix.computeIfAbsent(javelinTestId, k -> new HashSet<>()).add(mutantId);
            }
        }

        return new MutationData(mutants, killMatrix);
    }

    /**
     * Extracts killing test names from a mutation element.
     *
     * Tries FULL_MUTATION_MATRIX format first ({@code <killingTests>} with pipe separator),
     * then falls back to standard format ({@code <killingTest>} with single test name).
     */
    private List<String> extractKillingTests(Element mutationEl) {
        List<String> tests = new ArrayList<>();

        // Try FULL_MUTATION_MATRIX format: <killingTests>test1|test2|test3</killingTests>
        String killingTests = getElementText(mutationEl, "killingTests");
        if (killingTests != null && !killingTests.isBlank()) {
            for (String testName : killingTests.split(PITEST_TEST_SEPARATOR)) {
                String trimmed = testName.trim();
                if (!trimmed.isEmpty()) {
                    tests.add(trimmed);
                }
            }
            return tests;
        }

        // Fallback: standard XML format: <killingTest>single test name</killingTest>
        String killingTest = getElementText(mutationEl, "killingTest");
        if (killingTest != null && !killingTest.isBlank() && !"none".equals(killingTest)) {
            tests.add(killingTest.trim());
        }

        return tests;
    }

    /**
     * Converts a PITest test identifier to a Javelin test ID.
     *
     * PITest format examples:
     *   "com.example.CalculatorTest.testAdd(com.example.CalculatorTest)"
     *   "com.example.CalculatorTest.[engine:junit-jupiter]/[class:com.example.CalculatorTest]/[method:testAdd()]"
     *
     * Javelin format:
     *   "CalculatorTest#testAdd"
     *
     * Strategy: extract the simple class name and method name from the PITest string.
     */
    static String normalizeTestId(String pitestTestName) {
        if (pitestTestName == null || pitestTestName.isBlank()) {
            return pitestTestName;
        }

        String className;
        String methodName;

        // Handle JUnit5 unique ID format: [engine:...]/[class:...]/[method:...]
        if (pitestTestName.contains("[class:") && pitestTestName.contains("[method:")) {
            className = extractBetween(pitestTestName, "[class:", "]");
            methodName = extractBetween(pitestTestName, "[method:", "(");
            if (methodName == null) {
                methodName = extractBetween(pitestTestName, "[method:", "]");
            }
        }
        // Handle standard PITest format: com.example.TestClass.testMethod(com.example.TestClass)
        else if (pitestTestName.contains("(")) {
            String beforeParen = pitestTestName.substring(0, pitestTestName.indexOf('('));
            int lastDot = beforeParen.lastIndexOf('.');
            if (lastDot > 0) {
                methodName = beforeParen.substring(lastDot + 1);
                String fqClass = beforeParen.substring(0, lastDot);
                int classDot = fqClass.lastIndexOf('.');
                className = classDot > 0 ? fqClass.substring(classDot + 1) : fqClass;
            } else {
                // No dots — use the whole thing
                className = beforeParen;
                methodName = "";
            }
        }
        // Handle simple dotted format: com.example.TestClass.testMethod
        else if (pitestTestName.contains(".")) {
            int lastDot = pitestTestName.lastIndexOf('.');
            methodName = pitestTestName.substring(lastDot + 1);
            String fqClass = pitestTestName.substring(0, lastDot);
            int classDot = fqClass.lastIndexOf('.');
            className = classDot > 0 ? fqClass.substring(classDot + 1) : fqClass;
        }
        // Already simple format or unknown — return as-is
        else {
            return pitestTestName;
        }

        // Extract simple class name if still fully qualified
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }

        return className + "#" + methodName;
    }

    // -------------------------------------------------------------------------
    // XML helpers
    // -------------------------------------------------------------------------

    private Document loadXmlDocument(Path xmlPath) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(xmlPath.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse mutations.xml: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the text content of the first child element with the given tag name.
     * Returns null if the element is not found or has no text.
     */
    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    /**
     * Extracts the simple mutator name from the fully qualified PITest mutator class.
     * e.g. "org.pitest.mutationtest.engine.gregor.mutators.MathMutator" → "MathMutator"
     */
    private static String extractMutatorSuffix(String mutator) {
        if (mutator == null || mutator.isBlank()) {
            return "Unknown";
        }
        int lastDot = mutator.lastIndexOf('.');
        return lastDot >= 0 ? mutator.substring(lastDot + 1) : mutator;
    }

    /**
     * Extracts the substring between startMarker and endMarker.
     * Returns null if markers are not found.
     */
    private static String extractBetween(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) return null;
        start += startMarker.length();
        int end = text.indexOf(endMarker, start);
        if (end < 0) return null;
        return text.substring(start, end);
    }
}
