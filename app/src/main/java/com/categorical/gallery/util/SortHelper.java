package com.categorical.gallery.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class SortHelper {

    public static final int SORT_CUSTOM = 0;
    public static final int SORT_MODIFIED_DESC = 1;
    public static final int SORT_NAME_ASC = 2;
    public static final int SORT_MODIFIED_ASC = 3;
    public static final int SORT_NAME_DESC = 4;

    private static final String PREFS_NAME = "sort_prefs";
    private static final String KEY_WS_SORT_MODE = "workspace_sort_mode";
    private static final String KEY_WS_CUSTOM_ORDER = "workspace_custom_order";
    private static final String KEY_ALBUM_SORT_MODE_PREFIX = "album_sort_mode_";
    private static final String KEY_ALBUM_CUSTOM_ORDER_PREFIX = "album_custom_order_";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getWorkspaceSortMode(Context context) {
        return getPrefs(context).getInt(KEY_WS_SORT_MODE, SORT_CUSTOM);
    }

    public static void setWorkspaceSortMode(Context context, int mode) {
        getPrefs(context).edit().putInt(KEY_WS_SORT_MODE, mode).apply();
    }

    public static List<String> getWorkspaceCustomOrder(Context context) {
        return stringToList(getPrefs(context).getString(KEY_WS_CUSTOM_ORDER, null));
    }

    public static void saveWorkspaceCustomOrder(Context context, List<String> order) {
        getPrefs(context).edit()
                .putString(KEY_WS_CUSTOM_ORDER, listToString(order))
                .apply();
    }

    public static int getAlbumSortMode(Context context, String workspaceName) {
        return getPrefs(context).getInt(KEY_ALBUM_SORT_MODE_PREFIX + workspaceName, SORT_CUSTOM);
    }

    public static void setAlbumSortMode(Context context, String workspaceName, int mode) {
        getPrefs(context).edit()
                .putInt(KEY_ALBUM_SORT_MODE_PREFIX + workspaceName, mode)
                .apply();
    }

    public static List<String> getAlbumCustomOrder(Context context, String workspaceName) {
        return stringToList(getPrefs(context).getString(KEY_ALBUM_CUSTOM_ORDER_PREFIX + workspaceName, null));
    }

    public static void saveAlbumCustomOrder(Context context, String workspaceName, List<String> order) {
        getPrefs(context).edit()
                .putString(KEY_ALBUM_CUSTOM_ORDER_PREFIX + workspaceName, listToString(order))
                .apply();
    }

    private static String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|||");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static List<String> stringToList(String s) {
        List<String> result = new ArrayList<>();
        if (s == null || s.isEmpty()) return result;
        String[] parts = s.split("\\|\\|\\|");
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }
}
