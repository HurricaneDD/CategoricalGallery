package com.categorical.gallery;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.MediaStoreUtils;

import java.io.File;
import java.util.List;

/**
 * Export album wizard.
 * <p>
 * Flow:
 * <ol>
 *     <li>State 1: Choose export mode (move / copy).</li>
 *     <li>State 2: Export all photos in the album to the system gallery in a
 *         background thread, updating a progress bar.</li>
 *     <li>State 3: Show success and let the user finish.</li>
 * </ol>
 * Copy mode: export every photo to the system gallery and keep the app-side file.
 * Move mode: export every photo, then delete the app-side original file.
 */
public class ExportActivity extends AppCompatActivity {

    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";
    public static final String EXTRA_ALBUM_NAME = "albumName";

    private static final int STATE_SELECT_MODE = 0;
    private static final int STATE_EXPORTING = 1;
    private static final int STATE_COMPLETE = 2;

    private Toolbar toolbar;

    private View stateSelectMode;
    private View stateExporting;
    private View stateComplete;

    private ProgressBar progressBar;
    private TextView textProgress;
    private Button btnFinishExit;

    private String workspaceName;
    private String albumName;

    private int currentState = STATE_SELECT_MODE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        albumName = getIntent().getStringExtra(EXTRA_ALBUM_NAME);
        if (workspaceName == null || workspaceName.isEmpty()) {
            workspaceName = getString(R.string.default_workspace_name);
        }
        if (albumName == null) {
            albumName = "";
        }

        initViews();
        setState(STATE_SELECT_MODE);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        stateSelectMode = findViewById(R.id.stateSelectMode);
        stateExporting = findViewById(R.id.stateExporting);
        stateComplete = findViewById(R.id.stateComplete);

        findViewById(R.id.btnMoveMode).setOnClickListener(v -> startExport(true));
        findViewById(R.id.btnCopyMode).setOnClickListener(v -> startExport(false));

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
        stateSelectMode.setVisibility(state == STATE_SELECT_MODE ? View.VISIBLE : View.GONE);
        stateExporting.setVisibility(state == STATE_EXPORTING ? View.VISIBLE : View.GONE);
        stateComplete.setVisibility(state == STATE_COMPLETE ? View.VISIBLE : View.GONE);

        switch (state) {
            case STATE_SELECT_MODE:
                toolbar.setTitle(R.string.title_select_export_mode);
                toolbar.setNavigationIcon(R.drawable.ic_back);
                break;
            case STATE_EXPORTING:
                toolbar.setTitle(R.string.exporting_please_wait);
                toolbar.setNavigationIcon(null);
                break;
            case STATE_COMPLETE:
                toolbar.setTitle(R.string.export_success);
                toolbar.setNavigationIcon(null);
                break;
        }
    }

    /**
     * Begin the export operation.
     *
     * @param move true for move mode (export + delete app-side file),
     *             false for copy only.
     */
    private void startExport(final boolean move) {
        setState(STATE_EXPORTING);
        progressBar.setProgress(0);
        textProgress.setText("0%");

        new Thread(() -> {
            final List<Photo> photos = FileUtils.getPhotos(this, workspaceName, albumName);
            final int total = photos.size();

            for (int i = 0; i < total; i++) {
                File file = photos.get(i).getFile();
                boolean ok = MediaStoreUtils.exportPhotoToSystemGallery(this, file);
                if (ok && move) {
                    FileUtils.deleteFile(file);
                }

                final int done = i + 1;
                runOnUiThread(() -> updateProgress(done, total));
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

    @Override
    public void onBackPressed() {
        // Block leaving while an export is in progress.
        if (currentState == STATE_EXPORTING) {
            return;
        }
        super.onBackPressed();
    }
}
