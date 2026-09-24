package app.animego.inc.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class ApiClient {
    public static final String BASE = "https://karanime.com/wp-json/wp/v2";
    private static final String ORIGIN = "https://karanime.com/";
    private static final int TIMEOUT = 18000;
    private static final int RETRIES = 3;
    private static final Object COOKIE_LOCK = new Object();
    private static boolean warmedUp;

    private final String userAgent;
    private final BrowserFetcher browserFetcher;

    public ApiClient() {
        this(null, null);
    }

    public ApiClient(String userAgent) {
        this(userAgent, null);
    }

    public ApiClient(String userAgent, BrowserFetcher browserFetcher) {
        if (userAgent == null || userAgent.trim().length() == 0) {
            this.userAgent = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 AnimeGo/1.0";
        } else {
            this.userAgent = userAgent;
        }
        this.browserFetcher = browserFetcher;
        enableCookies();
    }

    public String get(String path, Map<String, String> params) throws Exception {
        Response r = request(path, params);
        return r.body;
    }

    public Response getWithHeaders(String path, Map<String, String> params) throws Exception {
        return request(path, params);
    }

    private Response request(String path, Map<String, String> params) throws Exception {
        warmUpOrigin();
        String url = buildUrl(path, params);
        Exception last = null;
        boolean sawNonJson = false;

        for (int attempt = 0; attempt < RETRIES; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7");
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Referer", ORIGIN);
                conn.setRequestProperty("Origin", ORIGIN.substring(0, ORIGIN.length() - 1));
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setRequestProperty("Accept-Encoding", "identity");

                int code = conn.getResponseCode();
                InputStream raw = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
                String body = readResponse(raw);
                int total = parseIntHeader(conn.getHeaderField("X-WP-Total"));
                int totalPages = parseIntHeader(conn.getHeaderField("X-WP-TotalPages"));

                if (code < 200 || code >= 300) {
                    String detail = compact(body);
                    last = new IOException("HTTP " + code + (detail.length() > 0 ? ": " + detail : ""));
                    if (code == 429 || code >= 500) {
                        sleepRetry(attempt);
                        continue;
                    }
                    throw last;
                }

                String json = normalizeJsonBody(body);
                if (json.length() == 0) {
                    last = new IOException("Server API mengembalikan respons kosong");
                    sleepRetry(attempt);
                    continue;
                }
                if (!looksLikeJson(json)) {
                    sawNonJson = true;
                    last = new IOException("Server API sementara mengembalikan halaman non-JSON: " + compact(json));
                    sleepRetry(attempt);
                    continue;
                }
                return new Response(json, total, totalPages);
            } catch (Exception e) {
                last = e;
                if (attempt + 1 < RETRIES) sleepRetry(attempt);
                else throw e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        if (sawNonJson && browserFetcher != null) {
            try {
                String browserBody = browserFetcher.fetch(url);
                String browserJson = normalizeJsonBody(browserBody);
                if (looksLikeJson(browserJson)) return new Response(browserJson, 0, 0);
                last = new IOException("Browser API juga mengembalikan respons non-JSON: " + compact(browserJson));
            } catch (Exception e) {
                last = new IOException("Koneksi API gagal dan mode browser tidak dapat mengambil JSON: " +
                        (e.getMessage() == null ? "kesalahan jaringan" : e.getMessage()));
            }
        }
        throw last == null ? new IOException("Request API gagal") : last;
    }

    private void warmUpOrigin() {
        synchronized (COOKIE_LOCK) {
            if (warmedUp) return;
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                conn = (HttpURLConnection) new URL(ORIGIN).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(9000);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Accept-Encoding", "identity");
                int code = conn.getResponseCode();
                in = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (in != null) {
                    byte[] skip = new byte[4096];
                    while (in.read(skip) != -1) { }
                }
            } catch (Exception ignored) {
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
                warmedUp = true;
            }
        }
    }

    private void enableCookies() {
        try {
            CookieHandler current = CookieHandler.getDefault();
            if (!(current instanceof CookieManager)) {
                CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            }
        } catch (Exception ignored) {
        }
    }

    private String readResponse(InputStream source) throws Exception {
        if (source == null) return "";
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int read;
            while ((read = source.read(data)) != -1) buffer.write(data, 0, read);
            return buffer.toString("UTF-8");
        } finally {
            try { source.close(); } catch (Exception ignored) {}
        }
    }

    private String normalizeJsonBody(String body) {
        if (body == null) return "";
        String out = body.replace("\ufeff", "").trim();
        if (looksLikeJson(out)) return out;
        int firstObject = out.indexOf('{');
        int firstArray = out.indexOf('[');
        int start = -1;
        if (firstObject >= 0 && firstArray >= 0) start = Math.min(firstObject, firstArray);
        else if (firstObject >= 0) start = firstObject;
        else if (firstArray >= 0) start = firstArray;
        if (start > 0) {
            String candidate = out.substring(start).trim();
            if (looksLikeJson(candidate)) return candidate;
        }
        return out;
    }

    private boolean looksLikeJson(String body) {
        if (body == null || body.length() < 2) return false;
        char first = body.charAt(0);
        char last = body.charAt(body.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    private String compact(String body) {
        if (body == null) return "";
        String value = body.replaceAll("\\s+", " ").trim();
        if (value.length() > 180) value = value.substring(0, 180);
        return value;
    }

    private void sleepRetry(int attempt) {
        try { Thread.sleep(500L * (attempt + 1)); } catch (InterruptedException ignored) {}
    }

    private String buildUrl(String path, Map<String, String> params) throws Exception {
        StringBuilder out = new StringBuilder(BASE);
        if (!path.startsWith("/")) out.append('/');
        out.append(path);
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> item : params.entrySet()) {
                String value = item.getValue();
                if (value == null || value.length() == 0) continue;
                out.append(first ? '?' : '&');
                first = false;
                out.append(percentEncode(item.getKey()));
                out.append('=');
                out.append(percentEncode(value));
            }
        }
        return out.toString();
    }

    private String percentEncode(String value) {
        if (value == null) return "";
        byte[] bytes;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (Exception e) {
            bytes = value.getBytes();
        }
        final char[] hex = "0123456789ABCDEF".toCharArray();
        StringBuilder out = new StringBuilder(bytes.length + 16);
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 255;
            boolean safe = (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') ||
                    (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.' || b == '~';
            if (safe) out.append((char) b);
            else {
                out.append('%');
                out.append(hex[(b >>> 4) & 15]);
                out.append(hex[b & 15]);
            }
        }
        return out.toString();
    }

    private int parseIntHeader(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
    }

    public interface BrowserFetcher {
        String fetch(String url) throws Exception;
    }

    public static class Response {
        public final String body;
        public final int total;
        public final int totalPages;

        public Response(String body, int total, int totalPages) {
            this.body = body;
            this.total = total;
            this.totalPages = totalPages;
        }
    }
}
