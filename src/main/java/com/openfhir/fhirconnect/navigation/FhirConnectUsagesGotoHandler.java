package com.openfhir.fhirconnect.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.openfhir.fhirconnect.FhirConnectBundle;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ctrl+click / Ctrl+B on a {@code metadata.name} value jumps to the places that reference the mapper (one target
 * navigates directly, several open the chooser). This keeps the behaviour of plugin 1.0.x.
 */
public final class FhirConnectUsagesGotoHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset,
                                                             Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        YAMLScalar scalar = PsiTreeUtil.getParentOfType(sourceElement, YAMLScalar.class, false);
        if (scalar == null || !FhirConnectPsiUtil.isMapperNameElement(scalar)) {
            return null;
        }
        Project project = scalar.getProject();
        if (DumbService.isDumb(project)) {
            return null;
        }
        PsiElement target = FhirConnectMapperTarget.toPsi((YAMLFile) scalar.getContainingFile());
        Set<PsiElement> usages = new LinkedHashSet<>();
        for (PsiReference reference : ReferencesSearch.search(target, GlobalSearchScope.projectScope(project)).findAll()) {
            usages.add(reference.getElement());
        }
        if (usages.isEmpty()) {
            return null;
        }
        return usages.stream()
                .sorted(Comparator.comparing((PsiElement element) -> element.getContainingFile().getVirtualFile().getPath())
                        .thenComparingInt(PsiElement::getTextOffset))
                .toArray(PsiElement[]::new);
    }

    @Override
    public @Nullable String getActionText(@NotNull DataContext context) {
        return FhirConnectBundle.message("goto.usages.action.text");
    }
}
