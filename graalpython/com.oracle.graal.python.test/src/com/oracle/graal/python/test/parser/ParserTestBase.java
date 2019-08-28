/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.rules.TestName;

public class ParserTestBase {
    protected PythonContext context;
    protected final static String GOLDEN_FILE_EXT = ".tast";
    protected final static String NEW_GOLDEN_FILE_EXT = ".new.tast";
    private final static String SCOPE_FILE_EXT = ".scope";
    private final static String NEW_SCOPE_FILE_EXT = ".new.scope";

    /**
     * When the tree is generated by old parser (the golden file doesn't exist), then the lines that
     * have wrong text (the old parser has some issues) are replaced with the lines from the new
     * tree.
     */
    protected boolean correctIssues = true;

    protected int printOnlyDiffIfLenIsBigger = 1000;

    @Rule public TestName name = new TestName();

    private ScopeInfo lastGlobalScope;

    public ParserTestBase() {
        PythonTests.enterContext();
        context = PythonLanguage.getContextRef().get();
    }

    protected Source createSource(File testFile) throws Exception {
        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
        return PythonLanguage.newSource(context, src, getFileName(testFile));
    }

    protected RootNode parseOld(String src, String moduleName, PythonParser.ParserMode mode, Frame frame) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, moduleName).build();
        PythonParser parser = context.getCore().getParser();
        RootNode result = (RootNode) ((PythonParserImpl) parser).parseO(mode, context.getCore(), source, frame);
        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
        return result;
    }

    protected RootNode parseOld(String src, String moduleName, PythonParser.ParserMode mode) {
        return parseOld(src, moduleName, mode, null);
    }

    protected RootNode parseOld(Source source, PythonParser.ParserMode mode) {
        PythonParser parser = context.getCore().getParser();
        RootNode result = (RootNode) ((PythonParserImpl) parser).parseO(mode, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
        return result;
    }

    protected Node parseNew(String src, String moduleName, PythonParser.ParserMode mode, Frame fd) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, moduleName).build();
        PythonParser parser = context.getCore().getParser();
        Node result = ((PythonParserImpl) parser).parseN(mode, context.getCore(), source, fd);
        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
        return result;
    }

    protected Node parseNew(String src, String moduleName, PythonParser.ParserMode mode) {
        return parseNew(src, moduleName, mode, null);
    }

    protected Node parseNew(Source source, PythonParser.ParserMode mode) {
        PythonParser parser = context.getCore().getParser();
        Node result = ((PythonParserImpl) parser).parseN(mode, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
        return result;
    }

    protected ScopeInfo getLastGlobalScope() {
        return lastGlobalScope;
    }

    public void checkSyntaxError(String source) throws Exception {
        boolean thrown = false;
        try {
            parseNew(source, name.getMethodName(), PythonParser.ParserMode.File);
        } catch (PException e) {
            thrown = e.isSyntaxError();
        }

        assertTrue("Expected SyntaxError was not thrown.", thrown);
    }

    public void saveNewTreeResult(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
        Node resultNew = parseNew(source, PythonParser.ParserMode.File);
        String tree = printTreeToString(resultNew);
        File newGoldenFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + NEW_GOLDEN_FILE_EXT)
                        : getGoldenFile(NEW_GOLDEN_FILE_EXT);
        if (!newGoldenFile.exists()) {
            try (FileWriter fw = new FileWriter(newGoldenFile)) {
                fw.write(tree);
            }

        }
    }

    public void checkTreeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
        Node resultNew = parseNew(source, PythonParser.ParserMode.File);
        String tree = printTreeToString(resultNew);
        File goldenFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT)
                        : getGoldenFile(GOLDEN_FILE_EXT);
        if (!goldenFile.exists()) {
            // parse it with old parser and create golden file with this result
            // TODO, when the new parser will work, it has to be removed
            RootNode resultOld = parseOld(source, PythonParser.ParserMode.File);
            String oldTree = printTreeToString(resultOld);
            if (correctIssues) {
                oldTree = correctKnownIssues(tree, oldTree);
            }
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(oldTree);
            }

        }
        assertDescriptionMatches(tree, goldenFile);
    }

    public void saveNewScope(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
        parseNew(source, PythonParser.ParserMode.File);
        ScopeInfo scopeNew = getLastGlobalScope();
        StringBuilder scopes = new StringBuilder();
        scopeNew.debugPrint(scopes, 0);
        File newScopeFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + NEW_SCOPE_FILE_EXT)
                        : getGoldenFile(NEW_SCOPE_FILE_EXT);
        if (!newScopeFile.exists()) {
            try (FileWriter fw = new FileWriter(newScopeFile)) {
                fw.write(scopes.toString());
            }
        }
    }

    public void checkScopeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
        parseNew(source, PythonParser.ParserMode.File);
        ScopeInfo scopeNew = getLastGlobalScope();
        StringBuilder scopes = new StringBuilder();
        scopeNew.debugPrint(scopes, 0);
        File goldenScopeFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + SCOPE_FILE_EXT)
                        : getGoldenFile(SCOPE_FILE_EXT);
        if (!goldenScopeFile.exists()) {
            parseOld(source, PythonParser.ParserMode.File);
            StringBuilder oldScope = new StringBuilder();
            getLastGlobalScope().debugPrint(oldScope, 0);
            try (FileWriter fw = new FileWriter(goldenScopeFile)) {
                fw.write(oldScope.toString());
            }

        }
        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
    }

    public void checkTreeResult(String source, PythonParser.ParserMode mode, Frame frame) throws Exception {
        Node resultNew = parseNew(source, name.getMethodName(), mode, frame);
        String tree = printTreeToString(resultNew);
        File goldenFile = getGoldenFile(GOLDEN_FILE_EXT);
        if (!goldenFile.exists()) {
            // parse it with old parser and create golden file with this result
            // TODO, when the new parser will work, it has to be removed
            String oldTree;
            try {
                RootNode resultOld = parseOld(source, name.getMethodName(), mode, frame);
                oldTree = printTreeToString(resultOld);
            } catch (RuntimeException e) {
                oldTree = tree;
            }

            if (correctIssues) {
                oldTree = correctKnownIssues(tree, oldTree);
            }
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(oldTree);
            }

        }
        assertDescriptionMatches(tree, goldenFile);
    }

    public void checkTreeResult(String source, PythonParser.ParserMode mode) throws Exception {
        checkTreeResult(source, mode, null);
    }

    public void checkScopeResult(String source, PythonParser.ParserMode mode) throws Exception {
        parseNew(source, name.getMethodName(), mode);
        ScopeInfo scopeNew = getLastGlobalScope();
        StringBuilder scopes = new StringBuilder();
        scopeNew.debugPrint(scopes, 0);
        File goldenScopeFile = getGoldenFile(SCOPE_FILE_EXT);
        if (!goldenScopeFile.exists()) {
            parseOld(source, name.getMethodName(), mode);
            StringBuilder oldScope = new StringBuilder();
            getLastGlobalScope().debugPrint(oldScope, 0);
            try (FileWriter fw = new FileWriter(goldenScopeFile)) {
                fw.write(oldScope.toString());
            }

        }
        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
    }

    private static String printTreeToString(Node node) {
        ParserTreePrinter visitor = new ParserTreePrinter();
        node.accept(visitor);
        return visitor.getTree();
    }

    protected void assertDescriptionMatches(String actual, File goldenFile) throws IOException {
        if (!goldenFile.exists()) {
            if (!goldenFile.createNewFile()) {
                assertTrue("Cannot create file " + goldenFile.getAbsolutePath(), false);
            }
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(actual);
            }
            assertTrue("Created generated golden file " + goldenFile.getAbsolutePath() + "\nPlease re-run the test.", false);
        }
        String expected = readFile(goldenFile);

        final String expectedTrimmed = expected.trim();
        final String actualTrimmed = actual.trim();

        if (expectedTrimmed.equals(actualTrimmed)) {
            // Actual and expected content are equals --> Test passed
            
        } else {
            // We want to ignore different line separators (like \r\n against \n) because they
            // might be causing failing tests on a different operation systems like Windows :]
            final String expectedUnified = expectedTrimmed.replaceAll("\r", "");
            final String actualUnified = actualTrimmed.replaceAll("\r", "");

            if (expectedUnified.equals(actualUnified)) {
                return; // Only difference is in line separation --> Test passed
            }

            // There are some diffrerences between expected and actual content --> Test failed

            assertTrue("Not matching goldenfile: " + goldenFile.getName() + lineSeparator(2) + getContentDifferences(expectedUnified, actualUnified), false);
        }
    }

    /**
     * Return basically the oldTT with corrected lines, that are wrongly generated with the old
     * parser.
     * 
     * @param newTT Truffle tree from new parser
     * @param oldTT Truffle tree from old parser
     * @return corrected Truffle tree
     */
    private static String correctKnownIssues(String newTT, String oldTT) {
        String LINE_TEXT = "\n";
        List<String> oldLines = Arrays.asList(oldTT.split(LINE_TEXT));
        List<String> newLines = Arrays.asList(newTT.split(LINE_TEXT));

        StringBuilder corrected = new StringBuilder();

        int odlLineIndex = 0;
        int newLineIndex = 0;
        String oldLine;
        String newLine;
        int ssIndex;

        while (odlLineIndex < oldLines.size()) {
            oldLine = oldLines.get(odlLineIndex);
            newLine = newLineIndex < newLines.size() ? newLines.get(newLineIndex) : "";

            if (oldLine.equals(newLine)) {
                // the same lines
                corrected.append(oldLine);
            } else {
                if (oldLine.contains("Frame: [") && newLine.contains("Frame: [")) {
                    corrected.append(correctFrame(oldLine, newLine));
                } else if (oldLine.contains("flagSlot:") && newLine.contains("flagSlot:")) {
                    corrected.append(correctSlot(oldLine, newLine));
                } else if (oldLine.contains("FrameDescriptor:") && newLine.contains("FrameDescriptor:")) {
                    corrected.append(correctFrameDesc(oldLine, newLine));
                } else if (oldLine.contains("Identifier:") && newLine.contains("Identifier:")) {
                    corrected.append(correctIdentifier(oldLine, newLine));
                } else if (oldLine.contains("Documentation:") && newLine.contains("Documentation:")) {
                    corrected.append(correctDocumentation(oldLine, newLine));
                } else if (oldLine.contains(" Name:") && newLine.contains(" Name:")) {
                    corrected.append(correctName(oldLine, newLine));
                } else if (oldLine.contains("UnaryArithmeticExpression") &&
                                (newLine.contains("IntegerLiteralNode") || newLine.contains("LongLiteralNode") || newLine.contains("PIntLiteralNode"))) {
                    // replace unary operation node for negative numbers
                    int oldIndex = oldLine.indexOf("UnaryArithmeticExpression");
                    int newIndex = newLine.contains("IntegerLiteralNode")
                                    ? newLine.indexOf("IntegerLiteralNode")
                                    : newLine.contains("LongLiteralNode")
                                                    ? newLine.indexOf("LongLiteralNode")
                                                    : newLine.indexOf("PIntLiteralNode");
                    boolean wasCorrected = false;
                    if (oldIndex == newIndex) {
                        if (odlLineIndex + 4 < oldLines.size() && oldLines.get(odlLineIndex + 1).contains("LookupAndCallUnaryNodeGen") && oldLines.get(odlLineIndex + 2).contains("Op: __neg__")) {
                            odlLineIndex += 4;
                            newLineIndex++;
                            corrected.append(newLine).append(LINE_TEXT);
                            corrected.append(newLines.get(newLineIndex));
                            wasCorrected = true;
                        }
                    }
                    if (!wasCorrected) {
                        corrected.append(oldLine);
                    }
                } else {
                    corrected.append(correctSourceSection(oldLine, newLine));
                }
            }
            odlLineIndex++;
            newLineIndex++;
            if (odlLineIndex < oldLines.size()) {
                corrected.append(LINE_TEXT);
            }
        }
        return corrected.toString();
    }

    private static String correctSourceSection(String oldLine, String newLine) {
        int oldIndex = oldLine.indexOf("SourceSection:");
        int newIndex = newLine.indexOf("SourceSection:");
        if (oldIndex != newIndex || oldIndex == -1) {
            return oldLine;
        }
        String oldBegin = oldLine.substring(0, oldIndex);
        if (newLine.startsWith(oldBegin)) {
            return newLine;
        }
        return oldLine;
    }

    private static String correctName(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf(" Name: ");
        int newStart = newLine.indexOf(" Name: ");

        if (oldStart != newStart) {
            return oldLine;
        }
        int lastColumn = oldLine.lastIndexOf(':', oldStart + 6);
        if (newLine.startsWith(oldLine.substring(0, lastColumn))) {
            return newLine;
        }
        return oldLine;
    }

    private static String correctFrame(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("Frame: [");
        int newStart = newLine.indexOf("Frame: [");
        if (oldStart != newStart) {
            return oldLine;
        }
        int oldComma = oldLine.indexOf(',', oldStart);
        int newComma = newLine.indexOf(',', oldStart);
        String oldRest = oldLine.substring(oldComma);
        String newRest = newLine.substring(newComma);
        if (oldRest.equals(newRest) || (oldRest.contains("<>temp") && newRest.contains("<>temp"))) {
            return newLine;
        }
        return oldLine;
    }

    private static String correctSlot(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("flagSlot:");
        int newStart = newLine.indexOf("flagSlot:");
        if (oldStart != newStart) {
            return oldLine;
        }
        return newLine;
    }

    private static String correctDocumentation(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("Documentation:");
        int newStart = newLine.indexOf("Documentation:");
        if (oldStart != newStart) {
            return oldLine;
        }
        return newLine;
    }

    private static String correctFrameDesc(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("FrameDescriptor:");
        int newStart = newLine.indexOf("FrameDescriptor:");
        if (oldStart != newStart) {
            return oldLine;
        }
        int oldBracket = oldLine.indexOf('[', oldStart);
        int newBracket = newLine.indexOf('[', oldStart);
        if (oldBracket == -1 || newBracket == -1) {
            return oldLine;
        }
        String oldPart = oldLine.substring(0, oldBracket);
        String newPart = newLine.substring(0, newBracket);
        if (oldPart.equals(newPart)) {
            return newLine;
        }
        return oldLine;
    }

    private static String correctIdentifier(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("Identifier:");
        int newStart = newLine.indexOf("Identifier:");
        if (oldStart != newStart) {
            return oldLine;
        }
        if (oldLine.contains("Identifier: <>temp") && newLine.contains("Identifier: <>temp")) {
            return newLine;
        }
        return oldLine;
    }

    public static String readFile(File f) throws IOException {
        FileReader r = new FileReader(f);
        int fileLen = (int) f.length();
        CharBuffer cb = CharBuffer.allocate(fileLen);
        r.read(cb);
        cb.rewind();
        return cb.toString();
    }

    public File getTestFilesDir() {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/testData/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The test files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File testFile = new File(testDir, name.getMethodName() + ".py");
        return testFile;
    }

    public File getGoldenDataDir() {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/testData/goldenFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The golden files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getGoldenFile(String ext) {
        File goldenDir = getGoldenDataDir();
        File testDir = new File(goldenDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File goldenFile = new File(testDir, name.getMethodName() + ext);
        return goldenFile;
    }

    protected String getFileName(File file) {
        return file.getName().substring(0, file.getName().lastIndexOf('.'));
    }

    private String getContentDifferences(String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        if (expected.length() < printOnlyDiffIfLenIsBigger && actual.length() < printOnlyDiffIfLenIsBigger) {
            sb.append("Expected content is:").append(lineSeparator(2)).append(expected).append(lineSeparator(2)).append("but actual is:").append(lineSeparator(2)).append(actual).append(
                            lineSeparator(2)).append("It differs in the following things:").append(lineSeparator(2));
        } else {
            sb.append("Expected and actual differ in the following things:").append(lineSeparator(2));
        }

        List<String> expectedLines = Arrays.asList(expected.split("\n"));
        List<String> actualLines = Arrays.asList(actual.split("\n"));

        if (expectedLines.size() != actualLines.size()) {
            sb.append("Number of lines: \n\tExpected: ").append(expectedLines.size()).append("\n\tActual: ").append(actualLines.size()).append("\n\n");
        }

        // Appending lines which are missing in expected content and are present in actual content
        boolean noErrorInActual = true;
        for (String actualLine : actualLines) {
            if (expectedLines.contains(actualLine) == false) {
                if (noErrorInActual) {
                    sb.append("Actual content contains following lines which are missing in expected content: ").append(lineSeparator(1));
                    noErrorInActual = false;
                }
                sb.append("\t").append(actualLine).append(lineSeparator(1));
            }
        }

        // Appending lines which are missing in actual content and are present in expected content
        boolean noErrorInExpected = true;
        for (String expectedLine : expectedLines) {
            if (actualLines.contains(expectedLine) == false) {
                // If at least one line missing in actual content we want to append header line
                if (noErrorInExpected) {
                    sb.append("Expected content contains following lines which are missing in actual content: ").append(lineSeparator(1));
                    noErrorInExpected = false;
                }
                sb.append("\t").append(expectedLine).append(lineSeparator(1));
            }
        }

        // If both values are true it means the content is the same, but some lines are
        // placed on a different line number in actual and expected content
        if (noErrorInActual && noErrorInExpected && expectedLines.size() == actualLines.size()) {
            for (int lineNumber = 0; lineNumber < expectedLines.size(); lineNumber++) {
                String expectedLine = expectedLines.get(lineNumber);
                String actualLine = actualLines.get(lineNumber);

                if (!expectedLine.equals(actualLine)) {
                    sb.append("Line ").append(lineNumber).append(" contains different content than expected: ").append(lineSeparator(1)).append("Expected: \t").append(expectedLine).append(
                                    lineSeparator(1)).append("Actual:  \t").append(actualLine).append(lineSeparator(2));

                }
            }
        }

        return sb.toString();
    }

    private static String lineSeparator(int number) {
        final String lineSeparator = System.getProperty("line.separator");
        if (number > 1) {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < number; i++) {
                sb.append(lineSeparator);
            }
            return sb.toString();
        }
        return lineSeparator;
    }

    public void checkScopeAndTree(String source, PythonParser.ParserMode mode) throws Exception {
        checkScopeResult(source, mode);
        checkTreeResult(source, mode);
    }

    public void checkScopeAndTree(String source) throws Exception {
        checkScopeAndTree(source, PythonParser.ParserMode.File);
    }

    public void checkTreeResult(String source) throws Exception {
        checkTreeResult(source, PythonParser.ParserMode.File);
    }

    public void checkScopeAndTreeFromFile(File testFile) throws Exception {
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
