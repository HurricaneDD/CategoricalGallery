package com.categorical.gallery;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.categorical.gallery.adapter.SystemAlbumAdapter;
import com.categorical.gallery.model.SystemAlbum;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.MediaStoreUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Import system album wizard.
 * <p>
 * Flow:
 * <ol>
 *     <li>State 1: Select a system album from the list.</li>
 *     <li>State 2: Choose import mode (move / copy).</li>
 *     <li>State 3: Copy (and optionally delete original) photos in a background thread,
 *         updating a progress bar.</li>
 *     <li>State 4: Show success and let the user finish.</li>
 * </ol>
 * Imported photos are placed under
 * {@code workspaces/<workspaceName>/<albumName>/} where {@code albumName} is the
 * system album's name. File name conflicts are resolved by appending {@code _1, _2, ...}.
 */
public class ImportActivity extends AppCompatActivity implements SystemAlbumAdapter.OnAlbumSelectedListener {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";

    private static final int STATE_SELECT_ALBUM = 0;
    private static final int STATE_SELECT_MODE = 1;
    private static final int STATE_IMPORTING = 2;
    private static final int STATE_COMPLETE = 3;

    private Toolbar toolbar;

    private View stateSelectAlbum;
    private View stateSelectMode;
    private View stateImporting;
    private View stateComplete;

    private RecyclerView recyclerView;
    private TextView textEmpty;
    private Button btnNextStep;
    private ProgressBar progressBar;
    private TextView textProgress;
    private Button btnFinishExit;

    private String workspaceName;
    private final List<SystemAlbum> systemAlbums = new ArrayList<>();
    private SystemAlbumAdapter adapter;
    private SystemAlbum selectedAlbum;

    /** true = move mode (copy then delete original), false = copy only. */
    private boolean moveMode;
    private int currentState = STATE_SELECT_ALBUM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        if (workspaceName == null || workspaceName.isEmpty()) {
            workspaceName = getString(R.string.default_workspace_name);
        }

        initViews();
        setState(STATE_SELECT_ALBUM);
        loadSystemAlbums();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        stateSelectAlbum = findViewById(R.id.stateSelectAlbum);
        stateSelectMode = findViewById(R.id.stateSelectMode);
        stateImporting = findViewById(R.id.stateImporting);
        stateComplete = findViewById(R.id.stateComplete);

        recyclerView = findViewById(R.id.recyclerAlbums);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(false);

        textEmpty = findViewById(R.id.textEmpty);

        btnNextStep = findViewById(R.id.btnNextStep);
        btnNextStep.setEnabled(false);
        btnNextStep.setOnClickListener(v -> {
            if (selectedAlbum == null) {
                Toast.makeText(this, R.string.title_select_system_album, Toast.LENGTH_SHORT).show();
                return;
            }
            setState(STATE_SELECT_MODE);
        });

        findViewById(R.id.btnMoveMode).setOnClickListener(v -> startImport(true));
        findViewById(R.id.btnCopyMode).setOnClickListener(v -> startImport(false));

        progressBar = findViewById(R.id.progressBar);
        textProgress = findViewById(R.id.textProgress);

        btnFinishExit = findViewById(R.id.btnFinishExit);
        btnFinishExit.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    private void setState(int state) {
        currentState = state;
        stateSelectAlbum.setVisibility(state == STATE_SELECT_ALBUM ? View.VISIBLE : View.GONE);
        stateSelectMode.setVisibility(state == STATE_SELECT_MODE ? View.VISIBLE : View.GONE);
        stateImporting.setVisibility(state == STATE_IMPORTING ? View.VISIBLE : View.GONE);
        stateComplete.setVisibility(state == STATE_COMPLETE ? View.VISIBLE : View.GONE);

        switch (state) {
            case STATE_SELECT_ALBUM:
                toolbar.setTitle(R.string.title_select_system_album);
                toolbar.setNavigationIcon(R.drawable.ic_back);
                break;
            case STATE_SELECT_MODE:
                toolbar.setTitle(R.string.title_select_import_mode);
                toolbar.setNavigationIcon(R.drawable.ic_back);
                break;
            case STATE_IMPORTING:
                toolbar.setTitle(R.string.importing_please_wait);
                toolbar.setNavigationIcon(null);
                break;
            case STATE_COMPLETE:
                toolbar.setTitle(R.string.import_success);
                toolbar.setNavigationIcon(null);
                break;
        }
    }

    private void loadSystemAlbums() {
        new Thread(() -> {
            final List<SystemAlbum> albums = MediaStoreUtils.getSystemAlbums(this);
            runOnUiThread(() -> {
                systemAlbums.clear();
                systemAlbums.addAll(albums);
                adapter = new SystemAlbumAdapter(this, systemAlbums, ImportActivity.this);
                recyclerView.setAdapter(adapter);
                textEmpty.setVisibility(systemAlbums.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    @Override
    public void onAlbumSelected(SystemAlbum album) {
        selectedAlbum = album;
        btnNextStep.setEnabled(album != null);
    }

    /**
     * Begin the import operation.
     *
     * @param move true for move mode (copy + delete original), false for copy only.
     */
    private void startImport(final boolean move) {
        if (selectedAlbum == null) {
            return;
        }
        moveMode = move;
        setState(STATE_IMPORTING);
        progressBar.setProgress(0);
        textProgress.setText("0%");

        final SystemAlbum album = selectedAlbum;
        final String albumName = sanitizeName(album.getName());
        final List<String> uriStrings = new ArrayList<>(album.getPhotoUris());
        final int total = uriStrings.size();
        final List<Uri> copiedUris = new ArrayList<>();

        new Thread(() -> {
            File albumDir = FileUtils.getAlbumDir(workspaceName, albumName);
            if (!albumDir.exists() && !albumDir.mkdirs()) {
                runOnUiThread(this::showComplete);
                return;
            }

            for (int i = 0; i < total; i++) {
                Uri uri = Uri.parse(uriStrings.get(i));
                String fileName = queryDisplayName(uri);
                File destFile = resolveUniqueFile(albumDir, fileName);

                boolean ok = MediaStoreUtils.copyPhotoToAppDir(this, uri, destFile);
                if (ok && moveMode) {
                    copiedUris.add(uri);
                }

                final int done = i + 1;
                runOnUiThread(() -> updateProgress(done, total));
            }

            // Move mode: delete the original system photos after a successful copy.
            if (moveMode) {
                for (Uri uri : copiedUris) {
                    MediaStoreUtils.deleteSystemPhoto(this, uri);
                }
            }

            runOnUiThread(this::showComplete);
        }).start();
    }

    private void updateProgress(int done, int total) {
        int percent = total > 0 ? done * 100 / total : 100;
        progressBar.setProgress(percent);
        textProgress.setText(getString(R.string.progress_format, percent, done, total));
    }

    private void showComplete() {
        setState(STATE_COMPLETE);
    }

    /**
     * Query the original display name of a MediaStore image.
     */
    private String queryDisplayName(Uri uri) {
        String displayName = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{MediaStore.Images.Media.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception e) {
            // ignore, fall back to generated name
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = "photo_" + System.currentTimeMillis() + ".jpg";
        }
        return displayName;
    }

    /**
     * Resolve a non-conflicting destination file inside {@code dir}. If a file with
     * the given name already exists, append {@code _1, _2, ...} before the extension.
     */
    private File resolveUniqueFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) {
            return file;
        }
        String base;
        String ext;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        } else {
            base = fileName;
            ext = "";
        }
        int seq = 1;
        while (file.exists()) {
            file = new File(dir, base + "_" + seq + ext);
            seq++;
        }
        return file;
    }

    /**
     * Replace filesystem-illegal characters in an album name.
     */
    private String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return getString(R.string.default_workspace_name);
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public void onBackPressed() {
        // Block leaving while an import is in progress.
        if (currentState == STATE_IMPORTING) {
            return;
        }
        // From mode selection, go back to album selection instead of finishing.
        if (currentState == STATE_SELECT_MODE) {
            setState(STATE_SELECT_ALBUM);
            return;
        }
        super.onBackPressed();
    }
}
