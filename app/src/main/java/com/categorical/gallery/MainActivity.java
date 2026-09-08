package com.categorical.gallery;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.WorkspaceAdapter;
import com.categorical.gallery.model.Workspace;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.PermissionUtils;
import com.categorical.gallery.util.SortHelper;
import com.categorical.gallery.util.ThemeHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements WorkspaceAdapter.OnWorkspaceClickListener,
                   WorkspaceAdapter.OnAddClickListener,
                   WorkspaceAdapter.OnMultiSelectListener {

    private Toolbar toolbar;
    private TextView tvToolbarTitle;
    private RecyclerView recyclerView;
    private WorkspaceAdapter adapter;
    private LinearLayout bottomToolbar;
    private boolean isFirstLoad = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String[] sortOptions = {
            "自定义排序",
            "按修改时间（新→旧）",
            "按字母（A-Z）",
            "按修改时间（旧→新）",
            "按字母（Z-A）"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileUtils.initDirectories();

        toolbar = findViewById(R.id.toolbar);
        ThemeHelper.applyToolbar(this, toolbar);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvToolbarTitle.setText(R.string.toolbar_workspace);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);

        bottomToolbar = findViewById(R.id.bottomToolbar);
        ImageButton btnMultiDelete = findViewById(R.id.btnMultiDelete);
        ImageButton btnMultiRename = findViewById(R.id.btnMultiRename);

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        ImageButton btnSort = findViewById(R.id.btnSort);
        btnSort.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            showSortDialog();
        });

        btnMultiDelete.setOnClickListener(v -> handleBatchDelete());
        btnMultiRename.setOnClickListener(v -> handleBatchRename());

        adapter = new WorkspaceAdapter(this, null, this, this);
        adapter.setOnMultiSelectListener(this);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter.isDividerPosition(position)) {
                    return 2;
                }
                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.attachToRecyclerView(recyclerView);

        if (!PermissionUtils.hasStoragePermission(this)) {
            PermissionUtils.requestStoragePermission(this);
        }

        loadWorkspaces();
    }

    private void exitMultiSelectIfNeeded() {
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (toolbar != null) {
            ThemeHelper.applyToolbar(this, toolbar);
        }
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        } else if (!isFirstLoad) {
            loadWorkspaces();
        }
        isFirstLoad = false;
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

    @Override
    public void onMultiSelectModeEntered(int selectedCount) {
        bottomToolbar.setVisibility(View.VISIBLE);
        updateMultiSelectTitle(selectedCount);
    }

    @Override
    public void onMultiSelectModeExited() {
        bottomToolbar.setVisibility(View.GONE);
        tvToolbarTitle.setText(R.string.toolbar_workspace);
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        updateMultiSelectTitle(selectedCount);
    }

    private void updateMultiSelectTitle(int count) {
        tvToolbarTitle.setText("已选择" + count + "个");
    }

    private void handleBatchDelete() {
        Set<String> selected = adapter.getSelectedNames();
        List<String> toDelete = new ArrayList<>();
        for (String name : selected) {
            if (!FileUtils.isDefaultWorkspace(name)) {
                toDelete.add(name);
            }
        }
        if (toDelete.isEmpty()) {
            Toast.makeText(this, "默认工作区不可删除", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < toDelete.size(); i++) {
            if (i > 0) names.append("、");
            names.append(toDelete.get(i));
        }
        new AlertDialog.Builder(this)
            .setTitle("删除工作区")
            .setMessage("确定要删除工作区「" + names + "」吗？其内部的所有相册将被移动到「暂未归入」工作区。")
            .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                int successCount = 0;
                for (String name : toDelete) {
                    if (FileUtils.deleteWorkspace(name)) {
                        successCount++;
                    }
                }
                if (successCount > 0) {
                    Toast.makeText(this, "已删除" + successCount + "个工作区",
                            Toast.LENGTH_SHORT).show();
                }
                adapter.exitMultiSelectMode();
                loadWorkspaces();
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void handleBatchRename() {
        Set<String> selected = adapter.getSelectedNames();
        if (selected.size() != 1) {
            Toast.makeText(this, "请选择一个工作区进行重命名", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = selected.iterator().next();
        if (FileUtils.isDefaultWorkspace(name)) {
            Toast.makeText(this, "默认工作区不可重命名", Toast.LENGTH_SHORT).show();
            return;
        }
        showRenameWorkspaceDialog(name);
    }

    private void showSortDialog() {
        int currentMode = SortHelper.getWorkspaceSortMode(this);
        new AlertDialog.Builder(this)
                .setTitle("排序方式")
                .setSingleChoiceItems(sortOptions, currentMode, (dialog, which) -> {
                    if (currentMode == SortHelper.SORT_CUSTOM) {
                        SortHelper.saveWorkspaceCustomOrder(this, adapter.getWorkspaceNames());
                    }
                    SortHelper.setWorkspaceSortMode(this, which);
                    loadWorkspaces();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void saveCustomOrderIfCustomSort() {
        if (adapter != null && adapter.isCustomSort()) {
            List<String> names = adapter.getWorkspaceNames();
            if (names != null && !names.isEmpty()) {
                SortHelper.saveWorkspaceCustomOrder(this, names);
            }
        }
    }

    private void loadWorkspaces() {
        List<Workspace> workspaces = FileUtils.getWorkspaces();
        int sortMode = SortHelper.getWorkspaceSortMode(this);

        for (Workspace ws : workspaces) {
            ws.setHasAlbums(FileUtils.hasAlbums(ws.getName()));
            ws.setCoverPaths(new ArrayList<>());
        }

        sortWorkspaces(workspaces, sortMode);

        adapter.setSortMode(sortMode);
        adapter.setWorkspaces(workspaces);

        loadWorkspaceCovers(workspaces);
    }

    private void loadWorkspaceCovers(List<Workspace> workspaces) {
        executor.execute(() -> {
            for (Workspace ws : workspaces) {
                if (ws.isHasAlbums()) {
                    List<String> covers = FileUtils.getWorkspaceCovers(ws.getName());
                    final String wsName = ws.getName();
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        adapter.updateWorkspaceCovers(wsName, covers);
                    });
                }
            }
        });
    }

    private void sortWorkspaces(List<Workspace> workspaces, int sortMode) {
        switch (sortMode) {
            case SortHelper.SORT_CUSTOM:
                applyCustomOrder(workspaces);
                break;
            case SortHelper.SORT_MODIFIED_DESC:
                Collections.sort(workspaces, (w1, w2) ->
                        Long.compare(w2.getPath().lastModified(), w1.getPath().lastModified()));
                break;
            case SortHelper.SORT_NAME_ASC:
                Collections.sort(workspaces, (w1, w2) ->
                        Collator.getInstance().compare(w1.getName(), w2.getName()));
                break;
            case SortHelper.SORT_MODIFIED_ASC:
                Collections.sort(workspaces, (w1, w2) ->
                        Long.compare(w1.getPath().lastModified(), w2.getPath().lastModified()));
                break;
            case SortHelper.SORT_NAME_DESC:
                Collections.sort(workspaces, (w1, w2) ->
                        Collator.getInstance().compare(w2.getName(), w1.getName()));
                break;
        }
    }

    private void applyCustomOrder(List<Workspace> workspaces) {
        List<String> customOrder = SortHelper.getWorkspaceCustomOrder(this);
        if (customOrder == null || customOrder.isEmpty()) {
            return;
        }
        List<Workspace> sorted = new ArrayList<>();
        for (String name : customOrder) {
            for (Workspace workspace : workspaces) {
                if (workspace.getName().equals(name)) {
                    sorted.add(workspace);
                    break;
                }
            }
        }
        for (Workspace workspace : workspaces) {
            boolean found = false;
            for (Workspace w : sorted) {
                if (w.getName().equals(workspace.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                sorted.add(workspace);
            }
        }
        workspaces.clear();
        workspaces.addAll(sorted);
    }

    @Override
    public void onWorkspaceClick(Workspace workspace, int position) {
        Intent intent = new Intent(this, WorkspaceActivity.class);
        intent.putExtra("workspaceName", workspace.getName());
        startActivity(intent);
    }

    @Override
    public void onAddClick() {
        showCreateWorkspaceDialog();
    }

    private void showCreateWorkspaceDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_workspace, null);
        EditText etName = view.findViewById(R.id.etWorkspaceName);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        updateConfirmButtonState(btnConfirm, false);

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
                boolean valid = !text.trim().isEmpty();
                updateConfirmButtonState(btnConfirm, valid);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.trim().isEmpty()) {
                return;
            }
            boolean success = FileUtils.createWorkspace(name);
            if (success) {
                loadWorkspaces();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "创建失败，工作区可能已存在", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showRenameWorkspaceDialog(String originalName) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rename_workspace, null);
        TextView tvOriginalName = view.findViewById(R.id.tvOriginalName);
        EditText etName = view.findViewById(R.id.etWorkspaceName);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        tvOriginalName.setText(getString(R.string.dialog_original_name, originalName));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        updateConfirmButtonState(btnConfirm, false);

        etName.setText(originalName);
        etName.setSelection(originalName.length());

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
                updateConfirmButtonState(btnConfirm, valid);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.trim().isEmpty() || newName.equals(originalName)) {
                return;
            }
            boolean success = FileUtils.renameWorkspace(originalName, newName);
            if (success) {
                dialog.dismiss();
                adapter.exitMultiSelectMode();
                loadWorkspaces();
            } else {
                Toast.makeText(this, "重命名失败，名称可能已存在", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void updateConfirmButtonState(Button button, boolean enabled) {
        button.setEnabled(enabled);
        if (enabled) {
            button.setTextColor(ContextCompat.getColor(this, R.color.colorButtonNormal));
        } else {
            button.setTextColor(ContextCompat.getColor(this, R.color.colorButtonDisabled));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.REQUEST_STORAGE_PERMISSION) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                loadWorkspaces();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }
}
