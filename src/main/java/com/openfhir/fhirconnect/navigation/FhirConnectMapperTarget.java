package com.openfhir.fhirconnect.navigation;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTarget;
import com.openfhir.fhirconnect.psi.FhirConnectFileType;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * The declaration a FHIRConnect mapper reference resolves to: the {@code metadata.name} of a FHIRConnect file.
 * <p>
 * The target is identified by its <em>file</em>, not by the {@code metadata.name} scalar, because the YAML PSI
 * replaces the scalar element on every text change (including the rename performed by {@link #setName}); a
 * file-based identity stays valid across such edits. Wrapping it with {@link PomService#convertToPsi} yields a
 * {@link PomTargetPsiElement} that the platform treats as a renameable named element, which is what makes
 * Find Usages, Show Usages and Rename work on the name.
 */
public final class FhirConnectMapperTarget implements PsiTarget, PomRenameableTarget<FhirConnectMapperTarget> {

    private final YAMLFile file;

    public FhirConnectMapperTarget(@NotNull YAMLFile file) {
        this.file = file;
    }

    /**
     * @return the target wrapped in the given PSI element, or {@code null} when it is not a mapper target.
     */
    public static @Nullable FhirConnectMapperTarget from(@Nullable PsiElement element) {
        if (element instanceof PomTargetPsiElement pomElement
                && pomElement.getTarget() instanceof FhirConnectMapperTarget target) {
            return target;
        }
        return null;
    }

    /**
     * Interprets a PSI element as a mapper declaration: either an already wrapped target, the {@code metadata.name}
     * scalar, or the {@code name} key-value under {@code metadata}.
     *
     * @return the target, or {@code null} when the element is not a mapper declaration.
     */
    public static @Nullable FhirConnectMapperTarget fromDeclaration(@Nullable PsiElement element) {
        FhirConnectMapperTarget target = from(element);
        if (target != null) {
            return target;
        }
        PsiElement scalar = element instanceof YAMLKeyValue keyValue ? keyValue.getValue() : element;
        if (FhirConnectPsiUtil.isMapperNameElement(scalar)) {
            return new FhirConnectMapperTarget((YAMLFile) scalar.getContainingFile());
        }
        return null;
    }

    /**
     * @return the PSI element the platform uses for this target (navigation, find usages, rename).
     */
    public static @NotNull PsiElement toPsi(@NotNull YAMLFile file) {
        return PomService.convertToPsi(file.getProject(), new FhirConnectMapperTarget(file));
    }

    public @NotNull YAMLFile getFile() {
        return file;
    }

    public @Nullable FhirConnectFileType getFileType() {
        return FhirConnectPsiUtil.getFileType(file);
    }

    /**
     * @return the {@code metadata.name} scalar, or {@code null} if the file no longer declares one.
     */
    public @Nullable YAMLScalar getNameElement() {
        return file.isValid() ? FhirConnectPsiUtil.getMapperNameElement(file) : null;
    }

    @Override
    public @NotNull String getName() {
        YAMLScalar name = getNameElement();
        return name == null ? "" : FhirConnectPsiUtil.scalarValue(name);
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        YAMLScalar name = getNameElement();
        return name != null ? name : file;
    }

    @Override
    public boolean isValid() {
        return getNameElement() != null;
    }

    @Override
    public boolean isWritable() {
        return file.isWritable();
    }

    @Override
    public FhirConnectMapperTarget setName(@NotNull String newName) {
        YAMLScalar name = getNameElement();
        if (name != null) {
            ElementManipulators.handleContentChange(name, newName);
        }
        return this;
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiElement element = getNavigationElement();
        if (element instanceof NavigationItem navigatable) {
            navigatable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return getNavigationElement() instanceof NavigationItem navigatable && navigatable.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return getNavigationElement() instanceof NavigationItem navigatable && navigatable.canNavigateToSource();
    }

    public @NotNull Project getProject() {
        return file.getProject();
    }

    private @NotNull VirtualFile identity() {
        return file.getViewProvider().getVirtualFile();
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || o instanceof FhirConnectMapperTarget other && identity().equals(other.identity());
    }

    @Override
    public int hashCode() {
        return identity().hashCode();
    }

    @Override
    public String toString() {
        PsiFile containingFile = file;
        return "FhirConnectMapperTarget(" + getName() + " in " + containingFile.getName() + ")";
    }
}
