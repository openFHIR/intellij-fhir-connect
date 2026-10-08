package com.openfhir.fhirconnect;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.openfhir.fhirconnect.navigation.FhirConnectMapperReference;
import com.openfhir.fhirconnect.navigation.FhirConnectMapperTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;

/**
 * Copies the mini mapping library from {@code src/test/testData} into the test project and offers caret helpers.
 * Test data files contain no caret markup so that the same files can be used by every test.
 */
public abstract class FhirConnectTestBase extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("", "");
    }

    protected @NotNull PsiFile configure(@NotNull String path) {
        return myFixture.configureFromTempProjectFile(path);
    }

    /**
     * @return the offset one character into {@code value}, searched after the first occurrence of {@code line}.
     */
    protected int offsetOf(@NotNull String line, @NotNull String value) {
        String text = myFixture.getEditor().getDocument().getText();
        int lineOffset = text.indexOf(line);
        assertTrue("snippet not found: " + line, lineOffset >= 0);
        int valueOffset = text.indexOf(value, lineOffset);
        assertTrue("value not found after snippet: " + value, valueOffset >= 0);
        return valueOffset + 1;
    }

    protected void moveCaretTo(@NotNull String line, @NotNull String value) {
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf(line, value));
    }

    protected @NotNull FhirConnectMapperReference referenceAt(@NotNull String path, @NotNull String line,
                                                              @NotNull String value) {
        configure(path);
        PsiReference reference = myFixture.getFile().findReferenceAt(offsetOf(line, value));
        assertNotNull("no reference at " + line + " / " + value, reference);
        assertTrue("unexpected reference " + reference, reference instanceof FhirConnectMapperReference);
        return (FhirConnectMapperReference) reference;
    }

    protected static @NotNull FhirConnectMapperTarget assertResolvesTo(@NotNull PsiReference reference,
                                                                       @NotNull String expectedName,
                                                                       @NotNull String expectedPathSuffix) {
        PsiElement resolved = reference.resolve();
        assertNotNull("reference did not resolve to exactly one target: " + resolvedPaths(reference), resolved);
        FhirConnectMapperTarget target = FhirConnectMapperTarget.from(resolved);
        assertNotNull("not a mapper target: " + resolved, target);
        assertEquals(expectedName, target.getName());
        assertTrue("resolved to " + path(target) + ", expected *" + expectedPathSuffix,
                path(target).endsWith(expectedPathSuffix));
        PsiElement navigationElement = resolved.getNavigationElement();
        assertTrue("navigation element must be the metadata.name scalar: " + navigationElement,
                navigationElement instanceof YAMLScalar);
        assertEquals(expectedName, ((YAMLScalar) navigationElement).getTextValue().trim());
        return target;
    }

    protected static @NotNull List<String> resolvedPaths(@NotNull PsiReference reference) {
        List<String> paths = new ArrayList<>();
        if (reference instanceof FhirConnectMapperReference mapperReference) {
            for (ResolveResult result : mapperReference.multiResolve(false)) {
                FhirConnectMapperTarget target = FhirConnectMapperTarget.from(result.getElement());
                paths.add(target == null ? String.valueOf(result.getElement()) : path(target));
            }
        }
        return paths;
    }

    protected static @NotNull String path(@NotNull FhirConnectMapperTarget target) {
        return target.getFile().getVirtualFile().getPath();
    }

    /**
     * @return the current (possibly unsaved) text of a file in the test project.
     */
    protected @NotNull String textOf(@NotNull String path) {
        VirtualFile virtualFile = myFixture.findFileInTempDir(path);
        assertNotNull("missing file " + path, virtualFile);
        PsiFile file = PsiManager.getInstance(getProject()).findFile(virtualFile);
        assertNotNull(file);
        return file.getText();
    }
}
