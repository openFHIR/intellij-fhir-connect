package com.openfhir.fhirconnect;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YamlEditorMouseListener implements EditorMouseListener {

    private final String NAME = "name";
    private final String METADATA = "metadata";

    private final Logger logger = Logger.getInstance(YamlEditorMouseListener.class);

    @Override
    public void mousePressed(EditorMouseEvent event) {
        if (event.getArea() != EditorMouseEventArea.EDITING_AREA) {
            return;
        }

        EditorEx editor = (EditorEx) event.getEditor();
        Project project = editor.getProject();
        if (project == null) {
            return;
        }

        YAMLFile psiFile = getYamlPsiFile(editor, project);
        if (psiFile == null) {
            return;
        }

        int clickedOffset = editor.visualPositionToOffset(editor.xyToVisualPosition(event.getMouseEvent().getPoint()));
        handleEditorClick(project, psiFile, clickedOffset, event);
    }

    private YAMLFile getYamlPsiFile(EditorEx editor, Project project) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        return (psiFile instanceof YAMLFile) ? (YAMLFile) psiFile : null;
    }

    private void handleEditorClick(Project project, YAMLFile psiFile, int offset, EditorMouseEvent event) {
        var clickedElement = psiFile.findElementAt(offset);
        if (clickedElement == null) {
            return;
        }

        String wordClicked = clickedElement.getText().replace("\"", "");
        YAMLKeyValue yamlKeyValue = PsiTreeUtil.getParentOfType(clickedElement, YAMLKeyValue.class);
        String currentFilePath = psiFile.getVirtualFile().getPath();

        if (yamlKeyValue != null && event.getMouseEvent().isControlDown()) {
            handleYamlKeyClick(project, yamlKeyValue, currentFilePath, wordClicked);
        }
    }

    private void handleYamlKeyClick(Project project, YAMLKeyValue yamlKeyValue,
                                    String currentFilePath, String wordClicked) {
        String keyText = yamlKeyValue.getKeyText();
        String valueText = Optional.ofNullable(yamlKeyValue.getValue()).map(Object::toString).orElse(null);
        if (valueText == null) {
            return;
        }

        logger.debug("Clicked on key: " + keyText + ", value: " + valueText + ", word: " + wordClicked);


        ReadAction.nonBlocking(() -> {
                    final List<RelevantFile> relevantFiles;
                    if (NAME.equals(keyText)
                            && ((YAMLKeyValue) yamlKeyValue.getParent().getParent()).getKey().getText().equals(METADATA)) {
                        // if metadata.name was clicked, then we'll find all context .yamls where this model mapping is referenced and all model mappers where it's slotArchetype or extends
                        relevantFiles = findRelevantYamlFiles(project, currentFilePath, wordClicked,
                                                              Set.of(CLICKED_ON.METADATA_NAME));
                    } else {
                        relevantFiles = findRelevantYamlFiles(project, currentFilePath, wordClicked,
                                                              Set.of(CLICKED_ON.SLOT_ARCHETYPE, CLICKED_ON.ARCHETYPES,
                                                                     CLICKED_ON.START));
                    }
                    return relevantFiles;
                })
                .finishOnUiThread(ModalityState.defaultModalityState(), relevantFiles -> {
                    if (relevantFiles.size() == 1) {
                        locateAndOpenAnchor(project, relevantFiles.get(0).getVirtualFile(), wordClicked,
                                            relevantFiles.get(0).getRegex());
                    } else if (relevantFiles.size() > 1) {
                        // More than one file found, show a popup list
                        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(
                                "Multiple Destinations Found, Select One",
                                relevantFiles.stream().map(rf -> rf.getVirtualFile()).collect(
                                        Collectors.toList())) {
                            @Override
                            public String getTextFor(VirtualFile file) {
                                return file.getPresentableName(); // Show the file path in the popup
                            }

                            @Override
                            public PopupStep<?> onChosen(VirtualFile selectedFile, boolean finalChoice) {
                                final String regex = relevantFiles.stream()
                                        .filter(rf -> rf.getVirtualFile().getName().equals(selectedFile.getName()))
                                        .map(rf -> rf.getRegex()).findAny().orElse(null);
                                locateAndOpenAnchor(project, selectedFile, wordClicked, regex);
                                return FINAL_CHOICE;
                            }
                        }).showInFocusCenter();
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());

    }

    /**
     * Recursively searches for YAML files in the project and finds the correct file based on content.
     *
     * @param project The IntelliJ project instance.
     * @return The matching VirtualFile if found, otherwise null.
     */
    public List<RelevantFile> findRelevantYamlFiles(final Project project,
                                                    final String currentFile,
                                                    String wordClicked,
                                                    final Set<CLICKED_ON> clickedOn) {
        VirtualFile projectBaseDir = project.getBaseDir(); // Get root directory of the project

        if (projectBaseDir == null) {
            return null;
        }

        List<VirtualFile> yamlFiles = new ArrayList<>();

        // Recursively search for YAML files
        VfsUtil.visitChildrenRecursively(projectBaseDir, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(VirtualFile file) {
                if (file.isDirectory()
                        && ("target".equals(file.getName())
                        || "build".equals(file.getName())
                        || "_build".equals(file.getName()))) {
                    return false; // skip target directory
                }

                if (!file.isDirectory()
                        && isYamlFile(file)) {
                    yamlFiles.add(file);
                }
                return true; // Continue recursion
            }
        });

        final List<RelevantFile> relevantFiles = new ArrayList<>();
        // Check each YAML file's content
        for (VirtualFile yamlFile : yamlFiles) {
            if (yamlFile.getPath().equals(currentFile)) {
                continue;
            }
            logger.warn("Reading file content: " + yamlFile.getPath());
            String content = readFileContent(yamlFile);
            if (content != null) {
                wordClicked = wordClicked.toLowerCase();
                content = content.toLowerCase()
                        .replace(String.format("\"%s\"", wordClicked), wordClicked)
                        .replace(" ", "");

                boolean isContextFile = content.contains("text:context");

                // regex for care position after clicking
                final String metadataNameRegex =
                        "metadata:\\s*\\n(?:\\s+.*\\n)*?\\s*name:\\s*[\"']?(" + wordClicked + ")[\"']?";
                final String slotRegex = "slotArchetype:\\s*[\"']?(" + wordClicked + ")[\"']?";
                final String contextArchetypesRegex =
                        "archetypes:\\s*\\n(?:\\s*-\\s*[\"']?(" + wordClicked + ")[\"']?\\s*\\n)*";
                final String contextExtensionsRegex =
                        "extensions:\\s*\\n(?:\\s*-\\s*[\"']?(" + wordClicked + ")[\"']?\\s*\\n)*";
                final String slotStartsRegex = "starts:\\s*[\"']?(" + wordClicked + ")[\"']?";
                final String extendsRegex =
                        "spec:\\s*\\n(?:\\s+.*\\n)*?\\s*extends:\\s*[\"']?(" + wordClicked + ")[\"']?";


                final String slotString = String.format("slotArchetype:%s", wordClicked);
                final String startsString = String.format("starts:%s", wordClicked);
                final String extendsString = String.format("extends:%s", wordClicked);
                final String archetypesString = String.format("-%s", wordClicked);
                final String extensionsString = String.format("-%s", wordClicked);


                if (clickedOn.contains(CLICKED_ON.SLOT_ARCHETYPE)
                        || clickedOn.contains(CLICKED_ON.ARCHETYPES)
                        || clickedOn.contains(CLICKED_ON.START)) {
                    if (checkRegex(content, metadataNameRegex)) {
                        relevantFiles.add(new RelevantFile(metadataNameRegex, yamlFile));
                    } else if (checkStringContains(content, startsString)) {
                        relevantFiles.add(new RelevantFile(slotStartsRegex, yamlFile));
                    }
                }

                if (clickedOn.contains(CLICKED_ON.METADATA_NAME)) {
                    if (checkStringContains(content, slotString)) {
                        relevantFiles.add(new RelevantFile(slotRegex, yamlFile));
                    } else if (checkStringContains(content, archetypesString)) {
                        relevantFiles.add(new RelevantFile(contextArchetypesRegex, yamlFile));
                    } else if (checkStringContains(content, extendsString)) {
                        relevantFiles.add(new RelevantFile(extendsRegex, yamlFile));
                    } else if (checkStringContains(content, startsString)) {
                        relevantFiles.add(new RelevantFile(slotStartsRegex, yamlFile));
                    } else if (checkStringContains(content, extensionsString)) {
                        relevantFiles.add(new RelevantFile(contextExtensionsRegex, yamlFile));
                    }
                }
            } else {
                System.out.println();
            }
        }

        return relevantFiles;
    }

    private static boolean checkRegex(final String content, final String regex) {
        var match = Pattern.compile(regex, Pattern.MULTILINE
        ).matcher(content);
        return match.find();
    }

    private static boolean checkStringContains(final String content, final String toContain) {
        return content.contains(toContain);
    }

    /**
     * Checks if the given file is a YAML file.
     *
     * @param file VirtualFile to check.
     * @return True if it's a YAML file, false otherwise.
     */
    private static boolean isYamlFile(VirtualFile file) {
        return file.getFileType() instanceof YAMLFileType
                || file.getName().endsWith(".yaml") || file.getName().endsWith(".yml");
    }

    private static boolean isFhirConnectContext(VirtualFile file) {
        return file.getFileType() instanceof YAMLFileType
                && (file.getName().endsWith("context.yaml") || file.getName().endsWith("context.yml"));
    }

    /**
     * Reads the content of a given VirtualFile.
     *
     * @param file The file to read.
     * @return The content as a String, or null if an error occurs.
     */
    private String readFileContent(VirtualFile file) {
        try {
            return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error trying to open file " + file.getName() + " with err ", e);
            return null;
        }
    }

    private boolean locateAndOpenAnchor(Project project, VirtualFile file, String anchorName, String regex) {
        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            content = content.toLowerCase();

            Position position = findExactWordPosition(content, regex, anchorName);
            if(position == null) {
                return false;
            }
            openFileAtPosition(project, file, position.line, position.character);

            logger.info("Anchor '" + anchorName + "' found in " + file.getPath() + " at line " + position.line
                                + ", character " + position.character);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Position findExactWordPosition(String content, String regex, String wordClicked) {
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            // Ensure group is found
            int startIndex = matcher.start(1);  // Group 1 captures `wordClicked`
            if (startIndex >= 0) {
                return getLineAndColumn(content, startIndex);
            }
        }

        return null;  // No match found
    }

    private Position getLineAndColumn(String content, int offset) {
        String[] lines = content.substring(0, offset).split("\\r?\\n");
        return new Position(lines.length - 1, lines[lines.length - 1].length());
    }

    private void openFileAtPosition(Project project, VirtualFile file, int line, int character) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, character);
        var editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        if (editor != null) {
            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, character));
        }
    }

    private enum CLICKED_ON {
        SLOT_ARCHETYPE, METADATA_NAME, ARCHETYPES, START
    }


    private static class Position {

        int line, character;

        Position(int line, int character) {
            this.line = line;
            this.character = character;
        }
    }
}
