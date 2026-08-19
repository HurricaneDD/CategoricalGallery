package com.categorical.gallery;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.PhotoAdapter;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.MediaStoreUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows all photos inside an album as a 3-column grid.
 */
public class AlbumActivity extends AppCompatActivity implements PhotoAdapter.OnPhotoClickListener {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";
    public static final String EXTRA_ALBUM_NAME = "albumName";
    public static final String EXTRA_POSITION = "position";

    private static final int REQUEST_PICK_IMAGES = 2001;

    private String workspaceName;
    private String albumName;
    private RecyclerView recyclerView;
    private PhotoAdapter adapter;
    private List<Photo> photos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        albumName = getIntent().getStringExtra(EXTRA_ALBUM_NAME);
        if (workspaceName == null || albumName == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(albumName);

        ImageButton btnExport = findViewById(R.id.btn_export);
        btnExport.setOnClickListener(v -> {
            Intent intent = new Intent(AlbumActivity.this, ExportActivity.class);
            intent.putExtra(EXTRA_WORKSPACE_NAME, workspaceName);
            intent.putExtra(EXTRA_ALBUM_NAME, albumName);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.rv_photos);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setHasFixedSize(false);
        adapter = new PhotoAdapter(this, this);
        recyclerView.setAdapter(adapter);

        loadPhotos();
    }

    private void loadPhotos() {
        photos = FileUtils.getPhotos(this, workspaceName, albumName);
        adapter.setPhotos(photos);
    }

    @Override
    public void onPhotoClick(int position) {
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(EXTRA_WORKSPACE_NAME, workspaceName);
        intent.putExtra(EXTRA_ALBUM_NAME, albumName);
        intent.putExtra(EXTRA_POSITION, position);
        startActivity(intent);
    }

    @Override
    public void onAddClick() {
        openImagePicker();
    }

    @Override
    public void onPhotoLongClick(int position) {
        showDeleteDialog(position);
    }

    // ==================== Image Picking ====================

    private void openImagePicker() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 50);
        } else {
            intent = new Intent(Intent.ACTION_PICK);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGES && resultCode == RESULT_OK) {
            List<Uri> uris = new ArrayList<>();
            if (data != null && data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            } else if (data != null && data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                copyUrisToAlbum(uris);
            }
        }
    }

    private void copyUrisToAlbum(List<Uri> uris) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.importing_please_wait));
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            File albumDir = FileUtils.getAlbumDir(workspaceName, albumName);
            if (!albumDir.exists() && !albumDir.mkdirs()) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            int success = 0;
            for (Uri uri : uris) {
                String name = getDisplayName(uri);
                if (name == null || name.isEmpty()) {
                    name = "IMG_" + System.currentTimeMillis() + ".jpg";
                }
                File dest = ensureUnique(new File(albumDir, name));
                if (MediaStoreUtils.copyPhotoToAppDir(this, uri, dest)) {
                    success++;
                }
            }
            final int finalSuccess = success;
            runOnUiThread(() -> {
                pd.dismiss();
                loadPhotos();
                if (finalSuccess > 0) {
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                // ignore and fall back
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private File ensureUnique(File file) {
        if (!file.exists()) {
            return file;
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base;
        String ext;
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        } else {
            base = name;
            ext = "";
        }
        int i = 1;
        File f;
        do {
            f = new File(file.getParent(), base + "_" + i + ext);
            i++;
        } while (f.exists());
        return f;
    }

    // ==================== Delete ====================

    private void showDeleteDialog(int position) {
        if (position < 0 || position >= photos.size()) {
            return;
        }
        Photo photo = photos.get(position);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirm, null);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnConfirm.setOnClickListener(v -> {
            FileUtils.deleteFile(photo.getFile());
            dialog.dismiss();
            loadPhotos();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPhotos();
    }
}
