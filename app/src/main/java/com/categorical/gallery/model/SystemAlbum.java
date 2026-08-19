package com.categorical.gallery.model;

import java.util.ArrayList;
import java.util.List;

public class SystemAlbum {
    private long id;
    private String name;
    private String coverUri;
    private int photoCount;
    private List<String> photoUris;

    public SystemAlbum(long id, String name) {
        this.id = id;
        this.name = name;
        this.photoUris = new ArrayList<>();
        this.photoCount = 0;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCoverUri() {
        return coverUri;
    }

    public void setCoverUri(String coverUri) {
        this.coverUri = coverUri;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public List<String> getPhotoUris() {
        return photoUris;
    }

    public void addPhotoUri(String uri) {
        this.photoUris.add(uri);
    }
}
