package app.animego.inc;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import app.animego.inc.db.DatabaseHelper;

import org.json.JSONObject;

/** Floating WebView video player. Java 7 compatible for AIDE projects. */
public class FloatingPlayerService extends Service {
    public static final String ACTION_SHOW = "app.animego.inc.FLOATING_SHOW";
    public static final String ACTION_STOP = "app.animego.inc.FLOATING_STOP";
    private static final String CHANNEL_ID = "animego_floating_player";
    private static final int NOTIFICATION_ID = 3409;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private WebView webView;
    private NativePopupContainer overlayContainer;
    private DatabaseHelper database;
    private Handler handler;

    private String source = "";
    private String slug = "";
    private String title = "AnimeGo";
    private String episode = "Episode";
    private String cover = "";
    private boolean offline;
    private double position;
    private double duration;
    private float playbackRate = 1f;
    private boolean muted;
    private boolean wasPlaying;
    private long lastProgressSave;
    private float dragStartScreenX;
    private float dragStartScreenY;
    private int dragStartX;
    private int dragStartY;
    private boolean dragStarted;

    private final Runnable periodicSave = new Runnable() {
        @Override public void run() {
            saveProgress(false);
            if (webView != null) handler.postDelayed(this, 5000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        database = new DatabaseHelper(getApplicationContext());
        handler = new Handler();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, makeNotification("Pemutar mengambang aktif"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                MainActivity.notifyFloatingFailed();
                stopSelf();
                return START_NOT_STICKY;
            }
            readIntent(intent);
            updateNotification(title + " · " + episode);
            removeOverlay();
            createOverlay();
            handler.removeCallbacks(periodicSave);
            handler.postDelayed(periodicSave, 5000L);
        }
        return START_NOT_STICKY;
    }

    private void readIntent(Intent intent) {
        source = safe(intent.getStringExtra("url"));
        slug = safe(intent.getStringExtra("slug"));
        title = value(intent.getStringExtra("title"), "AnimeGo");
        episode = value(intent.getStringExtra("ep"), "Episode");
        cover = safe(intent.getStringExtra("cover"));
        offline = intent.getBooleanExtra("offline", false);
        position = Math.max(0, intent.getDoubleExtra("position", 0));
        duration = Math.max(0, intent.getDoubleExtra("duration", 0));
        playbackRate = intent.getFloatExtra("rate", 1f);
        if (playbackRate < 0.25f) playbackRate = 1f;
        muted = intent.getBooleanExtra("muted", false);
        wasPlaying = intent.getBooleanExtra("playing", true);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void createOverlay() {
        if (source.length() == 0) {
            MainActivity.notifyFloatingFailed();
            stopSelf();
            return;
        }

        overlayContainer = new NativePopupContainer();
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
        if (Build.VERSION.SDK_INT >= 16) settings.setAllowFileAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= 16) settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        String ua = settings.getUserAgentString();
        if (ua != null && ua.length() > 0) settings.setUserAgentString(ua);
        webView.setBackgroundColor(0x00000000);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PopupBridge(), "PopupBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                final String js = "window.__popupInit&&window.__popupInit(" +
                        quote(source) + "," + quote(title) + "," + quote(episode) + "," +
                        position + "," + duration + "," + playbackRate + "," + muted + "," + wasPlaying + ");";
                view.post(new Runnable() {
                    @Override public void run() {
                        if (webView != null) webView.evaluateJavascript(js, null);
                    }
                });
            }
        });

        overlayContainer.setBackgroundColor(0x00000000);
        overlayContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(dp(300), screenWidth - dp(20));
        int height = Math.min(dp(205), (int) (screenHeight * 0.72f));
        width = Math.max(dp(210), width);
        height = Math.max(dp(150), height);
        int type;
        if (Build.VERSION.SDK_INT >= 26) type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else type = WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                width,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.x = Math.max(dp(6), (screenWidth - width) / 2);
        windowParams.y = dp(90);
        windowParams.windowAnimations = android.R.style.Animation_Dialog;

        try {
            windowManager.addView(overlayContainer, windowParams);
            webView.loadUrl("file:///android_asset/popup_player.html");
        } catch (Exception error) {
            removeOverlay();
            MainActivity.notifyFloatingFailed();
            stopSelf();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String quote(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String value(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Pemutar mengambang", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Menjaga pemutar video AnimeGo tetap aktif di latar belakang.");
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification makeNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID);
        else builder = new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AnimeGo")
                .setContentText(value(text, "Pemutar mengambang aktif"))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setPriority(Notification.PRIORITY_LOW);
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, makeNotification(text));
    }

    private void saveProgress(boolean force) {
        if (offline || database == null || slug.length() == 0 || source.length() == 0) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && now - lastProgressSave < 4500L) return;
        lastProgressSave = now;
        try { database.updateProgress(slug, title, episode, cover, source, position, duration); }
        catch (Exception ignored) {}
    }

    private void removeOverlay() {
        if (webView != null) {
            try { webView.removeJavascriptInterface("PopupBridge"); } catch (Exception ignored) {}
            try { webView.stopLoading(); } catch (Exception ignored) {}
            try {
                if (overlayContainer != null && overlayContainer.getParent() != null && windowManager != null) windowManager.removeView(overlayContainer);
            } catch (Exception ignored) {}
            try { webView.destroy(); } catch (Exception ignored) {}
            webView = null;
            overlayContainer = null;
        }
    }

    private void stopOverlay(boolean syncToMain, boolean resumePlayback) {
        saveProgress(true);
        if (syncToMain) {
            MainActivity.deliverFloatingResult(position, duration, playbackRate, resumePlayback && wasPlaying, muted);
        }
        stopSelf();
    }

    private void returnToApp() {
        MainActivity.deliverFloatingResult(position, duration, playbackRate, wasPlaying, muted);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); } catch (Exception ignored) {}
        stopSelf();
    }

    private void resizeBy(final float factor) {
        if (factor <= 0f || Float.isNaN(factor) || Float.isInfinite(factor)) return;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (windowParams == null || overlayContainer == null || windowManager == null) return;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int minWidth = dp(210), minHeight = dp(150);
                int maxWidth = screenWidth - dp(12), maxHeight = (int) (screenHeight * 0.78f);
                int newWidth = Math.max(minWidth, Math.min(maxWidth, Math.round(windowParams.width * factor)));
                int newHeight = Math.max(minHeight, Math.min(maxHeight, Math.round(windowParams.height * factor)));
                if (newWidth == windowParams.width && newHeight == windowParams.height) return;
                windowParams.width = newWidth;
                windowParams.height = newHeight;
                clampPosition();
                try { windowManager.updateViewLayout(overlayContainer, windowParams); } catch (Exception ignored) {}
            }
        });
    }

	private void runOnUiThread(Object run)
	{
		// TODO: Implement this method
	}

    private void clampPosition() {
        if (windowParams == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        windowParams.x = Math.max(0, Math.min(windowParams.x, screenWidth - windowParams.width));
        windowParams.y = Math.max(0, Math.min(windowParams.y, screenHeight - windowParams.height));
    }

    private void beginDrag(final float screenX, final float screenY) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (windowParams == null) return;
                dragStartScreenX = screenX;
                dragStartScreenY = screenY;
                dragStartX = windowParams.x;
                dragStartY = windowParams.y;
                dragStarted = true;
            }
        });
    }

    private void dragTo(final float screenX, final float screenY) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!dragStarted || windowParams == null || overlayContainer == null || windowManager == null) return;
                windowParams.x = dragStartX + Math.round(screenX - dragStartScreenX);
                windowParams.y = dragStartY + Math.round(screenY - dragStartScreenY);
                clampPosition();
                try { windowManager.updateViewLayout(overlayContainer, windowParams); } catch (Exception ignored) {}
            }
        });
    }

    private void endDrag() { dragStarted = false; }

    /** Native gesture layer: taps remain WebView interactions; long-press-drag moves the whole popup,
     * and a two-finger pinch changes the overlay window size. */
    private class NativePopupContainer extends FrameLayout {
        private final int touchSlop = ViewConfiguration.get(FloatingPlayerService.this).getScaledTouchSlop();
        private float downRawX, downRawY, lastRawX, lastRawY, pinchStartDistance, pinchStartWidth, pinchStartHeight;
        private long downTime;
        private boolean nativeMove, nativePinch, childCancelled;

        NativePopupContainer() { super(FloatingPlayerService.this); setClipChildren(true); setClipToPadding(true); }

        private float pointerDistance(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0f;
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float)Math.sqrt(dx * dx + dy * dy);
        }

        private void cancelChild(MotionEvent event) {
            if (childCancelled) return;
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            childCancelled = true;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downRawX = lastRawX = event.getRawX();
                downRawY = lastRawY = event.getRawY();
                downTime = android.os.SystemClock.uptimeMillis();
                nativeMove = nativePinch = childCancelled = false;
                return super.dispatchTouchEvent(event);
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                nativePinch = true; nativeMove = false; cancelChild(event);
                pinchStartDistance = pointerDistance(event);
                if (windowParams != null) { pinchStartWidth = windowParams.width; pinchStartHeight = windowParams.height; }
                return true;
            }
            if (nativePinch) {
                if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2 && pinchStartDistance > 0f) {
                    float scale = pointerDistance(event) / pinchStartDistance;
                    resizeToScale(scale, pinchStartWidth, pinchStartHeight);
                    return true;
                }
                if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    nativePinch = false; childCancelled = false;
                    return true;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1) {
                float x = event.getRawX(), y = event.getRawY();
                float dx = x - downRawX, dy = y - downRawY;
                if (!nativeMove && android.os.SystemClock.uptimeMillis() - downTime >= 280L &&
                        (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    nativeMove = true; cancelChild(event);
                }
                if (nativeMove && windowParams != null && windowManager != null) {
                    windowParams.x += Math.round(x - lastRawX);
                    windowParams.y += Math.round(y - lastRawY);
                    clampPosition();
                    try { windowManager.updateViewLayout(this, windowParams); } catch (Exception ignored) {}
                    lastRawX = x; lastRawY = y;
                    return true;
                }
            }
            if (nativeMove) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { nativeMove = false; childCancelled = false; }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) childCancelled = false;
            return super.dispatchTouchEvent(event);
        }

        private void resizeToScale(float scale, float startWidth, float startHeight) {
            if (windowParams == null || windowManager == null || Float.isNaN(scale) || Float.isInfinite(scale)) return;
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            windowParams.width = Math.max(dp(210), Math.min(sw - dp(12), Math.round(startWidth * scale)));
            windowParams.height = Math.max(dp(150), Math.min((int)(sh * 0.78f), Math.round(startHeight * scale)));
            clampPosition();
            try { windowManager.updateViewLayout(this, windowParams); } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacks(periodicSave);
        saveProgress(true);
        removeOverlay();
        if (database != null) {
            try { database.close(); } catch (Exception ignored) {}
            database = null;
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(true);
        else stopForeground(true);
        super.onDestroy();
    }

    public class PopupBridge {
        @JavascriptInterface
        public void updateState(double pos, double dur, float rate, boolean isPlaying, boolean isMuted) {
            position = Math.max(0, pos);
            duration = Math.max(0, dur);
            if (rate > 0.1f) playbackRate = rate;
            wasPlaying = isPlaying;
            muted = isMuted;
            saveProgress(false);
        }

        @JavascriptInterface
        public void closeWithState(double pos, double dur, float rate, boolean isPlaying, boolean isMuted) {
            position = Math.max(0, pos);
            duration = Math.max(0, dur);
            if (rate > 0.1f) playbackRate = rate;
            muted = isMuted;
            wasPlaying = false;
            stopOverlay(true, false);
        }

        @JavascriptInterface
        public void returnToApp(double pos, double dur, float rate, boolean isPlaying, boolean isMuted) {
            position = Math.max(0, pos);
            duration = Math.max(0, dur);
            if (rate > 0.1f) playbackRate = rate;
            wasPlaying = isPlaying;
            muted = isMuted;
            FloatingPlayerService.this.returnToApp();
        }

        @JavascriptInterface
        public void beginDrag(float screenX, float screenY) { FloatingPlayerService.this.beginDrag(screenX, screenY); }
        @JavascriptInterface
        public void dragTo(float screenX, float screenY) { FloatingPlayerService.this.dragTo(screenX, screenY); }
        @JavascriptInterface
        public void endDrag() { FloatingPlayerService.this.endDrag(); }
        @JavascriptInterface
        public void resizeBy(float factor) { FloatingPlayerService.this.resizeBy(factor); }
    }
}
