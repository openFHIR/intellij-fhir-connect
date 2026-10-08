package com.openfhir.fhirconnect.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure PSI helpers for FHIRConnect YAML files. No I/O, no index access.
 */
public final class FhirConnectPsiUtil {

    /** Every FHIRConnect file starts with {@code grammar: FHIRConnect/v...}. */
    public static final String GRAMMAR_PREFIX = "FHIRConnect/";

    public static final String KEY_GRAMMAR = "grammar";
    public static final String KEY_TYPE = "type";
    public static final String KEY_METADATA = "metadata";
    public static final String KEY_NAME = "name";
    public static final String KEY_SPEC = "spec";
    public static final String KEY_OPENEHR_CONFIG = "openEhrConfig";
    public static final String KEY_ARCHETYPE = "archetype";
    public static final String KEY_EXTENDS = "extends";
    public static final String KEY_CONTEXT = "context";
    public static final String KEY_START = "start";
    public static final String KEY_ARCHETYPES = "archetypes";
    public static final String KEY_EXTENSIONS = "extensions";
    public static final String KEY_CONTEXTS = "contexts";
    public static final String KEY_MAPPINGS = "mappings";
    public static final String KEY_SLOT_ARCHETYPE = "slotArchetype";
    public static final String KEY_SLOT_CONTEXT = "slotContext";

    private FhirConnectPsiUtil() {
    }

    /**
     * @return {@code true} when the file is a YAML file whose top-level {@code grammar} value starts with
     * {@value #GRAMMAR_PREFIX}.
     */
    public static boolean isFhirConnectFile(@Nullable PsiFile file) {
        if (!(file instanceof YAMLFile yamlFile)) {
            return false;
        }
        YAMLScalar grammar = getScalar(yamlFile, KEY_GRAMMAR);
        return grammar != null && scalarValue(grammar).startsWith(GRAMMAR_PREFIX);
    }

    /**
     * @return the file type declared by the top-level {@code type} key, or {@code null} when absent or unknown.
     */
    public static @Nullable FhirConnectFileType getFileType(@NotNull YAMLFile file) {
        YAMLScalar type = getScalar(file, KEY_TYPE);
        return type == null ? null : FhirConnectFileType.fromKey(scalarValue(type));
    }

    /**
     * @return the scalar holding the value of {@code metadata.name}, or {@code null}.
     */
    public static @Nullable YAMLScalar getMapperNameElement(@NotNull YAMLFile file) {
        return getScalar(file, KEY_METADATA, KEY_NAME);
    }

    /**
     * @return the trimmed, unquoted value of {@code metadata.name}, or {@code null} when absent or blank.
     */
    public static @Nullable String getMapperName(@NotNull YAMLFile file) {
        YAMLScalar name = getMapperNameElement(file);
        if (name == null) {
            return null;
        }
        String value = scalarValue(name);
        return value.isEmpty() ? null : value;
    }

    /**
     * @return the trimmed, unquoted value of {@code spec.openEhrConfig.archetype}, or {@code null}.
     */
    public static @Nullable String getArchetypeId(@NotNull YAMLFile file) {
        YAMLScalar archetype = getScalar(file, KEY_SPEC, KEY_OPENEHR_CONFIG, KEY_ARCHETYPE);
        if (archetype == null) {
            return null;
        }
        String value = scalarValue(archetype);
        return value.isEmpty() ? null : value;
    }

    /**
     * @return {@code true} when {@code element} is the scalar value of {@code metadata.name} in a FHIRConnect file.
     */
    public static boolean isMapperNameElement(@Nullable PsiElement element) {
        if (!(element instanceof YAMLScalar scalar)) {
            return false;
        }
        YAMLKeyValue keyValue = valueOwner(scalar);
        if (keyValue == null || !KEY_NAME.equals(keyValue.getKeyText())) {
            return false;
        }
        List<String> path = keyPath(keyValue);
        return path.size() == 2 && KEY_METADATA.equals(path.get(0)) && isFhirConnectFile(scalar.getContainingFile());
    }

    /**
     * Determines whether the scalar sits at one of the reference sites listed in {@link ReferenceKind}.
     * This is a structural check only; callers must verify {@link #isFhirConnectFile(PsiFile)} themselves.
     *
     * @return the reference kind, or {@code null} when the scalar is not a reference site.
     */
    public static @Nullable ReferenceKind getReferenceKind(@Nullable YAMLScalar scalar) {
        if (scalar == null) {
            return null;
        }
        PsiElement parent = scalar.getParent();
        if (parent instanceof YAMLKeyValue keyValue) {
            if (keyValue.getValue() != scalar) {
                return null;
            }
            return kindForKeyValue(keyValue);
        }
        if (parent instanceof YAMLSequenceItem item) {
            if (item.getValue() != scalar) {
                return null;
            }
            PsiElement sequence = item.getParent();
            if (sequence instanceof YAMLSequence && sequence.getParent() instanceof YAMLKeyValue keyValue) {
                return kindForSequenceItem(keyValue);
            }
        }
        return null;
    }

    private static @Nullable ReferenceKind kindForKeyValue(@NotNull YAMLKeyValue keyValue) {
        String key = keyValue.getKeyText();
        switch (key) {
            case KEY_SLOT_ARCHETYPE, KEY_SLOT_CONTEXT -> {
                List<String> path = keyPath(keyValue);
                if (path.size() < 2 || !KEY_MAPPINGS.equals(path.get(0))) {
                    return null;
                }
                return KEY_SLOT_ARCHETYPE.equals(key) ? ReferenceKind.SLOT_ARCHETYPE : ReferenceKind.SLOT_CONTEXT;
            }
            case KEY_EXTENDS -> {
                return isPath(keyValue, KEY_SPEC, KEY_EXTENDS) ? ReferenceKind.EXTENDS : null;
            }
            case KEY_START -> {
                return isPath(keyValue, KEY_CONTEXT, KEY_START) ? ReferenceKind.CONTEXT_START : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable ReferenceKind kindForSequenceItem(@NotNull YAMLKeyValue sequenceOwner) {
        String key = sequenceOwner.getKeyText();
        return switch (key) {
            case KEY_ARCHETYPES -> isPath(sequenceOwner, KEY_CONTEXT, KEY_ARCHETYPES) ? ReferenceKind.CONTEXT_ARCHETYPE : null;
            case KEY_EXTENSIONS -> isPath(sequenceOwner, KEY_CONTEXT, KEY_EXTENSIONS) ? ReferenceKind.CONTEXT_EXTENSION : null;
            case KEY_CONTEXTS -> isPath(sequenceOwner, KEY_CONTEXT, KEY_CONTEXTS) ? ReferenceKind.CONTEXT_CONTEXT : null;
            default -> null;
        };
    }

    private static boolean isPath(@NotNull YAMLKeyValue keyValue, String... expected) {
        return keyPath(keyValue).equals(List.of(expected));
    }

    /**
     * @return the key names from the document root down to (and including) {@code keyValue}; sequence items and
     * mappings in between are skipped.
     */
    public static @NotNull List<String> keyPath(@NotNull YAMLKeyValue keyValue) {
        List<String> path = new ArrayList<>();
        YAMLKeyValue current = keyValue;
        while (current != null) {
            path.add(current.getKeyText());
            current = PsiTreeUtil.getParentOfType(current, YAMLKeyValue.class);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * @return the key-value whose value is exactly {@code scalar}, or {@code null}.
     */
    public static @Nullable YAMLKeyValue valueOwner(@NotNull YAMLScalar scalar) {
        return scalar.getParent() instanceof YAMLKeyValue keyValue && keyValue.getValue() == scalar ? keyValue : null;
    }

    /**
     * @return the unquoted, trimmed text of the scalar.
     */
    public static @NotNull String scalarValue(@NotNull YAMLScalar scalar) {
        return scalar.getTextValue().trim();
    }

    private static @Nullable YAMLScalar getScalar(@NotNull YAMLFile file, String... keys) {
        YAMLMapping mapping = getTopLevelMapping(file);
        YAMLValue value = null;
        for (String key : keys) {
            if (mapping == null) {
                return null;
            }
            YAMLKeyValue keyValue = mapping.getKeyValueByKey(key);
            if (keyValue == null) {
                return null;
            }
            value = keyValue.getValue();
            mapping = value instanceof YAMLMapping nested ? nested : null;
        }
        return value instanceof YAMLScalar scalar ? scalar : null;
    }

    private static @Nullable YAMLMapping getTopLevelMapping(@NotNull YAMLFile file) {
        List<YAMLDocument> documents = file.getDocuments();
        if (documents.isEmpty()) {
            return null;
        }
        return documents.get(0).getTopLevelValue() instanceof YAMLMapping mapping ? mapping : null;
    }
}
