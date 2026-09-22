package com.categorical.gallery.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SftpConfig {
    private static final String PREFS_NAME = "sftp_config_prefs";
    private static final String KEY_HOST = "sftp_host";
    private static final String KEY_PORT = "sftp_port";
    private static final String KEY_USER = "sftp_user";
    private static final String KEY_PASS = "sftp_pass";
    private static final String KEY_REMOTE_ROOT = "sftp_remote_root";
    private static final String KEY_LAST_SYNC_TIME = "sftp_last_sync_time";
    private static final String KEY_IS_ONLINE_STATUS = "sftp_is_online_status";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getHost(Context context) {
        return getPrefs(context).getString(KEY_HOST, "");
    }

    public static int getPort(Context context) {
        return getPrefs(context).getInt(KEY_PORT, 22);
    }

    public static String getUsername(Context context) {
        return getPrefs(context).getString(KEY_USER, "");
    }

    public static String getPassword(Context context) {
        return getPrefs(context).getString(KEY_PASS, "");
    }

    public static String getRemoteRootDir(Context context) {
        return getPrefs(context).getString(KEY_REMOTE_ROOT, "");
    }

    public static long getLastSyncTime(Context context) {
        return getPrefs(context).getLong(KEY_LAST_SYNC_TIME, 0);
    }

    public static boolean isOnlineStatus(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_ONLINE_STATUS, false);
    }

    public static void saveConfig(Context context, String host, int port, String user, String pass, String remoteRoot) {
        getPrefs(context).edit()
                .putString(KEY_HOST, host != null ? host.trim() : "")
                .putInt(KEY_PORT, port > 0 ? port : 22)
                .putString(KEY_USER, user != null ? user.trim() : "")
                .putString(KEY_PASS, pass != null ? pass : "")
                .putString(KEY_REMOTE_ROOT, remoteRoot != null ? remoteRoot.trim() : "")
                .apply();
    }

    public static void setRemoteRootDir(Context context, String remoteRoot) {
        getPrefs(context).edit()
                .putString(KEY_REMOTE_ROOT, remoteRoot != null ? remoteRoot.trim() : "")
                .apply();
    }

    public static void setOnlineStatus(Context context, boolean isOnline) {
        getPrefs(context).edit()
                .putBoolean(KEY_IS_ONLINE_STATUS, isOnline)
                .apply();
    }

    public static void setLastSyncTime(Context context, long timeMillis) {
        getPrefs(context).edit()
                .putLong(KEY_LAST_SYNC_TIME, timeMillis)
                .apply();
    }

    public static boolean isConfigured(Context context) {
        String host = getHost(context);
        String user = getUsername(context);
        String root = getRemoteRootDir(context);
        return !host.isEmpty() && !user.isEmpty() && !root.isEmpty();
    }

    public static String formatSyncTime(long timestamp) {
        if (timestamp <= 0) {
            return "尚未同步";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
