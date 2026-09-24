package app.animego.inc.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

import org.json.JSONArray;
import org.json.JSONObject;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String NAME = "animego.db";
    private static final int VERSION = 3;

    public DatabaseHelper(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createBase(db);
        createDownloads(db);
    }

    private void createBase(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites (slug TEXT PRIMARY KEY, title TEXT NOT NULL, cover TEXT, added_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS history (slug TEXT PRIMARY KEY, title TEXT NOT NULL, ep TEXT, cover TEXT, src TEXT, position REAL DEFAULT 0, duration REAL DEFAULT 0, watched_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS history_watched_at ON history(watched_at DESC)");
    }

    private void createDownloads(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, anime_slug TEXT, anime_id INTEGER, title TEXT NOT NULL, ep TEXT, url TEXT NOT NULL, path TEXT NOT NULL UNIQUE, filename TEXT NOT NULL, status TEXT NOT NULL, progress INTEGER DEFAULT 0, total INTEGER DEFAULT 0, error TEXT, headers TEXT, added_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS downloads_updated ON downloads(updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1) createBase(db);
        if (oldVersion < 2) createDownloads(db);
        if (oldVersion < 3) db.execSQL("ALTER TABLE downloads ADD COLUMN headers TEXT");
    }

    public synchronized void saveFavorite(String slug, String title, String cover) {
        ContentValues v = new ContentValues();
        v.put("slug", slug);
        v.put("title", title);
        v.put("cover", cover);
        v.put("added_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void removeFavorite(String slug) {
        getWritableDatabase().delete("favorites", "slug=?", new String[]{slug});
    }

    public synchronized boolean isFavorite(String slug) {
        Cursor c = getReadableDatabase().query("favorites", new String[]{"slug"}, "slug=?", new String[]{slug}, null, null, null, "1");
        boolean result = c.moveToFirst();
        c.close();
        return result;
    }

    public synchronized JSONArray getFavorites() {
        JSONArray out = new JSONArray();
        Cursor c = getReadableDatabase().query("favorites", null, null, null, null, null, "added_at DESC");
        try {
            while (c.moveToNext()) {
                JSONObject x = new JSONObject();
                x.put("slug", c.getString(c.getColumnIndex("slug")));
                x.put("title", c.getString(c.getColumnIndex("title")));
                x.put("cover", c.getString(c.getColumnIndex("cover")));
                x.put("addedAt", c.getLong(c.getColumnIndex("added_at")));
                out.put(x);
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized void saveHistory(String slug, String title, String ep, String cover, String src, double position, double duration) {
        ContentValues v = new ContentValues();
        v.put("slug", slug);
        v.put("title", title);
        v.put("ep", ep);
        v.put("cover", cover);
        v.put("src", src);
        v.put("position", position);
        v.put("duration", duration);
        v.put("watched_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void updateProgress(String slug, String title, String ep, String cover, String src, double position, double duration) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("ep", ep);
        v.put("cover", cover);
        v.put("src", src);
        v.put("position", position);
        v.put("duration", duration);
        v.put("watched_at", System.currentTimeMillis());
        int updated = getWritableDatabase().update("history", v, "slug=?", new String[]{slug});
        if (updated == 0) saveHistory(slug, title, ep, cover, src, position, duration);
    }

    public synchronized void removeHistory(String slug) {
        getWritableDatabase().delete("history", "slug=?", new String[]{slug});
    }

    public synchronized void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    public synchronized JSONArray getHistory() {
        JSONArray out = new JSONArray();
        Cursor c = getReadableDatabase().query("history", null, null, null, null, null, "watched_at DESC", "40");
        try {
            while (c.moveToNext()) {
                JSONObject x = new JSONObject();
                x.put("slug", c.getString(c.getColumnIndex("slug")));
                x.put("title", c.getString(c.getColumnIndex("title")));
                x.put("ep", c.getString(c.getColumnIndex("ep")));
                x.put("cover", c.getString(c.getColumnIndex("cover")));
                x.put("src", c.getString(c.getColumnIndex("src")));
                x.put("position", c.getDouble(c.getColumnIndex("position")));
                x.put("duration", c.getDouble(c.getColumnIndex("duration")));
                x.put("watchedAt", c.getLong(c.getColumnIndex("watched_at")));
                out.put(x);
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized long addDownload(String slug, int animeId, String title, String ep, String url, String path, String filename, String headers) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("anime_slug", slug);
        v.put("anime_id", animeId);
        v.put("title", title);
        v.put("ep", ep);
        v.put("url", url);
        v.put("path", path);
        v.put("filename", filename);
        v.put("status", "queued");
        v.put("progress", 0);
        v.put("total", 0);
        v.put("error", "");
        v.put("headers", headers == null ? "{}" : headers);
        v.put("added_at", now);
        v.put("updated_at", now);
        return getWritableDatabase().insertWithOnConflict("downloads", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized JSONObject findDownloadByPath(String path) {
        Cursor c = getReadableDatabase().query("downloads", null, "path=?", new String[]{path}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) return null;
            return cursorDownload(c);
        } catch (Exception e) {
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized JSONObject getDownload(long id) {
        Cursor c = getReadableDatabase().query("downloads", null, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) return null;
            return cursorDownload(c);
        } catch (Exception e) {
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized void retryDownload(long id) {
        updateDownload(id, "queued", 0, 0, "");
    }

    public synchronized void updateDownloadRequest(long id, String title, String ep, String url, String headers) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("ep", ep);
        v.put("url", url);
        v.put("headers", headers == null ? "{}" : headers);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("downloads", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized boolean hasFailedDownloads() {
        Cursor c = getReadableDatabase().query("downloads", new String[]{"id"}, "status=?", new String[]{"failed"}, null, null, null, "1");
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    public synchronized void updateDownload(long id, String status, long progress, long total, String error) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("progress", progress);
        v.put("total", total);
        v.put("error", error);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("downloads", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void removeDownload(long id) {
        getWritableDatabase().delete("downloads", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized boolean hasPendingDownloads() {
        Cursor c = getReadableDatabase().query("downloads", new String[]{"id"},
                "status IN (?,?)", new String[]{"queued", "downloading"}, null, null, "updated_at ASC", "1");
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    public synchronized JSONObject getNextPendingDownload() {
        Cursor c = getReadableDatabase().query("downloads", null,
                "status IN (?,?)", new String[]{"queued", "downloading"}, null, null, "updated_at ASC", "1");
        try {
            if (!c.moveToFirst()) return null;
            return cursorDownload(c);
        } catch (Exception e) {
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized JSONArray getDownloads() {
        JSONArray out = new JSONArray();
        Cursor c = getReadableDatabase().query("downloads", null, null, null, null, null, "updated_at DESC");
        try {
            while (c.moveToNext()) {
                JSONObject x = cursorDownload(c);
                String path = x.optString("path");
                if ("completed".equals(x.optString("status")) && !new File(path).isFile()) x.put("status", "missing");
                out.put(x);
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return out;
    }

    private JSONObject cursorDownload(Cursor c) throws Exception {
        JSONObject x = new JSONObject();
        x.put("id", c.getLong(c.getColumnIndex("id")));
        x.put("slug", c.getString(c.getColumnIndex("anime_slug")));
        x.put("animeId", c.getInt(c.getColumnIndex("anime_id")));
        x.put("title", c.getString(c.getColumnIndex("title")));
        x.put("ep", c.getString(c.getColumnIndex("ep")));
        x.put("url", c.getString(c.getColumnIndex("url")));
        x.put("path", c.getString(c.getColumnIndex("path")));
        x.put("filename", c.getString(c.getColumnIndex("filename")));
        x.put("status", c.getString(c.getColumnIndex("status")));
        x.put("progress", c.getLong(c.getColumnIndex("progress")));
        x.put("total", c.getLong(c.getColumnIndex("total")));
        x.put("error", c.getString(c.getColumnIndex("error")));
        int headersIndex = c.getColumnIndex("headers");
        x.put("headers", headersIndex >= 0 && !c.isNull(headersIndex) ? c.getString(headersIndex) : "{}");
        x.put("addedAt", c.getLong(c.getColumnIndex("added_at")));
        x.put("updatedAt", c.getLong(c.getColumnIndex("updated_at")));
        return x;
    }
}
