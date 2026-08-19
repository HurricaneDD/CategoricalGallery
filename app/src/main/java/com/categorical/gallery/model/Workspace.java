package com.categorical.gallery.model;

import java.io.File;

public class Workspace {
    private String name;
    private File path;

    public Workspace(String name, File path) {
        this.name = name;
        this.path = path;
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
}
