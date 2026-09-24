package app.animego.inc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import app.animego.inc.bridge.JsBridge;
import app.animego.inc.db.DatabaseHelper;

public class MainActivity extends Activity {
    private WebView webView;
    private JsBridge bridge;
    private DatabaseHelper database;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1018);
        getWindow().setNavigationBarColor(0xFF0B1018);
        getWindow().getDecorView().setSystemUiVisibility(0);

        database = new DatabaseHelper(this);
        webView = new WebView(this);
        configureWebView();
        bridge = new JsBridge(this, webView, database, webView.getSettings().getUserAgentString());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF08090B);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        webView.addJavascriptInterface(bridge, "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        if (getIntent() != null && "offline".equals(getIntent().getStringExtra("open_route"))) {
            webView.postDelayed(new Runnable() {
                @Override public void run() {
                    webView.evaluateJavascript("window.__openRoute && window.__openRoute('offline');", null);
                }
            }, 700);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 16) settings.setAllowFileAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= 16) settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setUserAgentString(settings.getUserAgentString());

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(0xFF0B1018);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
    }

    public void handleBackFromSystem() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript("window.__handleAndroidBack && window.__handleAndroidBack();", null);
    }

    @Override public void onBackPressed() { handleBackFromSystem(); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != JsBridge.REQUEST_LANDSCAPE || webView == null) return;
        if (resultCode != RESULT_OK || data == null) {
            webView.postDelayed(new Runnable() {
                @Override public void run() {
                    if (webView != null) webView.evaluateJavascript("window.RH&&RH.player&&RH.player.onLandscapeCancel&&RH.player.onLandscapeCancel();", null);
                }
            }, 120);
            return;
        }
        double position = data.getDoubleExtra("position", 0);
        double duration = data.getDoubleExtra("duration", 0);
        float rate = data.getFloatExtra("rate", 1);
        boolean playing = data.getBooleanExtra("playing", false);
        boolean muted = data.getBooleanExtra("muted", false);
        final String js = "window.RH&&RH.player&&RH.player.onLandscapeResult(" + position + "," + duration + "," + rate + "," + playing + "," + muted + ");";
        webView.postDelayed(new Runnable() {
            @Override public void run() {
                if (webView != null) webView.evaluateJavascript(js, null);
            }
        }, 120);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            handleBackFromSystem();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
