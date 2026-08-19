package com.categorical.gallery.model;

import java.io.File;

public class Album {
    private String name;
    private File path;
    private String workspaceName;

    public Album(String name, File path, String workspaceName) {
        this.name = name;
        this.path = path;
        this.workspaceName = workspaceName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public File getPath() {
        return path;
    }

    public void setPath(File path) {
        this.path = path;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }
}
