package com.categorical.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.AlbumAdapter;
import com.categorical.gallery.model.Album;
import com.categorical.gallery.util.FileUtils;

import java.util.List;

/**
 * Shows all albums inside a workspace as a 3-column grid.
 */
public class WorkspaceActivity extends AppCompatActivity implements AlbumAdapter.OnAlbumClickListener {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";

    private String workspaceName;
    private RecyclerView recyclerView;
    private AlbumAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workspace);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        if (workspaceName == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(workspaceName);

        ImageButton btnImport = findViewById(R.id.btn_import);
        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(WorkspaceActivity.this, ImportActivity.class);
            intent.putExtra(EXTRA_WORKSPACE_NAME, workspaceName);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.rv_albums);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setHasFixedSize(false);
        adapter = new AlbumAdapter(this, this);
        recyclerView.setAdapter(adapter);

        loadAlbums();
    }

    private void loadAlbums() {
        List<Album> albums = FileUtils.getAlbums(workspaceName);
        adapter.setAlbums(albums);
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

    private void showCreateAlbumDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_album, null);
        EditText etName = view.findViewById(R.id.et_album_name);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        // Confirm is disabled until a valid (non-empty, no-space) name is typed.
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
        loadAlbums();
    }
}
