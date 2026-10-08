package com.openfhir.fhirconnect.navigation;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.openfhir.fhirconnect.index.FhirConnectReferenceIndex;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds the references to a {@link FhirConnectMapperTarget}: looks the name up in
 * {@link FhirConnectReferenceIndex}, then walks only the candidate files for scalars at reference sites.
 */
public final class FhirConnectReferenceSearcher
        extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public FhirConnectReferenceSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                             @NotNull Processor<? super PsiReference> consumer) {
        PsiElement element = parameters.getElementToSearch();
        FhirConnectMapperTarget target = FhirConnectMapperTarget.fromDeclaration(element);
        if (target == null) {
            return;
        }
        String name = target.getName();
        Project project = target.getProject();
        if (name.isEmpty() || DumbService.isDumb(project)) {
            return;
        }

        SearchScope searchScope = parameters.getEffectiveSearchScope();
        GlobalSearchScope fileScope = GlobalSearchScope.projectScope(project);
        if (searchScope instanceof GlobalSearchScope globalScope) {
            fileScope = fileScope.intersectWith(globalScope);
        }

        FileBasedIndex index = FileBasedIndex.getInstance();
        Set<VirtualFile> files = new LinkedHashSet<>(index.getContainingFiles(FhirConnectReferenceIndex.NAME, name, fileScope));
        // References may use a different case; the resolver accepts them, so find them as usages too.
        for (String key : index.getAllKeys(FhirConnectReferenceIndex.NAME, project)) {
            if (!key.equals(name) && key.equalsIgnoreCase(name)) {
                files.addAll(index.getContainingFiles(FhirConnectReferenceIndex.NAME, key, fileScope));
            }
        }
        if (searchScope instanceof LocalSearchScope localScope) {
            // e.g. usage highlighting in the current editor: only visit the files of the local scope.
            Set<VirtualFile> localFiles = new HashSet<>();
            for (PsiElement scopeElement : localScope.getScope()) {
                PsiFile scopeFile = scopeElement.getContainingFile();
                if (scopeFile != null) {
                    localFiles.add(scopeFile.getViewProvider().getVirtualFile());
                }
            }
            files.retainAll(localFiles);
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            if (!(psiManager.findFile(file) instanceof YAMLFile yamlFile)) {
                continue;
            }
            for (YAMLScalar scalar : SyntaxTraverser.psiTraverser(yamlFile).filter(YAMLScalar.class)) {
                if (FhirConnectPsiUtil.getReferenceKind(scalar) == null
                        || !FhirConnectPsiUtil.scalarValue(scalar).equalsIgnoreCase(name)
                        || !PsiSearchScopeUtil.isInScope(searchScope, scalar)) {
                    continue;
                }
                for (PsiReference reference : scalar.getReferences()) {
                    if (reference instanceof FhirConnectMapperReference && reference.isReferenceTo(element)
                            && !consumer.process(reference)) {
                        return;
                    }
                }
            }
        }
    }
}
