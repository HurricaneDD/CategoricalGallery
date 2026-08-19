package com.categorical.gallery;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private WorkspaceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize app directory structure (creates default workspace "暂未归入")
        FileUtils.initDirectories();

        // Setup toolbar with title "工作区"
        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView tvTitle = findViewById(R.id.tvToolbarTitle);
        tvTitle.setText(R.string.toolbar_workspace);

        // Setup RecyclerView with 2-column grid
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // Add button: show create workspace dialog
        ImageButton btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> showCreateWorkspaceDialog());

        // Initialize adapter (data loaded in onResume)
        adapter = new WorkspaceAdapter(this, null,
                this::onWorkspaceClick,
                this::onWorkspaceLongClick);
        recyclerView.setAdapter(adapter);

        // Check and request storage permission
        if (!PermissionUtils.hasStoragePermission(this)) {
            PermissionUtils.requestStoragePermission(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWorkspaces();
    }

    private void loadWorkspaces() {
        List<Workspace> workspaces = FileUtils.getWorkspaces();
        adapter.setWorkspaces(workspaces);
    }

    private void onWorkspaceClick(Workspace workspace, int position) {
        Intent intent = new Intent(this, WorkspaceActivity.class);
        intent.putExtra("workspaceName", workspace.getName());
        startActivity(intent);
    }

    private void onWorkspaceLongClick(Workspace workspace, int position) {
        showRenameWorkspaceDialog(workspace.getName());
    }

    private void showCreateWorkspaceDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_workspace, null);
        EditText etName = view.findViewById(R.id.etWorkspaceName);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        // Confirm button starts disabled
        updateConfirmButtonState(btnConfirm, false);

        // Real-time validation: empty or contains space -> disable
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
                updateConfirmButtonState(btnConfirm, valid);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty() || name.contains(" ")) {
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

        // Confirm button starts disabled
        updateConfirmButtonState(btnConfirm, false);

        // Real-time validation: contains space or equals original name -> disable
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
                boolean valid = !text.isEmpty()
                        && !text.contains(" ")
                        && !text.equals(originalName);
                updateConfirmButtonState(btnConfirm, valid);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty() || newName.contains(" ") || newName.equals(originalName)) {
                return;
            }
            boolean success = FileUtils.renameWorkspace(originalName, newName);
            if (success) {
                loadWorkspaces();
                dialog.dismiss();
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
