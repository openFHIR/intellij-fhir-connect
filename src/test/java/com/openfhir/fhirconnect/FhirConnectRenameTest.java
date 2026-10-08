package com.openfhir.fhirconnect;

public class FhirConnectRenameTest extends FhirConnectTestBase {

    public void testRenameOnDeclarationUpdatesAllReferences() {
        configure("model/CLUSTER.case_identification.v0.yml");
        moveCaretTo("name:  CLUSTER.case_identification.v0", "CLUSTER.case_identification.v0");
        myFixture.renameElementAtCaret("CLUSTER.case_identification.v1");

        // The YAML manipulator normalises the whitespace after the key when it replaces the scalar.
        assertTrue(textOf("model/CLUSTER.case_identification.v0.yml")
                .matches("(?s).*name: +CLUSTER\\.case_identification\\.v1\\n.*"));
        assertFileContains("model/INSTRUCTION.service_request.v1.yml",
                "slotArchetype: \"CLUSTER.case_identification.v1\"");

        assertFileContains("projects/a/KDS_composition.yml", "    slotArchetype: \"CLUSTER.case_identification.v1\"");
        assertFileContains("projects/a/KDS_composition.yml", "          slotArchetype: \"CLUSTER.case_identification.v1\"");
        assertFalse(textOf("projects/a/KDS_composition.yml").contains("CLUSTER.case_identification.v0\""));

        assertFileContains("projects/a/KDS_laborauftrag.yml", "slotArchetype: \"CLUSTER.case_identification.v1\"");
        assertFileContains("projects/a/KDS_laborauftrag.context.yaml", "    - CLUSTER.case_identification.v1\n");

        // Non-FHIRConnect files and unrelated keys are untouched.
        assertFileContains("other.yaml", "slotArchetype: \"CLUSTER.case_identification.v0\"");
        assertFileContains("model/CLUSTER.case_identification.v0.yml",
                "archetype: openEHR-EHR-CLUSTER.case_identification.v0");
    }

    public void testRenameFromReferenceSiteUpdatesDeclaration() {
        configure("projects/a/KDS_laborauftrag.context.yaml");
        moveCaretTo("start: \"INSTRUCTION.service_request.v1\"", "INSTRUCTION.service_request.v1");
        myFixture.renameElementAtCaret("INSTRUCTION.service_request.v2");

        assertFileContains("model/INSTRUCTION.service_request.v1.yml", "name: INSTRUCTION.service_request.v2");
        assertFileContains("projects/a/KDS_laborauftrag.context.yaml", "- \"INSTRUCTION.service_request.v2\"");
        assertFileContains("projects/a/KDS_laborauftrag.context.yaml", "start: \"INSTRUCTION.service_request.v2\"");
        assertFileContains("projects/a/KDS_laborauftrag.yml", "extends: INSTRUCTION.service_request.v2");
        assertFileContains("projects/b/KDS_composition.yml", "slotArchetype: INSTRUCTION.service_request.v2");
        assertFileContains("projects/a/KDS_sub.context.yaml", "start: \"INSTRUCTION.service_request.v2\"");
    }

    private void assertFileContains(String path, String snippet) {
        String text = textOf(path);
        assertTrue("expected " + path + " to contain <" + snippet + ">, but it is:\n" + text, text.contains(snippet));
    }
}
