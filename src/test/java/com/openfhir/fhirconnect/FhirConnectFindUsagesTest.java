package com.openfhir.fhirconnect;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.openfhir.fhirconnect.navigation.FhirConnectMapperTarget;
import com.openfhir.fhirconnect.navigation.FhirConnectUsagesGotoHandler;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public class FhirConnectFindUsagesTest extends FhirConnectTestBase {

    private static final String MODEL_INSTRUCTION = "model/INSTRUCTION.service_request.v1.yml";

    private static final List<String> EXPECTED_USAGES = List.of(
            "projects/a/KDS_laborauftrag.context.yaml: - \"INSTRUCTION.service_request.v1\"",
            "projects/a/KDS_laborauftrag.context.yaml: start: \"INSTRUCTION.service_request.v1\"",
            "projects/a/KDS_laborauftrag.yml: extends: INSTRUCTION.service_request.v1",
            "projects/a/KDS_sub.context.yaml: - \"INSTRUCTION.service_request.v1\"",
            "projects/a/KDS_sub.context.yaml: start: \"INSTRUCTION.service_request.v1\"",
            "projects/b/KDS_composition.yml: slotArchetype: INSTRUCTION.service_request.v1");

    public void testCaretOnNameIsMapperDeclaration() {
        configure(MODEL_INSTRUCTION);
        moveCaretTo("name: INSTRUCTION.service_request.v1", "INSTRUCTION.service_request.v1");
        PsiElement element = myFixture.getElementAtCaret();
        FhirConnectMapperTarget target = FhirConnectMapperTarget.from(element);
        assertNotNull("element at caret should be the mapper target, was " + element, target);
        assertEquals("INSTRUCTION.service_request.v1", target.getName());
    }

    public void testFindUsagesOfModelName() {
        configure(MODEL_INSTRUCTION);
        moveCaretTo("name: INSTRUCTION.service_request.v1", "INSTRUCTION.service_request.v1");
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertEquals(EXPECTED_USAGES, describe(usages));
    }

    public void testFindUsagesFromReferenceSite() {
        configure("projects/a/KDS_laborauftrag.yml");
        moveCaretTo("extends: INSTRUCTION.service_request.v1", "INSTRUCTION.service_request.v1");
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertEquals(EXPECTED_USAGES, describe(usages));
    }

    public void testGotoDeclarationOnNameListsUsages() {
        configure(MODEL_INSTRUCTION);
        int offset = offsetOf("name: INSTRUCTION.service_request.v1", "INSTRUCTION.service_request.v1");
        PsiElement leaf = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = new FhirConnectUsagesGotoHandler()
                .getGotoDeclarationTargets(leaf, offset, myFixture.getEditor());
        assertNotNull(targets);
        TreeSet<String> described = new TreeSet<>();
        for (PsiElement target : targets) {
            described.add(describe(target));
        }
        assertEquals(new TreeSet<>(EXPECTED_USAGES), described);
    }

    public void testGotoDeclarationOnUnrelatedScalarDoesNothing() {
        configure(MODEL_INSTRUCTION);
        int offset = offsetOf("fhir: \"$resource.requester\"", "$resource.requester");
        PsiElement leaf = myFixture.getFile().findElementAt(offset);
        assertNull(new FhirConnectUsagesGotoHandler().getGotoDeclarationTargets(leaf, offset, myFixture.getEditor()));
    }

    private static List<String> describe(Collection<UsageInfo> usages) {
        TreeSet<String> result = new TreeSet<>();
        for (UsageInfo usage : usages) {
            result.add(describe(usage.getElement()));
        }
        return List.copyOf(result);
    }

    private static String describe(PsiElement element) {
        assertNotNull(element);
        String path = element.getContainingFile().getVirtualFile().getPath();
        String relative = path.substring(path.indexOf("/projects/") + 1);
        String text = element.getContainingFile().getText();
        int lineStart = text.lastIndexOf('\n', element.getTextOffset()) + 1;
        int lineEnd = text.indexOf('\n', element.getTextOffset());
        String line = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd).trim();
        return relative + ": " + line;
    }
}
