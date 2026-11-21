package com.zakkerni.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the list of upcoming adhan times and arms one alarm for the next one. */
final class AdhanScheduler {
    static final String PREFS = "zakkerni_adhan";
    static final String KEY = "times";
    static final String CHANNEL = "adhan";
    static final String ACTION_FIRE = "com.zakkerni.app.ADHAN";

    private AdhanScheduler() { }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        String name = null;
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long t = o.getLong("t");
                if (t > now + 1500 && t < best) { best = t; name = o.getString("n"); }
            }
        } catch (Exception ignored) { }

        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_FIRE);
        if (name != null) i.putExtra("name", name);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (name == null) { am.cancel(pi); return; }

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, best, pi);
        }
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("تنبيه دخول وقت الصلاة ورفع الأذان");
        ch.setSound(null, null); // the adhan itself is played by AdhanService
        nm.createNotificationChannel(ch);
    }
}
