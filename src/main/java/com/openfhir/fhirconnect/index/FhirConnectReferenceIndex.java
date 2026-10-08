package com.openfhir.fhirconnect.index;

import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps every mapper name referenced by a FHIRConnect file (see {@link com.openfhir.fhirconnect.psi.ReferenceKind})
 * to the file, so "find usages" of a {@code metadata.name} only has to visit files that mention it.
 */
public final class FhirConnectReferenceIndex extends ScalarIndexExtension<String> {

    public static final ID<String, Void> NAME = ID.create("com.openfhir.fhirconnect.references");

    /** Bump whenever the indexer changes. */
    private static final int VERSION = 1;

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            YAMLFile file = FhirConnectIndexUtil.asFhirConnectFile(inputData);
            if (file == null) {
                return Collections.emptyMap();
            }
            Map<String, Void> result = new HashMap<>();
            for (YAMLScalar scalar : SyntaxTraverser.psiTraverser(file).filter(YAMLScalar.class)) {
                if (FhirConnectPsiUtil.getReferenceKind(scalar) == null) {
                    continue;
                }
                String name = FhirConnectPsiUtil.scalarValue(scalar);
                if (!name.isEmpty()) {
                    result.put(name, null);
                }
            }
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(YAMLFileType.YML);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
