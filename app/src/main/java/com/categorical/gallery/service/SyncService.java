package com.categorical.gallery.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.categorical.gallery.MainActivity;
import com.categorical.gallery.R;
import com.categorical.gallery.util.SftpConfig;
import com.categorical.gallery.util.SftpManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground Service for SFTP synchronization.
 * Keeps app process active in the background and displays notifications with progress and ETA.
 */
public class SyncService extends Service {

    private static final String TAG = "SyncService";
    private static final String CHANNEL_ID = "sftp_sync_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_SYNC = "com.categorical.gallery.action.START_SYNC";

    public interface SyncListener {
        void onProgress(int percent, long remainingSeconds, String statusText);
        void onComplete(SftpManager.SyncResult result);
    }

    private static volatile boolean isRunning = false;
    private static volatile int currentPercent = 0;
    private static volatile long currentRemainingSeconds = -1;
    private static volatile String currentStatusText = "";
    private static SyncListener activeListener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private PendingIntent contentPendingIntent;

    public static boolean isSyncing() {
        return isRunning;
    }

    public static int getCurrentPercent() {
        return currentPercent;
    }

    public static long getCurrentRemainingSeconds() {
        return currentRemainingSeconds;
    }

    public static String getCurrentStatusText() {
        return currentStatusText;
    }

    public static void setListener(SyncListener listener) {
        activeListener = listener;
    }

    public static void startSync(Context context) {
        if (isRunning) return;
        Intent intent = new Intent(context, SyncService.class);
        intent.setAction(ACTION_START_SYNC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        contentPendingIntent = PendingIntent.getActivity(this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "云相册同步",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("在线相册SFTP同步进度与通知");
            channel.enableVibration(false);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_SYNC.equals(intent.getAction())) {
            if (!isRunning) {
                runSync();
            }
        }
        return START_NOT_STICKY;
    }

    private void runSync() {
        isRunning = true;
        currentPercent = 0;
        currentRemainingSeconds = -1;
        currentStatusText = "正在扫描相册并计算同步进度...";

        // Acquire WakeLock for up to 15 minutes to keep CPU active during transfer
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CategoricalGallery:SyncWakeLock");
                wakeLock.acquire(15 * 60 * 1000L);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire wake lock", e);
        }

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("在线相册同步")
                .setContentText(currentStatusText)
                .setSmallIcon(R.drawable.ic_sync)
                .setProgress(100, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground error", e);
        }

        executor.execute(() -> {
            SftpManager.SyncResult result = SftpManager.sync(getApplicationContext(), (percent, remainingSec, statusText) -> {
                currentPercent = percent;
                currentRemainingSeconds = remainingSec;
                currentStatusText = statusText;

                // Update system notification
                if (notificationBuilder != null && notificationManager != null) {
                    notificationBuilder.setContentText(statusText);
                    if (percent >= 0) {
                        notificationBuilder.setProgress(100, percent, false);
                    } else {
                        notificationBuilder.setProgress(100, 0, true);
                    }
                    try {
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                    } catch (Exception ignored) {
                    }
                }

                // Notify UI
                mainHandler.post(() -> {
                    if (activeListener != null) {
                        activeListener.onProgress(percent, remainingSec, statusText);
                    }
                });
            });

            // Completed
            isRunning = false;
            if (result.success) {
                SftpConfig.setLastSyncTime(getApplicationContext(), System.currentTimeMillis());
            }

            mainHandler.post(() -> {
                if (activeListener != null) {
                    activeListener.onComplete(result);
                }
            });

            // Final notification
            if (notificationManager != null) {
                NotificationCompat.Builder finalNotif = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_sync)
                        .setContentIntent(contentPendingIntent)
                        .setAutoCancel(true)
                        .setOngoing(false);

                if (result.success) {
                    finalNotif.setContentTitle("在线相册同步完成")
                            .setContentText("已上传 " + result.uploadedCount + " 项，已下载 " + result.downloadedCount + " 项");
                } else {
                    finalNotif.setContentTitle("在线相册同步未完成")
                            .setContentText(result.errorMessage != null ? result.errorMessage : "连接异常");
                }

                try {
                    notificationManager.notify(NOTIFICATION_ID, finalNotif.build());
                } catch (Exception ignored) {
                }
            }

            // Release wakeLock
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) {
                }
            }

            stopForeground(false);
            stopSelf();
        });
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
