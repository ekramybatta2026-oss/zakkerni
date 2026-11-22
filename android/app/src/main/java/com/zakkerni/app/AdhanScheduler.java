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
import android.media.AudioManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Upcoming prayer times (sent by the web app), the alarm chain (pre-adhan reminder + adhan),
 * the always-on prayer-times notification, and silent mode after the adhan.
 * Entry: {"t": epochMillis, "n": "الظهر", "s": "١١:٣٩ ص", "a": true, "f": "adhan.mp3"}
 */
final class AdhanScheduler {
    static final String PREFS = "zakkerni_adhan";
    static final String KEY = "times";
    static final String KEY_PIN = "pinned";
    static final String KEY_CITY = "city";
    static final String KEY_VOL = "vol";        // 10..100 (% of alarm volume)
    static final String KEY_PRE = "pre";        // minutes before adhan, 0 = off
    static final String KEY_SILENT = "silent";  // minutes of silent mode after adhan, 0 = off
    static final String KEY_FULL = "full";
    static final String KEY_SILENT_DELAY = "silent_delay"; // minutes after the adhan before silent mode starts      // full-screen adhan when locked
    static final String KEY_PREV_RINGER = "prev_ringer";

    static final String CHANNEL = "adhan_v2";
    static final String CHANNEL_TIMES = "prayer_times";
    static final String CHANNEL_PRE = "prayer_pre";
    static final String ACTION_FIRE = "com.zakkerni.app.ADHAN";
    static final String ACTION_RESTORE = "com.zakkerni.app.RESTORE_RINGER";
    static final String ACTION_SILENCE = "com.zakkerni.app.START_SILENCE";
    static final int PIN_ID = 78, PRE_ID = 79;

    private AdhanScheduler() { }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static void save(Context c, String json, boolean pinned, String city) {
        prefs(c).edit().putString(KEY, json).putBoolean(KEY_PIN, pinned).putString(KEY_CITY, city).apply();
    }

    static void saveSettings(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            prefs(c).edit()
                    .putInt(KEY_VOL, o.optInt("vol", 80))
                    .putInt(KEY_PRE, o.optInt("pre", 5))
                    .putInt(KEY_SILENT, o.optInt("silent", 0))
                    .putInt(KEY_SILENT_DELAY, o.optInt("sdelay", 5))
                    .putBoolean(KEY_FULL, o.optBoolean("full", true))
                    .putString(KEY_CITY, o.optString("city", prefs(c).getString(KEY_CITY, "")))
                    .putString("dig", o.optString("dig", "ar"))
                    .putString("tz", o.optString("tz", ""))
                    .apply();
        } catch (Exception ignored) { }
    }

    static JSONArray list(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** First prayer strictly after now (+1.5 s), or null. */
    static JSONObject next(JSONArray a, long now) {
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

    /** The five prayers of the day that `n` belongs to (from that day's Fajr). */
    static List<JSONObject> dayOf(JSONArray a, JSONObject n) {
        long nt = n.optLong("t"), fajr = Long.MIN_VALUE;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null && "الفجر".equals(x.optString("n")) && x.optLong("t") <= nt && x.optLong("t") > fajr) fajr = x.optLong("t");
        }
        if (fajr == Long.MIN_VALUE) fajr = nt;
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            long t = x.optLong("t");
            if (t >= fajr && t < fajr + 22L * 3600 * 1000) out.add(x);
        }
        return out;
    }

    /** Arms one alarm: the earliest of (next pre-adhan reminder, next prayer). */
    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray a = list(c);
        long now = System.currentTimeMillis();
        int pre = prefs(c).getInt(KEY_PRE, 5);

        long when = Long.MAX_VALUE;
        JSONObject ev = null;
        boolean isPre = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("t");
            if (t > now + 1500 && t < when) { when = t; ev = o; isPre = false; }
            if (pre > 0 && o.optBoolean("a", true)) {
                long p = t - pre * 60_000L;
                if (p > now + 1500 && p < when) { when = p; ev = o; isPre = true; }
            }
        }

        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_FIRE);
        if (ev != null) {
            i.putExtra("name", ev.optString("n"));
            i.putExtra("time", ev.optString("s"));
            i.putExtra("adhan", ev.optBoolean("a", true));
            i.putExtra("file", ev.optString("f", "adhan.mp3"));
            i.putExtra("pre", isPre);
            i.putExtra("at", when);
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (ev == null) { am.cancel(pi); return; }
        setClock(c, am, when, pi);
    }

    static void setAlarm(AlarmManager am, long when, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    /** Prayer alarms: alarm-clock alarms are the only kind OEM battery managers (vivo, Xiaomi...) never defer. */
    static void setClock(Context c, AlarmManager am, long when, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); return; }
        try {
            PendingIntent show = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class).putExtra("go", "adhan"), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
        } catch (Exception e) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    /** "Prayer is near" notification. */
    static void showPre(Context c, String name, String time) {
        createChannels(c);
        int pre = prefs(c).getInt(KEY_PRE, 5);
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL_PRE)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        b.setSmallIcon(R.drawable.ic_stat_zal).setColor(Color.parseColor("#F28A32"))
                .setContentTitle("اقترب موعد صلاة " + name)
                .setContentText(WidgetRenderer.digits(c, "باقي " + pre + " دقائق على الأذان — " + time))
                .setContentIntent(open).setAutoCancel(true)
                .setTimeoutAfter(pre * 60_000L)
                .setCategory(Notification.CATEGORY_REMINDER);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try { if (nm != null) nm.notify(PRE_ID, b.build()); } catch (SecurityException ignored) { }
    }

    /** At a prayer time: arm silent mode to start after the chosen delay (default 5 minutes). */
    static void scheduleSilence(Context c) {
        if (prefs(c).getInt(KEY_SILENT, 0) <= 0) return;
        int delay = prefs(c).getInt(KEY_SILENT_DELAY, 5);
        if (delay <= 0) { applySilence(c); return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1004, new Intent(c, AdhanReceiver.class).setAction(ACTION_SILENCE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(am, System.currentTimeMillis() + delay * 60_000L, pi);
    }

    /** Silent mode for the user-chosen minutes, then restore. Needs Do-Not-Disturb access. */
    static void applySilence(Context c) {
        int min = prefs(c).getInt(KEY_SILENT, 0);
        if (min <= 0) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        AudioManager au = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (nm == null || au == null) return;
        if (Build.VERSION.SDK_INT >= 23 && !nm.isNotificationPolicyAccessGranted()) return;
        try {
            int current = au.getRingerMode();
            if (current != AudioManager.RINGER_MODE_SILENT) prefs(c).edit().putInt(KEY_PREV_RINGER, current).apply();
            au.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        } catch (Exception ignored) { return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent r = new Intent(c, AdhanReceiver.class).setAction(ACTION_RESTORE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1002, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(am, System.currentTimeMillis() + min * 60_000L, pi);
    }

    static void restoreRinger(Context c) {
        AudioManager au = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int prev = prefs(c).getInt(KEY_PREV_RINGER, -1);
        if (au == null || prev < 0) return;
        try {
            if (au.getRingerMode() == AudioManager.RINGER_MODE_SILENT) au.setRingerMode(prev); // only undo our own change
        } catch (Exception ignored) { }
        prefs(c).edit().remove(KEY_PREV_RINGER).apply();
    }

    /** The always-on notification: next prayer, live countdown, and the day's times. */
    static void updatePinned(Context c) {
        WidgetRenderer.updateAll(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (!prefs(c).getBoolean(KEY_PIN, false)) { nm.cancel(PIN_ID); return; }
        createChannels(c);
        JSONArray a = list(c);
        JSONObject n = next(a, System.currentTimeMillis());
        if (n == null) { nm.cancel(PIN_ID); return; }

        StringBuilder line = new StringBuilder();
        for (JSONObject o : dayOf(a, n)) {
            if (line.length() > 0) line.append("  •  ");
            line.append(o.optString("n")).append(' ').append(o.optString("s"));
        }
        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        String city = prefs(c).getString(KEY_CITY, "");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL_TIMES)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_LOW);
        b.setSmallIcon(R.drawable.ic_stat_zal).setColor(Color.parseColor("#7137A8"))
                .setContentTitle("الصلاة القادمة: " + n.optString("n") + " " + n.optString("s"))
                .setContentText(city.isEmpty() ? line.toString() : city + " — " + line)
                .setStyle(new Notification.BigTextStyle().bigText(line + (city.isEmpty() ? "" : "\n" + city)))
                .setContentIntent(open).setOngoing(true).setShowWhen(true).setWhen(n.optLong("t"))
                .setUsesChronometer(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        try { nm.notify(PIN_ID, b.build()); } catch (SecurityException ignored) { }
    }

    static void createChannel(Context c) { createChannels(c); }

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel("adhan") != null) nm.deleteNotificationChannel("adhan"); // old channel
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("رفع الأذان عند دخول وقت الصلاة");
            ch.setSound(null, null); // the adhan is played by AdhanService
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_PRE) == null) {
            NotificationChannel p = new NotificationChannel(CHANNEL_PRE, "اقتراب موعد الصلاة", NotificationManager.IMPORTANCE_HIGH);
            p.setDescription("تنبيه قبل الأذان بدقائق");
            nm.createNotificationChannel(p);
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
