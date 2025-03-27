package com.openfhir.fhirconnect;

import com.intellij.openapi.vfs.VirtualFile;

public class RelevantFile {

    private String regex;
    private VirtualFile virtualFile;


    public RelevantFile(final String regex, final VirtualFile virtualFile) {
        this.regex = regex;
        this.virtualFile = virtualFile;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(final String regex) {
        this.regex = regex;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public void setVirtualFile(final VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

}
