package com.openfhir.fhirconnect.navigation;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

/**
 * Tells the platform that the {@code metadata.name} value of a FHIRConnect file declares a
 * {@link FhirConnectMapperTarget}. With the caret on the name this enables Find Usages (Alt+F7), Show Usages,
 * Rename (Shift+F6) and usage highlighting.
 */
public final class FhirConnectDeclarationSearcher extends PomDeclarationSearcher {

    @Override
    public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement,
                                   @NotNull Consumer<? super PomTarget> consumer) {
        if (FhirConnectPsiUtil.isMapperNameElement(element)) {
            consumer.consume(new FhirConnectMapperTarget((YAMLFile) element.getContainingFile()));
        }
    }
}
