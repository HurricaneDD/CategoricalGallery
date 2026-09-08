package com.categorical.gallery.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.categorical.gallery.R;

public class ThemeHelper {

    public static final int COLOR_BLUE = 0;
    public static final int COLOR_PURPLE = 1;
    public static final int COLOR_GREEN = 2;
    public static final int COLOR_RED = 3;
    public static final int COLOR_BLACKWHITE = 4;

    public static final int MODE_FOLLOW_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    public static int getThemeColor(Context context) {
        return getPrefs(context).getInt("theme_color", COLOR_BLUE);
    }

    public static void setThemeColor(Context context, int color) {
        getPrefs(context).edit().putInt("theme_color", color).apply();
    }

    public static int getDarkMode(Context context) {
        return getPrefs(context).getInt("dark_mode", MODE_FOLLOW_SYSTEM);
    }

    public static void setDarkMode(Context context, int mode) {
        getPrefs(context).edit().putInt("dark_mode", mode).apply();
        applyDarkMode(mode);
    }

    public static int getPrimaryColor(Context context) {
        switch (getThemeColor(context)) {
            case COLOR_PURPLE: return ContextCompat.getColor(context, R.color.themePurplePrimary);
            case COLOR_GREEN: return ContextCompat.getColor(context, R.color.themeGreenPrimary);
            case COLOR_RED: return ContextCompat.getColor(context, R.color.themeRedPrimary);
            case COLOR_BLACKWHITE: return ContextCompat.getColor(context, R.color.themeBWPrimary);
            default: return ContextCompat.getColor(context, R.color.themeBluePrimary);
        }
    }

    public static int getPrimaryDarkColor(Context context) {
        switch (getThemeColor(context)) {
            case COLOR_PURPLE: return ContextCompat.getColor(context, R.color.themePurpleDark);
            case COLOR_GREEN: return ContextCompat.getColor(context, R.color.themeGreenDark);
            case COLOR_RED: return ContextCompat.getColor(context, R.color.themeRedDark);
            case COLOR_BLACKWHITE: return ContextCompat.getColor(context, R.color.themeBWDark);
            default: return ContextCompat.getColor(context, R.color.themeBlueDark);
        }
    }

    public static void applyDarkMode(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void applyTheme(Activity activity) {
        applyDarkMode(getDarkMode(activity));
    }

    public static void applyToolbar(Activity activity, Toolbar toolbar) {
        int primary = getPrimaryColor(activity);
        int primaryDark = getPrimaryDarkColor(activity);
        toolbar.setBackgroundColor(primary);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(primaryDark);
        }
    }
}
