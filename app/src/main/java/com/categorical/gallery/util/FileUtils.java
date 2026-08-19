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
    private static final String DEFAULT_WORKSPACE_NAME = "暂未归入";
    private static final String PREFS_NAME = "pinned_photos_prefs";
    private static final String PINNED_KEY = "pinned_photo_paths";

    public static File getRootDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, HIDDEN_DIR_NAME);
    }

    public static File getWorkspacesDir() {
        return new File(getRootDir(), WORKSPACES_DIR_NAME);
    }

    public static File getWorkspaceDir(String workspaceName) {
        return new File(getWorkspacesDir(), workspaceName);
    }

    public static File getAlbumDir(String workspaceName, String albumName) {
        return new File(getWorkspaceDir(workspaceName), albumName);
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

        // Create default workspace if it doesn't exist
        File defaultWorkspace = getWorkspaceDir(DEFAULT_WORKSPACE_NAME);
        if (!defaultWorkspace.exists()) {
            defaultWorkspace.mkdirs();
        }
    }

    /**
     * Get all workspaces.
     */
    public static List<Workspace> getWorkspaces() {
        List<Workspace> workspaces = new ArrayList<>();
        File workspacesDir = getWorkspacesDir();
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
        File workspaceDir = getWorkspaceDir(name);
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
        File oldDir = getWorkspaceDir(oldName);
        File newDir = getWorkspaceDir(newName);
        if (!oldDir.exists() || newDir.exists()) {
            return false;
        }
        return oldDir.renameTo(newDir);
    }

    /**
     * Get all albums in a workspace.
     */
    public static List<Album> getAlbums(String workspaceName) {
        List<Album> albums = new ArrayList<>();
        File workspaceDir = getWorkspaceDir(workspaceName);
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
        File albumDir = getAlbumDir(workspaceName, albumName);
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
        List<Photo> photos = new ArrayList<>();
        File albumDir = getAlbumDir(workspaceName, albumName);
        File[] files = albumDir.listFiles(file -> file.isFile() && isImageFile(file.getName()));
        if (files != null) {
            Set<String> pinnedPaths = getPinnedPaths(context);
            for (File file : files) {
                Photo photo = new Photo(file);
                photo.setPinned(pinnedPaths.contains(file.getAbsolutePath()));
                photos.add(photo);
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
        File albumDir = getAlbumDir(workspaceName, albumName);
        File[] files = albumDir.listFiles(file -> file.isFile() && isImageFile(file.getName()));
        if (files == null || files.length == 0) {
            return null;
        }
        File earliest = files[0];
        for (File file : files) {
            if (file.lastModified() < earliest.lastModified()) {
                earliest = file;
            }
        }
        return new Photo(earliest);
    }

    /**
     * Get up to 4 album cover photos for a workspace icon (2x2 grid).
     * Uses the earliest modified photo from each of the first 4 albums.
     */
    public static List<String> getWorkspaceCovers(String workspaceName) {
        List<String> covers = new ArrayList<>();
        List<Album> albums = getAlbums(workspaceName);
        // Sort albums by name for consistent ordering
        albums.sort((a, b) -> a.getName().compareTo(b.getName()));
        int count = Math.min(albums.size(), 4);
        for (int i = 0; i < count; i++) {
            Photo photo = getEarliestPhoto(workspaceName, albums.get(i).getName());
            if (photo != null) {
                covers.add(photo.getAbsolutePath());
            } else {
                covers.add(null); // Empty album
            }
        }
        return covers;
    }

    /**
     * Check if a workspace has any albums.
     */
    public static boolean hasAlbums(String workspaceName) {
        File workspaceDir = getWorkspaceDir(workspaceName);
        File[] dirs = workspaceDir.listFiles(File::isDirectory);
        return dirs != null && dirs.length > 0;
    }

    /**
     * Check if an album has any photos.
     */
    public static boolean hasPhotos(String workspaceName, String albumName) {
        File albumDir = getAlbumDir(workspaceName, albumName);
        File[] files = albumDir.listFiles(file -> file.isFile() && isImageFile(file.getName()));
        return files != null && files.length > 0;
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
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
        File[] workspaceDirs = workspacesDir.listFiles(File::isDirectory);
        if (workspaceDirs != null) {
            for (File wsDir : workspaceDirs) {
                File[] albumDirs = wsDir.listFiles(File::isDirectory);
                if (albumDirs != null) {
                    for (File albumDir : albumDirs) {
                        File[] files = albumDir.listFiles(file -> file.isFile() && isImageFile(file.getName()));
                        if (files != null) {
                            for (File file : files) {
                                photos.add(file);
                            }
                        }
                    }
                }
            }
        }
        return photos;
    }
}
