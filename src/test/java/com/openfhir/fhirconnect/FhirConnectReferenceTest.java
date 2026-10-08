package com.openfhir.fhirconnect;

import com.intellij.psi.PsiReference;
import com.openfhir.fhirconnect.navigation.FhirConnectMapperReference;
import com.openfhir.fhirconnect.psi.ReferenceKind;

import java.util.List;

public class FhirConnectReferenceTest extends FhirConnectTestBase {

    private static final String CONTEXT = "projects/a/KDS_laborauftrag.context.yaml";
    private static final String MODEL_CLUSTER = "model/CLUSTER.case_identification.v0.yml";
    private static final String MODEL_INSTRUCTION = "model/INSTRUCTION.service_request.v1.yml";

    public void testSlotArchetypeResolvesToModel() {
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_composition.yml",
                "    slotArchetype: \"CLUSTER.case_identification.v0\"", "CLUSTER.case_identification.v0");
        assertEquals(ReferenceKind.SLOT_ARCHETYPE, reference.getKind());
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testSlotArchetypeNestedUnderReferenceMappings() {
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_composition.yml",
                "          slotArchetype: \"CLUSTER.case_identification.v0\"", "CLUSTER.case_identification.v0");
        assertEquals(ReferenceKind.SLOT_ARCHETYPE, reference.getKind());
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testSlotArchetypeNestedUnderFollowedByMappings() {
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_laborauftrag.yml",
                "          slotArchetype: \"CLUSTER.case_identification.v0\"", "CLUSTER.case_identification.v0");
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testSlotArchetypeInsideModel() {
        FhirConnectMapperReference reference = referenceAt(MODEL_INSTRUCTION,
                "slotArchetype: \"CLUSTER.case_identification.v0\"", "CLUSTER.case_identification.v0");
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testExtendsResolvesToModel() {
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_laborauftrag.yml",
                "extends: INSTRUCTION.service_request.v1", "INSTRUCTION.service_request.v1");
        assertEquals(ReferenceKind.EXTENDS, reference.getKind());
        assertResolvesTo(reference, "INSTRUCTION.service_request.v1", MODEL_INSTRUCTION);
    }

    public void testContextArchetypeQuoted() {
        FhirConnectMapperReference reference = referenceAt(CONTEXT,
                "- \"INSTRUCTION.service_request.v1\"", "INSTRUCTION.service_request.v1");
        assertEquals(ReferenceKind.CONTEXT_ARCHETYPE, reference.getKind());
        assertResolvesTo(reference, "INSTRUCTION.service_request.v1", MODEL_INSTRUCTION);
    }

    public void testContextArchetypeUnquoted() {
        FhirConnectMapperReference reference = referenceAt(CONTEXT,
                "- CLUSTER.case_identification.v0", "CLUSTER.case_identification.v0");
        assertEquals(ReferenceKind.CONTEXT_ARCHETYPE, reference.getKind());
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testContextExtensionResolvesToExtension() {
        FhirConnectMapperReference reference = referenceAt(CONTEXT, "- \"KDS_laborauftrag\"", "KDS_laborauftrag");
        assertEquals(ReferenceKind.CONTEXT_EXTENSION, reference.getKind());
        assertResolvesTo(reference, "KDS_laborauftrag", "projects/a/KDS_laborauftrag.yml");
    }

    public void testDuplicateNamePrefersNearestDirectory() {
        FhirConnectMapperReference reference = referenceAt(CONTEXT, "- \"KDS_composition\"", "KDS_composition");
        assertResolvesTo(reference, "KDS_composition", "projects/a/KDS_composition.yml");
    }

    public void testDuplicateNameAtEqualDistanceOffersAllCandidates() {
        myFixture.addFileToProject("projects/c/other.context.yaml", """
                grammar: FHIRConnect/v1.0.0
                type: context
                metadata:
                  name: other.context
                context:
                  extensions:
                    - "KDS_composition"
                """);
        FhirConnectMapperReference reference = referenceAt("projects/c/other.context.yaml",
                "- \"KDS_composition\"", "KDS_composition");
        List<String> paths = resolvedPaths(reference);
        assertEquals(paths.toString(), 2, paths.size());
        assertTrue(paths.stream().anyMatch(path -> path.endsWith("projects/a/KDS_composition.yml")));
        assertTrue(paths.stream().anyMatch(path -> path.endsWith("projects/b/KDS_composition.yml")));
    }

    public void testStartResolvesToModel() {
        // Regression: plugin 1.0.x looked for "starts:" and never navigated from "start".
        FhirConnectMapperReference reference = referenceAt(CONTEXT,
                "start: \"INSTRUCTION.service_request.v1\"", "INSTRUCTION.service_request.v1");
        assertEquals(ReferenceKind.CONTEXT_START, reference.getKind());
        assertResolvesTo(reference, "INSTRUCTION.service_request.v1", MODEL_INSTRUCTION);
    }

    public void testSlotContextResolvesToContext() {
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_laborauftrag.yml",
                "slotContext: \"KDS_sub.context\"", "KDS_sub.context");
        assertEquals(ReferenceKind.SLOT_CONTEXT, reference.getKind());
        assertResolvesTo(reference, "KDS_sub.context", "projects/a/KDS_sub.context.yaml");
    }

    public void testContextsResolvesToContext() {
        FhirConnectMapperReference reference = referenceAt(CONTEXT, "- \"KDS_sub.context\"", "KDS_sub.context");
        assertEquals(ReferenceKind.CONTEXT_CONTEXT, reference.getKind());
        assertResolvesTo(reference, "KDS_sub.context", "projects/a/KDS_sub.context.yaml");
    }

    public void testQuotedAndUnquotedNameDeclarationsBothResolve() {
        // COMPOSITION.request.v1.ServiceRequest declares its metadata.name in double quotes.
        FhirConnectMapperReference reference = referenceAt("projects/a/KDS_composition.yml",
                "extends: COMPOSITION.request.v1.ServiceRequest", "COMPOSITION.request.v1.ServiceRequest");
        assertResolvesTo(reference, "COMPOSITION.request.v1.ServiceRequest",
                "model/COMPOSITION.request.v1.ServiceRequest.yml");
    }

    public void testCaseInsensitiveFallback() {
        myFixture.addFileToProject("projects/a/mixed_case.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: mixed_case
                spec:
                  extends: cluster.CASE_identification.V0
                """);
        FhirConnectMapperReference reference = referenceAt("projects/a/mixed_case.yml",
                "extends: cluster.CASE_identification.V0", "cluster.CASE_identification.V0");
        assertResolvesTo(reference, "CLUSTER.case_identification.v0", MODEL_CLUSTER);
    }

    public void testUnknownNameResolvesToNothing() {
        myFixture.addFileToProject("projects/a/unknown.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: unknown
                spec:
                  extends: DOES.not.exist.v9
                """);
        FhirConnectMapperReference reference = referenceAt("projects/a/unknown.yml",
                "extends: DOES.not.exist.v9", "DOES.not.exist.v9");
        assertNull(reference.resolve());
        assertEquals(0, reference.multiResolve(false).length);
        assertTrue("unresolved references must stay soft", reference.isSoft());
    }

    public void testNoReferenceInNonFhirConnectFile() {
        configure("other.yaml");
        PsiReference reference = myFixture.getFile().findReferenceAt(
                offsetOf("slotArchetype: \"CLUSTER.case_identification.v0\"", "CLUSTER.case_identification.v0"));
        assertFalse("no mapper reference expected in other.yaml, got " + reference,
                reference instanceof FhirConnectMapperReference);
    }

    public void testNoReferenceOnUnrelatedKeys() {
        configure(MODEL_CLUSTER);
        PsiReference reference = myFixture.getFile().findReferenceAt(
                offsetOf("- name: \"identifierCaseParent\"", "identifierCaseParent"));
        assertFalse(reference instanceof FhirConnectMapperReference);
        reference = myFixture.getFile().findReferenceAt(
                offsetOf("archetype: openEHR-EHR-CLUSTER.case_identification.v0", "openEHR-EHR-CLUSTER"));
        assertFalse(reference instanceof FhirConnectMapperReference);
    }
}
