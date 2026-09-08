package com.categorical.gallery.model;

import java.io.File;
import java.util.List;

public class Workspace {
    private String name;
    private File path;
    private List<String> coverPaths;
    private boolean hasAlbums;

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

    public List<String> getCoverPaths() {
        return coverPaths;
    }

    public void setCoverPaths(List<String> coverPaths) {
        this.coverPaths = coverPaths;
    }

    public boolean isHasAlbums() {
        return hasAlbums;
    }

    public void setHasAlbums(boolean hasAlbums) {
        this.hasAlbums = hasAlbums;
    }
}
