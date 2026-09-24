package app.animego.inc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import app.animego.inc.db.DatabaseHelper;

public class LandscapePlayerActivity extends Activity {
    private WebView webView;
    private DatabaseHelper database;
    private boolean finishing;
    private String slug;
    private String title;
    private String ep;
    private String cover;
    private String source;
    private double position;
    private double duration;
    private float rate;
    private boolean muted;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUi();

        Intent in = getIntent();
        source = in == null ? "" : in.getStringExtra("url");
        slug = in == null ? "" : in.getStringExtra("slug");
        title = in == null ? "Anime" : in.getStringExtra("title");
        ep = in == null ? "Episode" : in.getStringExtra("ep");
        cover = in == null ? "" : in.getStringExtra("cover");
        position = in == null ? 0 : in.getDoubleExtra("position", 0);
        duration = in == null ? 0 : in.getDoubleExtra("duration", 0);
        rate = in == null ? 1 : in.getFloatExtra("rate", 1);
        muted = in != null && in.getBooleanExtra("muted", false);
        final boolean autoplay = in != null && in.getBooleanExtra("autoplay", false);

        database = new DatabaseHelper(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUserAgentString(settings.getUserAgentString());
        webView.setBackgroundColor(0xFF05070B);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                final String js = "window.__landscapeInit&&window.__landscapeInit(" +
                        quote(source) + "," + quote(title) + "," + quote(ep) + "," + position + "," + duration + "," + rate + "," + muted + "," + autoplay + ");";
                view.post(new Runnable() {
                    @Override public void run() {
                        if (webView != null) webView.evaluateJavascript(js, null);
                    }
                });
            }
        });
        webView.addJavascriptInterface(new LandscapeBridge(), "LandscapeBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/landscape_player.html");
    }

    private String quote(String value) {
        return org.json.JSONObject.quote(value == null ? "" : value);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(0xFF05070B);
        getWindow().setNavigationBarColor(0xFF05070B);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void finishPlayer(final double pos, final double dur, final float playbackRate, final boolean playing, final boolean isMuted) {
        if (finishing) return;
        finishing = true;
        position = pos;
        duration = dur;
        rate = playbackRate;
        muted = isMuted;
        saveProgress();
        Intent data = new Intent();
        data.putExtra("position", position);
        data.putExtra("duration", duration);
        data.putExtra("rate", rate);
        data.putExtra("playing", playing);
        data.putExtra("muted", muted);
        setResult(RESULT_OK, data);
        finish();
    }

    private void saveProgress() {
        if (database == null || source == null || source.length() == 0 || slug == null || slug.length() == 0) return;
        try {
            database.updateProgress(slug, title, ep, cover, source, position, duration);
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.__landscapeExit&&window.__landscapeExit();", null);
            webView.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!finishing) finishPlayer(position, duration, rate, false, muted);
                }
            }, 250);
        } else {
            finishPlayer(position, duration, rate, false, muted);
        }
    }

    public class LandscapeBridge {
        @JavascriptInterface
        public void save(double pos, double dur) {
            position = pos;
            duration = dur;
            saveProgress();
        }

        @JavascriptInterface
        public void exit(double pos, double dur, float playbackRate, boolean playing, boolean isMuted) {
            finishPlayer(pos, dur, playbackRate, playing, isMuted);
        }
    }

    @Override
    protected void onDestroy() {
        if (!finishing && webView != null) {
            try { webView.evaluateJavascript("window.__landscapeSnapshot&&window.__landscapeSnapshot();", null); } catch (Exception ignored) {}
        }
        if (webView != null) {
            webView.removeJavascriptInterface("LandscapeBridge");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        super.onDestroy();
    }
}
