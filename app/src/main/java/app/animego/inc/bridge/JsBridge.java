package app.animego.inc.bridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.AsyncTask;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.view.View;
import android.view.Window;

import app.animego.inc.api.ApiService;
import app.animego.inc.db.DatabaseHelper;
import app.animego.inc.download.DownloadService;
import app.animego.inc.LandscapePlayerActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class JsBridge implements app.animego.inc.api.ApiClient.BrowserFetcher {
    public static final int REQUEST_LANDSCAPE = 3407;
    private final Context context;
    private final WebView webView;
    private final DatabaseHelper db;
    private final ApiService api;
    private final String userAgent;
    private final Map<String, BrowserResult> browserResults = new HashMap<String, BrowserResult>();

    public JsBridge(Context context, WebView webView, DatabaseHelper db, String userAgent) {
        this.context = context;
        this.webView = webView;
        this.db = db;
        this.userAgent = userAgent == null || userAgent.length() == 0 ? "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 AnimeGo/1.0" : userAgent;
        this.api = new ApiService(this.userAgent, this);
    }

    @android.webkit.JavascriptInterface
    public void call(final String action, final String payload, final String callbackId) {
        new AsyncTask<Void, Void, String>() {
            private Exception error;

            @Override
            protected String doInBackground(Void... params) {
                try {
                    JSONObject p = payload == null || payload.length() == 0 ? new JSONObject() : new JSONObject(payload);
                    return runAction(action, p).toString();
                } catch (Exception e) {
                    error = e;
                    return "";
                }
            }

            @Override
            protected void onPostExecute(String result) {
                final String message;
                if (error == null) {
                    message = "window.__nativeDone(" + JSONObject.quote(callbackId) + ",true," + JSONObject.quote(result) + ");";
                } else {
                    String text = error.getMessage() == null ? "Terjadi kesalahan" : error.getMessage();
                    message = "window.__nativeDone(" + JSONObject.quote(callbackId) + ",false," + JSONObject.quote(text) + ");";
                }
                webView.post(new Runnable() {
                    @Override public void run() { webView.evaluateJavascript(message, null); }
                });
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public String fetch(final String url) throws Exception {
        final String id = UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserResult result = new BrowserResult(latch);
        synchronized (browserResults) { browserResults.put(id, result); }

        final String safeUrl = JSONObject.quote(url);
        final String safeId = JSONObject.quote(id);
        webView.post(new Runnable() {
            @Override public void run() {
                String js = "(function(){var id=" + safeId + ";var url=" + safeUrl + ";" +
                        "fetch('https://karanime.com/',{credentials:'include',cache:'no-store'}).catch(function(){}).then(function(){return fetch(url,{credentials:'include',cache:'no-store',headers:{'Accept':'application/json'}});}).then(function(r){return r.text();}).then(function(t){window.AndroidBridge.browserDone(id,true,t);}).catch(function(e){window.AndroidBridge.browserDone(id,false,String(e&&e.message||e));});})();";
                webView.evaluateJavascript(js, null);
            }
        });

        if (!latch.await(25000L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            synchronized (browserResults) { browserResults.remove(id); }
            throw new Exception("Browser API timeout");
        }
        synchronized (browserResults) { browserResults.remove(id); }
        if (!result.ok) throw new Exception(result.error == null ? "Browser API gagal" : result.error);
        return result.body == null ? "" : result.body;
    }

    @android.webkit.JavascriptInterface
    public void browserDone(String id, boolean ok, String body) {
        BrowserResult result;
        synchronized (browserResults) { result = browserResults.get(id); }
        if (result == null) return;
        result.ok = ok;
        if (ok) result.body = body;
        else result.error = body;
        result.latch.countDown();
    }

    @android.webkit.JavascriptInterface
    public void openUrl(final String url) {
        webView.post(new Runnable() {
            @Override public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void closeApp() {
        webView.post(new Runnable() {
            @Override public void run() { ((Activity) context).finish(); }
        });
    }

    private JSONObject runAction(String action, JSONObject p) throws Exception {
        if ("home".equals(action)) return api.getHome();
        if ("list".equals(action)) return api.getList(p.optString("kind", "latest"), p.optInt("page", 1), p.optInt("limit", 12));
        if ("search".equals(action)) return api.search(p.optString("q"), p.optInt("page", 1), p.optInt("limit", 12));
        if ("suggest".equals(action)) return new JSONObject().put("items", api.suggest(p.optString("q"), p.optInt("limit", 8)));
        if ("detail".equals(action)) return api.getDetail(p.optString("key"));
        if ("genres".equals(action)) return new JSONObject().put("items", api.getGenres());
        if ("genre".equals(action)) return api.getGenre(p.optString("key"), p.optInt("page", 1), p.optInt("limit", 12));
        if ("schedule".equals(action)) return api.getSchedule(p.optString("day", "senin"), p.optInt("page", 1), p.optInt("limit", 12));
        if ("orientation".equals(action)) return new JSONObject().put("mode", p.optString("mode", "normal"));
        if ("landscape_player".equals(action)) return launchLandscapePlayer(p);
        if ("theme".equals(action)) { setThemeBars(p.optBoolean("dark", true)); return new JSONObject().put("dark", p.optBoolean("dark", true)); }

        if ("favorites".equals(action)) return new JSONObject().put("items", db.getFavorites());
        if ("favorite_add".equals(action)) { db.saveFavorite(p.optString("slug"), p.optString("title"), p.optString("cover")); return new JSONObject().put("favorite", true); }
        if ("favorite_remove".equals(action)) { db.removeFavorite(p.optString("slug")); return new JSONObject().put("favorite", false); }
        if ("favorite_state".equals(action)) return new JSONObject().put("favorite", db.isFavorite(p.optString("slug")));

        if ("history".equals(action)) return new JSONObject().put("items", db.getHistory());
        if ("history_save".equals(action)) { db.saveHistory(p.optString("slug"), p.optString("title"), p.optString("ep"), p.optString("cover"), p.optString("src"), p.optDouble("position", 0), p.optDouble("duration", 0)); return new JSONObject().put("saved", true); }
        if ("history_progress".equals(action)) { db.updateProgress(p.optString("slug"), p.optString("title"), p.optString("ep"), p.optString("cover"), p.optString("src"), p.optDouble("position", 0), p.optDouble("duration", 0)); return new JSONObject().put("saved", true); }
        if ("history_remove".equals(action)) { db.removeHistory(p.optString("slug")); return new JSONObject().put("removed", true); }
        if ("history_clear".equals(action)) { db.clearHistory(); return new JSONObject().put("cleared", true); }

        if ("downloads".equals(action)) return new JSONObject().put("items", db.getDownloads());
        if ("download".equals(action)) return queueDownload(p);
        if ("download_retry".equals(action)) return retryDownload(p.optLong("id", -1));
        if ("download_delete".equals(action)) return deleteDownload(p.optLong("id", -1));
        if ("download_delete_file".equals(action)) return deleteDownloadFile(p.optLong("id", -1));
        if ("background_vivo".equals(action)) return openBackgroundSettings("vivo");
        if ("background_battery".equals(action)) return openBackgroundSettings("battery");
        throw new IllegalArgumentException("Aksi tidak dikenal: " + action);
    }

    private JSONObject launchLandscapePlayer(final JSONObject p) throws Exception {
        if (!(context instanceof Activity)) throw new Exception("Aktivitas pemutar tidak tersedia.");
        final Activity activity = (Activity) context;
        final Intent intent = new Intent(activity, LandscapePlayerActivity.class);
        intent.putExtra("url", p.optString("url", ""));
        intent.putExtra("slug", p.optString("slug", ""));
        intent.putExtra("anime_id", p.optInt("animeId", 0));
        intent.putExtra("title", p.optString("title", "Anime"));
        intent.putExtra("ep", p.optString("ep", "Episode"));
        intent.putExtra("cover", p.optString("cover", ""));
        intent.putExtra("position", p.optDouble("position", 0));
        intent.putExtra("duration", p.optDouble("duration", 0));
        intent.putExtra("rate", (float) p.optDouble("rate", 1));
        intent.putExtra("muted", p.optBoolean("muted", false));
        intent.putExtra("autoplay", p.optBoolean("autoplay", false));
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                activity.startActivityForResult(intent, REQUEST_LANDSCAPE);
            }
        });
        return new JSONObject().put("launched", true);
    }

    private void setThemeBars(final boolean dark) {
        if (!(context instanceof Activity)) return;
        final Activity activity = (Activity) context;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    Window window = activity.getWindow();
                    window.setStatusBarColor(dark ? 0xFF0B1018 : 0xFFF7F9FC);
                    window.setNavigationBarColor(dark ? 0xFF0B1018 : 0xFFFFFFFF);
                    int current = window.getDecorView().getSystemUiVisibility();
                    boolean immersive = (current & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
                    if (!immersive) window.getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                } catch (Exception ignored) {}
            }
        });
    }

    private JSONObject queueDownload(JSONObject p) throws Exception {
        String url = p.optString("url");
        if (url.length() == 0) throw new Exception("Tautan video kosong");
        String title = p.optString("title", "Anime");
        int animeId = p.optInt("animeId", 0);
        String ep = p.optString("ep", "Episode");
        String slug = p.optString("slug", "");
        String idPart = animeId > 0 ? String.valueOf(animeId) : "video";
        String fallback = "animego-" + idPart + "-" + DownloadService.sanitize(ep);
        String filename = DownloadService.fileNameFromUrl(url, fallback);
        File target = DownloadService.createTargetFile(context, title, animeId, filename);
        JSONObject existing = db.findDownloadByPath(target.getAbsolutePath());
        if (existing != null) {
            long existingId = existing.optLong("id", -1);
            String existingStatus = existing.optString("status");
            if (target.isFile()) {
                db.updateDownload(existingId, "completed", target.length(), target.length(), "");
                existingStatus = "completed";
            } else {
                String freshHeaders = buildDownloadHeaders(url);
                db.updateDownloadRequest(existingId, title, ep, url, freshHeaders);
                if ("failed".equals(existingStatus) || "missing".equals(existingStatus)) {
                    db.retryDownload(existingId);
                    existingStatus = "queued";
                }
                if ("downloading".equals(existingStatus) || "queued".equals(existingStatus)) DownloadService.enqueue(context, existingId);
            }
            return existing.put("status", existingStatus).put("title", title).put("ep", ep).put("url", url);
        }
        if (target.isFile()) {
            JSONObject out = new JSONObject();
            out.put("id", -1);
            out.put("status", "completed");
            out.put("path", target.getAbsolutePath());
            out.put("filename", target.getName());
            return out;
        }
        String headers = buildDownloadHeaders(url);
        long id = db.addDownload(slug, animeId, title, ep, url, target.getAbsolutePath(), target.getName(), headers);
        if (id <= 0) throw new Exception("Tidak dapat membuat tugas unduhan");
        DownloadService.enqueue(context, id);
        JSONObject out = new JSONObject();
        out.put("id", id);
        out.put("status", "queued");
        out.put("path", target.getAbsolutePath());
        out.put("filename", target.getName());
        return out;
    }

    private String buildDownloadHeaders(String url) {
        JSONObject headers = new JSONObject();
        try { headers.put("User-Agent", userAgent); } catch (Exception ignored) {}
        try { headers.put("Referer", "https://karanime.com/"); } catch (Exception ignored) {}
        try { headers.put("Accept", "video/mp4,video/*,*/*;q=0.8"); } catch (Exception ignored) {}
        try { headers.put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"); } catch (Exception ignored) {}
        try { headers.put("Accept-Encoding", "identity"); } catch (Exception ignored) {}
        try {
            CookieManager cm = CookieManager.getInstance();
            String cookie = cm.getCookie(url);
            if (cookie == null || cookie.length() == 0) cookie = cm.getCookie("https://karanime.com/");
            if (cookie != null && cookie.length() > 0) headers.put("Cookie", cookie);
        } catch (Exception ignored) {}
        return headers.toString();
    }

    private JSONObject retryDownload(long id) throws Exception {
        if (id <= 0) throw new Exception("ID unduhan tidak valid");
        JSONObject row = db.getDownload(id);
        if (row == null) throw new Exception("Unduhan tidak ditemukan");
        File file = new File(row.optString("path"));
        if (file.exists()) file.delete();
        db.retryDownload(id);
        DownloadService.enqueue(context, id);
        return new JSONObject().put("id", id).put("status", "queued");
    }

    private JSONObject deleteDownload(long id) throws Exception {
        if (id <= 0) throw new Exception("ID unduhan tidak valid");
        JSONObject row = db.getDownload(id);
        if (row != null) {
            File file = new File(row.optString("path"));
            if (file.exists()) file.delete();
            File part = new File(row.optString("path") + ".part");
            if (part.exists()) part.delete();
        }
        db.removeDownload(id);
        return new JSONObject().put("removed", true);
    }

    private JSONObject deleteDownloadFile(long id) throws Exception {
        if (id <= 0) throw new Exception("ID unduhan tidak valid");
        JSONObject row = db.getDownload(id);
        if (row == null) return new JSONObject().put("removed", false);
        File file = new File(row.optString("path"));
        boolean removed = !file.exists() || file.delete();
        if (removed) db.updateDownload(id, "deleted", 0, 0, "");
        return new JSONObject().put("removed", removed);
    }

    private JSONObject openBackgroundSettings(String kind) {
        boolean opened = false;
        if ("battery".equals(kind)) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    Intent battery = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                    battery.setData(Uri.parse("package:" + context.getPackageName()));
                    context.startActivity(battery);
                    opened = true;
                }
            } catch (Exception ignored) {}
        } else {
            try {
                Intent vivo = new Intent();
                vivo.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
                context.startActivity(vivo);
                opened = true;
            } catch (Exception ignored) {}
            if (!opened) {
                try {
                    Intent appInfo = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                    appInfo.setData(Uri.parse("package:" + context.getPackageName()));
                    context.startActivity(appInfo);
                    opened = true;
                } catch (Exception ignored) {}
            }
        }
        JSONObject out = new JSONObject();
        try { out.put("opened", opened); } catch (Exception ignored) {}
        return out;
    }

    private static class BrowserResult {
        final CountDownLatch latch;
        boolean ok;
        String body;
        String error;
        BrowserResult(CountDownLatch latch) { this.latch = latch; }
    }
}
