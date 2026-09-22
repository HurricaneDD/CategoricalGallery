package com.categorical.gallery;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.WorkspaceAdapter;
import com.categorical.gallery.model.Workspace;
import com.categorical.gallery.service.SyncService;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.PermissionUtils;
import com.categorical.gallery.util.SftpConfig;
import com.categorical.gallery.util.SftpManager;
import com.categorical.gallery.util.SortHelper;
import com.categorical.gallery.util.ThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private FrameLayout bottomContainer;
    private LinearLayout layoutBottomNavigation;
    private LinearLayout tabLocalWorkspace;
    private LinearLayout tabOnlineWorkspace;
    private ImageButton btnSync;
    private ImageView ivTabLocal;
    private TextView tvTabLocal;
    private ImageView ivTabOnline;
    private TextView tvTabOnline;
    private ImageButton btnCloud;
    private LinearLayout layoutSyncBanner;
    private ImageView ivSyncStatus;
    private TextView tvSyncTime;
    private TextView tvSftpStatusBadge;
    private LinearLayout layoutSyncProgress;
    private ProgressBar pbSyncProgress;
    private TextView tvSyncEstimateTime;

    private boolean isOnlineMode = false;
    private boolean isSyncing = false;
    private boolean isFirstLoad = true;

    private ObjectAnimator btnSyncAnimator;
    private ObjectAnimator ivSyncStatusAnimator;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
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

        FileUtils.init(this);
        FileUtils.initDirectories();

        toolbar = findViewById(R.id.toolbar);
        ThemeHelper.applyToolbar(this, toolbar);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvToolbarTitle.setText(R.string.toolbar_local_workspace);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);

        btnCloud = findViewById(R.id.btnCloud);
        btnCloud.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            Intent intent = new Intent(MainActivity.this, SftpConfigActivity.class);
            startActivity(intent);
        });

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

        btnSync = findViewById(R.id.btnSync);
        btnSync.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            performSync();
        });

        // Top Sync Banner
        layoutSyncBanner = findViewById(R.id.layoutSyncBanner);
        ivSyncStatus = findViewById(R.id.ivSyncStatus);
        tvSyncTime = findViewById(R.id.tvSyncTime);
        tvSftpStatusBadge = findViewById(R.id.tvSftpStatusBadge);
        layoutSyncProgress = findViewById(R.id.layoutSyncProgress);
        pbSyncProgress = findViewById(R.id.pbSyncProgress);
        tvSyncEstimateTime = findViewById(R.id.tvSyncEstimateTime);
        layoutSyncBanner.setOnClickListener(v -> {
            exitMultiSelectIfNeeded();
            performSync();
        });

        // Bottom Navigation Views
        bottomContainer = findViewById(R.id.bottomContainer);
        layoutBottomNavigation = findViewById(R.id.layoutBottomNavigation);
        tabLocalWorkspace = findViewById(R.id.tabLocalWorkspace);
        tabOnlineWorkspace = findViewById(R.id.tabOnlineWorkspace);
        ivTabLocal = findViewById(R.id.ivTabLocal);
        tvTabLocal = findViewById(R.id.tvTabLocal);
        ivTabOnline = findViewById(R.id.ivTabOnline);
        tvTabOnline = findViewById(R.id.tvTabOnline);

        tabLocalWorkspace.setOnClickListener(v -> switchWorkspaceTab(false));
        tabOnlineWorkspace.setOnClickListener(v -> switchWorkspaceTab(true));

        bottomToolbar = findViewById(R.id.bottomToolbar);
        ImageButton btnMultiDelete = findViewById(R.id.btnMultiDelete);
        ImageButton btnMultiRename = findViewById(R.id.btnMultiRename);
        TextView tvMultiDelete = findViewById(R.id.tvMultiDelete);
        TextView tvMultiRename = findViewById(R.id.tvMultiRename);
        View layoutMultiDelete = findViewById(R.id.layoutMultiDelete);
        View layoutMultiRename = findViewById(R.id.layoutMultiRename);

        int redColor = ContextCompat.getColor(this, R.color.colorRed);
        int primaryColor = ThemeHelper.getPrimaryColor(this);
        ImageViewCompat.setImageTintList(btnMultiDelete, ColorStateList.valueOf(redColor));
        if (tvMultiDelete != null) tvMultiDelete.setTextColor(redColor);
        ImageViewCompat.setImageTintList(btnMultiRename, ColorStateList.valueOf(primaryColor));
        if (tvMultiRename != null) tvMultiRename.setTextColor(primaryColor);

        View.OnClickListener deleteClick = v -> handleBatchDelete();
        btnMultiDelete.setOnClickListener(deleteClick);
        if (layoutMultiDelete != null) layoutMultiDelete.setOnClickListener(deleteClick);

        View.OnClickListener renameClick = v -> handleBatchRename();
        btnMultiRename.setOnClickListener(renameClick);
        if (layoutMultiRename != null) layoutMultiRename.setOnClickListener(renameClick);

        adapter = new WorkspaceAdapter(this, null, this, this);
        adapter.setOnMultiSelectListener(this);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter.isDividerPosition(position) || adapter.isEmptyPosition(position)) {
                    return 2;
                }
                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.attachToRecyclerView(recyclerView);

        if (!PermissionUtils.hasStoragePermission(this)) {
            showStoragePermissionDialog();
        } else {
            FileUtils.resetRootDir();
            FileUtils.initDirectories();
        }

        updateTabUi();
        loadWorkspaces();
    }

    private void showStoragePermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_message)
                .setPositiveButton(R.string.permission_dialog_btn_grant, (dialog, which) -> {
                    PermissionUtils.requestStoragePermission(this);
                })
                .setNegativeButton(R.string.btn_cancel, (dialog, which) -> {
                    dialog.dismiss();
                    loadWorkspaces();
                })
                .show();
    }

    private void switchWorkspaceTab(boolean online) {
        if (isOnlineMode == online) return;
        exitMultiSelectIfNeeded();
        isOnlineMode = online;
        updateTabUi();
        loadWorkspaces();
    }

    private void updateTabUi() {
        int primaryColor = ContextCompat.getColor(this, R.color.colorPrimary);
        int secondaryColor = ContextCompat.getColor(this, R.color.colorTextSecondary);

        if (isOnlineMode) {
            tvToolbarTitle.setText(R.string.toolbar_online_workspace);
            if (btnSync != null) btnSync.setVisibility(View.VISIBLE);
            btnCloud.setVisibility(View.VISIBLE);
            layoutSyncBanner.setVisibility(View.VISIBLE);
            updateSyncBanner();

            ivTabLocal.setColorFilter(secondaryColor);
            tvTabLocal.setTextColor(secondaryColor);
            tvTabLocal.setTypeface(null, Typeface.NORMAL);

            ivTabOnline.setColorFilter(primaryColor);
            tvTabOnline.setTextColor(primaryColor);
            tvTabOnline.setTypeface(null, Typeface.BOLD);
        } else {
            tvToolbarTitle.setText(R.string.toolbar_local_workspace);
            if (btnSync != null) btnSync.setVisibility(View.GONE);
            btnCloud.setVisibility(View.GONE);
            layoutSyncBanner.setVisibility(View.GONE);

            ivTabLocal.setColorFilter(primaryColor);
            tvTabLocal.setTextColor(primaryColor);
            tvTabLocal.setTypeface(null, Typeface.BOLD);

            ivTabOnline.setColorFilter(secondaryColor);
            tvTabOnline.setTextColor(secondaryColor);
            tvTabOnline.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void updateSyncBanner() {
        if (SyncService.isSyncing()) {
            return;
        }
        if (layoutSyncProgress != null) {
            layoutSyncProgress.setVisibility(View.GONE);
        }
        long lastSync = SftpConfig.getLastSyncTime(this);
        tvSyncTime.setText("最近同步时间：\n" + SftpConfig.formatSyncTime(lastSync));
        if (SftpConfig.isOnlineStatus(this)) {
            tvSftpStatusBadge.setVisibility(View.VISIBLE);
            tvSftpStatusBadge.setText("● SFTP服务在线");
            tvSftpStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.colorGreen));
        } else {
            tvSftpStatusBadge.setVisibility(View.VISIBLE);
            tvSftpStatusBadge.setText("● SFTP未在线");
            tvSftpStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
        }
    }

    private void updateSyncProgressUI(int percent, long remainingSeconds, String statusText) {
        if (layoutSyncProgress != null) {
            layoutSyncProgress.setVisibility(View.VISIBLE);
        }
        if (pbSyncProgress != null) {
            if (percent >= 0) {
                pbSyncProgress.setIndeterminate(false);
                pbSyncProgress.setProgress(percent);
            } else {
                pbSyncProgress.setIndeterminate(true);
            }
        }
        if (tvSyncTime != null) {
            tvSyncTime.setText(statusText != null && !statusText.isEmpty() ? statusText : "正在同步在线相册...");
        }
        if (tvSyncEstimateTime != null) {
            if (remainingSeconds >= 0) {
                tvSyncEstimateTime.setText(SftpManager.formatRemainingTime(remainingSeconds));
            } else {
                tvSyncEstimateTime.setText("预计时间：计算中...");
            }
        }
    }

    private void checkAndAttachSyncService() {
        if (SyncService.isSyncing()) {
            isSyncing = true;
            startSyncAnimation();
            int percent = SyncService.getCurrentPercent();
            long remSec = SyncService.getCurrentRemainingSeconds();
            String status = SyncService.getCurrentStatusText();
            updateSyncProgressUI(percent, remSec, status);
        } else {
            if (isSyncing) {
                isSyncing = false;
                stopSyncAnimation();
                updateSyncBanner();
            }
        }

        SyncService.setListener(new SyncService.SyncListener() {
            @Override
            public void onProgress(int percent, long remainingSeconds, String statusText) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    isSyncing = true;
                    if (btnSyncAnimator == null) {
                        startSyncAnimation();
                    }
                    updateSyncProgressUI(percent, remainingSeconds, statusText);
                });
            }

            @Override
            public void onComplete(SftpManager.SyncResult result) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    isSyncing = false;
                    stopSyncAnimation();
                    updateSyncBanner();
                    if (result.success) {
                        String msg = "在线工作区同步成功！已上传 " + result.uploadedCount + " 项，已下载 " + result.downloadedCount + " 项";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        loadWorkspaces();
                    } else {
                        String err = result.errorMessage != null ? result.errorMessage : "连接超时或错误";
                        Toast.makeText(MainActivity.this, "同步失败：" + err, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void startSyncAnimation() {
        stopSyncAnimation();

        if (btnSync != null && btnSync.getVisibility() == View.VISIBLE) {
            float btnPivotX = btnSync.getWidth() > 0 ? btnSync.getWidth() / 2f :
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
            float btnPivotY = btnSync.getHeight() > 0 ? btnSync.getHeight() / 2f :
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
            btnSync.setPivotX(btnPivotX);
            btnSync.setPivotY(btnPivotY);
            btnSyncAnimator = ObjectAnimator.ofFloat(btnSync, View.ROTATION, 0f, 360f);
            btnSyncAnimator.setDuration(900);
            btnSyncAnimator.setRepeatCount(ValueAnimator.INFINITE);
            btnSyncAnimator.setInterpolator(new LinearInterpolator());
            btnSyncAnimator.start();
        }

        if (ivSyncStatus != null) {
            float iconPivotX = ivSyncStatus.getWidth() > 0 ? ivSyncStatus.getWidth() / 2f :
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            float iconPivotY = ivSyncStatus.getHeight() > 0 ? ivSyncStatus.getHeight() / 2f :
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            ivSyncStatus.setPivotX(iconPivotX);
            ivSyncStatus.setPivotY(iconPivotY);
            ivSyncStatusAnimator = ObjectAnimator.ofFloat(ivSyncStatus, View.ROTATION, 0f, 360f);
            ivSyncStatusAnimator.setDuration(900);
            ivSyncStatusAnimator.setRepeatCount(ValueAnimator.INFINITE);
            ivSyncStatusAnimator.setInterpolator(new LinearInterpolator());
            ivSyncStatusAnimator.start();
        }
    }

    private void stopSyncAnimation() {
        if (btnSyncAnimator != null) {
            btnSyncAnimator.cancel();
            btnSyncAnimator = null;
        }
        if (btnSync != null) {
            btnSync.clearAnimation();
            btnSync.setRotation(0f);
        }

        if (ivSyncStatusAnimator != null) {
            ivSyncStatusAnimator.cancel();
            ivSyncStatusAnimator = null;
        }
        if (ivSyncStatus != null) {
            ivSyncStatus.clearAnimation();
            ivSyncStatus.setRotation(0f);
        }
    }

    private void performSync() {
        if (isSyncing || SyncService.isSyncing()) {
            Toast.makeText(this, "正在同步中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!SftpConfig.isConfigured(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("未配置SFTP服务器")
                    .setMessage("检测到尚未完成SFTP服务器及在线相册根目录配置，是否前往配置？")
                    .setPositiveButton("前往配置", (d, w) -> {
                        Intent intent = new Intent(this, SftpConfigActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        isSyncing = true;
        startSyncAnimation();
        updateSyncProgressUI(0, -1, "正在连接SFTP服务器并计算进度...");
        Toast.makeText(this, "正在与SFTP服务器同步...", Toast.LENGTH_SHORT).show();

        // Request POST_NOTIFICATIONS permission on Android 13+ if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        SyncService.startSync(this);
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
        checkAndAttachSyncService();
        if (isOnlineMode) {
            updateSyncBanner();
        }
        if (PermissionUtils.hasStoragePermission(this)) {
            FileUtils.resetRootDir();
            FileUtils.initDirectories();
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
        stopSyncAnimation();
        SyncService.setListener(null);
        executor.shutdown();
    }

    @Override
    public void onMultiSelectModeEntered(int selectedCount) {
        layoutBottomNavigation.setVisibility(View.GONE);
        bottomToolbar.setVisibility(View.VISIBLE);
        updateMultiSelectTitle(selectedCount);
    }

    @Override
    public void onMultiSelectModeExited() {
        bottomToolbar.setVisibility(View.GONE);
        layoutBottomNavigation.setVisibility(View.VISIBLE);
        tvToolbarTitle.setText(isOnlineMode ? R.string.toolbar_online_workspace : R.string.toolbar_local_workspace);
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
                    if (FileUtils.deleteWorkspace(name, isOnlineMode)) {
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
        List<Workspace> workspaces = FileUtils.getWorkspaces(isOnlineMode);
        int sortMode = SortHelper.getWorkspaceSortMode(this);

        for (Workspace ws : workspaces) {
            ws.setHasAlbums(FileUtils.hasAlbums(ws.getName(), isOnlineMode));
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
                    List<String> covers = FileUtils.getWorkspaceCovers(ws.getName(), isOnlineMode);
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
        intent.putExtra("isOnline", isOnlineMode);
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
            boolean success = FileUtils.createWorkspace(name, isOnlineMode);
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
            boolean success = FileUtils.renameWorkspace(originalName, newName, isOnlineMode);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionUtils.REQUEST_STORAGE_PERMISSION) {
            if (PermissionUtils.hasStoragePermission(this)) {
                FileUtils.resetRootDir();
                FileUtils.initDirectories();
                loadWorkspaces();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.REQUEST_STORAGE_PERMISSION) {
            if (PermissionUtils.hasStoragePermission(this)) {
                FileUtils.resetRootDir();
                FileUtils.initDirectories();
                loadWorkspaces();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }
}
