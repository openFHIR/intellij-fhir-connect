package com.openfhir.fhirconnect.navigation;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.util.ProcessingContext;
import com.openfhir.fhirconnect.FhirConnectBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mapper names are not identifiers (they contain dots, e.g. {@code CLUSTER.case_identification.v0}); accept any
 * single-line, non-blank name when renaming a mapper.
 */
public final class FhirConnectRenameInputValidator implements RenameInputValidatorEx {

    @Override
    public @NotNull ElementPattern<? extends PsiElement> getPattern() {
        return PlatformPatterns.psiElement(PomTargetPsiElement.class)
                .with(new PatternCondition<>("fhirConnectMapper") {
                    @Override
                    public boolean accepts(@NotNull PomTargetPsiElement element, ProcessingContext context) {
                        return FhirConnectMapperTarget.from(element) != null;
                    }
                });
    }

    @Override
    public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
        return getErrorMessage(newName, element.getProject()) == null;
    }

    @Override
    public @Nullable String getErrorMessage(@NotNull String newName, @NotNull com.intellij.openapi.project.Project project) {
        if (newName.isBlank() || newName.contains("\n") || newName.contains("\r")) {
            return FhirConnectBundle.message("rename.invalid.name");
        }
        return null;
    }
}
