package com.categorical.gallery.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.categorical.gallery.model.SystemAlbum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaStoreUtils {
    private static final String TAG = "MediaStoreUtils";

    /**
     * Query all system photo albums from MediaStore.
     * Albums are identified by MediaStore.Images.Media.BUCKET_DISPLAY_NAME.
     */
    public static List<SystemAlbum> getSystemAlbums(Context context) {
        List<SystemAlbum> albums = new ArrayList<>();
        Map<String, SystemAlbum> albumMap = new HashMap<>();

        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DISPLAY_NAME
            };
        } else {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
            };
        }

        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                int bucketIdIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
                int bucketNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);

                do {
                    long bucketId = cursor.getLong(bucketIdIndex);
                    String bucketName = cursor.getString(bucketNameIndex);
                    if (bucketName == null) bucketName = "未命名相册";

                    String key = String.valueOf(bucketId);
                    SystemAlbum album = albumMap.get(key);
                    if (album == null) {
                        album = new SystemAlbum(bucketId, bucketName);
                        albumMap.put(key, album);
                        albums.add(album);
                    }

                    long photoId = cursor.getLong(idIndex);
                    Uri photoUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId);
                    album.addPhotoUri(photoUri.toString());

                    if (album.getCoverUri() == null) {
                        album.setCoverUri(photoUri.toString());
                    }
                    album.setPhotoCount(album.getPhotoCount() + 1);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query system albums", e);
        }

        return albums;
    }

    /**
     * Copy a photo from a MediaStore Uri to the app's working directory.
     */
    public static boolean copyPhotoToAppDir(Context context, Uri photoUri, File destFile) {
        try (InputStream in = context.getContentResolver().openInputStream(photoUri)) {
            if (in == null) return false;
            FileUtils.copyStreamToFile(in, destFile);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy photo", e);
            return false;
        }
    }

    /**
     * Copy a photo from a File to the app's working directory.
     */
    public static boolean copyFileToAppDir(File srcFile, File destFile) {
        try {
            FileUtils.copyFile(srcFile, destFile);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file", e);
            return false;
        }
    }

    /**
     * Delete a system photo from MediaStore.
     */
    public static boolean deleteSystemPhoto(Context context, Uri photoUri) {
        try {
            int deleted = context.getContentResolver().delete(photoUri, null, null);
            return deleted > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete system photo", e);
            return false;
        }
    }

    /**
     * Delete a system photo by file path (for older Android versions).
     */
    public static boolean deleteSystemPhotoByPath(Context context, String path) {
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Export a photo file to the system gallery (DCIM/Camera or Pictures).
     */
    public static boolean exportPhotoToSystemGallery(Context context, File photoFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore insertion for Android 10+
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, photoFile.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/分类相册导出");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;

            try (OutputStream out = resolver.openOutputStream(uri);
                 FileInputStream in = new FileInputStream(photoFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to export photo", e);
                resolver.delete(uri, null, null);
                return false;
            }
        } else {
            // For older Android, copy to DCIM directory
            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File exportDir = new File(dcimDir, "分类相册导出");
            if (!exportDir.exists()) exportDir.mkdirs();
            File destFile = new File(exportDir, photoFile.getName());
            try {
                FileUtils.copyFile(photoFile, destFile);
                // Scan the file to make it visible in system gallery
                android.media.MediaScannerConnection.scanFile(context,
                        new String[]{destFile.getAbsolutePath()}, null, null);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to export photo (legacy)", e);
                return false;
            }
        }
    }
}
