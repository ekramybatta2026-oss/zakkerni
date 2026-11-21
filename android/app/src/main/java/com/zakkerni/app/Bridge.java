package com.zakkerni.app;

import android.app.AlarmManager;
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
}
