package com.zakkerni.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/** Methods the web app can call as window.AndroidBridge.* */
public class Bridge {
    private final Context ctx;

    Bridge(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    @JavascriptInterface
    public void vibrate(int ms) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }

    /** json: [{"t": epochMillis, "n": "الظهر", "s": "١١:٣٩ ص", "a": true, "f": "adhan.mp3"}, ...] */
    private static android.media.MediaPlayer tonePlayer;
    private static final android.os.Handler toneH = new android.os.Handler(android.os.Looper.getMainLooper());

    /** System alarm / ringtone / notification sounds: [{u,t,k}] */
    @JavascriptInterface
    public String tones() {
        org.json.JSONArray out = new org.json.JSONArray();
        int[] types = {android.media.RingtoneManager.TYPE_ALARM, android.media.RingtoneManager.TYPE_RINGTONE, android.media.RingtoneManager.TYPE_NOTIFICATION};
        String[] kinds = {"alarm", "ring", "notif"};
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int k = 0; k < types.length; k++) {
            try {
                android.media.RingtoneManager rm = new android.media.RingtoneManager(ctx);
                rm.setType(types[k]);
                android.database.Cursor cu = rm.getCursor();
                while (cu.moveToNext()) {
                    String u = cu.getString(android.media.RingtoneManager.URI_COLUMN_INDEX) + "/" + cu.getString(android.media.RingtoneManager.ID_COLUMN_INDEX);
                    if (!seen.add(u)) continue;
                    out.put(new org.json.JSONObject().put("u", u).put("t", cu.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)).put("k", kinds[k]));
                }
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public void previewTone(String u) {
        toneH.post(() -> {
            stopToneNow();
            try {
                tonePlayer = RingService.player(ctx, u, false);
                if (tonePlayer != null) { tonePlayer.start(); toneH.postDelayed(Bridge::stopToneNow, 7000); }
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void stopTone() { toneH.post(Bridge::stopToneNow); }

    private static void stopToneNow() {
        toneH.removeCallbacksAndMessages(null);
        if (tonePlayer != null) { try { tonePlayer.stop(); } catch (Exception ignored) { } tonePlayer.release(); tonePlayer = null; }
    }

    @JavascriptInterface
    public void setTimerTone(String u, String name) { ClockStore.p(ctx).edit().putString("t_tone", u).putString("t_tone_n", name).apply(); }

    @JavascriptInterface
    public String clockState() { return ClockStore.state(ctx); }

    @JavascriptInterface
    public void setAlarms(String json) { ClockStore.setAlarms(ctx, json); }

    @JavascriptInterface
    public void timerCmd(String cmd, double arg) { ClockStore.timerCmd(ctx, cmd, (long) arg); }

    @JavascriptInterface
    public void swCmd(String cmd) { ClockStore.swCmd(ctx, cmd); }

    @JavascriptInterface
    public void setLocation(double lat, double lng) {
        Weather.setLocation(ctx, lat, lng);
    }

    @JavascriptInterface
    public void scheduleAdhans(String json, boolean pinned, String city) {
        AdhanScheduler.save(ctx, json, pinned, city == null ? "" : city);
        AdhanScheduler.updatePinned(ctx);
        AdhanScheduler.scheduleNext(ctx);
    }

    @JavascriptInterface
    public boolean canExact() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    @JavascriptInterface
    public void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void stopAdhan() {
        ctx.stopService(new Intent(ctx, AdhanService.class));
    }

    @JavascriptInterface
    public boolean isNative() { return true; }

    /** {"vol":80,"pre":5,"silent":0,"full":true,"city":"الرياض"} */
    @JavascriptInterface
    public void setAdhanSettings(String json) {
        AdhanScheduler.saveSettings(ctx, json);
        AdhanScheduler.updatePinned(ctx);
        AdhanScheduler.scheduleNext(ctx);
    }

    @JavascriptInterface
    public boolean canFullScreen() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.canUseFullScreenIntent();
    }

    @JavascriptInterface
    public void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.getPackageName())));
    }

    /** "Display over other apps": lets the alarm / adhan screen open over the lock screen from the background. */
    @JavascriptInterface
    public boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx); }

    @JavascriptInterface
    public void openOverlaySettings() {
        open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.getPackageName())));
    }

    /** vivo's own permission page (background pop-ups, lock-screen display); falls back to app details. */
    @JavascriptInterface
    public void openVendorPermissions() {
        String pkg = ctx.getPackageName();
        Intent[] tries = {
                new Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity").putExtra("packagename", pkg),
                new Intent("permission.intent.action.softPermissionDetail").putExtra("packagename", pkg),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))};
        for (Intent i : tries) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return; } catch (Exception ignored) { }
        }
    }

    @JavascriptInterface
    public boolean hasDnd() {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    @JavascriptInterface
    public void openDndSettings() { open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }

    @JavascriptInterface
    public boolean isIgnoringBattery() {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    @JavascriptInterface
    public void openBatterySettings() { open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }

    @JavascriptInterface
    public boolean isSystemDark() {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @JavascriptInterface
    public void setDarkBars(boolean dark) {
        MainActivity a = MainActivity.current;
        if (a != null) a.runOnUiThread(() -> a.applyBars(dark));
    }

    /** Share a PNG rendered by the page (data: URL) through the system share sheet. */
    @JavascriptInterface
    public boolean shareImage(String dataUrl, String text) {
        try {
            String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            java.io.File dir = new java.io.File(ctx.getCacheDir(), "share");
            dir.mkdirs();
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File o : old) o.delete();
            java.io.File f = new java.io.File(dir, "zakkerni-" + System.currentTimeMillis() + ".png");
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) { os.write(bytes); }
            Uri u = androidx.core.content.FileProvider.getUriForFile(ctx, "com.zakkerni.app.files", f);
            Intent s = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (text != null && !text.isEmpty()) s.putExtra(Intent.EXTRA_TEXT, text);
            s.setClipData(android.content.ClipData.newRawUri("", u));
            Intent ch = Intent.createChooser(s, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Context a = MainActivity.current != null ? MainActivity.current : ctx;
            a.startActivity(ch);
            return true;
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void setImmersive(boolean on, String color) {
        MainActivity a = MainActivity.current;
        if (a != null) a.runOnUiThread(() -> a.setImmersive(on, color));
    }

    /** Ask the launcher to add a widget: 0 small, 1 wide, 2 large, 3 list. */
    @JavascriptInterface
    public boolean pinWidget(int type) {
        if (Build.VERSION.SDK_INT < 26) return false;
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        if (!m.isRequestPinAppWidgetSupported()) return false;
        try { return m.requestPinAppWidget(new ComponentName(ctx, WidgetRenderer.PROVIDERS[Math.max(0, Math.min(WidgetRenderer.PROVIDERS.length - 1, type))]), null, null); }
        catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void startCompass() { MainActivity a = MainActivity.current; if (a != null) a.runOnUiThread(a::startCompass); }

    @JavascriptInterface
    public void stopCompass() { MainActivity a = MainActivity.current; if (a != null) a.runOnUiThread(a::stopCompass); }

    /* ---------- audio player ---------- */
    @JavascriptInterface
    public boolean hasAudioPermission() {
        String p = Build.VERSION.SDK_INT >= 33 ? android.Manifest.permission.READ_MEDIA_AUDIO : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        return ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void requestAudioPermission() { MainActivity a = MainActivity.current; if (a != null) a.runOnUiThread(a::requestAudio); }

    /** All audio files on the phone: [{i:id,t:title,a:artist,al:album,d:durationMs,f:folder}] */
    @JavascriptInterface
    public String getLibrary() {
        org.json.JSONArray out = new org.json.JSONArray();
        String[] proj = Build.VERSION.SDK_INT >= 29
                ? new String[]{"_id", "title", "artist", "album", "duration", "relative_path"}
                : new String[]{"_id", "title", "artist", "album", "duration", "_data"};
        try (android.database.Cursor c = ctx.getContentResolver().query(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "duration >= 3000", null, "title COLLATE NOCASE")) {
            while (c != null && c.moveToNext()) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("i", c.getLong(0)); o.put("t", c.getString(1)); o.put("a", c.getString(2)); o.put("al", c.getString(3)); o.put("d", c.getLong(4));
                String f = c.getString(5);
                if (f != null && Build.VERSION.SDK_INT < 29) { int s = f.lastIndexOf('/'); f = s > 0 ? f.substring(0, s) : f; int t = f.lastIndexOf('/'); f = t >= 0 ? f.substring(t + 1) : f; }
                else if (f != null) { f = f.replaceAll("/+$", ""); int t = f.lastIndexOf('/'); f = t >= 0 ? f.substring(t + 1) : f; }
                o.put("f", f == null ? "" : f);
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public void playerQueue(String idsCsv, int start) {
        String[] parts = idsCsv.split(",");
        long[] ids = new long[parts.length];
        try { for (int k = 0; k < parts.length; k++) ids[k] = Long.parseLong(parts[k].trim()); } catch (Exception e) { return; }
        send(new Intent(ctx, PlayerService.class).setAction(PlayerService.A_QUEUE).putExtra("ids", ids).putExtra("start", start));
    }

    /** cmd: toggle|play|pause|next|prev|stop|seek|shuffle|repeat|speed|sleep|eq|jump */
    @JavascriptInterface
    public void playerCmd(String cmd, String arg) {
        Intent i = new Intent(ctx, PlayerService.class);
        switch (cmd) {
            case "toggle": i.setAction(PlayerService.A_TOGGLE); break;
            case "play": i.setAction(PlayerService.A_PLAY); break;
            case "pause": i.setAction(PlayerService.A_PAUSE); break;
            case "next": i.setAction(PlayerService.A_NEXT); break;
            case "prev": i.setAction(PlayerService.A_PREV); break;
            case "stop": i.setAction(PlayerService.A_STOP); break;
            case "seek": i.setAction(PlayerService.A_SEEK).putExtra("ms", (long) num(arg)); break;
            case "jump": i.setAction(PlayerService.A_JUMP).putExtra("pos", (int) num(arg)); break;
            case "shuffle": i.setAction(PlayerService.A_SHUFFLE).putExtra("on", "1".equals(arg)); break;
            case "repeat": i.setAction(PlayerService.A_REPEAT).putExtra("mode", (int) num(arg)); break;
            case "speed": i.setAction(PlayerService.A_SPEED).putExtra("v", (float) num(arg)); break;
            case "sleep": i.setAction(PlayerService.A_SLEEP).putExtra("min", "end".equals(arg) ? 0 : (int) num(arg)).putExtra("end", "end".equals(arg)); break;
            case "eq": i.setAction(PlayerService.A_EQ).putExtra("preset", (int) num(arg)); break;
            default: return;
        }
        if (PlayerService.inst == null && ("stop".equals(cmd) || "pause".equals(cmd))) return;
        send(i);
    }

    @JavascriptInterface
    public String playerState() { PlayerService p = PlayerService.inst; return p != null ? p.stateJson() : "{}"; }

    @JavascriptInterface
    public String eqPresets() { return PlayerService.eqPresets(ctx); }

    private double num(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }

    private void send(Intent i) { try { ctx.startService(i); } catch (Exception e) { try { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i.putExtra("fg", true)); } catch (Exception ignored) { } } }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception e) {
            try { ctx.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        }
    }
}
