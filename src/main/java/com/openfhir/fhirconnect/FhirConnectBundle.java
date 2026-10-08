package com.openfhir.fhirconnect;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * Message bundle for all user-facing texts of the plugin.
 */
public final class FhirConnectBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.FhirConnectBundle";

    private static final FhirConnectBundle INSTANCE = new FhirConnectBundle();

    private FhirConnectBundle() {
        super(FhirConnectBundle.class, BUNDLE);
    }

    public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                               Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                                Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
