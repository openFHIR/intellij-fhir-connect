package com.openfhir.fhirconnect;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.openfhir.fhirconnect.index.FhirConnectNameIndex;
import com.openfhir.fhirconnect.index.FhirConnectReferenceIndex;
import com.openfhir.fhirconnect.psi.FhirConnectFileType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FhirConnectIndexTest extends FhirConnectTestBase {

    public void testNameIndexMapsNameToDeclaringFile() {
        assertEquals(List.of("model/CLUSTER.case_identification.v0.yml"),
                files(FhirConnectNameIndex.NAME, "CLUSTER.case_identification.v0"));
        assertEquals(List.of("projects/a/KDS_composition.yml", "projects/b/KDS_composition.yml"),
                files(FhirConnectNameIndex.NAME, "KDS_composition"));
    }

    public void testNameIndexRecordsFileTypeAndArchetypeId() {
        List<FhirConnectNameIndex.Entry> entries = new ArrayList<>();
        FileBasedIndex.getInstance().processValues(FhirConnectNameIndex.NAME, "CLUSTER.case_identification.v0", null,
                (file, entry) -> entries.add(entry), scope());
        assertEquals(List.of(new FhirConnectNameIndex.Entry(FhirConnectFileType.MODEL, false)), entries);

        entries.clear();
        FileBasedIndex.getInstance().processValues(FhirConnectNameIndex.NAME,
                "openEHR-EHR-CLUSTER.case_identification.v0", null, (file, entry) -> entries.add(entry), scope());
        assertEquals(List.of(new FhirConnectNameIndex.Entry(FhirConnectFileType.MODEL, true)), entries);

        entries.clear();
        FileBasedIndex.getInstance().processValues(FhirConnectNameIndex.NAME, "KDS_laborauftrag.context", null,
                (file, entry) -> entries.add(entry), scope());
        assertEquals(List.of(new FhirConnectNameIndex.Entry(FhirConnectFileType.CONTEXT, false)), entries);
    }

    public void testReferenceIndexMapsReferencedNameToReferencingFiles() {
        assertEquals(List.of("projects/a/KDS_laborauftrag.context.yaml", "projects/a/KDS_laborauftrag.yml"),
                files(FhirConnectReferenceIndex.NAME, "KDS_sub.context"));
        assertEquals(List.of("model/INSTRUCTION.service_request.v1.yml", "projects/a/KDS_composition.yml",
                        "projects/a/KDS_laborauftrag.context.yaml", "projects/a/KDS_laborauftrag.yml"),
                files(FhirConnectReferenceIndex.NAME, "CLUSTER.case_identification.v0"));
    }

    public void testNonFhirConnectYamlIsNotIndexed() {
        for (String key : List.of("CLUSTER.case_identification.v0", "INSTRUCTION.service_request.v1")) {
            assertFalse(files(FhirConnectNameIndex.NAME, key).contains("other.yaml"));
            assertFalse(files(FhirConnectReferenceIndex.NAME, key).contains("other.yaml"));
        }
    }

    public void testIndexFollowsEdits() {
        myFixture.addFileToProject("projects/a/fresh.yml", """
                grammar: FHIRConnect/v1.0.0
                type: extension
                metadata:
                  name: fresh_extension
                spec:
                  extends: INSTRUCTION.service_request.v1
                """);
        assertEquals(List.of("projects/a/fresh.yml"), files(FhirConnectNameIndex.NAME, "fresh_extension"));
        assertTrue(files(FhirConnectReferenceIndex.NAME, "INSTRUCTION.service_request.v1").contains("projects/a/fresh.yml"));
    }

    private GlobalSearchScope scope() {
        return GlobalSearchScope.projectScope(getProject());
    }

    private <K> List<String> files(com.intellij.util.indexing.ID<K, ?> id, K key) {
        Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(id, key, scope());
        VirtualFile root = myFixture.getTempDirFixture().getFile("");
        assertNotNull(root);
        return files.stream()
                .map(file -> file.getPath().substring(root.getPath().length() + 1))
                .sorted()
                .toList();
    }
}
