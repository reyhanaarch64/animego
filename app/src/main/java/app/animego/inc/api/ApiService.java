package app.animego.inc.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ApiService {
    public static final int ONGOING = 2872;
    public static final int COMPLETED = 2883;
    public static final int TV = 2900;
    public static final int MOVIE = 2916;
    public static final int TOP_YA = 2901;

    private static final String LIST_FIELDS = "id,slug,title,date,modified,featured_media,link,animegenre,animetype,animestatus,meta_box.ero_image,meta_box.ero_episode,meta_box.ero_episodebaru,meta_box.ero_status,meta_box.ero_type,meta_box.ero_skor,meta_box.ero_tayang,meta_box.ero_sub,meta_box.ero_durasi,meta_box.ero_genreapp,meta_box.ero_japanese,meta_box.ero_trailer";
    private static final String SUGGEST_FIELDS = "id,slug,title,featured_media,meta_box.ero_image,meta_box.ero_episode,meta_box.ero_episodebaru,meta_box.ero_skor,meta_box.ero_status";

    private final ApiClient client;
    private final ExecutorService homePool = Executors.newFixedThreadPool(4);

    public ApiService(String userAgent) {
        client = new ApiClient(userAgent);
    }

    public ApiService(String userAgent, ApiClient.BrowserFetcher browserFetcher) {
        client = new ApiClient(userAgent, browserFetcher);
    }

    public JSONObject getHome() throws Exception {
        Future<JSONObject> ongoing = submitList("ongoing");
        Future<JSONObject> top = submitList("top");
        Future<JSONObject> movies = submitList("movies");
        Future<JSONObject> completed = submitList("completed");
        JSONObject out = new JSONObject();
        out.put("ongoing", safeFuture(ongoing));
        out.put("top", safeFuture(top));
        out.put("movies", safeFuture(movies));
        out.put("completed", safeFuture(completed));
        return out;
    }

    private Future<JSONObject> submitList(final String kind) {
        return homePool.submit(new Callable<JSONObject>() {
            @Override public JSONObject call() throws Exception { return getList(kind, 1, 12); }
        });
    }

    private JSONObject safeFuture(Future<JSONObject> future) {
        try { return future.get(); }
        catch (Exception e) {
            JSONObject x = new JSONObject();
            try {
                x.put("items", new JSONArray());
                x.put("total", 0);
                x.put("totalPages", 0);
                x.put("error", e.getMessage() == null ? "Gagal memuat data" : e.getMessage());
            } catch (Exception ignored) {}
            return x;
        }
    }

    public JSONObject getList(String kind, int page, int limit) throws Exception {
        Map<String, String> p = baseListParams(page, limit);
        if ("ongoing".equals(kind)) p.put("animestatus", String.valueOf(ONGOING));
        else if ("completed".equals(kind)) p.put("animestatus", String.valueOf(COMPLETED));
        else if ("movies".equals(kind)) p.put("animetype", String.valueOf(MOVIE));
        else if ("top".equals(kind)) p.put("animetop", String.valueOf(TOP_YA));
        else if ("random".equals(kind)) p.put("orderby", "rand");
        else if ("latest".equals(kind)) { p.put("orderby", "modified"); p.put("order", "desc"); }
        else if ("oldest".equals(kind)) { p.put("orderby", "date"); p.put("order", "asc"); }

        ApiClient.Response r = client.getWithHeaders("/animes", p);
        return wrapList(normalizeList(new JSONArray(r.body)), r.total, r.totalPages);
    }

    public JSONObject search(String q, int page, int limit) throws Exception {
        Map<String, String> p = new HashMap<String, String>();
        p.put("search", q == null ? "" : q);
        p.put("page", String.valueOf(page));
        p.put("per_page", String.valueOf(limit));
        p.put("_fields", LIST_FIELDS);
        ApiClient.Response r = client.getWithHeaders("/animes", p);
        JSONArray raw = new JSONArray(r.body);
        String normalized = normalizeTitle(q);
        String querySlug = slugify(q);
        JSONArray items = new JSONArray();
        Set<String> seen = new HashSet<String>();

        if (page == 1 && querySlug.length() > 0) {
            try {
                Map<String, String> exact = new HashMap<String, String>();
                exact.put("slug", querySlug);
                exact.put("_fields", LIST_FIELDS);
                JSONArray exactArr = new JSONArray(client.get("/animes", exact));
                if (exactArr.length() > 0) {
                    JSONObject hit = normalizeAnime(exactArr.getJSONObject(0), true);
                    String key = hit.optString("slug");
                    if (key.length() > 0) { items.put(hit); seen.add(key); }
                }
            } catch (Exception ignored) {}
        }

        List<JSONObject> sorted = new ArrayList<JSONObject>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = normalizeAnime(raw.getJSONObject(i), false);
            String key = item.optString("slug");
            if (key.length() == 0) key = String.valueOf(item.optInt("id"));
            if (!seen.contains(key)) {
                item.put("_score", searchScore(item, normalized, querySlug));
                sorted.add(item);
            }
        }
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return b.optInt("_score") - a.optInt("_score"); }
        });
        for (JSONObject item : sorted) { item.remove("_score"); items.put(item); }
        return wrapList(items, r.total + (seen.isEmpty() ? 0 : 0), r.totalPages);
    }

    public JSONArray suggest(String q, int limit) throws Exception {
        Map<String, String> p = new HashMap<String, String>();
        p.put("search", q == null ? "" : q);
        p.put("page", "1");
        p.put("per_page", String.valueOf(limit));
        p.put("_fields", SUGGEST_FIELDS);
        JSONArray raw = new JSONArray(client.get("/animes", p));
        JSONArray out = new JSONArray();
        for (int i = 0; i < raw.length() && out.length() < limit; i++) {
            JSONObject x = normalizeAnime(raw.getJSONObject(i), false);
            if (x.optString("slug").length() == 0) continue;
            JSONObject result = new JSONObject();
            result.put("slug", x.optString("slug"));
            result.put("title", x.optString("title"));
            result.put("cover", x.optString("cover"));
            result.put("totalEps", x.optInt("episode", 0));
            result.put("score", x.optString("score"));
            result.put("status", x.optString("status"));
            out.put(result);
        }
        return out;
    }

    public JSONObject getDetail(String key) throws Exception {
        JSONObject anime = findAnime(key);
        if (anime == null) return null;
        return normalizeDetail(anime);
    }

    public JSONArray getGenres() throws Exception {
        JSONArray all = new JSONArray();
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages) {
            Map<String, String> p = new HashMap<String, String>();
            p.put("per_page", "100");
            p.put("page", String.valueOf(page));
            p.put("orderby", "name");
            p.put("order", "asc");
            ApiClient.Response r = client.getWithHeaders("/animegenre", p);
            JSONArray part = new JSONArray(r.body);
            for (int i = 0; i < part.length(); i++) all.put(part.getJSONObject(i));
            totalPages = r.totalPages > 0 ? r.totalPages : page;
            page++;
        }
        return all;
    }

    public JSONObject getGenre(String slugOrId, int page, int limit) throws Exception {
        JSONArray genres = getGenres();
        JSONObject term = null;
        for (int i = 0; i < genres.length(); i++) {
            JSONObject g = genres.getJSONObject(i);
            if (String.valueOf(g.optInt("id")).equals(slugOrId)
                    || g.optString("slug").equalsIgnoreCase(slugOrId)
                    || g.optString("name").equalsIgnoreCase(slugOrId)) {
                term = g;
                break;
            }
        }
        JSONObject out = new JSONObject();
        if (term == null) {
            out.put("term", JSONObject.NULL);
            out.put("list", wrapList(new JSONArray(), 0, 0));
            return out;
        }
        out.put("term", term);
        Map<String, String> p = baseListParams(page, limit);
        p.put("animegenre", String.valueOf(term.optInt("id")));
        ApiClient.Response r = client.getWithHeaders("/animes", p);
        out.put("list", wrapList(normalizeList(new JSONArray(r.body)), r.total, r.totalPages));
        return out;
    }

    public JSONObject getSchedule(String day, int page, int limit) throws Exception {
        int id = scheduleId(day);
        Map<String, String> p = baseListParams(page, limit);
        if (id > 0) p.put("jadwalrilis", String.valueOf(id));
        else { p.put("orderby", "rand"); }
        ApiClient.Response r = client.getWithHeaders("/animes", p);
        JSONObject out = wrapList(normalizeList(new JSONArray(r.body)), r.total, r.totalPages);
        out.put("day", day);
        return out;
    }

    private int scheduleId(String day) {
        if (day == null) return 0;
        if ("senin".equalsIgnoreCase(day)) return 3058;
        if ("selasa".equalsIgnoreCase(day)) return 3059;
        if ("rabu".equalsIgnoreCase(day)) return 3061;
        if ("kamis".equalsIgnoreCase(day)) return 3062;
        if ("jumat".equalsIgnoreCase(day)) return 3063;
        if ("sabtu".equalsIgnoreCase(day)) return 3057;
        if ("minggu".equalsIgnoreCase(day)) return 3064;
        return 0;
    }

    private Map<String, String> baseListParams(int page, int limit) {
        Map<String, String> p = new HashMap<String, String>();
        p.put("_fields", LIST_FIELDS);
        p.put("page", String.valueOf(Math.max(1, page)));
        p.put("per_page", String.valueOf(Math.max(1, Math.min(limit, 24))));
        return p;
    }

    private JSONArray normalizeList(JSONArray raw) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < raw.length(); i++) out.put(normalizeAnime(raw.getJSONObject(i), false));
        return out;
    }

    private JSONObject normalizeAnime(JSONObject a, boolean includeGenres) throws Exception {
        JSONObject out = new JSONObject();
        out.put("id", a.optInt("id"));
        out.put("slug", a.optString("slug"));
        out.put("title", titleOf(a));
        out.put("date", a.optString("date"));
        out.put("modified", a.optString("modified"));
        out.put("link", a.optString("link"));
        out.put("cover", metaString(a, "ero_image"));
        out.put("episode", episodeLabel(a));
        out.put("status", metaString(a, "ero_status"));
        out.put("type", metaString(a, "ero_type"));
        out.put("score", metaString(a, "ero_skor"));
        out.put("year", metaString(a, "ero_tayang"));
        out.put("sub", metaString(a, "ero_sub"));
        out.put("duration", metaString(a, "ero_durasi"));
        out.put("genreText", metaString(a, "ero_genreapp"));
        out.put("japanese", metaString(a, "ero_japanese"));
        out.put("trailer", metaString(a, "ero_trailer"));
        if (includeGenres || a.has("animegenre")) out.put("genreIds", intArray(a.optJSONArray("animegenre")));
        out.put("statusIds", intArray(a.optJSONArray("animestatus")));
        out.put("typeIds", intArray(a.optJSONArray("animetype")));
        return out;
    }

    private JSONObject normalizeDetail(JSONObject a) throws Exception {
        JSONObject out = normalizeAnime(a, true);
        String synopsis = stripHtml(getNestedString(a, "content", "rendered"));
        out.put("synopsis", synopsis);
        JSONArray eps = sortEpisodes(metaArray(a, "ab_cdngroup"));
        out.put("episodes", eps);
        out.put("episodeCount", eps.length());
        return out;
    }

    private JSONObject findAnime(String key) throws Exception {
        if (key == null || key.trim().length() == 0) return null;
        if (key.matches("\\d+")) {
            try { return new JSONObject(client.get("/animes/" + key, null)); }
            catch (Exception ignored) {}
        }
        String[] candidates = slugCandidates(key);
        for (String candidate : candidates) {
            Map<String, String> p = new HashMap<String, String>();
            p.put("slug", candidate);
            String body = client.get("/animes", p);
            JSONArray arr = new JSONArray(body);
            if (arr.length() > 0) return arr.getJSONObject(0);
        }
        return null;
    }

    private String[] slugCandidates(String slug) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        List<String> frontier = new ArrayList<String>();
        addCandidate(out, seen, slug);
        frontier.add(slug);
        for (int depth = 0; depth < 3; depth++) {
            List<String> next = new ArrayList<String>();
            for (String s : frontier) {
                String[] strips = new String[] {
                        s.replaceAll("-episode-\\d+$", ""),
                        s.replaceAll("-season-\\d+$", ""),
                        s.replaceAll("-part-\\d+$", ""),
                        s.replaceAll("-\\d+$", "")
                };
                for (String x : strips) if (x.length() > 0 && !x.equals(s) && !seen.contains(x)) {
                    addCandidate(out, seen, x);
                    next.add(x);
                }
            }
            if (next.isEmpty()) break;
            frontier = next;
        }
        return out.toArray(new String[out.size()]);
    }

    private void addCandidate(List<String> out, Set<String> seen, String value) {
        if (value == null || value.length() == 0 || seen.contains(value)) return;
        seen.add(value);
        out.add(value);
    }

    private JSONArray sortEpisodes(JSONArray raw) throws Exception {
        List<JSONObject> list = new ArrayList<JSONObject>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject e = raw.getJSONObject(i);
            String name = e.optString("ab_namaep");
            if (name.length() == 0 || seen.contains(name)) continue;
            seen.add(name);
            JSONObject x = new JSONObject();
            x.put("name", name);
            x.put("url", encodeMedia(e.optString("ab_linkcdn")));
            list.add(x);
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                double na = episodeNumber(a.optString("name"));
                double nb = episodeNumber(b.optString("name"));
                if (Double.isNaN(na) && Double.isNaN(nb)) return 0;
                if (Double.isNaN(na)) return 1;
                if (Double.isNaN(nb)) return -1;
                return na < nb ? -1 : (na > nb ? 1 : 0);
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject e : list) out.put(e);
        return out;
    }

    private double episodeNumber(String value) {
        if (value == null) return Double.NaN;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);
        if (m.find()) {
            try { return Double.parseDouble(m.group()); } catch (Exception ignored) {}
        }
        return Double.NaN;
    }

    private String encodeMedia(String value) {
        if (value == null || value.length() == 0) return "";
        try {
            URL u = new URL(value);
            String path = u.getPath();
            String[] chunks = path.split("/", -1);
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < chunks.length; i++) {
                if (i > 0) encoded.append('/');
                encoded.append(encodePathSegmentPreservingEscapes(chunks[i]));
            }
            String result = u.getProtocol() + "://" + u.getAuthority() + encoded.toString();
            if (u.getQuery() != null && u.getQuery().length() > 0) result += "?" + u.getQuery();
            if (u.getRef() != null && u.getRef().length() > 0) result += "#" + u.getRef();
            return result;
        } catch (Exception e) {
            return value.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D");
        }
    }

    private String encodePathSegmentPreservingEscapes(String value) {
        if (value == null || value.length() == 0) return "";
        final char[] hex = "0123456789ABCDEF".toCharArray();
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
                out.append('%');
                out.append(Character.toUpperCase(value.charAt(i + 1)));
                out.append(Character.toUpperCase(value.charAt(i + 2)));
                i += 2;
                continue;
            }
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                    (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch);
            } else if (ch < 128) {
                int b = (int) ch;
                out.append('%');
                out.append(hex[(b >>> 4) & 15]);
                out.append(hex[b & 15]);
            } else {
                byte[] bytes;
                try { bytes = String.valueOf(ch).getBytes("UTF-8"); }
                catch (Exception e) { bytes = String.valueOf(ch).getBytes(); }
                for (int j = 0; j < bytes.length; j++) {
                    int b = bytes[j] & 255;
                    out.append('%');
                    out.append(hex[(b >>> 4) & 15]);
                    out.append(hex[b & 15]);
                }
            }
        }
        return out.toString();
    }

    private boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private int episodeLabel(JSONObject a) {
        String raw = metaString(a, "ero_episode");
        if (raw.length() == 0) raw = metaString(a, "ero_episodebaru");
        try {
            double n = Double.parseDouble(raw);
            if (n >= 99999) return 0;
            return (int) n;
        } catch (Exception e) { return 0; }
    }

    private String metaString(JSONObject a, String key) {
        JSONObject mb = a.optJSONObject("meta_box");
        if (mb == null) return "";
        Object value = mb.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value);
    }

    private JSONArray metaArray(JSONObject a, String key) {
        JSONObject mb = a.optJSONObject("meta_box");
        if (mb == null) return new JSONArray();
        JSONArray arr = mb.optJSONArray(key);
        return arr == null ? new JSONArray() : arr;
    }

    private String getNestedString(JSONObject object, String parent, String child) {
        JSONObject p = object.optJSONObject(parent);
        return p == null ? "" : p.optString(child, "");
    }

    private int[] intArray(JSONArray arr) throws Exception {
        if (arr == null) return new int[0];
        int[] out = new int[arr.length()];
        for (int i = 0; i < arr.length(); i++) out[i] = arr.optInt(i);
        return out;
    }

    private String titleOf(JSONObject a) {
        JSONObject title = a.optJSONObject("title");
        return stripHtml(title != null ? title.optString("rendered", "") : a.optString("title", ""));
    }

    private String normalizeTitle(String value) {
        return stripHtml(value).toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private String slugify(String value) {
        return normalizeTitle(value).replaceAll("\\s+", "-");
    }

    private int searchScore(JSONObject a, String normalized, String querySlug) {
        String title = normalizeTitle(a.optString("title"));
        String slug = a.optString("slug").toLowerCase();
        if (title.equals(normalized)) return 100;
        if (slug.equals(querySlug)) return 90;
        if (title.startsWith(normalized) || normalized.startsWith(title)) return 60;
        if (normalized.length() > 0 && title.contains(normalized)) return 40;
        return 0;
    }

    private String stripHtml(String value) {
        if (value == null) return "";
        String out = value.replaceAll("<[^>]*>", " ");
        out = out.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#038;", "&");
        out = out.replaceAll("&#(\\d+);", "");
        return out.replaceAll("\\s+", " ").trim();
    }

    private JSONObject wrapList(JSONArray items, int total, int totalPages) throws Exception {
        JSONObject out = new JSONObject();
        out.put("items", items);
        out.put("total", total);
        out.put("totalPages", totalPages);
        return out;
    }
}
