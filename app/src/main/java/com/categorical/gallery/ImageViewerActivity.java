package com.categorical.gallery;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen image viewer with swipe/pinch-to-zoom support.
 * <p>
 * Receives the workspace name, album name and the index of the photo to show
 * first, then lets the user swipe through every photo in that album.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";
    public static final String EXTRA_ALBUM_NAME = "albumName";
    public static final String EXTRA_POSITION = "position";

    private static final int REQUEST_PHOTO_SELECT = 3001;

    private String workspaceName;
    private String albumName;
    private int currentPosition;

    private final List<Photo> photos = new ArrayList<>();
    private ImagePagerAdapter adapter;

    private ViewPager2 viewPager;
    private TextView tvPhotoName;
    private ImageButton btnPin;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        albumName = getIntent().getStringExtra(EXTRA_ALBUM_NAME);
        currentPosition = getIntent().getIntExtra(EXTRA_POSITION, 0);
        if (workspaceName == null || albumName == null) {
            finish();
            return;
        }

        bindViews();
        loadPhotos();
    }

    private void bindViews() {
        viewPager = findViewById(R.id.view_pager);
        tvPhotoName = findViewById(R.id.tv_photo_name);
        btnPin = findViewById(R.id.btn_pin);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnDelete = findViewById(R.id.btn_delete);
        ImageButton btnShare = findViewById(R.id.btn_share);
        ImageButton btnMultiView = findViewById(R.id.btn_multi_view);
        ImageButton btnInfo = findViewById(R.id.btn_info);

        btnBack.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> showDeleteDialog());
        btnShare.setOnClickListener(v -> shareCurrentPhoto());
        btnMultiView.setOnClickListener(v -> openPhotoSelect());
        btnInfo.setOnClickListener(v -> showDetailsDialog());
        btnPin.setOnClickListener(v -> togglePin());
        tvPhotoName.setOnClickListener(v -> showRenameDialog());
    }

    private void loadPhotos() {
        photos.clear();
        photos.addAll(FileUtils.getPhotos(this, workspaceName, albumName));
        if (photos.isEmpty()) {
            finish();
            return;
        }
        if (currentPosition < 0 || currentPosition >= photos.size()) {
            currentPosition = 0;
        }

        adapter = new ImagePagerAdapter(photos);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateTopBar();
                updatePinIcon();
            }
        };
        viewPager.registerOnPageChangeCallback(pageChangeCallback);

        updateTopBar();
        updatePinIcon();
    }

    @Override
    protected void onDestroy() {
        if (viewPager != null && pageChangeCallback != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        super.onDestroy();
    }

    // ==================== Helpers ====================

    private Photo getCurrentPhoto() {
        if (currentPosition < 0 || currentPosition >= photos.size()) {
            return null;
        }
        return photos.get(currentPosition);
    }

    private void updateTopBar() {
        Photo photo = getCurrentPhoto();
        tvPhotoName.setText(photo != null ? photo.getName() : "");
    }

    private void updatePinIcon() {
        Photo photo = getCurrentPhoto();
        if (photo == null) {
            return;
        }
        boolean pinned = FileUtils.isPinned(this, photo.getAbsolutePath());
        photo.setPinned(pinned);
        btnPin.setImageResource(pinned ? R.drawable.ic_pin_remove : R.drawable.ic_pin);
    }

    // ==================== Action: Delete ====================

    private void showDeleteDialog() {
        if (getCurrentPhoto() == null) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirm, null);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnConfirm.setOnClickListener(v -> {
            deleteCurrentPhoto();
            dialog.dismiss();
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void deleteCurrentPhoto() {
        int pos = currentPosition;
        if (pos < 0 || pos >= photos.size()) {
            return;
        }
        Photo photo = photos.get(pos);

        // If pinned, also remove the pin record.
        if (FileUtils.isPinned(this, photo.getAbsolutePath())) {
            FileUtils.unpinPhoto(this, photo.getAbsolutePath());
        }

        if (!FileUtils.deleteFile(photo.getFile())) {
            Toast.makeText(this, R.string.delete_failed_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        photos.remove(pos);
        if (photos.isEmpty()) {
            finish();
            return;
        }

        adapter.notifyItemRemoved(pos);
        adapter.notifyItemRangeChanged(pos, photos.size() - pos);

        if (pos >= photos.size()) {
            currentPosition = photos.size() - 1;
        } else {
            currentPosition = pos;
        }
        viewPager.setCurrentItem(currentPosition, false);
        updateTopBar();
        updatePinIcon();
    }

    // ==================== Action: Share ====================

    private void shareCurrentPhoto() {
        Photo photo = getCurrentPhoto();
        if (photo == null) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photo.getFile());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_failed_toast, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Action: Multi-view ====================

    private void openPhotoSelect() {
        if (getCurrentPhoto() == null) {
            return;
        }
        Intent intent = new Intent(this, PhotoSelectActivity.class);
        // Open the selector directly inside the current album's photo grid.
        intent.putExtra(PhotoSelectActivity.EXTRA_DEFAULT_WORKSPACE, workspaceName);
        intent.putExtra(PhotoSelectActivity.EXTRA_DEFAULT_ALBUM, albumName);
        startActivityForResult(intent, REQUEST_PHOTO_SELECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PHOTO_SELECT && resultCode == RESULT_OK && data != null) {
            String secondPhotoPath = data.getStringExtra(
                    PhotoSelectActivity.EXTRA_SELECTED_PHOTO_PATH);
            Photo current = getCurrentPhoto();
            if (secondPhotoPath != null && !secondPhotoPath.isEmpty() && current != null) {
                Intent intent = new Intent(this, MultiImageActivity.class);
                intent.putExtra(MultiImageActivity.EXTRA_PHOTO1_PATH, current.getAbsolutePath());
                intent.putExtra(MultiImageActivity.EXTRA_PHOTO2_PATH, secondPhotoPath);
                intent.putExtra(MultiImageActivity.EXTRA_WORKSPACE_NAME, workspaceName);
                intent.putExtra(MultiImageActivity.EXTRA_ALBUM_NAME, albumName);
                intent.putExtra(MultiImageActivity.EXTRA_POSITION, currentPosition);
                startActivity(intent);
            }
        }
    }

    // ==================== Action: Details ====================

    private void showDetailsDialog() {
        Photo photo = getCurrentPhoto();
        if (photo == null) {
            return;
        }
        File file = photo.getFile();

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_details, null);
        TextView tvFilename = view.findViewById(R.id.tv_detail_filename);
        TextView tvModified = view.findViewById(R.id.tv_detail_modified_time);
        TextView tvCreated = view.findViewById(R.id.tv_detail_created_time);
        TextView tvDirectory = view.findViewById(R.id.tv_detail_directory);
        TextView tvSize = view.findViewById(R.id.tv_detail_size);
        TextView tvDimensions = view.findViewById(R.id.tv_detail_dimensions);
        Button btnConfirm = view.findViewById(R.id.btn_detail_confirm);

        int[] dims = FileUtils.getImageDimensions(file);
        File parent = file.getParentFile();
        String dirPath = parent != null ? parent.getAbsolutePath() : "";

        tvFilename.setText(getString(R.string.detail_filename, file.getName()));
        tvModified.setText(getString(R.string.detail_modified_time,
                FileUtils.formatDate(file.lastModified())));
        tvCreated.setText(getString(R.string.detail_created_time,
                FileUtils.formatDate(getCreatedTime(file))));
        tvDirectory.setText(getString(R.string.detail_directory, dirPath));
        tvSize.setText(getString(R.string.detail_size, FileUtils.formatFileSize(file.length())));
        tvDimensions.setText(getString(R.string.detail_dimensions, dims[0], dims[1]));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        btnConfirm.setOnClickListener(v -> dialog.dismiss());
        Button btnRename = view.findViewById(R.id.btn_detail_rename);
        btnRename.setOnClickListener(v -> {
            dialog.dismiss();
            showRenameDialog();
        });
        dialog.show();
    }

    // ==================== Action: Rename ====================

    private void showRenameDialog() {
        Photo photo = getCurrentPhoto();
        if (photo == null) {
            return;
        }
        String oldName = photo.getName();
        int dotIndex = oldName.lastIndexOf('.');
        String nameWithoutExt = dotIndex > 0 ? oldName.substring(0, dotIndex) : oldName;
        String extension = dotIndex > 0 ? oldName.substring(dotIndex) : "";

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rename_workspace, null);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvOriginalName = view.findViewById(R.id.tvOriginalName);
        EditText etName = view.findViewById(R.id.etWorkspaceName);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        tvTitle.setText(R.string.dialog_rename_photo_title);
        tvOriginalName.setText(getString(R.string.dialog_original_name, oldName));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        etName.setText(nameWithoutExt);
        etName.setSelection(nameWithoutExt.length());

        btnConfirm.setEnabled(false);
        btnConfirm.setTextColor(ContextCompat.getColor(this, R.color.colorButtonDisabled));

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                boolean valid = !text.isEmpty()
                        && !text.contains(" ")
                        && !text.equals(nameWithoutExt);
                btnConfirm.setEnabled(valid);
                btnConfirm.setTextColor(ContextCompat.getColor(ImageViewerActivity.this,
                        valid ? R.color.colorPrimary : R.color.colorButtonDisabled));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim() + extension;
            if (newName.equals(oldName)) {
                return;
            }
            File oldFile = photo.getFile();
            File newFile = new File(oldFile.getParentFile(), newName);
            boolean success = FileUtils.renamePhoto(this, oldFile, newFile);
            if (success) {
                photo.setFile(newFile);
                photo.setName(newName);
                adapter.notifyItemChanged(currentPosition);
                updateTopBar();
                Toast.makeText(this, R.string.rename_photo_success, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, R.string.rename_photo_failed, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    /**
     * Try to obtain the original capture time from EXIF metadata; fall back to
     * the file's last-modified time when EXIF data is unavailable.
     */
    private long getCreatedTime(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dateTime == null) {
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            }
            if (dateTime != null) {
                // EXIF stores the value as "yyyy:MM:dd HH:mm:ss".
                SimpleDateFormat exifFormat =
                        new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
                Date date = exifFormat.parse(dateTime);
                if (date != null) {
                    return date.getTime();
                }
            }
        } catch (Exception e) {
            // Ignore and fall back to last-modified.
        }
        return file.lastModified();
    }

    // ==================== Action: Pin / Unpin ====================

    private void togglePin() {
        Photo photo = getCurrentPhoto();
        if (photo == null) {
            return;
        }
        String path = photo.getAbsolutePath();
        if (FileUtils.isPinned(this, path)) {
            FileUtils.unpinPhoto(this, path);
            photo.setPinned(false);
            Toast.makeText(this, R.string.unpinned_toast, Toast.LENGTH_SHORT).show();
        } else {
            FileUtils.pinPhoto(this, path);
            photo.setPinned(true);
            Toast.makeText(this, R.string.pinned_toast, Toast.LENGTH_SHORT).show();
        }
        updatePinIcon();
    }

    // ==================== ViewPager2 Adapter ====================

    /**
     * RecyclerView adapter backing the ViewPager2. Each page is a single
     * {@link PhotoView} loaded with Glide, supporting pinch-to-zoom and swipe.
     */
    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.PhotoViewHolder> {

        private final List<Photo> photoList;

        ImagePagerAdapter(List<Photo> photoList) {
            this.photoList = photoList;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PhotoView photoView = new PhotoView(parent.getContext());
            photoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            photoView.setAdjustViewBounds(true);
            return new PhotoViewHolder(photoView);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            Photo photo = photoList.get(position);
            Glide.with(holder.photoView.getContext())
                    .load(photo.getFile())
                    .fitCenter()
                    .into(holder.photoView);
        }

        @Override
        public int getItemCount() {
            return photoList.size();
        }

        static class PhotoViewHolder extends RecyclerView.ViewHolder {
            final PhotoView photoView;

            PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = (PhotoView) itemView;
            }
        }
    }
}
