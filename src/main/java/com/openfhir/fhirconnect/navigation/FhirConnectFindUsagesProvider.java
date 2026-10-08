package com.openfhir.fhirconnect.navigation;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.openfhir.fhirconnect.FhirConnectBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Makes Find Usages available for FHIRConnect mapper declarations. Other YAML elements are left to the YAML
 * plugin's own provider (the platform asks every provider registered for the language).
 */
public final class FhirConnectFindUsagesProvider implements FindUsagesProvider {

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return FhirConnectMapperTarget.fromDeclaration(psiElement) != null;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @Nls @NotNull String getType(@NotNull PsiElement element) {
        return canFindUsagesFor(element) ? FhirConnectBundle.message("find.usages.type") : "";
    }

    @Override
    public @Nls @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        FhirConnectMapperTarget target = FhirConnectMapperTarget.fromDeclaration(element);
        return target == null ? "" : target.getName();
    }

    @Override
    public @Nls @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
