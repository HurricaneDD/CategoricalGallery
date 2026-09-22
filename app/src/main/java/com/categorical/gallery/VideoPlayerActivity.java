package com.categorical.gallery;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.categorical.gallery.util.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity providing simple, responsive video playback preview with scrub controls,
 * rewind/fast-forward, auto-hiding overlay, share, and info dialog.
 */
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    public static void start(Context context, String videoPath, String title) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO_PATH, videoPath);
        intent.putExtra(EXTRA_VIDEO_TITLE, title);
        context.startActivity(intent);
    }

    private String videoPath;
    private String videoTitle;
    private File videoFile;

    private VideoView videoView;
    private ProgressBar pbLoading;
    private View layoutError;
    private View layoutControls;
    private View topBar;
    private View bottomBar;
    private View layoutCenterControls;

    private TextView tvTitle;
    private ImageButton btnBack;
    private ImageButton btnInfo;
    private ImageButton btnShare;

    private ImageButton btnCenterPlayPause;
    private ImageButton btnBottomPlayPause;
    private ImageButton btnRewind10;
    private ImageButton btnForward10;

    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isTrackingSeekBar = false;
    private boolean isControlsVisible = true;
    private int savedPosition = 0;
    private boolean wasPlayingBeforePause = false;

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && !isTrackingSeekBar && videoView.isPlaying()) {
                int current = videoView.getCurrentPosition();
                int total = videoView.getDuration();
                if (total > 0) {
                    seekBar.setProgress((int) (((long) current * 1000) / total));
                    tvCurrentTime.setText(FileUtils.formatDuration(current));
                }
            }
            handler.postDelayed(this, 300);
        }
    };

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isPlaying()) {
                hideControls();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        if (videoPath == null) {
            Toast.makeText(this, R.string.video_error_cannot_play, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Toast.makeText(this, R.string.video_error_cannot_play, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (videoTitle == null || videoTitle.isEmpty()) {
            videoTitle = videoFile.getName();
        }

        initViews();
        setupVideoView();
    }

    private void initViews() {
        videoView = findViewById(R.id.video_view);
        pbLoading = findViewById(R.id.pb_loading);
        layoutError = findViewById(R.id.layout_error);
        layoutControls = findViewById(R.id.layout_controls);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        layoutCenterControls = findViewById(R.id.layout_center_controls);

        tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(videoTitle);

        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        btnInfo = findViewById(R.id.btn_info);
        btnInfo.setOnClickListener(v -> showVideoDetailsDialog());

        btnShare = findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> shareVideo());

        btnCenterPlayPause = findViewById(R.id.btn_center_play_pause);
        btnBottomPlayPause = findViewById(R.id.btn_bottom_play_pause);
        btnRewind10 = findViewById(R.id.btn_rewind_10);
        btnForward10 = findViewById(R.id.btn_forward_10);

        btnCenterPlayPause.setOnClickListener(v -> togglePlayPause());
        btnBottomPlayPause.setOnClickListener(v -> togglePlayPause());

        btnRewind10.setOnClickListener(v -> rewind10Seconds());
        btnForward10.setOnClickListener(v -> forward10Seconds());

        Button btnRetry = findViewById(R.id.btn_retry);
        btnRetry.setOnClickListener(v -> setupVideoView());

        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekBar = findViewById(R.id.seek_bar);

        // Tap background to toggle controls
        View container = findViewById(R.id.player_container);
        container.setOnClickListener(v -> toggleControlsVisibility());
        layoutControls.setOnClickListener(v -> toggleControlsVisibility());

        setupSeekBar();
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoView != null) {
                    int duration = videoView.getDuration();
                    if (duration > 0) {
                        int newPos = (int) (((long) progress * duration) / 1000);
                        tvCurrentTime.setText(FileUtils.formatDuration(newPos));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTrackingSeekBar = true;
                cancelHideControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTrackingSeekBar = false;
                if (videoView != null) {
                    int duration = videoView.getDuration();
                    if (duration > 0) {
                        int newPos = (int) (((long) seekBar.getProgress() * duration) / 1000);
                        videoView.seekTo(newPos);
                    }
                }
                resetHideControlsTimer();
            }
        });
    }

    private void setupVideoView() {
        pbLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutCenterControls.setVisibility(View.GONE);

        videoView.setVideoPath(videoFile.getAbsolutePath());

        videoView.setOnPreparedListener(mp -> {
            pbLoading.setVisibility(View.GONE);
            layoutCenterControls.setVisibility(View.VISIBLE);
            int duration = videoView.getDuration();
            tvTotalTime.setText(FileUtils.formatDuration(duration));

            if (savedPosition > 0) {
                videoView.seekTo(savedPosition);
            }
            videoView.start();
            updatePlayPauseIcons(true);
            resetHideControlsTimer();
        });

        videoView.setOnCompletionListener(mp -> {
            updatePlayPauseIcons(false);
            btnCenterPlayPause.setImageResource(R.drawable.ic_replay);
            showControls();
            cancelHideControls();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            pbLoading.setVisibility(View.GONE);
            layoutError.setVisibility(View.VISIBLE);
            layoutControls.setVisibility(View.GONE);
            return true;
        });
    }

    private void togglePlayPause() {
        if (videoView == null) return;
        if (videoView.isPlaying()) {
            videoView.pause();
            updatePlayPauseIcons(false);
            cancelHideControls();
        } else {
            // If completed and at end, replay from beginning
            if (videoView.getCurrentPosition() >= videoView.getDuration() - 200) {
                videoView.seekTo(0);
            }
            videoView.start();
            updatePlayPauseIcons(true);
            resetHideControlsTimer();
        }
    }

    private void rewind10Seconds() {
        if (videoView == null) return;
        int current = videoView.getCurrentPosition();
        int newPos = Math.max(0, current - 10000);
        videoView.seekTo(newPos);
        tvCurrentTime.setText(FileUtils.formatDuration(newPos));
        resetHideControlsTimer();
    }

    private void forward10Seconds() {
        if (videoView == null) return;
        int current = videoView.getCurrentPosition();
        int duration = videoView.getDuration();
        int newPos = Math.min(duration, current + 10000);
        videoView.seekTo(newPos);
        tvCurrentTime.setText(FileUtils.formatDuration(newPos));
        resetHideControlsTimer();
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        if (isPlaying) {
            btnCenterPlayPause.setImageResource(R.drawable.ic_pause);
            btnBottomPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnCenterPlayPause.setImageResource(R.drawable.ic_play_arrow);
            btnBottomPlayPause.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    private void toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        isControlsVisible = true;
        layoutControls.animate().alpha(1f).setDuration(200).withStartAction(() -> {
            layoutControls.setVisibility(View.VISIBLE);
        }).start();
        resetHideControlsTimer();
    }

    private void hideControls() {
        isControlsVisible = false;
        layoutControls.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            layoutControls.setVisibility(View.GONE);
        }).start();
        cancelHideControls();
    }

    private void resetHideControlsTimer() {
        cancelHideControls();
        if (videoView != null && videoView.isPlaying()) {
            handler.postDelayed(hideControlsRunnable, 3500);
        }
    }

    private void cancelHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
    }

    private void showVideoDetailsDialog() {
        cancelHideControls();
        long durationMs = videoView != null ? videoView.getDuration() : 0;
        int width = 0;
        int height = 0;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (wStr != null) width = Integer.parseInt(wStr);
            if (hStr != null) height = Integer.parseInt(hStr);
            if (durationMs <= 0) {
                String dStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (dStr != null) durationMs = Long.parseLong(dStr);
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }

        String resolution = (width > 0 && height > 0) ? (width + " x " + height) : "未知";
        String durationStr = FileUtils.formatDuration(durationMs);
        String sizeStr = Formatter.formatFileSize(this, videoFile.length());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String modifiedStr = sdf.format(new Date(videoFile.lastModified()));

        String message = getString(R.string.detail_filename, videoFile.getName()) + "\n"
                + getString(R.string.video_duration_format, durationStr) + "\n"
                + "分辨率：" + resolution + "\n"
                + getString(R.string.detail_size, sizeStr) + "\n"
                + getString(R.string.detail_modified_time, modifiedStr) + "\n"
                + getString(R.string.detail_directory, videoFile.getAbsolutePath());

        new AlertDialog.Builder(this)
                .setTitle(R.string.video_info_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                    resetHideControlsTimer();
                })
                .show();
    }

    private void shareVideo() {
        try {
            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", videoFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("video/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_video)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_failed_toast, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateProgressRunnable);
        if (wasPlayingBeforePause && videoView != null) {
            videoView.seekTo(savedPosition);
            videoView.start();
            updatePlayPauseIcons(true);
            resetHideControlsTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateProgressRunnable);
        cancelHideControls();
        if (videoView != null) {
            wasPlayingBeforePause = videoView.isPlaying();
            savedPosition = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}
