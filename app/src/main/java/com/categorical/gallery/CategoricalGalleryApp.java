package com.categorical.gallery;

import android.app.Application;

import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.ThemeHelper;

public class CategoricalGalleryApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FileUtils.init(this);
        ThemeHelper.applyDarkMode(ThemeHelper.getDarkMode(this));
    }
}
