package com.openfhir.fhirconnect.psi;

import org.jetbrains.annotations.NotNull;

/**
 * The places in a FHIRConnect file whose scalar value names another mapper.
 *
 * <table>
 * <caption>Reference sites</caption>
 * <tr><th>Kind</th><th>Key path</th><th>Resolves to</th></tr>
 * <tr><td>{@link #SLOT_ARCHETYPE}</td><td>{@code mappings[*].slotArchetype} (also nested under
 * {@code followedBy.mappings} / {@code reference.mappings})</td><td>model {@code metadata.name}</td></tr>
 * <tr><td>{@link #SLOT_CONTEXT}</td><td>{@code mappings[*].slotContext}</td><td>context {@code metadata.name}</td></tr>
 * <tr><td>{@link #EXTENDS}</td><td>{@code spec.extends}</td><td>model {@code metadata.name}</td></tr>
 * <tr><td>{@link #CONTEXT_START}</td><td>{@code context.start}</td><td>model {@code metadata.name}</td></tr>
 * <tr><td>{@link #CONTEXT_ARCHETYPE}</td><td>{@code context.archetypes[*]}</td><td>model {@code metadata.name}</td></tr>
 * <tr><td>{@link #CONTEXT_EXTENSION}</td><td>{@code context.extensions[*]}</td><td>extension {@code metadata.name}</td></tr>
 * <tr><td>{@link #CONTEXT_CONTEXT}</td><td>{@code context.contexts[*]}</td><td>context {@code metadata.name}</td></tr>
 * </table>
 */
public enum ReferenceKind {
    SLOT_ARCHETYPE(FhirConnectFileType.MODEL),
    SLOT_CONTEXT(FhirConnectFileType.CONTEXT),
    EXTENDS(FhirConnectFileType.MODEL),
    CONTEXT_START(FhirConnectFileType.MODEL),
    CONTEXT_ARCHETYPE(FhirConnectFileType.MODEL),
    CONTEXT_EXTENSION(FhirConnectFileType.EXTENSION),
    CONTEXT_CONTEXT(FhirConnectFileType.CONTEXT);

    private final FhirConnectFileType targetType;

    ReferenceKind(FhirConnectFileType targetType) {
        this.targetType = targetType;
    }

    /**
     * The {@code type} of the file whose {@code metadata.name} this reference is expected to point to.
     */
    public @NotNull FhirConnectFileType getTargetType() {
        return targetType;
    }
}
