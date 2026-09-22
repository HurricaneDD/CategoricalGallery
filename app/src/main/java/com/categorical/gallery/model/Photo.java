package com.categorical.gallery.model;

import com.categorical.gallery.util.FileUtils;

import java.io.File;

public class Photo {
    private File file;
    private String name;
    private long lastModified;
    private boolean pinned;
    private long duration = -1;

    public Photo(File file) {
        this.file = file;
        this.name = file.getName();
        this.lastModified = file.lastModified();
        this.pinned = false;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public String getAbsolutePath() {
        return file.getAbsolutePath();
    }

    public boolean isVideo() {
        return FileUtils.isVideoFile(file);
    }

    public long getDuration() {
        if (duration < 0) {
            if (isVideo()) {
                duration = FileUtils.getVideoDuration(file);
            } else {
                duration = 0;
            }
        }
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getFormattedDuration() {
        return FileUtils.formatDuration(getDuration());
    }
}
