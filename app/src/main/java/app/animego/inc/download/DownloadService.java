package app.animego.inc.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.RemoteViews;

import app.animego.inc.MainActivity;
import app.animego.inc.R;
import app.animego.inc.db.DatabaseHelper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public class DownloadService extends Service {
    private static final String CHANNEL_ID = "animego_downloads";
    private static final int NOTIFICATION_ID = 4207;
    private static final String ACTION_QUEUE = "app.animego.inc.DOWNLOAD";
    private static final String PREFS = "animego_downloads";
    private static final String PREF_ACTIVE = "service_enabled";

    private final Object workerLock = new Object();
    private volatile boolean workerRunning;
    private DatabaseHelper db;
    private PowerManager.WakeLock wakeLock;

    public static void enqueue(Context context, long id) {
        if (id <= 0) return;
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ACTIVE, true).commit();
        Intent intent = new Intent(app, DownloadService.class);
        intent.setAction(ACTION_QUEUE);
        intent.putExtra("id", id);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
        else app.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = new DatabaseHelper(this);
        createChannel();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":download");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}
        startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan unduhan...", 0, false, "AnimeGo", "", 0, 0));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startWorker();
        return START_STICKY;
    }

    private void startWorker() {
        synchronized (workerLock) {
            if (workerRunning) return;
            workerRunning = true;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    while (true) {
                        JSONObject row = db.getNextPendingDownload();
                        if (row == null) break;
                        long id = row.optLong("id", -1);
                        if (id <= 0) break;
                        download(id);
                    }
                } finally {
                    synchronized (workerLock) { workerRunning = false; }
                    boolean pending = db.hasPendingDownloads();
                    if (!pending) {
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ACTIVE, false).commit();
                        if (!db.hasFailedDownloads()) showNotification("Semua unduhan selesai", 100, true, "AnimeGo", "", 0, 0);
                        stopForeground(false);
                        stopSelf();
                    }
                }
            }
        }, "animego-download-worker").start();
    }

    private void download(long id) {
        HttpURLConnection conn = null;
        FileOutputStream out = null;
        InputStream in = null;
        File part = null;
        try {
            JSONObject row = db.getDownload(id);
            if (row == null) return;
            String url = row.optString("url");
            String path = row.optString("path");
            File target = new File(path);
            File parent = target.getParentFile();
            if (parent == null) throw new Exception("Folder unduhan tidak valid");
            if (!parent.exists() && !parent.mkdirs()) throw new Exception("Tidak dapat membuat folder unduhan");
            part = new File(path + ".part");

            if (target.isFile() && target.length() > 0) {
                db.updateDownload(id, "completed", target.length(), target.length(), "");
                showNotification("Unduhan selesai", 100, true, row.optString("title"), row.optString("ep"), target.length(), target.length());
                return;
            }

            long existing = part.isFile() ? part.length() : 0;
            db.updateDownload(id, "downloading", existing, 0, "");
            showNotification("Mengunduh", 0, false, row.optString("title"), row.optString("ep"), existing, 0);

            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(45000);
            conn.setInstanceFollowRedirects(true);
            conn.setUseCaches(false);
            JSONObject headers = parseHeaders(row.optString("headers"));
            applyHeaders(conn, headers);
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");

            int code = conn.getResponseCode();
            boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
                append = false;
                existing = 0;
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw new Exception("HTTP " + code);

            long contentLength = parseLong(conn.getHeaderField("Content-Length"));
            long total = append ? parseTotalFromContentRange(conn.getHeaderField("Content-Range"), contentLength + existing) : contentLength;
            long done = append ? existing : 0;

            in = new BufferedInputStream(conn.getInputStream());
            out = new FileOutputStream(part, append);
            byte[] buffer = new byte[32768];
            long lastUpdate = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (isCancelled(id)) throw new DownloadCancelledException();
                out.write(buffer, 0, read);
                done += read;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 700) {
                    lastUpdate = now;
                    db.updateDownload(id, "downloading", done, total, "");
                    int percent = total > 0 ? (int) Math.min(100, (done * 100L) / total) : 0;
                    showNotification("Mengunduh", percent, false, row.optString("title"), row.optString("ep"), done, total);
                }
            }
            out.flush();
            out.close();
            out = null;
            if (!part.isFile() || part.length() <= 0) throw new Exception("File unduhan kosong");
            if (target.exists() && !target.delete()) throw new Exception("File tujuan tidak dapat diganti");
            if (!part.renameTo(target)) {
                copyFile(part, target);
                if (!part.delete()) part.deleteOnExit();
            }
            long finalTotal = total > 0 ? total : done;
            db.updateDownload(id, "completed", done, finalTotal, "");
            showNotification("Unduhan selesai", 100, true, row.optString("title"), row.optString("ep"), done, finalTotal);
        } catch (DownloadCancelledException e) {
            try { db.updateDownload(id, "cancelled", 0, 0, ""); } catch (Exception ignored) {}
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().length() == 0 ? "Unduhan gagal" : e.getMessage();
            try { db.updateDownload(id, "failed", 0, 0, message); } catch (Exception ignored) {}
            showNotification("Unduhan gagal", 0, true, "AnimeGo", "", 0, 0, message);
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private JSONObject parseHeaders(String raw) {
        try { return raw == null || raw.length() == 0 ? new JSONObject() : new JSONObject(raw); }
        catch (Exception e) { return new JSONObject(); }
    }

    private void applyHeaders(HttpURLConnection conn, JSONObject headers) {
        try {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.optString(key, "");
                if (value.length() > 0) conn.setRequestProperty(key, value);
            }
        } catch (Exception ignored) {}
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Cache-Control", "no-cache");
    }

    private long parseTotalFromContentRange(String range, long fallback) {
        try {
            if (range == null) return fallback;
            int slash = range.lastIndexOf('/');
            if (slash >= 0) return Long.parseLong(range.substring(slash + 1));
        } catch (Exception ignored) {}
        return fallback;
    }

    private void copyFile(File source, File target) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target, false);
        byte[] buffer = new byte[32768];
        int n;
        try {
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isCancelled(long id) {
        try {
            JSONObject row = db.getDownload(id);
            return row == null || "cancelled".equals(row.optString("status"));
        } catch (Exception e) {
            return false;
        }
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0; }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Unduhan AnimeGo", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Status unduhan video AnimeGo");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, int percent, boolean done, String title, String ep, long progress, long total) {
        return buildNotification(text, percent, done, title, ep, progress, total, "");
    }

    private Notification buildNotification(String text, int percent, boolean done, String title, String ep, long progress, long total, String error) {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("open_route", "offline");
        PendingIntent pi = PendingIntent.getActivity(this, 4207, open, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_download);
        String safeTitle = title == null || title.length() == 0 ? "AnimeGo" : title;
        String line = ep == null || ep.length() == 0 ? text : text + " · Episode " + ep;
        if (!done && error != null && error.length() > 0) line += " · " + error;
        else if (!done) line += formatProgress(progress, total, percent);
        else if (progress > 0) line += " · " + formatSize(progress);
        views.setTextViewText(R.id.notificationTitle, done ? text + ": " + safeTitle : "Mengunduh: " + safeTitle);
        views.setTextViewText(R.id.notificationText, line);
        views.setProgressBar(R.id.notificationProgress, 100, Math.max(0, Math.min(100, percent)), !done && total <= 0);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(done ? text + ": " + safeTitle : "Mengunduh: " + safeTitle)
                .setContentText(line)
                .setContentIntent(pi)
                .setCustomContentView(views)
                .setOngoing(!done)
                .setAutoCancel(done)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 24) b.setCustomBigContentView(views);
        return b.build();
    }

    private String formatProgress(long progress, long total, int percent) {
        if (total > 0) return " · " + percent + "% · " + formatSize(progress) + " / " + formatSize(total);
        return " · " + formatSize(progress);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = new String[]{"KB", "MB", "GB", "TB"};
        int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024 && unit < units.length - 1);
        if (value >= 100) return String.format(java.util.Locale.US, "%.0f %s", value, units[unit]);
        if (value >= 10) return String.format(java.util.Locale.US, "%.1f %s", value, units[unit]);
        return String.format(java.util.Locale.US, "%.2f %s", value, units[unit]);
    }

    private void showNotification(String text, int percent, boolean done, String title, String ep, long progress, long total) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text, percent, done, title, ep, progress, total));
    }

    private void showNotification(String text, int percent, boolean done, String title, String ep, long progress, long total, String error) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text, percent, done, title, ep, progress, total, error));
    }

    public static File getRootDirectory(Context context) {
        try {
            File[] dirs = context.getExternalMediaDirs();
            if (dirs != null && dirs.length > 0 && dirs[0] != null) return dirs[0];
        } catch (Exception ignored) {}
        return new File(Environment.getExternalStorageDirectory(), "Android/media/app.animego.inc");
    }

    public static File createTargetFile(Context context, String title, int animeId, String filename) throws Exception {
        File animeDir = new File(getRootDirectory(context), sanitize(title) + File.separator + String.valueOf(animeId));
        if (!animeDir.exists() && !animeDir.mkdirs()) throw new Exception("Tidak dapat membuat folder AnimeGo");
        return new File(animeDir, sanitizeFileName(filename));
    }

    public static String fileNameFromUrl(String url, String fallback) {
        String value = "";
        try {
            String path = new URL(url).getPath();
            int slash = path.lastIndexOf('/');
            if (slash >= 0) value = path.substring(slash + 1);
        } catch (Exception ignored) {}
        value = value.replace('%', '_');
        if (value.length() == 0 || value.equals(".mp4")) value = fallback;
        if (!value.toLowerCase().endsWith(".mp4")) value += ".mp4";
        return sanitizeFileName(value);
    }

    public static String sanitize(String value) {
        String out = value == null ? "Anime" : value;
        out = out.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (out.length() == 0) out = "Anime";
        return out;
    }

    public static String sanitizeFileName(String value) {
        String out = sanitize(value);
        while (out.endsWith(".")) out = out.substring(0, out.length() - 1);
        if (out.length() == 0) out = "video";
        return out;
    }

    public static boolean isDownloaded(String path) {
        return path != null && path.length() > 0 && new File(path).isFile();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
        if (db != null) db.close();
        super.onDestroy();
    }

    private static class DownloadCancelledException extends Exception {
    }
}
