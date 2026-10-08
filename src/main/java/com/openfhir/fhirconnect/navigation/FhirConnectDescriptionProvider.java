package com.openfhir.fhirconnect.navigation;

import com.intellij.ide.util.DeleteTypeDescriptionLocation;
import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.openfhir.fhirconnect.FhirConnectBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a {@link FhirConnectMapperTarget} in the Find Usages tool window, the Rename dialog and usage
 * highlighting ("FHIRConnect mapper 'CLUSTER.case_identification.v0'").
 */
public final class FhirConnectDescriptionProvider extends PomDescriptionProvider {

    @Override
    public @Nullable String getElementDescription(@NotNull PomTarget element,
                                                  @NotNull ElementDescriptionLocation location) {
        if (!(element instanceof FhirConnectMapperTarget target)) {
            return null;
        }
        if (location instanceof UsageViewTypeLocation || location instanceof DeleteTypeDescriptionLocation) {
            return FhirConnectBundle.message("find.usages.type");
        }
        return target.getName();
    }
}
