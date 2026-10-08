package com.openfhir.fhirconnect;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;

import java.util.List;

public class FhirConnectCompletionTest extends FhirConnectTestBase {

    private static final List<String> MODELS = List.of(
            "CLUSTER.case_identification.v0", "COMPOSITION.request.v1.ServiceRequest", "INSTRUCTION.service_request.v1");
    private static final List<String> EXTENSIONS = List.of("KDS_composition", "KDS_laborauftrag");
    private static final List<String> CONTEXTS = List.of("KDS_laborauftrag.context", "KDS_sub.context");

    public void testSlotArchetypeOffersModelNamesOnly() {
        myFixture.addFileToProject("projects/a/new_extension.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: new_extension
                spec:
                  extends: INSTRUCTION.service_request.v1
                mappings:
                  - name: "m"
                    slotArchetype: ""
                """);
        configure("projects/a/new_extension.yml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("slotArchetype: \"\"", "\"\""));
        LookupElement[] items = myFixture.completeBasic();
        assertNotNull(items);
        assertEquals(MODELS, sortedLookupStrings());
        LookupElementPresentation presentation = new LookupElementPresentation();
        items[0].renderElement(presentation);
        assertEquals("model mapper", presentation.getTypeText());
        assertTrue(String.valueOf(presentation.getTailText()), presentation.getTailText().contains("model/"));
    }

    public void testCompletionInsertsSelectedName() {
        myFixture.addFileToProject("projects/a/new_extension.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: new_extension
                spec:
                  extends: INSTRUCTION.service_request.v1
                mappings:
                  - name: "m"
                    slotArchetype: "CLUSTER.ca"
                """);
        configure("projects/a/new_extension.yml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("slotArchetype: \"CLUSTER.ca\"", "CLUSTER.ca") + 9);
        LookupElement[] items = myFixture.completeBasic();
        assertNull("single match should be inserted directly, got " + myFixture.getLookupElementStrings(), items);
        assertTrue(myFixture.getEditor().getDocument().getText().contains("slotArchetype: \"CLUSTER.case_identification.v0\""));
    }

    public void testContextExtensionsOffersExtensionNamesOnly() {
        myFixture.addFileToProject("projects/a/new.context.yaml", """
                grammar: FHIRConnect/v1.0.0
                type: context
                metadata:
                  name: new.context
                context:
                  extensions:
                    -\s
                  start: "INSTRUCTION.service_request.v1"
                """);
        configure("projects/a/new.context.yaml");
        int offset = myFixture.getEditor().getDocument().getText().indexOf("- \n") + 2;
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertNotNull(myFixture.completeBasic());
        assertEquals(EXTENSIONS, sortedLookupStrings());
    }

    public void testContextStartOffersModelNames() {
        myFixture.addFileToProject("projects/a/new.context.yaml", """
                grammar: FHIRConnect/v1.0.0
                type: context
                metadata:
                  name: new.context
                context:
                  start: ""
                """);
        configure("projects/a/new.context.yaml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("start: \"\"", "\"\""));
        assertNotNull(myFixture.completeBasic());
        assertEquals(MODELS, sortedLookupStrings());
    }

    public void testSlotContextOffersContextNames() {
        myFixture.addFileToProject("projects/a/new_extension.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: new_extension
                spec:
                  extends: INSTRUCTION.service_request.v1
                mappings:
                  - name: "m"
                    slotContext: ""
                """);
        configure("projects/a/new_extension.yml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("slotContext: \"\"", "\"\""));
        assertNotNull(myFixture.completeBasic());
        assertEquals(CONTEXTS, sortedLookupStrings());
    }

    public void testNoMapperCompletionOutsideReferenceSites() {
        myFixture.addFileToProject("projects/a/new_extension.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: new_extension
                spec:
                  extends: INSTRUCTION.service_request.v1
                mappings:
                  - name: ""
                """);
        configure("projects/a/new_extension.yml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("- name: \"\"", "\"\""));
        myFixture.completeBasic();
        List<String> strings = myFixture.getLookupElementStrings();
        if (strings != null) {
            for (String model : MODELS) {
                assertFalse("unexpected completion " + model, strings.contains(model));
            }
        }
    }

    public void testNoMapperCompletionInNonFhirConnectFile() {
        myFixture.addFileToProject("plain.yaml", """
                mappings:
                  - name: "m"
                    slotArchetype: ""
                """);
        configure("plain.yaml");
        myFixture.getEditor().getCaretModel().moveToOffset(offsetOf("slotArchetype: \"\"", "\"\""));
        myFixture.completeBasic();
        List<String> strings = myFixture.getLookupElementStrings();
        if (strings != null) {
            for (String model : MODELS) {
                assertFalse("unexpected completion " + model, strings.contains(model));
            }
        }
    }

    private List<String> sortedLookupStrings() {
        List<String> strings = myFixture.getLookupElementStrings();
        assertNotNull(strings);
        return strings.stream().sorted().toList();
    }
}
