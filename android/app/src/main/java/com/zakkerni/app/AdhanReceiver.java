package com.zakkerni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (WidgetRenderer.ACTION_TICK.equals(action)) {
            WidgetRenderer.updateAll(c);
            return;
        }
        if (AdhanScheduler.ACTION_SILENCE.equals(action)) {
            AdhanScheduler.applySilence(c);
            return;
        }
        if (AdhanScheduler.ACTION_RESTORE.equals(action)) {
            AdhanScheduler.restoreRinger(c);
            return;
        }
        if (action != null && !AdhanScheduler.ACTION_FIRE.equals(action)) AdhanLog.add(c, "حدث النظام: " + action.substring(action.lastIndexOf('.') + 1));
        if (AdhanScheduler.ACTION_FIRE.equals(action) && intent.getStringExtra("name") == null) AdhanLog.add(c, "منبّه بدون بيانات صلاة");
        if (AdhanScheduler.ACTION_FIRE.equals(action) && intent.getStringExtra("name") != null) {
            String name = intent.getStringExtra("name");
            boolean pre = intent.getBooleanExtra("pre", false);
            long late = System.currentTimeMillis() - intent.getLongExtra("at", System.currentTimeMillis());
            AdhanLog.add(c, "وصل " + (pre ? "تنبيه قبل " : "أذان ") + name + (late > 30_000 ? " متأخرًا " + (late / 60_000) + " دقيقة" : " في موعده")
                    + (intent.getBooleanExtra("adhan", true) ? "" : " (الأذان مطفأ)"));
            if (late > (pre ? 3 : 10) * 60_000L) AdhanLog.add(c, "تم تجاهله لأنه تأخّر كثيرًا");
            // the system delivered this alarm late (phone asleep under a battery manager): never play a stale adhan
            if (late > (pre ? 3 : 10) * 60_000L) {
                if (!pre) AdhanScheduler.scheduleSilence(c, name, intent.getLongExtra("at", System.currentTimeMillis())); // still silence the phone for the rest of the prayer time
                AdhanScheduler.updatePinned(c);
                AdhanScheduler.scheduleNext(c);
                return;
            }
            if (pre) {
                AdhanScheduler.showPre(c, name, intent.getStringExtra("time"));
            } else {
                AdhanScheduler.scheduleSilence(c, name, intent.getLongExtra("at", System.currentTimeMillis())); // silent mode N minutes after the prayer time
            }
            if (!pre && intent.getBooleanExtra("adhan", true)) {
                Intent s = new Intent(c, AdhanService.class)
                        .putExtra("name", name)
                        .putExtra("time", intent.getStringExtra("time"))
                        .putExtra("file", intent.getStringExtra("file"));
                try {
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                    else c.startService(s);
                } catch (Exception ignored) { }
            }
        }
        // after any event, boot, time change or update: refresh notification/widget, arm the next event
        AdhanScheduler.updatePinned(c);
        AdhanScheduler.scheduleNext(c);
    }
}
