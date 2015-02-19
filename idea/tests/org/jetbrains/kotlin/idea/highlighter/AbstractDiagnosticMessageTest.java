/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.highlighter;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analyzer.AnalysisResult;
import org.jetbrains.kotlin.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.kotlin.diagnostics.Diagnostic;
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory;
import org.jetbrains.kotlin.diagnostics.Errors;
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages;
import org.jetbrains.kotlin.idea.PluginTestCaseBase;
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs;
import org.jetbrains.kotlin.psi.JetFile;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm;
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil;
import org.jetbrains.kotlin.test.ConfigurationKind;
import org.jetbrains.kotlin.test.JetLiteFixture;
import org.jetbrains.kotlin.test.JetTestUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.kotlin.idea.highlighter.formatHtml.FormatHtmlPackage.formatHtml;

public abstract class AbstractDiagnosticMessageTest extends JetLiteFixture {
    private static final String DIAGNOSTICS_NUMBER_DIRECTIVE = "DIAGNOSTICS_NUMBER";
    private static final String DIAGNOSTICS_DIRECTIVE = "DIAGNOSTICS";
    private static final String MESSAGE_TYPE_DIRECTIVE = "MESSAGE_TYPE";

    private enum MessageType {
        TEXT("TEXT", "txt"), HTML("HTML", "html");

        public final String directive;
        public final String extension;

        MessageType(String directive, String extension) {
            this.directive = directive;
            this.extension = extension;
        }
    }

    @NotNull
    @Override
    protected JetCoreEnvironment createEnvironment() {
        return createEnvironmentWithMockJdk(ConfigurationKind.JDK_ONLY);
    }

    @NotNull
    @Override
    protected String getTestDataPath() {
        return PluginTestCaseBase.getTestDataPathBase() + "/diagnosticMessage/";
    }

    @NotNull
    protected AnalysisResult analyze(@NotNull JetFile file) {
        return JvmResolveUtil.analyzeOneFileWithJavaIntegration(file);
    }

    public void doTest(String filePath) throws Exception {
        File file = new File(filePath);
        String fileName = file.getName();

        String fileData = JetTestUtils.doLoadFile(file);
        Map<String,String> directives = JetTestUtils.parseDirectives(fileData);
        int diagnosticNumber = getDiagnosticNumber(directives);
        final Set<DiagnosticFactory<?>> diagnosticFactories = getDiagnosticFactories(directives);
        MessageType messageType = getMessageTypeDirective(directives);

        JetFile psiFile = createPsiFile(null, fileName, loadFile(fileName));
        AnalysisResult analysisResult = analyze(psiFile);
        BindingContext bindingContext = analysisResult.getBindingContext();

        List<Diagnostic> diagnostics = ContainerUtil.filter(bindingContext.getDiagnostics().all(), new Condition<Diagnostic>() {
            @Override
            public boolean value(Diagnostic diagnostic) {
                return diagnosticFactories.contains(diagnostic.getFactory());
            }
        });

        assertEquals("Expected diagnostics number mismatch:", diagnosticNumber, diagnostics.size());

        int index = 1;
        String name = FileUtil.getNameWithoutExtension(fileName);
        for (Diagnostic diagnostic : diagnostics) {
            String readableDiagnosticText;
            String extension;
            if (messageType != MessageType.TEXT && IdeErrorMessages.hasIdeSpecificMessage(diagnostic)) {
                readableDiagnosticText = formatHtml(IdeErrorMessages.render(diagnostic));
                extension = MessageType.HTML.extension;
            }
            else {
                readableDiagnosticText = DefaultErrorMessages.render(diagnostic);
                extension = MessageType.TEXT.extension;
            }
            String errorMessageFileName = name + index;
            String path = getTestDataPath() + "/" + errorMessageFileName + "." + extension;
            String actualText = "<!-- " + errorMessageFileName + " -->\n" + readableDiagnosticText;
            assertSameLinesWithFile(path, actualText);

            index++;
        }
    }

    private static int getDiagnosticNumber(Map<String, String> directives) {
        String diagnosticsNumber = directives.get(DIAGNOSTICS_NUMBER_DIRECTIVE);
        assert diagnosticsNumber != null : DIAGNOSTICS_NUMBER_DIRECTIVE + " should be present.";
        try {
            return Integer.parseInt(diagnosticsNumber);
        }
        catch (NumberFormatException e) {
            throw new AssertionError(DIAGNOSTICS_NUMBER_DIRECTIVE + " should contain number as its value.");
        }
    }

    @NotNull
    private Set<DiagnosticFactory<?>> getDiagnosticFactories(Map<String, String> directives) {
        String diagnosticsData = directives.get(DIAGNOSTICS_DIRECTIVE);
        assert diagnosticsData != null : DIAGNOSTICS_DIRECTIVE + " should be present.";
        Set<DiagnosticFactory<?>> diagnosticFactories = Sets.newHashSet();
        String[] diagnostics = diagnosticsData.split(" ");
        for (String diagnosticName : diagnostics) {
            Object diagnostic = getDiagnostic(diagnosticName);
            assert diagnostic instanceof DiagnosticFactory: "Can't load diagnostic factory for " + diagnosticName;
            diagnosticFactories.add((DiagnosticFactory) diagnostic);
        }
        return diagnosticFactories;
    }

    @Nullable
    private Object getDiagnostic(@NotNull String diagnosticName) {
        Field field = getPlatformSpecificDiagnosticField(diagnosticName);

        if (field == null) {
            field = getFieldOrNull(Errors.class, diagnosticName);
        }

        if (field == null) return null;

        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Nullable
    protected Field getPlatformSpecificDiagnosticField(@NotNull String diagnosticName) {
        return getFieldOrNull(ErrorsJvm.class, diagnosticName);
    }

    @Nullable
    protected static Field getFieldOrNull(@NotNull Class<?> kind, @NotNull String field) {
        try {
            return kind.getField(field);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Nullable
    private static MessageType getMessageTypeDirective(Map<String, String> directives) {
        String messageType = directives.get(MESSAGE_TYPE_DIRECTIVE);
        if (messageType == null) return null;
        try {
            return MessageType.valueOf(messageType);
        }
        catch (IllegalArgumentException e) {
            throw new AssertionError(MESSAGE_TYPE_DIRECTIVE + " should be " + MessageType.TEXT.directive + " or " +
                                     MessageType.HTML.directive + ". But was: \"" + messageType + "\".");
        }
    }
}
