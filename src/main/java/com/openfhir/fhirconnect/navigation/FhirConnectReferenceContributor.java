package com.openfhir.fhirconnect.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import com.openfhir.fhirconnect.psi.ReferenceKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * Attaches a {@link FhirConnectMapperReference} to every scalar at a reference site of a FHIRConnect file.
 * This is what gives Ctrl+click, Ctrl+B, the Ctrl+hover underline and the multi-target chooser.
 */
public final class FhirConnectReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLScalar.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                   @NotNull ProcessingContext context) {
                YAMLScalar scalar = (YAMLScalar) element;
                ReferenceKind kind = FhirConnectPsiUtil.getReferenceKind(scalar);
                if (kind == null || !FhirConnectPsiUtil.isFhirConnectFile(scalar.getContainingFile())) {
                    return PsiReference.EMPTY_ARRAY;
                }
                TextRange range = ElementManipulators.getValueTextRange(scalar);
                return new PsiReference[]{new FhirConnectMapperReference(scalar, range, kind)};
            }
        });
    }
}
