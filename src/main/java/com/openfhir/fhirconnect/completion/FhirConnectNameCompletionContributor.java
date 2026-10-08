package com.openfhir.fhirconnect.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.openfhir.fhirconnect.FhirConnectBundle;
import com.openfhir.fhirconnect.index.FhirConnectNameIndex;
import com.openfhir.fhirconnect.psi.FhirConnectFileType;
import com.openfhir.fhirconnect.psi.FhirConnectPsiUtil;
import com.openfhir.fhirconnect.psi.ReferenceKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers the indexed mapper names at every reference site, filtered by the file type the site expects
 * (e.g. only models inside {@code slotArchetype}, only extensions inside {@code context.extensions}).
 */
public final class FhirConnectNameCompletionContributor extends CompletionContributor {

    public FhirConnectNameCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        addMapperNames(parameters, result);
                    }
                });
    }

    private static void addMapperNames(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        YAMLScalar scalar = PsiTreeUtil.getParentOfType(position, YAMLScalar.class, false);
        ReferenceKind kind = FhirConnectPsiUtil.getReferenceKind(scalar);
        if (kind == null || !FhirConnectPsiUtil.isFhirConnectFile(parameters.getOriginalFile())) {
            return;
        }
        Project project = position.getProject();
        if (DumbService.isDumb(project)) {
            return;
        }
        FhirConnectFileType expected = kind.getTargetType();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        FileBasedIndex index = FileBasedIndex.getInstance();
        ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);

        index.processAllKeys(FhirConnectNameIndex.NAME, name -> {
            List<String> locations = new ArrayList<>();
            index.processValues(FhirConnectNameIndex.NAME, name, null, (file, entry) -> {
                if (!entry.archetype() && entry.type() == expected) {
                    locations.add(presentablePath(projectFileIndex, file));
                }
                return true;
            }, scope);
            if (!locations.isEmpty()) {
                result.addElement(LookupElementBuilder.create(name)
                        .withTypeText(typeText(expected))
                        .withTailText("  " + String.join(", ", locations), true));
            }
            return true;
        }, project);
    }

    private static @NotNull String presentablePath(@NotNull ProjectFileIndex projectFileIndex, @NotNull VirtualFile file) {
        VirtualFile root = projectFileIndex.getContentRootForFile(file);
        String relative = root == null ? null : VfsUtilCore.getRelativePath(file, root);
        return relative != null ? relative : file.getPresentableUrl();
    }

    private static @NotNull String typeText(@NotNull FhirConnectFileType type) {
        return switch (type) {
            case MODEL -> FhirConnectBundle.message("completion.type.model");
            case EXTENSION -> FhirConnectBundle.message("completion.type.extension");
            case CONTEXT -> FhirConnectBundle.message("completion.type.context");
        };
    }
}
