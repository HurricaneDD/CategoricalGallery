package com.categorical.gallery;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.util.SftpConfig;
import com.categorical.gallery.util.SftpManager;
import com.categorical.gallery.util.ThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SftpConfigActivity extends AppCompatActivity {

    private TextInputEditText etHost;
    private TextInputEditText etPort;
    private TextInputEditText etUser;
    private TextInputEditText etPass;
    private TextInputEditText etRemoteRoot;
    private Button btnTestConnection;
    private Button btnBrowseSftp;
    private Button btnSaveConfig;
    private LinearLayout layoutTestResult;
    private ImageView ivTestStatusIcon;
    private TextView tvTestStatusText;
    private TextView tvLastSyncInfo;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sftp_config);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ThemeHelper.applyToolbar(this, toolbar);
        toolbar.setTitle("配置SFTP服务器");
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        loadSavedConfig();
    }

    private void initViews() {
        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        etRemoteRoot = findViewById(R.id.etRemoteRoot);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        btnBrowseSftp = findViewById(R.id.btnBrowseSftp);
        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        layoutTestResult = findViewById(R.id.layoutTestResult);
        ivTestStatusIcon = findViewById(R.id.ivTestStatusIcon);
        tvTestStatusText = findViewById(R.id.tvTestStatusText);
        tvLastSyncInfo = findViewById(R.id.tvLastSyncInfo);

        btnTestConnection.setOnClickListener(v -> testConnection());
        btnBrowseSftp.setOnClickListener(v -> openSftpDirectoryBrowser());
        btnSaveConfig.setOnClickListener(v -> saveConfigAndExit(false));
    }

    private void loadSavedConfig() {
        etHost.setText(SftpConfig.getHost(this));
        int port = SftpConfig.getPort(this);
        etPort.setText(String.valueOf(port > 0 ? port : 22));
        etUser.setText(SftpConfig.getUsername(this));
        etPass.setText(SftpConfig.getPassword(this));
        String rootDir = SftpConfig.getRemoteRootDir(this);
        etRemoteRoot.setText(rootDir);

        long lastSync = SftpConfig.getLastSyncTime(this);
        tvLastSyncInfo.setText("最近一次同步时间：" + SftpConfig.formatSyncTime(lastSync));

        if (SftpConfig.isOnlineStatus(this)) {
            showConnectionStatus(true, "SFTP服务在线");
        }
    }

    private int getEnteredPort() {
        try {
            String pStr = etPort.getText() != null ? etPort.getText().toString().trim() : "";
            if (!pStr.isEmpty()) {
                return Integer.parseInt(pStr);
            }
        } catch (Exception ignored) {
        }
        return 22;
    }

    private void testConnection() {
        String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        int port = getEnteredPort();
        String user = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pass = etPass.getText() != null ? etPass.getText().toString() : "";

        if (host.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "请填写SFTP地址和账户名", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTestConnection.setEnabled(false);
        btnTestConnection.setText("正在测试连接…");
        layoutTestResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                boolean success = SftpManager.testConnection(host, port, user, pass);
                mainHandler.post(() -> {
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("尝试连接");
                    if (success) {
                        showConnectionStatus(true, "连接成功：SFTP服务在线");
                        Toast.makeText(SftpConfigActivity.this, "连接成功：SFTP服务在线", Toast.LENGTH_SHORT).show();
                        SftpConfig.setOnlineStatus(SftpConfigActivity.this, true);
                    } else {
                        showConnectionStatus(false, "连接失败，请检查服务器地址与认证信息");
                        SftpConfig.setOnlineStatus(SftpConfigActivity.this, false);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("尝试连接");
                    showConnectionStatus(false, "连接失败：" + e.getMessage());
                    SftpConfig.setOnlineStatus(SftpConfigActivity.this, false);
                    Toast.makeText(SftpConfigActivity.this, "连接失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showConnectionStatus(boolean isSuccess, String message) {
        layoutTestResult.setVisibility(View.VISIBLE);
        tvTestStatusText.setText(message);
        if (isSuccess) {
            ivTestStatusIcon.setImageResource(R.drawable.ic_check_circle);
            ivTestStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.colorGreen));
            tvTestStatusText.setTextColor(ContextCompat.getColor(this, R.color.colorGreen));
        } else {
            ivTestStatusIcon.setImageResource(R.drawable.ic_info);
            ivTestStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.colorRed));
            tvTestStatusText.setTextColor(ContextCompat.getColor(this, R.color.colorRed));
        }
    }

    private void saveConfigAndExit(boolean finishAfterSave) {
        String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        int port = getEnteredPort();
        String user = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pass = etPass.getText() != null ? etPass.getText().toString() : "";
        String rootDir = etRemoteRoot.getText() != null ? etRemoteRoot.getText().toString().trim() : "";

        if (host.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "请先填写SFTP地址和账户名", Toast.LENGTH_SHORT).show();
            return;
        }

        SftpConfig.saveConfig(this, host, port, user, pass, rootDir);
        Toast.makeText(this, "SFTP配置已保存", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        if (finishAfterSave) {
            finish();
        }
    }

    /**
     * Open graphical SFTP directory browser dialog.
     */
    private void openSftpDirectoryBrowser() {
        String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        int port = getEnteredPort();
        String user = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pass = etPass.getText() != null ? etPass.getText().toString() : "";

        if (host.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "请先填写SFTP地址、账户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create browser dialog
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sftp_browser);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvCurrentPath = dialog.findViewById(R.id.tvCurrentPath);
        ImageButton btnNewDir = dialog.findViewById(R.id.btnNewDir);
        RecyclerView rvDirList = dialog.findViewById(R.id.rvDirList);
        ProgressBar pbLoading = dialog.findViewById(R.id.pbLoading);
        TextView tvEmptyDir = dialog.findViewById(R.id.tvEmptyDir);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnSelectCurrent = dialog.findViewById(R.id.btnSelectCurrent);

        rvDirList.setLayoutManager(new LinearLayoutManager(this));
        final List<SftpManager.RemoteDirItem> dirItems = new ArrayList<>();
        final String[] currentPathHolder = new String[1];

        // Initial path: entered remote root or home directory
        String initialPath = etRemoteRoot.getText() != null ? etRemoteRoot.getText().toString().trim() : "";
        currentPathHolder[0] = initialPath.isEmpty() ? "/" : initialPath;
        tvCurrentPath.setText(currentPathHolder[0]);

        class DirAdapter extends RecyclerView.Adapter<DirAdapter.DirViewHolder> {
            @NonNull
            @Override
            public DirViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sftp_dir, parent, false);
                return new DirViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull DirViewHolder holder, int position) {
                SftpManager.RemoteDirItem item = dirItems.get(position);
                holder.tvDirName.setText(item.name);
                if (item.isParent) {
                    holder.ivDirIcon.setImageResource(R.drawable.ic_back);
                    holder.ivDirArrow.setVisibility(View.GONE);
                } else {
                    holder.ivDirIcon.setImageResource(R.drawable.ic_folder);
                    holder.ivDirArrow.setVisibility(View.VISIBLE);
                }
                holder.itemView.setOnClickListener(v -> {
                    currentPathHolder[0] = item.fullPath;
                    loadDir(currentPathHolder[0]);
                });
            }

            @Override
            public int getItemCount() {
                return dirItems.size();
            }

            class DirViewHolder extends RecyclerView.ViewHolder {
                ImageView ivDirIcon;
                TextView tvDirName;
                ImageView ivDirArrow;

                DirViewHolder(@NonNull View itemView) {
                    super(itemView);
                    ivDirIcon = itemView.findViewById(R.id.ivDirIcon);
                    tvDirName = itemView.findViewById(R.id.tvDirName);
                    ivDirArrow = itemView.findViewById(R.id.ivDirArrow);
                }
            }

            void loadDir(String path) {
                pbLoading.setVisibility(View.VISIBLE);
                rvDirList.setVisibility(View.INVISIBLE);
                tvEmptyDir.setVisibility(View.GONE);
                tvCurrentPath.setText(path);

                executor.execute(() -> {
                    try {
                        List<SftpManager.RemoteDirItem> list = SftpManager.listDirectories(host, port, user, pass, path);
                        mainHandler.post(() -> {
                            pbLoading.setVisibility(View.GONE);
                            rvDirList.setVisibility(View.VISIBLE);
                            dirItems.clear();
                            dirItems.addAll(list);
                            notifyDataSetChanged();
                            if (dirItems.isEmpty() || (dirItems.size() == 1 && dirItems.get(0).isParent)) {
                                tvEmptyDir.setVisibility(View.VISIBLE);
                            } else {
                                tvEmptyDir.setVisibility(View.GONE);
                            }
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            pbLoading.setVisibility(View.GONE);
                            rvDirList.setVisibility(View.VISIBLE);
                            Toast.makeText(SftpConfigActivity.this, "加载目录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        }

        DirAdapter adapter = new DirAdapter();
        rvDirList.setAdapter(adapter);
        adapter.loadDir(currentPathHolder[0]);

        btnNewDir.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("目录名（如 .CategoricalGallery）");
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            input.setPadding(padding, padding, padding, padding);

            new MaterialAlertDialogBuilder(this)
                    .setTitle("新建远程文件夹")
                    .setMessage("将在 " + currentPathHolder[0] + " 下创建新目录")
                    .setView(input)
                    .setPositiveButton("创建", (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) return;
                        executor.execute(() -> {
                            try {
                                SftpManager.createDirectory(host, port, user, pass, currentPathHolder[0], name);
                                mainHandler.post(() -> {
                                    Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show();
                                    adapter.loadDir(currentPathHolder[0]);
                                });
                            } catch (Exception ex) {
                                mainHandler.post(() -> {
                                    Toast.makeText(this, "创建失败: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnSelectCurrent.setOnClickListener(v -> {
            String selected = currentPathHolder[0];
            etRemoteRoot.setText(selected);
            Toast.makeText(this, "已设置在线相册根目录：" + selected, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
