package com.zakkerni.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keeps the list of upcoming prayer times (sent by the web app), arms one alarm for the
 * next prayer, and maintains the always-on "prayer times" notification.
 * Entry format: {"t": epochMillis, "n": "الظهر", "s": "١١:٣٩ ص", "a": true, "f": "adhan.mp3"}
 */
final class AdhanScheduler {
    static final String PREFS = "zakkerni_adhan";
    static final String KEY = "times";
    static final String KEY_PIN = "pinned";
    static final String KEY_CITY = "city";
    static final String CHANNEL = "adhan";
    static final String CHANNEL_TIMES = "prayer_times";
    static final String ACTION_FIRE = "com.zakkerni.app.ADHAN";
    static final int PIN_ID = 78;

    private AdhanScheduler() { }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static void save(Context c, String json, boolean pinned, String city) {
        prefs(c).edit().putString(KEY, json).putBoolean(KEY_PIN, pinned).putString(KEY_CITY, city).apply();
    }

    private static JSONArray list(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** First entry strictly after now (+1.5 s), or null. */
    private static JSONObject next(JSONArray a, long now) {
        JSONObject best = null;
        long bt = Long.MAX_VALUE;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("t");
            if (t > now + 1500 && t < bt) { bt = t; best = o; }
        }
        return best;
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONObject n = next(list(c), System.currentTimeMillis());

        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_FIRE);
        if (n != null) {
            i.putExtra("name", n.optString("n"));
            i.putExtra("adhan", n.optBoolean("a", true));
            i.putExtra("file", n.optString("f", "adhan.mp3"));
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (n == null) { am.cancel(pi); return; }
        long when = n.optLong("t");

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    /** The always-on notification: next prayer, live countdown, and the day's times. */
    static void updatePinned(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (!prefs(c).getBoolean(KEY_PIN, true)) { nm.cancel(PIN_ID); return; }
        createChannels(c);

        JSONArray a = list(c);
        long now = System.currentTimeMillis();
        JSONObject n = next(a, now);
        if (n == null) { nm.cancel(PIN_ID); return; }

        // the five prayers of the day the next prayer belongs to
        long dayStart = n.optLong("t") - 20L * 3600 * 1000;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("t");
            if (t >= dayStart && t <= n.optLong("t") + 20L * 3600 * 1000 && sameDay(o, n, a)) {
                if (line.length() > 0) line.append("  •  ");
                line.append(o.optString("n")).append(' ').append(o.optString("s"));
            }
        }

        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        String city = prefs(c).getString(KEY_CITY, "");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL_TIMES)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_LOW);
        b.setSmallIcon(R.drawable.ic_stat_mosque)
                .setColor(Color.parseColor("#7137A8"))
                .setContentTitle("الصلاة القادمة: " + n.optString("n") + " " + n.optString("s"))
                .setContentText(city.isEmpty() ? line.toString() : city + " — " + line)
                .setStyle(new Notification.BigTextStyle().bigText(line.toString() + (city.isEmpty() ? "" : "\n" + city)))
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(n.optLong("t"))
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        try { nm.notify(PIN_ID, b.build()); } catch (SecurityException ignored) { }
    }

    /** Entries belong to the same prayer day if they fall between that day's Fajr and Isha. */
    private static boolean sameDay(JSONObject o, JSONObject n, JSONArray a) {
        // find the Fajr at or before n, then take the 5 entries starting there
        long nt = n.optLong("t");
        long fajr = Long.MIN_VALUE;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null && "الفجر".equals(x.optString("n")) && x.optLong("t") <= nt && x.optLong("t") > fajr) fajr = x.optLong("t");
        }
        if (fajr == Long.MIN_VALUE) fajr = nt;
        long t = o.optLong("t");
        return t >= fajr && t < fajr + 22L * 3600 * 1000;
    }

    static void createChannel(Context c) { createChannels(c); }

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيه دخول وقت الصلاة ورفع الأذان");
            ch.setSound(null, null); // the adhan itself is played by AdhanService
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_TIMES) == null) {
            NotificationChannel t = new NotificationChannel(CHANNEL_TIMES, "مواقيت الصلاة", NotificationManager.IMPORTANCE_LOW);
            t.setDescription("إشعار دائم بالصلاة القادمة ومواقيت اليوم");
            t.setShowBadge(false);
            t.setSound(null, null);
            nm.createNotificationChannel(t);
        }
    }
}
