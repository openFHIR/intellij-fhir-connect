package com.openfhir.fhirconnect.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

final class FhirConnectIndexUtil {

    private FhirConnectIndexUtil() {
    }

    /**
     * Cheap pre-check followed by the PSI check: returns the YAML file when it is a FHIRConnect file, otherwise
     * {@code null} without ever building PSI for unrelated YAML.
     */
    static @Nullable YAMLFile asFhirConnectFile(@NotNull FileContent inputData) {
        if (!StringUtil.contains(inputData.getContentAsText(), FhirConnectPsiUtil.GRAMMAR_PREFIX)) {
            return null;
        }
        PsiFile psiFile = inputData.getPsiFile();
        if (!(psiFile instanceof YAMLFile yamlFile) || !FhirConnectPsiUtil.isFhirConnectFile(yamlFile)) {
            return null;
        }
        return yamlFile;
    }
}
