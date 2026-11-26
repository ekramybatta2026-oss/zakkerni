package com.zakkerni.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_GEO = 11;
    private static final int REQ_NOTIF = 12;
    private static final int REQ_AUDIO = 13;
    static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";

    static MainActivity current;
    private SensorManager sensors;
    private boolean compassWanted = false;
    private int compassAcc = 3;
    private long lastHeadingPush = 0;
    private final float[] rot = new float[9], ori = new float[3], remapped = new float[9];
    private FrameLayout root;
    private WebView web;
    WebView webView() { return web; }

    static final int REQ_ADHAN_BG = 7701;

    void pickAdhanBg() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_ADHAN_BG);
        } catch (Exception e) {
            try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_ADHAN_BG); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PHOTO) {
            java.util.List<android.net.Uri> u = res == RESULT_OK ? PhotoPickActivity.uris(data) : new java.util.ArrayList<>();
            if (!u.isEmpty() && photoFor != 0) {
                final int id = photoFor;
                new Thread(() -> {
                    int n = PhotoPickActivity.save(this, id, u);
                    runOnUiThread(() -> { android.widget.Toast.makeText(this, "تمت إضافة " + n + (n == 1 ? " صورة" : " صور") + " للويدجت", android.widget.Toast.LENGTH_LONG).show(); moveTaskToBack(true); });
                }).start();
            }
            photoFor = 0;
            return;
        }
        if (req != REQ_ADHAN_BG) return;
        boolean ok = false;
        if (res == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(in);
                if (bm != null) {
                    float s = Math.min(1f, 1600f / Math.max(bm.getWidth(), bm.getHeight()));
                    if (s < 1f) bm = android.graphics.Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * s), Math.round(bm.getHeight() * s), true);
                    try (java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "adhan_bg.jpg"))) {
                        bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, os);
                    }
                    AdhanScheduler.prefs(this).edit().putString("adhan_bg", "custom").apply();
                    ok = true;
                }
            } catch (Exception ignored) { }
        }
        if (web != null) web.evaluateJavascript("window.onAdhanBg && window.onAdhanBg(" + ok + ")", null);
    }
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AdhanScheduler.createChannel(this);

        current = this;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FAF6FD"));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Keep content clear of status / navigation bars (edge-to-edge on Android 15+).
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                // mushaf reading: draw right up to the top edge (under the hidden status bar and the camera cut-out)
                v.setPadding(b.left, immersive ? 0 : b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        applyBars((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocation()) {
                    callback.invoke(origin, true, false);
                } else {
                    geoCallback = callback;
                    geoOrigin = origin;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION}, REQ_GEO);
                }
            }
        });

        web.addJavascriptInterface(new Bridge(this), "AndroidBridge");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else {
            String go = getIntent().getStringExtra("go");
            web.loadUrl(START_URL + (goOk(go) ? "#" + go : ""));
        }
        maybePickPhotos(getIntent());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && web != null) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            web.evaluateJavascript("window.onAudioPermission && window.onAudioPermission(" + ok + ")", null);
        }
        if (requestCode == REQ_GEO && geoCallback != null) {
            geoCallback.invoke(geoOrigin, hasLocation(), false);
            geoCallback = null;
        }
    }

    /** Status/navigation bar colours that match the app theme (light or dark). */
    /** Mushaf reading: hide the status bar (swipe down shows it briefly) and paint the cut-out area like the page. */
    boolean immersive = false;

    void setImmersive(boolean on, String color) {
        immersive = on;
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = on ? (Build.VERSION.SDK_INT >= 30 ? android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
                    : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            getWindow().setAttributes(lp);
        }
        if (root != null) root.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (on) { c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); c.hide(WindowInsets.Type.statusBars()); }
                else c.show(WindowInsets.Type.statusBars());
            }
        } else {
            if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (on && color != null && root != null) { try { root.setBackgroundColor(Color.parseColor(color)); } catch (Exception ignored) { } }
        if (!on) applyBars(darkUi);
    }

    boolean darkUi = false;

    void applyBars(boolean dark) {
        darkUi = dark;
        int bg = Color.parseColor(dark ? "#1D1726" : "#FAF6FD");
        if (root != null) root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(dark ? 0 : light, light);
        } else if (Build.VERSION.SDK_INT >= 26) {
            int f = getWindow().getDecorView().getSystemUiVisibility();
            f = dark ? (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                     : (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    void requestAudio() {
        requestPermissions(new String[]{Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_AUDIO);
    }

    void pushPlayer(String json) {
        if (web != null) web.evaluateJavascript("window.onPlayer && window.onPlayer(" + json + ")", null);
    }

    /* ---------- Qibla compass (rotation-vector sensor) ---------- */
    void startCompass() {
        compassWanted = true;
        if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors == null) return;
        Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (s == null) s = sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
        Sensor mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (mag != null) sensors.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stopCompass() {
        compassWanted = false;
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int t = e.sensor.getType();
        if (t == Sensor.TYPE_MAGNETIC_FIELD) return; // registered only to receive accuracy updates
        long now = System.currentTimeMillis();
        if (now - lastHeadingPush < 50) return;
        lastHeadingPush = now;
        SensorManager.getRotationMatrixFromVector(rot, e.values);
        int r = getWindowManager().getDefaultDisplay().getRotation();
        int ax = SensorManager.AXIS_X, ay = SensorManager.AXIS_Y;
        if (r == Surface.ROTATION_90) { ax = SensorManager.AXIS_Y; ay = SensorManager.AXIS_MINUS_X; }
        else if (r == Surface.ROTATION_180) { ax = SensorManager.AXIS_MINUS_X; ay = SensorManager.AXIS_MINUS_Y; }
        else if (r == Surface.ROTATION_270) { ax = SensorManager.AXIS_MINUS_Y; ay = SensorManager.AXIS_X; }
        SensorManager.remapCoordinateSystem(rot, ax, ay, remapped);
        SensorManager.getOrientation(remapped, ori);
        double deg = (Math.toDegrees(ori[0]) + 360) % 360;
        if (web != null) web.evaluateJavascript("window.onHeading && window.onHeading(" + String.format(java.util.Locale.US, "%.1f", deg) + "," + compassAcc + ")", null);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) compassAcc = accuracy;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (web != null) web.evaluateJavascript("window.onSystemTheme && window.onSystemTheme()", null);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    static final int REQ_PHOTO = 7702;
    private int photoFor = 0;

    private void maybePickPhotos(Intent i) {
        int id = i == null ? 0 : i.getIntExtra("photo_pick", 0);
        if (id == 0) return;
        i.removeExtra("photo_pick");
        photoFor = id;
        try { startActivityForResult(PhotoPickActivity.picker(), REQ_PHOTO); }
        catch (Exception e) { try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_PHOTO); } catch (Exception ignored) { } }
        android.widget.Toast.makeText(this, "اختر صورة أو أكثر للويدجت", android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        maybePickPhotos(intent);
        String go = intent.getStringExtra("go");
        if (web != null && goOk(go))
            web.evaluateJavascript("window.zGo && window.zGo('" + go + "')", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (compassWanted) startCompass();
        AdhanScheduler.updatePinned(this);
        if (web != null) web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    static boolean goOk(String g) {
        return g != null && java.util.Arrays.asList("adhan", "player", "clock", "weather", "alarm", "timer", "stopwatch", "calendar").contains(g);
    }
}
