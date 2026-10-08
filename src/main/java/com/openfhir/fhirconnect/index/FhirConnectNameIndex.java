package com.openfhir.fhirconnect.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.openfhir.fhirconnect.psi.FhirConnectFileType;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the names a FHIRConnect file can be referenced by to the file: its {@code metadata.name} and, for models,
 * its {@code spec.openEhrConfig.archetype}. The value records the file's {@code type} and whether the key is the
 * archetype id, so resolution and completion can filter candidates without loading PSI.
 */
public final class FhirConnectNameIndex extends FileBasedIndexExtension<String, FhirConnectNameIndex.Entry> {

    public static final ID<String, Entry> NAME = ID.create("com.openfhir.fhirconnect.names");

    /** Bump whenever the indexer or the value format changes. */
    private static final int VERSION = 1;

    /**
     * @param type      the {@code type} of the file, {@code null} when missing or unknown
     * @param archetype {@code true} when the key is the {@code spec.openEhrConfig.archetype} id rather than the name
     */
    public record Entry(@Nullable FhirConnectFileType type, boolean archetype) {
    }

    @Override
    public @NotNull ID<String, Entry> getName() {
        return NAME;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public @NotNull DataIndexer<String, Entry, FileContent> getIndexer() {
        return inputData -> {
            YAMLFile file = FhirConnectIndexUtil.asFhirConnectFile(inputData);
            if (file == null) {
                return Collections.emptyMap();
            }
            FhirConnectFileType type = FhirConnectPsiUtil.getFileType(file);
            Map<String, Entry> result = new HashMap<>(2);
            String name = FhirConnectPsiUtil.getMapperName(file);
            if (name != null) {
                result.put(name, new Entry(type, false));
            }
            String archetype = FhirConnectPsiUtil.getArchetypeId(file);
            if (archetype != null && !archetype.equals(name)) {
                result.put(archetype, new Entry(type, true));
            }
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<Entry> getValueExternalizer() {
        return new DataExternalizer<>() {
            @Override
            public void save(@NotNull DataOutput out, Entry value) throws IOException {
                out.writeByte(value.type() == null ? -1 : value.type().ordinal());
                out.writeBoolean(value.archetype());
            }

            @Override
            public Entry read(@NotNull DataInput in) throws IOException {
                int ordinal = in.readByte();
                boolean archetype = in.readBoolean();
                FhirConnectFileType[] types = FhirConnectFileType.values();
                FhirConnectFileType type = ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
                return new Entry(type, archetype);
            }
        };
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
