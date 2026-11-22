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

    /** Ask the launcher to add a widget: 0 small, 1 wide, 2 large, 3 list. */
    @JavascriptInterface
    public boolean pinWidget(int type) {
        if (Build.VERSION.SDK_INT < 26) return false;
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        if (!m.isRequestPinAppWidgetSupported()) return false;
        try { return m.requestPinAppWidget(new ComponentName(ctx, WidgetRenderer.PROVIDERS[Math.max(0, Math.min(3, type))]), null, null); }
        catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void startCompass() { MainActivity a = MainActivity.current; if (a != null) a.runOnUiThread(a::startCompass); }

    @JavascriptInterface
    public void stopCompass() { MainActivity a = MainActivity.current; if (a != null) a.runOnUiThread(a::stopCompass); }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception e) {
            try { ctx.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        }
    }
}
