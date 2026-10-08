package com.openfhir.fhirconnect.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import com.openfhir.fhirconnect.psi.ReferenceKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.List;

/**
 * A reference from a scalar at one of the {@link ReferenceKind reference sites} to the mapper(s) with that name.
 * <p>
 * The reference is soft: an unresolved name is not an error until a dedicated inspection exists.
 */
public final class FhirConnectMapperReference extends PsiPolyVariantReferenceBase<YAMLScalar> {

    private static final ResolveCache.PolyVariantResolver<FhirConnectMapperReference> RESOLVER =
            (reference, incompleteCode) -> {
                List<PsiElement> targets = FhirConnectResolver.resolve(reference.getElement().getProject(),
                        reference.getName(), reference.getElement().getContainingFile(), reference.getKind());
                return PsiElementResolveResult.createResults(targets);
            };

    private final ReferenceKind kind;

    public FhirConnectMapperReference(@NotNull YAMLScalar element, @NotNull TextRange rangeInElement,
                                      @NotNull ReferenceKind kind) {
        super(element, rangeInElement, true);
        this.kind = kind;
    }

    public @NotNull ReferenceKind getKind() {
        return kind;
    }

    /**
     * @return the referenced mapper name (the reference text, trimmed).
     */
    public @NotNull String getName() {
        return getValue().trim();
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return ResolveCache.getInstance(getElement().getProject())
                .resolveWithCaching(this, RESOLVER, false, incompleteCode);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Completion is provided by FhirConnectNameCompletionContributor.
        return EMPTY_ARRAY;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        FhirConnectMapperTarget target = FhirConnectMapperTarget.fromDeclaration(element);
        if (target == null || !target.getName().equalsIgnoreCase(getName())) {
            return false;
        }
        for (ResolveResult result : multiResolve(false)) {
            if (target.equals(FhirConnectMapperTarget.from(result.getElement()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), newElementName);
    }

    @Override
    public @Nullable PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        FhirConnectMapperTarget target = FhirConnectMapperTarget.fromDeclaration(element);
        if (target == null) {
            throw new IncorrectOperationException("Cannot bind to " + element);
        }
        return handleElementRename(target.getName());
    }
}
