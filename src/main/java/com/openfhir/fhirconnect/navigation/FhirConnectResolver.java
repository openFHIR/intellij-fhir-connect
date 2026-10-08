package com.openfhir.fhirconnect.navigation;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.openfhir.fhirconnect.index.FhirConnectNameIndex;
import com.openfhir.fhirconnect.psi.FhirConnectFileType;
import com.openfhir.fhirconnect.psi.ReferenceKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a mapper name to the FHIRConnect files declaring it, using {@link FhirConnectNameIndex}.
 */
public final class FhirConnectResolver {

    private FhirConnectResolver() {
    }

    /**
     * Resolves {@code name} as referenced from {@code from} at a reference site of the given {@code kind}.
     * <ol>
     * <li>exact, case-sensitive lookup in the name index; if that yields nothing, a case-insensitive scan of all
     * indexed names (keeps everything that resolved in plugin 1.0.x working);</li>
     * <li>candidates whose {@code type} matches the kind's expected target type are preferred; when none match,
     * all candidates are kept (the engine is just as lenient);</li>
     * <li>when several files remain, the ones sharing the longest common ancestor directory with {@code from}
     * win; ties are all returned and the platform shows a chooser.</li>
     * </ol>
     *
     * @return the declaration elements (see {@link FhirConnectMapperTarget#toPsi}); empty while indexing.
     */
    public static @NotNull List<PsiElement> resolve(@NotNull Project project,
                                                    @NotNull String name,
                                                    @Nullable PsiFile from,
                                                    @Nullable ReferenceKind kind) {
        if (name.isBlank() || DumbService.isDumb(project)) {
            return Collections.emptyList();
        }
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        FileBasedIndex index = FileBasedIndex.getInstance();

        Map<VirtualFile, FhirConnectNameIndex.Entry> candidates = collect(index, name, scope);
        if (candidates.isEmpty()) {
            for (String key : index.getAllKeys(FhirConnectNameIndex.NAME, project)) {
                if (key.equalsIgnoreCase(name)) {
                    candidates.putAll(collect(index, key, scope));
                }
            }
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<VirtualFile> files = new ArrayList<>(candidates.keySet());
        if (kind != null) {
            files = preferType(files, candidates, kind.getTargetType());
        }
        if (files.size() > 1 && from != null) {
            files = preferNearest(files, from.getViewProvider().getVirtualFile());
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiElement> result = new ArrayList<>(files.size());
        for (VirtualFile file : files) {
            if (psiManager.findFile(file) instanceof YAMLFile yamlFile) {
                result.add(FhirConnectMapperTarget.toPsi(yamlFile));
            }
        }
        return result;
    }

    private static @NotNull Map<VirtualFile, FhirConnectNameIndex.Entry> collect(@NotNull FileBasedIndex index,
                                                                                @NotNull String key,
                                                                                @NotNull GlobalSearchScope scope) {
        Map<VirtualFile, FhirConnectNameIndex.Entry> result = new LinkedHashMap<>();
        index.processValues(FhirConnectNameIndex.NAME, key, null, (file, entry) -> {
            result.put(file, entry);
            return true;
        }, scope);
        return result;
    }

    private static @NotNull List<VirtualFile> preferType(@NotNull List<VirtualFile> files,
                                                         @NotNull Map<VirtualFile, FhirConnectNameIndex.Entry> entries,
                                                         @NotNull FhirConnectFileType expected) {
        List<VirtualFile> matching = new ArrayList<>();
        for (VirtualFile file : files) {
            if (entries.get(file).type() == expected) {
                matching.add(file);
            }
        }
        return matching.isEmpty() ? files : matching;
    }

    private static @NotNull List<VirtualFile> preferNearest(@NotNull List<VirtualFile> files, @NotNull VirtualFile from) {
        VirtualFile fromDir = from.getParent();
        if (fromDir == null) {
            return files;
        }
        List<VirtualFile> nearest = new ArrayList<>();
        int best = -1;
        for (VirtualFile file : files) {
            int depth = commonAncestorDepth(fromDir, file.getParent());
            if (depth > best) {
                best = depth;
                nearest.clear();
            }
            if (depth == best) {
                nearest.add(file);
            }
        }
        return nearest;
    }

    /**
     * @return the number of path segments the two directories share from the root.
     */
    static int commonAncestorDepth(@Nullable VirtualFile a, @Nullable VirtualFile b) {
        if (a == null || b == null) {
            return 0;
        }
        String[] pathA = a.getPath().split("/");
        String[] pathB = b.getPath().split("/");
        int depth = 0;
        while (depth < pathA.length && depth < pathB.length && pathA[depth].equals(pathB[depth])) {
            depth++;
        }
        return depth;
    }
}
