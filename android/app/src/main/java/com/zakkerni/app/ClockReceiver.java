package com.zakkerni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Alarm / timer firing, widget buttons, and re-arming after boot or updates. */
public class ClockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (ClockStore.A_ALARM.equals(a)) {
            int id = intent.getIntExtra("id", 0);
            ClockStore.afterRing(c, id);
            ring(c, "alarm", id, intent.getStringExtra("label"));
            WidgetRenderer.updateAll(c);
            return;
        }
        if (ClockStore.A_TIMER.equals(a)) {
            boolean wasRunning = ClockStore.p(c).getBoolean("t_run", false);
            long late = System.currentTimeMillis() - ClockStore.p(c).getLong("t_end", 0);
            ClockStore.timerCmd(c, "done", 0);
            if (wasRunning && late < 10 * 60_000L) ring(c, "timer", 0, "");
            return;
        }
        if (ClockStore.A_CMD.equals(a)) {
            String w = intent.getStringExtra("w"), cmd = intent.getStringExtra("c");
            if (w == null || cmd == null) return;
            if (w.equals("timer")) ClockStore.timerCmd(c, cmd, intent.getLongExtra("arg", 60_000L));
            else if (w.equals("sw")) ClockStore.swCmd(c, cmd);
            else if (w.equals("cal")) {
                int wid = intent.getIntExtra("wid", 0);
                int off = ClockStore.p(c).getInt("cal_" + wid, 0);
                off = cmd.equals("next") ? off + 1 : cmd.equals("prev") ? off - 1 : 0;
                ClockStore.p(c).edit().putInt("cal_" + wid, off).apply();
                WidgetRenderer.updateAll(c);
            }
            return;
        }
        // boot / update / time change
        ClockStore.scheduleAlarms(c);
        ClockStore.armTimer(c);
    }

    static void ring(Context c, String kind, int id, String label) {
        try {
            boolean fs = Build.VERSION.SDK_INT < 34 || ((android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).canUseFullScreenIntent();
            boolean ov = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(c);
            boolean locked = ((android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
            AdhanLog.add(c, ("timer".equals(kind) ? "رنّ المؤقت" : "رنّ المنبه") + " — ملء الشاشة: " + (fs ? "مسموح" : "غير مسموح")
                    + "، فوق التطبيقات: " + (ov ? "مسموح" : "غير مسموح") + "، الجوال " + (locked ? "مقفول" : "مفتوح"));
        } catch (Exception ignored) { }
        RingService.kind = kind; RingService.alarmId = id; RingService.label = label == null ? "" : label;
        Intent s = new Intent(c, RingService.class).putExtra("kind", kind).putExtra("id", id).putExtra("label", label == null ? "" : label);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) { }
        // also try to open the ring screen directly (allowed while the alarm's grace window is open on most phones)
        try {
            c.startActivity(new Intent(c, RingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        } catch (Exception e) { AdhanLog.add(c, "تعذّر فتح شاشة المنبه: " + e.getClass().getSimpleName()); }
    }
}
