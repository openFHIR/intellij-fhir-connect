package com.openfhir.fhirconnect;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;


public class YamlGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Set<String> NAVIGATEABLE_YAML_KEYS = Set.of("archetypes", "start", "extensions", "slotArchetype",
                                                                     "extends", "metadata.name");

    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement element, int offset, Editor editor) {
        if (element == null) {
            return null;
        }

        PsiFile psiFile = element.getContainingFile();
        if (!(psiFile instanceof YAMLFile)) {
            return null;
        }

        final YAMLKeyValue yamlKeyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
        if (!isNavigateable(yamlKeyValue)) {
            return null;
        }
        int startOffset = yamlKeyValue.getTextRange().getStartOffset();
        int endOffset = yamlKeyValue.getTextRange().getEndOffset();

        editor.getMarkupModel().addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SELECTION - 1,
                null,
                HighlighterTargetArea.EXACT_RANGE
        );

        return new PsiElement[]{yamlKeyValue};
    }

    private boolean isNavigateable(final YAMLKeyValue yamlKeyValue) {
        if (yamlKeyValue == null) {
            return false;
        }
        final String text = Objects.requireNonNull(yamlKeyValue.getKey()).getText();
        if (NAVIGATEABLE_YAML_KEYS.contains(text)) {
            return true;
        }
        final List<String> allWithDots = NAVIGATEABLE_YAML_KEYS.stream()
                .filter(navigatable -> navigatable.contains("."))
                .toList();
        for (final String withDot : allWithDots) {
            // if last one matches clicked element, then check parents
            final String[] splitByDot = withDot.split("\\.");
            if (text.equals(splitByDot[splitByDot.length - 1])) {
                // do parents check
                return isNavigateable(yamlKeyValue, withDot);
            }
        }
        return false;
    }

    private boolean isNavigateable(final YAMLKeyValue yamlKeyValue, final String path) {
        if (path.contains(".")) {
            final PsiElement parent = yamlKeyValue.getParent().getParent();
            final String substring = path.substring(0, path.lastIndexOf("."));
            if (parent instanceof YAMLKeyValue) {
                return isNavigateable((YAMLKeyValue) parent,
                                      substring);

            } else if (parent instanceof YAMLSequenceItem) {
                return isNavigateable(((YAMLKeyValueImpl) yamlKeyValue.getParent().getParent().getParent().getParent()),
                                      substring);
            }
        }
        return path.equals(Objects.requireNonNull(yamlKeyValue.getKey()).getText());
    }

    @Override
    public String getActionText(DataContext context) {
        return "Navigate to YAML Definition";
    }
}
