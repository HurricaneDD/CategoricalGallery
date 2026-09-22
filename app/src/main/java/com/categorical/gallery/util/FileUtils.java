package com.categorical.gallery.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.categorical.gallery.model.Album;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.model.Workspace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final String HIDDEN_DIR_NAME = ".CategoricalGallery";
    private static final String WORKSPACES_DIR_NAME = "workspaces";
    private static final String ONLINE_WORKSPACES_DIR_NAME = "online_workspaces";
    private static final String DEFAULT_WORKSPACE_NAME = "暂未归入";
    private static final String PREFS_NAME = "pinned_photos_prefs";
    private static final String PINNED_KEY = "pinned_photo_paths";
    private static File sRootDir = null;
    private static Context sContext = null;

    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }
    }

    public static void resetRootDir() {
        sRootDir = null;
    }

    public static File getRootDir() {
        if (sRootDir != null && sRootDir.exists()) {
            return sRootDir;
        }

        // Candidates to check: prioritize directories that already have workspaces
        List<File> candidates = new ArrayList<>();

        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null) {
                candidates.add(new File(downloadsDir, HIDDEN_DIR_NAME));
                candidates.add(new File(downloadsDir, "CategoricalGallery"));
            }
        } catch (Exception ignored) {}

        try {
            File extStorage = Environment.getExternalStorageDirectory();
            if (extStorage != null) {
                candidates.add(new File(extStorage, "Download/" + HIDDEN_DIR_NAME));
                candidates.add(new File(extStorage, "Downloads/" + HIDDEN_DIR_NAME));
                candidates.add(new File(extStorage, HIDDEN_DIR_NAME));
                candidates.add(new File(extStorage, "Download/CategoricalGallery"));
                candidates.add(new File(extStorage, "Downloads/CategoricalGallery"));
                candidates.add(new File(extStorage, "CategoricalGallery"));
            }
        } catch (Exception ignored) {}

        // Check if any candidate exists and already has legacy workspaces
        for (File candidate : candidates) {
            try {
                if (candidate.exists()) {
                    File wsDir = new File(candidate, WORKSPACES_DIR_NAME);
                    if (wsDir.exists() && wsDir.isDirectory()) {
                        File[] list = wsDir.listFiles();
                        if (list != null && list.length > 0) {
                            sRootDir = candidate;
                            return sRootDir;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Check if any candidate exists and is readable
        for (File candidate : candidates) {
            try {
                if (candidate.exists() && candidate.canRead()) {
                    sRootDir = candidate;
                    return sRootDir;
                }
            } catch (Exception ignored) {}
        }

        // Try standard downloads directory first
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null) {
                File rootDir = new File(downloadsDir, HIDDEN_DIR_NAME);
                if (rootDir.exists() || rootDir.mkdirs()) {
                    sRootDir = rootDir;
                    return sRootDir;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not access Downloads directory: " + e.getMessage());
        }

        // Fallback to app-specific external files dir if scoped storage prevents public download directory access
        if (sContext != null) {
            try {
                File extDir = sContext.getExternalFilesDir(null);
                if (extDir != null) {
                    File rootDir = new File(extDir, HIDDEN_DIR_NAME);
                    if (rootDir.exists() || rootDir.mkdirs()) {
                        sRootDir = rootDir;
                        return sRootDir;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not access getExternalFilesDir: " + e.getMessage());
            }

            // Fallback to internal files dir
            try {
                File intDir = sContext.getFilesDir();
                if (intDir != null) {
                    File rootDir = new File(intDir, HIDDEN_DIR_NAME);
                    if (rootDir.exists() || rootDir.mkdirs()) {
                        sRootDir = rootDir;
                        return sRootDir;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not access getFilesDir: " + e.getMessage());
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, HIDDEN_DIR_NAME);
    }

    public static File getWorkspacesDir() {
        return new File(getRootDir(), WORKSPACES_DIR_NAME);
    }

    public static File getOnlineWorkspacesDir() {
        return new File(getRootDir(), ONLINE_WORKSPACES_DIR_NAME);
    }

    public static File getWorkspacesBaseDir(boolean isOnline) {
        return isOnline ? getOnlineWorkspacesDir() : getWorkspacesDir();
    }

    public static File getWorkspaceDir(String workspaceName) {
        return getWorkspaceDir(workspaceName, false);
    }

    public static File getWorkspaceDir(String workspaceName, boolean isOnline) {
        return new File(getWorkspacesBaseDir(isOnline), workspaceName);
    }

    public static File getAlbumDir(String workspaceName, String albumName) {
        return getAlbumDir(workspaceName, albumName, false);
    }

    public static File getAlbumDir(String workspaceName, String albumName, boolean isOnline) {
        return new File(getWorkspaceDir(workspaceName, isOnline), albumName);
    }

    /**
     * Initialize the app's directory structure.
     * Creates root dir, workspaces dir, and default workspace.
     */
    public static void initDirectories() {
        File rootDir = getRootDir();
        if (!rootDir.exists()) {
            rootDir.mkdirs();
            // Create .nomedia to prevent system gallery from scanning
            File nomedia = new File(rootDir, ".nomedia");
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Failed to create .nomedia", e);
            }
        }

        File workspacesDir = getWorkspacesDir();
        if (!workspacesDir.exists()) {
            workspacesDir.mkdirs();
        }

        File onlineWorkspacesDir = getOnlineWorkspacesDir();
        if (!onlineWorkspacesDir.exists()) {
            onlineWorkspacesDir.mkdirs();
        }

        // Create default workspace if it doesn't exist
        File defaultWorkspace = getWorkspaceDir(DEFAULT_WORKSPACE_NAME, false);
        if (!defaultWorkspace.exists()) {
            defaultWorkspace.mkdirs();
        }

        File defaultOnlineWorkspace = getWorkspaceDir(DEFAULT_WORKSPACE_NAME, true);
        if (!defaultOnlineWorkspace.exists()) {
            defaultOnlineWorkspace.mkdirs();
        }
    }

    /**
     * Get all workspaces.
     */
    public static List<Workspace> getWorkspaces() {
        return getWorkspaces(false);
    }

    public static List<Workspace> getWorkspaces(boolean isOnline) {
        List<Workspace> workspaces = new ArrayList<>();
        File workspacesDir = getWorkspacesBaseDir(isOnline);
        if (!workspacesDir.exists()) {
            workspacesDir.mkdirs();
        }
        File[] dirs = workspacesDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                workspaces.add(new Workspace(dir.getName(), dir));
            }
        }
        return workspaces;
    }

    /**
     * Create a new workspace.
     * @return true if created successfully, false if already exists or creation failed
     */
    public static boolean createWorkspace(String name) {
        return createWorkspace(name, false);
    }

    public static boolean createWorkspace(String name, boolean isOnline) {
        File workspaceDir = getWorkspaceDir(name, isOnline);
        if (workspaceDir.exists()) {
            return false;
        }
        return workspaceDir.mkdirs();
    }

    /**
     * Rename a workspace.
     * @return true if renamed successfully
     */
    public static boolean renameWorkspace(String oldName, String newName) {
        return renameWorkspace(oldName, newName, false);
    }

    public static boolean renameWorkspace(String oldName, String newName, boolean isOnline) {
        File oldDir = getWorkspaceDir(oldName, isOnline);
        File newDir = getWorkspaceDir(newName, isOnline);
        if (!oldDir.exists() || newDir.exists()) {
            return false;
        }
        return oldDir.renameTo(newDir);
    }

    /**
     * Get all albums in a workspace.
     */
    public static List<Album> getAlbums(String workspaceName) {
        return getAlbums(workspaceName, false);
    }

    public static List<Album> getAlbums(String workspaceName, boolean isOnline) {
        List<Album> albums = new ArrayList<>();
        File workspaceDir = getWorkspaceDir(workspaceName, isOnline);
        File[] dirs = workspaceDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                albums.add(new Album(dir.getName(), dir, workspaceName));
            }
        }
        return albums;
    }

    /**
     * Create a new album in a workspace.
     */
    public static boolean createAlbum(String workspaceName, String albumName) {
        return createAlbum(workspaceName, albumName, false);
    }

    public static boolean createAlbum(String workspaceName, String albumName, boolean isOnline) {
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (albumDir.exists()) {
            return false;
        }
        return albumDir.mkdirs();
    }

    /**
     * Get all photos in an album, sorted by modification time (newest first).
     * Pinned photos are placed at the beginning.
     */
    public static List<Photo> getPhotos(Context context, String workspaceName, String albumName) {
        return getPhotos(context, workspaceName, albumName, false);
    }

    public static List<Photo> getPhotos(Context context, String workspaceName, String albumName, boolean isOnline) {
        List<Photo> photos = new ArrayList<>();
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (albumDir == null || !albumDir.exists()) {
            return photos;
        }
        File[] files = albumDir.listFiles();
        if (files != null) {
            Set<String> pinnedPaths = getPinnedPaths(context);
            for (File file : files) {
                if (isMediaFile(file)) {
                    Photo photo = new Photo(file);
                    photo.setPinned(pinnedPaths.contains(file.getAbsolutePath()));
                    photos.add(photo);
                }
            }
        }
        // Sort: pinned first, then by modification time (newest first)
        photos.sort((a, b) -> {
            if (a.isPinned() && !b.isPinned()) return -1;
            if (!a.isPinned() && b.isPinned()) return 1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });
        return photos;
    }

    /**
     * Get the earliest modified photo in an album (for album cover).
     */
    public static Photo getEarliestPhoto(String workspaceName, String albumName) {
        return getEarliestPhoto(workspaceName, albumName, false);
    }

    public static Photo getEarliestPhoto(String workspaceName, String albumName, boolean isOnline) {
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (albumDir == null || !albumDir.exists()) {
            return null;
        }
        File[] files = albumDir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        File earliest = null;
        for (File file : files) {
            if (isMediaFile(file)) {
                if (earliest == null || file.lastModified() < earliest.lastModified()) {
                    earliest = file;
                }
            }
        }
        return earliest != null ? new Photo(earliest) : null;
    }

    /**
     * Get the most recently modified photo in an album (for album cover).
     */
    public static Photo getMostRecentPhoto(String workspaceName, String albumName) {
        return getMostRecentPhoto(workspaceName, albumName, false);
    }

    public static Photo getMostRecentPhoto(String workspaceName, String albumName, boolean isOnline) {
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (albumDir == null || !albumDir.exists()) {
            return null;
        }
        File[] files = albumDir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        File latest = null;
        for (File file : files) {
            if (isMediaFile(file)) {
                if (latest == null || file.lastModified() > latest.lastModified()) {
                    latest = file;
                }
            }
        }
        return latest != null ? new Photo(latest) : null;
    }

    /**
     * Get up to 4 album cover photos for a workspace icon (2x2 grid).
     * Uses the most recently modified photo from each of up to 4 albums,
     * sorted by album modification time (newest first).
     */
    public static List<String> getWorkspaceCovers(String workspaceName) {
        return getWorkspaceCovers(workspaceName, false);
    }

    public static List<String> getWorkspaceCovers(String workspaceName, boolean isOnline) {
        List<String> covers = new ArrayList<>();
        List<Album> albums = getAlbums(workspaceName, isOnline);
        albums.sort((a, b) -> Long.compare(b.getPath().lastModified(), a.getPath().lastModified()));
        int count = Math.min(albums.size(), 4);
        for (int i = 0; i < count; i++) {
            Photo photo = getMostRecentPhoto(workspaceName, albums.get(i).getName(), isOnline);
            if (photo != null) {
                covers.add(photo.getAbsolutePath());
            } else {
                covers.add(null);
            }
        }
        return covers;
    }

    public static boolean isDefaultWorkspace(String name) {
        return DEFAULT_WORKSPACE_NAME.equals(name);
    }

    public static boolean deleteWorkspace(String name) {
        return deleteWorkspace(name, false);
    }

    public static boolean deleteWorkspace(String name, boolean isOnline) {
        if (isDefaultWorkspace(name)) return false;
        File dir = getWorkspaceDir(name, isOnline);
        if (!dir.exists()) return false;
        File defaultDir = getWorkspaceDir(DEFAULT_WORKSPACE_NAME, isOnline);
        if (!defaultDir.exists()) defaultDir.mkdirs();
        File[] albums = dir.listFiles(File::isDirectory);
        if (albums != null) {
            for (File album : albums) {
                File dest = new File(defaultDir, album.getName());
                if (dest.exists()) {
                    int suffix = 1;
                    while (new File(defaultDir, album.getName() + "_" + suffix).exists()) {
                        suffix++;
                    }
                    dest = new File(defaultDir, album.getName() + "_" + suffix);
                }
                album.renameTo(dest);
            }
        }
        return deleteRecursive(dir);
    }

    public static boolean deleteAlbum(String workspaceName, String albumName) {
        return deleteAlbum(workspaceName, albumName, false);
    }

    public static boolean deleteAlbum(String workspaceName, String albumName, boolean isOnline) {
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (!albumDir.exists()) return false;
        return deleteRecursive(albumDir);
    }

    public static boolean renameAlbum(String workspaceName, String oldName, String newName) {
        return renameAlbum(workspaceName, oldName, newName, false);
    }

    public static boolean renameAlbum(String workspaceName, String oldName, String newName, boolean isOnline) {
        File oldDir = getAlbumDir(workspaceName, oldName, isOnline);
        File newDir = getAlbumDir(workspaceName, newName, isOnline);
        if (!oldDir.exists() || newDir.exists()) return false;
        return oldDir.renameTo(newDir);
    }

    public static boolean renamePhoto(Context context, File oldFile, File newFile) {
        if (!oldFile.exists() || newFile.exists()) return false;
        boolean success = oldFile.renameTo(newFile);
        if (success) {
            String oldPath = oldFile.getAbsolutePath();
            if (isPinned(context, oldPath)) {
                unpinPhoto(context, oldPath);
                pinPhoto(context, newFile.getAbsolutePath());
            }
        }
        return success;
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return file.delete();
    }

    /**
     * Check if a workspace has any albums.
     */
    public static boolean hasAlbums(String workspaceName) {
        return hasAlbums(workspaceName, false);
    }

    public static boolean hasAlbums(String workspaceName, boolean isOnline) {
        File workspaceDir = getWorkspaceDir(workspaceName, isOnline);
        File[] dirs = workspaceDir.listFiles(File::isDirectory);
        return dirs != null && dirs.length > 0;
    }

    /**
     * Check if an album has any photos.
     */
    public static boolean hasPhotos(String workspaceName, String albumName) {
        return hasPhotos(workspaceName, albumName, false);
    }

    public static boolean hasPhotos(String workspaceName, String albumName, boolean isOnline) {
        File albumDir = getAlbumDir(workspaceName, albumName, isOnline);
        if (albumDir == null || !albumDir.exists()) return false;
        File[] files = albumDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isMediaFile(file)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isImageFile(File file) {
        if (file == null) return false;
        String name = file.getName().trim();
        if (name.startsWith(".")) return false;
        if (file.isDirectory()) return false;
        if (isImageFile(name)) return true;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outWidth > 0 && options.outHeight > 0) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean isImageFile(String name) {
        if (name == null || name.isEmpty() || name.startsWith(".")) return false;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")
                || lower.endsWith(".heic") || lower.endsWith(".heif") || lower.endsWith(".dng")
                || lower.endsWith(".jfif") || lower.endsWith(".jpe") || lower.endsWith(".avif")
                || lower.endsWith(".tif") || lower.endsWith(".tiff") || lower.endsWith(".svg")
                || lower.endsWith(".cr2") || lower.endsWith(".nef") || lower.endsWith(".arw");
    }

    public static boolean isVideoFile(File file) {
        if (file == null) return false;
        String name = file.getName().trim();
        if (name.startsWith(".")) return false;
        if (file.isDirectory()) return false;
        return isVideoFile(name);
    }

    public static boolean isVideoFile(String name) {
        if (name == null || name.isEmpty() || name.startsWith(".")) return false;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov")
                || lower.endsWith(".avi") || lower.endsWith(".3gp") || lower.endsWith(".webm")
                || lower.endsWith(".flv") || lower.endsWith(".wmv") || lower.endsWith(".m4v")
                || lower.endsWith(".ts") || lower.endsWith(".mpg") || lower.endsWith(".mpeg");
    }

    public static boolean isMediaFile(File file) {
        return isImageFile(file) || isVideoFile(file);
    }

    public static boolean isMediaFile(String name) {
        return isImageFile(name) || isVideoFile(name);
    }

    public static long getVideoDuration(File file) {
        if (file == null || !file.exists()) return 0;
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }
    }

    private static boolean isNonMediaFile(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".pdf") || lower.endsWith(".doc")
                || lower.endsWith(".docx") || lower.endsWith(".zip") || lower.endsWith(".rar")
                || lower.endsWith(".7z") || lower.endsWith(".apk") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".html") || lower.endsWith(".db");
    }

    // ==================== Pin Management ====================

    public static Set<String> getPinnedPaths(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(PINNED_KEY, new HashSet<>());
    }

    public static void pinPhoto(Context context, String photoPath) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(getPinnedPaths(context));
        pinned.add(photoPath);
        prefs.edit().putStringSet(PINNED_KEY, pinned).apply();
    }

    public static void unpinPhoto(Context context, String photoPath) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(getPinnedPaths(context));
        pinned.remove(photoPath);
        prefs.edit().putStringSet(PINNED_KEY, pinned).apply();
    }

    public static boolean isPinned(Context context, String photoPath) {
        return getPinnedPaths(context).contains(photoPath);
    }

    // ==================== File Operations ====================

    public static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    public static boolean deleteFile(File file) {
        return file != null && file.exists() && file.delete();
    }

    /**
     * Copy a file from an input stream to a destination.
     */
    public static void copyStreamToFile(InputStream in, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    // ==================== Utility Methods ====================

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Get image dimensions (width x height).
     */
    public static int[] getImageDimensions(File imageFile) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        return new int[]{options.outWidth, options.outHeight};
    }

    /**
     * Get all photo files across all albums in all workspaces.
     * Used for the photo selection in multi-image mode.
     */
    public static List<File> getAllPhotos() {
        List<File> photos = new ArrayList<>();
        File workspacesDir = getWorkspacesDir();
        if (workspacesDir == null || !workspacesDir.exists()) return photos;
        File[] workspaceDirs = workspacesDir.listFiles(File::isDirectory);
        if (workspaceDirs != null) {
            for (File wsDir : workspaceDirs) {
                File[] albumDirs = wsDir.listFiles(File::isDirectory);
                if (albumDirs != null) {
                    for (File albumDir : albumDirs) {
                        File[] files = albumDir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (isMediaFile(file)) {
                                    photos.add(file);
                                }
                            }
                        }
                    }
                }
            }
        }
        return photos;
    }
}
