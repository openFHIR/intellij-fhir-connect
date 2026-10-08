package com.openfhir.fhirconnect.psi;

import org.jetbrains.annotations.Nullable;

/**
 * The value of the top-level {@code type} key of a FHIRConnect file.
 */
public enum FhirConnectFileType {
    MODEL("model"),
    EXTENSION("extension"),
    CONTEXT("context");

    private final String key;

    FhirConnectFileType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static @Nullable FhirConnectFileType fromKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        for (FhirConnectFileType type : values()) {
            if (type.key.equalsIgnoreCase(key.trim())) {
                return type;
            }
        }
        return null;
    }
}
