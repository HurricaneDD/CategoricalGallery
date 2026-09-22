package com.categorical.gallery.util;

import android.content.Context;
import android.util.Log;

import com.categorical.gallery.model.Album;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.model.Workspace;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import com.jcraft.jsch.SftpProgressMonitor;

public class SftpManager {
    private static final String TAG = "SftpManager";
    private static final int TIMEOUT_MS = 15000;

    private static class SimpleUserInfo implements UserInfo, UIKeyboardInteractive {
        private final String password;

        SimpleUserInfo(String password) {
            this.password = password != null ? password : "";
        }

        @Override
        public String getPassphrase() { return null; }

        @Override
        public String getPassword() { return password; }

        @Override
        public boolean promptPassword(String message) { return true; }

        @Override
        public boolean promptPassphrase(String message) { return false; }

        @Override
        public boolean promptYesNo(String message) { return true; }

        @Override
        public void showMessage(String message) {}

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction,
                                                 String[] prompt, boolean[] echo) {
            if (prompt == null || prompt.length == 0) {
                return new String[0];
            }
            String[] response = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) {
                response[i] = password;
            }
            return response;
        }
    }

    public static class RemoteDirItem {
        public final String name;
        public final String fullPath;
        public final boolean isParent;

        public RemoteDirItem(String name, String fullPath, boolean isParent) {
            this.name = name;
            this.fullPath = fullPath;
            this.isParent = isParent;
        }
    }

    public static class SyncResult {
        public boolean success;
        public int uploadedCount;
        public int downloadedCount;
        public String errorMessage;

        public SyncResult(boolean success, int uploaded, int downloaded, String error) {
            this.success = success;
            this.uploadedCount = uploaded;
            this.downloadedCount = downloaded;
            this.errorMessage = error;
        }
    }

    public interface SyncProgressListener {
        void onProgress(int percent, long remainingSeconds, String statusText);
    }

    public static String formatRemainingTime(long seconds) {
        if (seconds <= 0) {
            return "即将完成";
        }
        if (seconds < 60) {
            return "预计剩余 " + seconds + " 秒";
        }
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes < 60) {
            return "预计剩余 " + minutes + " 分" + (remSec > 0 ? " " + remSec + " 秒" : "");
        }
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return "预计剩余 " + hours + " 小时 " + remMin + " 分";
    }

    private static ChannelSftp createSftpChannel(String host, int port, String user, String pass)
            throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(pass);
        session.setUserInfo(new SimpleUserInfo(pass));
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        config.put("compression.s2c", "none");
        config.put("compression.c2s", "none");
        session.setConfig(config);
        session.setServerAliveInterval(5000);
        session.setServerAliveCountMax(3);
        session.setTimeout(TIMEOUT_MS);
        session.connect(TIMEOUT_MS);

        Channel channel = session.openChannel("sftp");
        channel.connect(TIMEOUT_MS);
        ChannelSftp channelSftp = (ChannelSftp) channel;
        try {
            channelSftp.setFilenameEncoding("UTF-8");
        } catch (SftpException ignored) {
        }
        return channelSftp;
    }

    private static void disconnect(ChannelSftp sftp) {
        if (sftp != null) {
            try {
                Session session = sftp.getSession();
                sftp.disconnect();
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Test connection to SFTP server.
     */
    public static boolean testConnection(String host, int port, String user, String pass) throws Exception {
        ChannelSftp sftp = null;
        try {
            sftp = createSftpChannel(host, port, user, pass);
            return sftp.isConnected();
        } finally {
            disconnect(sftp);
        }
    }

    /**
     * List remote directories in a given path.
     */
    public static List<RemoteDirItem> listDirectories(String host, int port, String user, String pass, String currentPath)
            throws Exception {
        ChannelSftp sftp = null;
        List<RemoteDirItem> items = new ArrayList<>();
        try {
            sftp = createSftpChannel(host, port, user, pass);
            if (currentPath == null || currentPath.trim().isEmpty()) {
                currentPath = sftp.pwd();
            }
            if (currentPath == null || currentPath.isEmpty()) {
                currentPath = "/";
            }
            sftp.cd(currentPath);
            String pwd = sftp.pwd();

            // Add parent directory item if not root
            if (!"/".equals(pwd) && !pwd.isEmpty()) {
                String parentPath = getParentPath(pwd);
                items.add(new RemoteDirItem(".. (返回上一级)", parentPath, true));
            }

            Vector<?> vector = sftp.ls(pwd);
            List<RemoteDirItem> dirItems = new ArrayList<>();
            for (Object obj : vector) {
                if (obj instanceof ChannelSftp.LsEntry) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) obj;
                    String filename = entry.getFilename();
                    if (".".equals(filename) || "..".equals(filename)) {
                        continue;
                    }
                    if (entry.getAttrs().isDir()) {
                        String fullPath = pwd.endsWith("/") ? pwd + filename : pwd + "/" + filename;
                        dirItems.add(new RemoteDirItem(filename, fullPath, false));
                    }
                }
            }

            // Sort directories alphabetically
            Collections.sort(dirItems, (a, b) -> a.name.compareToIgnoreCase(b.name));
            items.addAll(dirItems);
            return items;
        } finally {
            disconnect(sftp);
        }
    }

    public static String getDefaultHomePath(String host, int port, String user, String pass) throws Exception {
        ChannelSftp sftp = null;
        try {
            sftp = createSftpChannel(host, port, user, pass);
            return sftp.pwd();
        } finally {
            disconnect(sftp);
        }
    }

    public static String getParentPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return "/";
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Create a new directory on the remote SFTP server.
     */
    public static boolean createDirectory(String host, int port, String user, String pass, String parentPath, String dirName)
            throws Exception {
        ChannelSftp sftp = null;
        try {
            sftp = createSftpChannel(host, port, user, pass);
            String fullPath = parentPath.endsWith("/") ? parentPath + dirName : parentPath + "/" + dirName;
            makeRemoteDirs(sftp, fullPath);
            return true;
        } finally {
            disconnect(sftp);
        }
    }

    public static void makeRemoteDirs(ChannelSftp sftp, String path) throws SftpException {
        if (path == null || path.trim().isEmpty()) return;
        path = path.trim();
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        String[] parts = path.split("/");
        StringBuilder current = new StringBuilder();
        if (path.startsWith("/")) {
            current.append("/");
        }
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current.length() > 0 && current.charAt(current.length() - 1) != '/') {
                current.append("/");
            }
            current.append(part);
            String checkPath = current.toString();
            try {
                sftp.stat(checkPath);
            } catch (SftpException e) {
                try {
                    sftp.mkdir(checkPath);
                } catch (SftpException ex) {
                    try {
                        sftp.stat(checkPath);
                    } catch (SftpException statEx) {
                        Log.w(TAG, "makeRemoteDirs stat/mkdir failed for " + checkPath, ex);
                    }
                }
            }
        }
    }

    private static class SyncTask {
        final boolean isUpload;
        final File localFile;
        final String remotePath;
        final long sizeBytes;

        SyncTask(boolean isUpload, File localFile, String remotePath, long sizeBytes) {
            this.isUpload = isUpload;
            this.localFile = localFile;
            this.remotePath = remotePath;
            this.sizeBytes = sizeBytes;
        }
    }

    private static volatile long sLastReportTime = 0;

    private static void reportSyncProgress(SyncProgressListener listener, long transferredBytes, long totalBytes, long startTime) {
        if (listener == null) return;
        long now = System.currentTimeMillis();
        if (now - sLastReportTime < 250 && transferredBytes < totalBytes) {
            return;
        }
        sLastReportTime = now;

        int percent = totalBytes > 0 ? (int) Math.min(99, (transferredBytes * 100) / totalBytes) : 0;
        long elapsedMs = now - startTime;
        long remainingSec = -1;
        String etaStr = "计算中...";
        if (elapsedMs > 400 && transferredBytes > 0) {
            double bytesPerSec = (double) transferredBytes / (elapsedMs / 1000.0);
            long remainingBytes = Math.max(0, totalBytes - transferredBytes);
            remainingSec = bytesPerSec > 0 ? (long) (remainingBytes / bytesPerSec) : 0;
            etaStr = formatRemainingTime(remainingSec);
        }

        String status = "正在同步在线相册 (" + percent + "%) · " + etaStr;
        listener.onProgress(percent, remainingSec, status);
    }

    /**
     * Synchronize local gallery and online gallery with remote SFTP server.
     */
    public static SyncResult sync(Context context, SyncProgressListener listener) {
        String host = SftpConfig.getHost(context);
        int port = SftpConfig.getPort(context);
        String user = SftpConfig.getUsername(context);
        String pass = SftpConfig.getPassword(context);
        String remoteRoot = SftpConfig.getRemoteRootDir(context);

        if (host.isEmpty() || user.isEmpty()) {
            return new SyncResult(false, 0, 0, "SFTP服务器未配置");
        }

        ChannelSftp sftp = null;
        int uploaded = 0;
        int downloaded = 0;

        try {
            if (listener != null) listener.onProgress(0, -1, "正在连接SFTP服务器...");
            sftp = createSftpChannel(host, port, user, pass);

            String baseDir = remoteRoot;
            if (baseDir == null || baseDir.trim().isEmpty() || ".".equals(baseDir.trim())) {
                try {
                    baseDir = sftp.pwd();
                } catch (Exception e) {
                    baseDir = "/";
                }
            }
            if (baseDir == null || baseDir.trim().isEmpty()) {
                baseDir = "/";
            }
            baseDir = baseDir.trim();
            while (baseDir.endsWith("/") && baseDir.length() > 1) {
                baseDir = baseDir.substring(0, baseDir.length() - 1);
            }

            // 1. Ensure remote root directory exists
            if (!"/".equals(baseDir)) {
                makeRemoteDirs(sftp, baseDir);
            }

            // Determine remote workspaces path
            String remoteWorkspacesPath;
            if (baseDir.endsWith("/workspaces") || "workspaces".equals(baseDir)) {
                remoteWorkspacesPath = baseDir;
            } else {
                String checkSubWorkspaces = "/".equals(baseDir) ? "/workspaces" : baseDir + "/workspaces";
                boolean subWorkspacesExists = false;
                try {
                    SftpATTRS attrs = sftp.stat(checkSubWorkspaces);
                    if (attrs != null && attrs.isDir()) {
                        subWorkspacesExists = true;
                    }
                } catch (Exception ignored) {
                }

                if (subWorkspacesExists) {
                    remoteWorkspacesPath = checkSubWorkspaces;
                } else {
                    boolean baseDirHasOtherDirs = false;
                    try {
                        Vector<?> baseEntries = sftp.ls(baseDir);
                        if (baseEntries != null) {
                            for (Object o : baseEntries) {
                                if (o instanceof ChannelSftp.LsEntry) {
                                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
                                    String fname = entry.getFilename();
                                    if (!".".equals(fname) && !"..".equals(fname) && entry.getAttrs().isDir()) {
                                        baseDirHasOtherDirs = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    if (baseDirHasOtherDirs) {
                        remoteWorkspacesPath = baseDir;
                    } else {
                        remoteWorkspacesPath = checkSubWorkspaces;
                    }
                }
            }
            makeRemoteDirs(sftp, remoteWorkspacesPath);

            if (listener != null) listener.onProgress(0, -1, "正在扫描在线相册并计算进度...");

            // 2. Scan and pre-calculate sync tasks for ONLY online workspaces (FileUtils.getOnlineWorkspacesDir())
            File onlineWorkspacesDir = FileUtils.getOnlineWorkspacesDir();
            if (!onlineWorkspacesDir.exists()) {
                onlineWorkspacesDir.mkdirs();
            }

            List<SyncTask> taskList = new ArrayList<>();
            Set<String> processedWorkspaces = new HashSet<>();

            File[] wsList = onlineWorkspacesDir.listFiles(File::isDirectory);
            if (wsList != null) {
                for (File wsDir : wsList) {
                    String wsName = wsDir.getName();
                    if (wsName.startsWith(".")) continue;
                    processedWorkspaces.add(wsName);
                    String remoteWsDir = remoteWorkspacesPath + "/" + wsName;
                    makeRemoteDirs(sftp, remoteWsDir);

                    Map<String, Boolean> remoteAlbums = new HashMap<>();
                    try {
                        Vector<?> rAlbums = sftp.ls(remoteWsDir);
                        if (rAlbums != null) {
                            for (Object o : rAlbums) {
                                if (o instanceof ChannelSftp.LsEntry) {
                                    ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) o;
                                    if (e.getAttrs().isDir() && !".".equals(e.getFilename()) && !"..".equals(e.getFilename())) {
                                        remoteAlbums.put(e.getFilename(), true);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    File[] albumList = wsDir.listFiles(File::isDirectory);
                    Set<String> processedAlbums = new HashSet<>();
                    if (albumList != null) {
                        for (File albumDir : albumList) {
                            String albumName = albumDir.getName();
                            if (albumName.startsWith(".")) continue;
                            processedAlbums.add(albumName);
                            String remoteAlbumDir = remoteWsDir + "/" + albumName;
                            makeRemoteDirs(sftp, remoteAlbumDir);

                            Map<String, Long> remotePhotoMap = new HashMap<>();
                            try {
                                Vector<?> rPhotos = sftp.ls(remoteAlbumDir);
                                if (rPhotos != null) {
                                    for (Object o : rPhotos) {
                                        if (o instanceof ChannelSftp.LsEntry) {
                                            ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) o;
                                            if (!e.getAttrs().isDir() && !".".equals(e.getFilename()) && !"..".equals(e.getFilename())) {
                                                remotePhotoMap.put(e.getFilename(), e.getAttrs().getSize());
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}

                            File[] photos = albumDir.listFiles(f -> f.isFile() && !f.getName().startsWith(".") && FileUtils.isImageFile(f));
                            if (photos != null) {
                                for (File photoFile : photos) {
                                    Long remoteSize = remotePhotoMap.get(photoFile.getName());
                                    if (remoteSize == null || remoteSize != photoFile.length()) {
                                        taskList.add(new SyncTask(true, photoFile, remoteAlbumDir + "/" + photoFile.getName(), photoFile.length()));
                                    }
                                }
                            }

                            for (Map.Entry<String, Long> remotePhoto : remotePhotoMap.entrySet()) {
                                String pName = remotePhoto.getKey();
                                if (!FileUtils.isImageFile(pName)) continue;
                                File localPhoto = new File(albumDir, pName);
                                if (!localPhoto.exists() || localPhoto.length() != remotePhoto.getValue()) {
                                    taskList.add(new SyncTask(false, localPhoto, remoteAlbumDir + "/" + pName, remotePhoto.getValue()));
                                }
                            }
                        }
                    }

                    for (String rAlbumName : remoteAlbums.keySet()) {
                        if (!processedAlbums.contains(rAlbumName)) {
                            File localAlbumDir = new File(wsDir, rAlbumName);
                            localAlbumDir.mkdirs();
                            String remoteAlbumDir = remoteWsDir + "/" + rAlbumName;
                            try {
                                Vector<?> rPhotos = sftp.ls(remoteAlbumDir);
                                if (rPhotos != null) {
                                    for (Object o : rPhotos) {
                                        if (o instanceof ChannelSftp.LsEntry) {
                                            ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) o;
                                            if (!e.getAttrs().isDir() && FileUtils.isImageFile(e.getFilename())) {
                                                File targetFile = new File(localAlbumDir, e.getFilename());
                                                taskList.add(new SyncTask(false, targetFile, remoteAlbumDir + "/" + e.getFilename(), e.getAttrs().getSize()));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            // 3. Scan remote workspaces not yet present locally
            try {
                Vector<?> rWsList = sftp.ls(remoteWorkspacesPath);
                if (rWsList != null) {
                    for (Object o : rWsList) {
                        if (o instanceof ChannelSftp.LsEntry) {
                            ChannelSftp.LsEntry wsEntry = (ChannelSftp.LsEntry) o;
                            String wsName = wsEntry.getFilename();
                            if (wsEntry.getAttrs().isDir() && !".".equals(wsName) && !"..".equals(wsName)) {
                                if (!processedWorkspaces.contains(wsName)) {
                                    File localWsDir = new File(onlineWorkspacesDir, wsName);
                                    localWsDir.mkdirs();
                                    String remoteWsDir = remoteWorkspacesPath + "/" + wsName;
                                    Vector<?> rAlbums = null;
                                    try {
                                        rAlbums = sftp.ls(remoteWsDir);
                                    } catch (Exception ignored) {}
                                    if (rAlbums != null) {
                                        for (Object aObj : rAlbums) {
                                            if (aObj instanceof ChannelSftp.LsEntry) {
                                                ChannelSftp.LsEntry aEntry = (ChannelSftp.LsEntry) aObj;
                                                String aName = aEntry.getFilename();
                                                if (aEntry.getAttrs().isDir() && !".".equals(aName) && !"..".equals(aName)) {
                                                    File localAlbumDir = new File(localWsDir, aName);
                                                    localAlbumDir.mkdirs();
                                                    String remoteAlbumDir = remoteWsDir + "/" + aName;
                                                    Vector<?> rPhotos = null;
                                                    try {
                                                        rPhotos = sftp.ls(remoteAlbumDir);
                                                    } catch (Exception ignored) {}
                                                    if (rPhotos != null) {
                                                        for (Object pObj : rPhotos) {
                                                            if (pObj instanceof ChannelSftp.LsEntry) {
                                                                ChannelSftp.LsEntry pEntry = (ChannelSftp.LsEntry) pObj;
                                                                if (!pEntry.getAttrs().isDir() && FileUtils.isImageFile(pEntry.getFilename())) {
                                                                    File localPhoto = new File(localAlbumDir, pEntry.getFilename());
                                                                    taskList.add(new SyncTask(false, localPhoto, remoteAlbumDir + "/" + pEntry.getFilename(), pEntry.getAttrs().getSize()));
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            long totalBytes = 0;
            for (SyncTask t : taskList) {
                totalBytes += Math.max(1, t.sizeBytes);
            }

            if (taskList.isEmpty()) {
                SftpConfig.setOnlineStatus(context, true);
                SftpConfig.setLastSyncTime(context, System.currentTimeMillis());
                if (listener != null) listener.onProgress(100, 0, "在线相册已是最新状态");
                return new SyncResult(true, 0, 0, null);
            }

            // 4. Perform high-speed transfers with 128KB buffering and ETA tracking
            final long finalTotalBytes = totalBytes;
            final long[] bytesTransferred = new long[]{0};
            final long startTime = System.currentTimeMillis();

            reportSyncProgress(listener, 0, finalTotalBytes, startTime);

            for (SyncTask task : taskList) {
                if (task.isUpload) {
                    String remoteDir = task.remotePath.substring(0, task.remotePath.lastIndexOf('/'));
                    makeRemoteDirs(sftp, remoteDir);

                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(task.localFile), 128 * 1024)) {
                        sftp.put(bis, task.remotePath, new SftpProgressMonitor() {
                            @Override
                            public void init(int op, String src, String dest, long max) {}

                            @Override
                            public boolean count(long count) {
                                bytesTransferred[0] += count;
                                reportSyncProgress(listener, bytesTransferred[0], finalTotalBytes, startTime);
                                return true;
                            }

                            @Override
                            public void end() {}
                        }, ChannelSftp.OVERWRITE);
                        uploaded++;
                    }
                } else {
                    File parentDir = task.localFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(task.localFile), 128 * 1024)) {
                        sftp.get(task.remotePath, bos, new SftpProgressMonitor() {
                            @Override
                            public void init(int op, String src, String dest, long max) {}

                            @Override
                            public boolean count(long count) {
                                bytesTransferred[0] += count;
                                reportSyncProgress(listener, bytesTransferred[0], finalTotalBytes, startTime);
                                return true;
                            }

                            @Override
                            public void end() {}
                        });
                        downloaded++;
                    }
                }
            }

            // Update success status and timestamp
            SftpConfig.setOnlineStatus(context, true);
            SftpConfig.setLastSyncTime(context, System.currentTimeMillis());

            if (listener != null) listener.onProgress(100, 0, "同步完成");
            return new SyncResult(true, uploaded, downloaded, null);

        } catch (Exception e) {
            Log.e(TAG, "SFTP Sync error", e);
            SftpConfig.setOnlineStatus(context, false);
            String err = e.getMessage();
            if (err == null || err.trim().isEmpty()) {
                err = e.getClass().getSimpleName();
            }
            return new SyncResult(false, uploaded, downloaded, err);
        } finally {
            disconnect(sftp);
        }
    }
}
