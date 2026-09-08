package com.categorical.gallery;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.AlbumAdapter;
import com.categorical.gallery.model.Album;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.SortHelper;
import com.categorical.gallery.util.ThemeHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkspaceActivity extends AppCompatActivity
        implements AlbumAdapter.OnAlbumClickListener,
                   AlbumAdapter.OnImportClickListener,
                   AlbumAdapter.OnMultiSelectListener {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";

    private String workspaceName;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private AlbumAdapter adapter;
    private int currentSortMode;
    private LinearLayout bottomToolbar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workspace);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        if (workspaceName == null) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        ThemeHelper.applyToolbar(this, toolbar);
        setBoldTitle();

        ImageButton btnSort = findViewById(R.id.btnSort);
        btnSort.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            showSortDialog();
        });

        recyclerView = findViewById(R.id.rv_albums);
        recyclerView.setHasFixedSize(false);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);

        bottomToolbar = findViewById(R.id.bottomToolbar);
        ImageButton btnMultiDelete = findViewById(R.id.btnMultiDelete);
        ImageButton btnMultiRename = findViewById(R.id.btnMultiRename);
        btnMultiDelete.setOnClickListener(v -> handleBatchDelete());
        btnMultiRename.setOnClickListener(v -> handleBatchRename());

        adapter = new AlbumAdapter(this, this, this);
        adapter.setOnMultiSelectListener(this);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter.isDividerPosition(position)) {
                    return 3;
                }
                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.attachToRecyclerView(recyclerView);

        loadAlbums();
    }

    private void setBoldTitle() {
        String wsTitle = "工作区：" + workspaceName;
        SpannableString spannableTitle = new SpannableString(wsTitle);
        spannableTitle.setSpan(new StyleSpan(Typeface.BOLD), 4, wsTitle.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        toolbar.setTitle(spannableTitle);
    }

    private void exitMultiSelectIfNeeded() {
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        }
    }

    private void loadAlbums() {
        List<Album> albums = FileUtils.getAlbums(workspaceName);
        currentSortMode = SortHelper.getAlbumSortMode(this, workspaceName);

        for (Album album : albums) {
            album.setCoverPath(null);
            album.setHasPhotos(FileUtils.hasPhotos(workspaceName, album.getName()));
        }

        switch (currentSortMode) {
            case SortHelper.SORT_CUSTOM:
                applyCustomOrder(albums);
                break;
            case SortHelper.SORT_MODIFIED_DESC:
                Collections.sort(albums, (a, b) ->
                        Long.compare(b.getPath().lastModified(), a.getPath().lastModified()));
                break;
            case SortHelper.SORT_NAME_ASC:
                Collections.sort(albums, (a, b) ->
                        Collator.getInstance(java.util.Locale.CHINESE)
                                .compare(a.getName(), b.getName()));
                break;
            case SortHelper.SORT_MODIFIED_ASC:
                Collections.sort(albums, (a, b) ->
                        Long.compare(a.getPath().lastModified(), b.getPath().lastModified()));
                break;
            case SortHelper.SORT_NAME_DESC:
                Collections.sort(albums, (a, b) ->
                        Collator.getInstance(java.util.Locale.CHINESE)
                                .compare(b.getName(), a.getName()));
                break;
            default:
                break;
        }

        adapter.setSortMode(currentSortMode);
        adapter.setAlbums(albums);

        loadAlbumCovers(albums);
    }

    private void loadAlbumCovers(List<Album> albums) {
        executor.execute(() -> {
            for (Album album : albums) {
                if (album.isHasPhotos()) {
                    Photo cover = FileUtils.getMostRecentPhoto(workspaceName, album.getName());
                    final String albumName = album.getName();
                    final String coverPath = cover != null ? cover.getAbsolutePath() : null;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        adapter.updateAlbumCover(albumName, coverPath);
                    });
                }
            }
        });
    }

    private void applyCustomOrder(List<Album> albums) {
        List<String> savedOrder = SortHelper.getAlbumCustomOrder(this, workspaceName);
        if (savedOrder == null || savedOrder.isEmpty()) {
            return;
        }
        List<Album> ordered = new ArrayList<>();
        for (String name : savedOrder) {
            for (Album album : albums) {
                if (album.getName().equals(name)) {
                    ordered.add(album);
                    break;
                }
            }
        }
        for (Album album : albums) {
            if (!ordered.contains(album)) {
                ordered.add(album);
            }
        }
        albums.clear();
        albums.addAll(ordered);
    }

    private void showSortDialog() {
        String[] options = {
            "自定义排序",
            "按修改时间（新→旧）",
            "按字母（A-Z）",
            "按修改时间（旧→新）",
            "按字母（Z-A）"
        };
        new AlertDialog.Builder(this)
            .setTitle("排序方式")
            .setSingleChoiceItems(options, currentSortMode, (dialog, which) -> {
                if (currentSortMode == SortHelper.SORT_CUSTOM) {
                    SortHelper.saveAlbumCustomOrder(this, workspaceName,
                            adapter.getAlbumNames());
                }
                SortHelper.setAlbumSortMode(this, workspaceName, which);
                dialog.dismiss();
                loadAlbums();
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    @Override
    public void onMultiSelectModeEntered(int selectedCount) {
        bottomToolbar.setVisibility(View.VISIBLE);
        updateMultiSelectTitle(selectedCount);
    }

    @Override
    public void onMultiSelectModeExited() {
        bottomToolbar.setVisibility(View.GONE);
        setBoldTitle();
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        updateMultiSelectTitle(selectedCount);
    }

    private void updateMultiSelectTitle(int count) {
        toolbar.setTitle("已选择" + count + "个");
    }

    private void saveCustomOrderIfCustomSort() {
        if (currentSortMode == SortHelper.SORT_CUSTOM && adapter != null) {
            SortHelper.saveAlbumCustomOrder(this, workspaceName, adapter.getAlbumNames());
        }
    }

    private void handleBatchDelete() {
        Set<String> selected = adapter.getSelectedNames();
        if (selected.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        List<String> nameList = new ArrayList<>(selected);
        for (int i = 0; i < nameList.size(); i++) {
            if (i > 0) names.append("、");
            names.append(nameList.get(i));
        }
        TextView tvMessage = new TextView(this);
        tvMessage.setText("删除相册「" + names + "」将同时删除其中的所有图片，且不可恢复。确定要删除吗？");
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.colorRed));
        tvMessage.setTextSize(14);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        tvMessage.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
            .setTitle("删除相册")
            .setView(tvMessage)
            .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                int successCount = 0;
                for (String name : selected) {
                    if (FileUtils.deleteAlbum(workspaceName, name)) {
                        successCount++;
                    }
                }
                if (successCount > 0) {
                    Toast.makeText(this, "已删除" + successCount + "个相册",
                            Toast.LENGTH_SHORT).show();
                }
                adapter.exitMultiSelectMode();
                loadAlbums();
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void handleBatchRename() {
        Set<String> selected = adapter.getSelectedNames();
        if (selected.size() != 1) {
            Toast.makeText(this, "请选择一个相册进行重命名", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = selected.iterator().next();
        showRenameAlbumDialog(name);
    }

    @Override
    public void onAlbumClick(Album album, int position) {
        Intent intent = new Intent(this, AlbumActivity.class);
        intent.putExtra(AlbumActivity.EXTRA_WORKSPACE_NAME, workspaceName);
        intent.putExtra(AlbumActivity.EXTRA_ALBUM_NAME, album.getName());
        startActivity(intent);
    }

    @Override
    public void onAddClick() {
        showCreateAlbumDialog();
    }

    @Override
    public void onImportClick() {
        Intent intent = new Intent(this, ImportActivity.class);
        intent.putExtra(EXTRA_WORKSPACE_NAME, workspaceName);
        startActivity(intent);
    }

    private void showRenameAlbumDialog(String originalName) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rename_workspace, null);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvOriginalName = view.findViewById(R.id.tvOriginalName);
        EditText etName = view.findViewById(R.id.etWorkspaceName);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        tvTitle.setText(R.string.dialog_rename_album_title);
        tvOriginalName.setText(getString(R.string.dialog_original_name, originalName));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        etName.setText(originalName);
        etName.setSelection(originalName.length());

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
                String text = s.toString();
                boolean valid = !text.trim().isEmpty()
                        && !text.trim().equals(originalName);
                btnConfirm.setEnabled(valid);
                btnConfirm.setTextColor(ContextCompat.getColor(WorkspaceActivity.this,
                        valid ? R.color.colorPrimary : R.color.colorButtonDisabled));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.trim().isEmpty() || newName.equals(originalName)) {
                return;
            }
            boolean success = FileUtils.renameAlbum(workspaceName, originalName, newName);
            if (success) {
                dialog.dismiss();
                adapter.exitMultiSelectMode();
                loadAlbums();
            } else {
                Toast.makeText(this, R.string.rename_album_failed, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showCreateAlbumDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_album, null);
        EditText etName = view.findViewById(R.id.et_album_name);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

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
                String text = s.toString();
                boolean valid = !text.isEmpty() && !text.contains(" ");
                btnConfirm.setEnabled(valid);
                btnConfirm.setTextColor(ContextCompat.getColor(WorkspaceActivity.this,
                        valid ? R.color.colorPrimary : R.color.colorButtonDisabled));
            }
        });

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty() || name.contains(" ")) {
                return;
            }
            FileUtils.createAlbum(workspaceName, name);
            dialog.dismiss();
            loadAlbums();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (toolbar != null) {
            ThemeHelper.applyToolbar(this, toolbar);
        }
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        } else {
            loadAlbums();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCustomOrderIfCustomSort();
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
