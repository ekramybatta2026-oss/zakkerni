package com.zakkerni.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Alarms, timer and stopwatch state (shared by the app page and the widgets).
 * Alarms: [{id,h,m,days:[0..6 = Sun..Sat],label,on}] — empty days = one-time.
 */
final class ClockStore {
    static final String PREFS = "zakkerni_clock";
    static final String A_ALARM = "com.zakkerni.app.CLOCK_ALARM";
    static final String A_TIMER = "com.zakkerni.app.CLOCK_TIMER";
    static final String A_CMD = "com.zakkerni.app.CLOCK_CMD";
    static final long SNOOZE = 10 * 60_000L;

    private ClockStore() { }

    static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /* ---------------- alarms ---------------- */
    static JSONArray alarms(Context c) {
        try { return new JSONArray(p(c).getString("alarms", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static void setAlarms(Context c, String json) {
        try { new JSONArray(json); } catch (Exception e) { return; }
        p(c).edit().putString("alarms", json).apply();
        scheduleAlarms(c);
        WidgetRenderer.updateAll(c);
    }

    /** Next ring time of one alarm after `from` (0 if disabled). */
    static long nextOf(JSONObject a, long from) {
        if (a == null || !a.optBoolean("on", true)) return 0;
        JSONArray days = a.optJSONArray("days");
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        cal.set(Calendar.HOUR_OF_DAY, a.optInt("h"));
        cal.set(Calendar.MINUTE, a.optInt("m"));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (cal.getTimeInMillis() > from + 1000) {
                int dow = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
                boolean ok = days == null || days.length() == 0;
                if (!ok) for (int k = 0; k < days.length(); k++) if (days.optInt(k) == dow) { ok = true; break; }
                if (ok) return cal.getTimeInMillis();
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    /** {time, alarm} of the soonest alarm (snoozes included). */
    static Object[] nextAlarm(Context c) {
        JSONArray a = alarms(c);
        long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        JSONObject who = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            long t = nextOf(o, now);
            long sz = o == null ? 0 : p(c).getLong("snz_" + o.optInt("id"), 0);
            if (sz > now && sz < (t == 0 ? Long.MAX_VALUE : t)) t = sz;
            if (t > 0 && t < best) { best = t; who = o; }
        }
        return who == null ? null : new Object[]{best, who};
    }

    private static PendingIntent alarmPi(Context c, int id, String label, boolean create) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(A_ALARM).putExtra("id", id).putExtra("label", label == null ? "" : label);
        int flags = PendingIntent.FLAG_IMMUTABLE | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(c, 5000 + Math.floorMod(id, 4000), i, flags);
    }

    static void scheduleAlarms(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // cancel what was armed before
        try {
            JSONArray old = new JSONArray(p(c).getString("armed", "[]"));
            for (int i = 0; i < old.length(); i++) { PendingIntent pi = alarmPi(c, old.optInt(i), "", false); if (pi != null) { am.cancel(pi); pi.cancel(); } }
        } catch (Exception ignored) { }
        JSONArray armed = new JSONArray();
        JSONArray a = alarms(c);
        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = nextOf(o, now);
            long sz = p(c).getLong("snz_" + o.optInt("id"), 0);
            if (sz > now && (t == 0 || sz < t)) t = sz;
            if (t == 0) continue;
            AdhanScheduler.setClock(c, am, t, alarmPi(c, o.optInt("id"), o.optString("label"), true));
            armed.put(o.optInt("id"));
        }
        p(c).edit().putString("armed", armed.toString()).apply();
    }

    /** Called when an alarm rang: one-time alarms switch off, snooze is cleared. */
    static void afterRing(Context c, int id) {
        p(c).edit().remove("snz_" + id).apply();
        JSONArray a = alarms(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("id") == id) {
                JSONArray d = o.optJSONArray("days");
                if (d == null || d.length() == 0) { try { o.put("on", false); } catch (Exception ignored) { } }
            }
        }
        p(c).edit().putString("alarms", a.toString()).apply();
        scheduleAlarms(c);
    }

    static void snooze(Context c, int id) {
        p(c).edit().putLong("snz_" + id, System.currentTimeMillis() + SNOOZE).apply();
        scheduleAlarms(c);
    }

    static String toneFor(Context c, String kind, int id) {
        if ("timer".equals(kind)) return p(c).getString("t_tone", "");
        JSONArray a = alarms(c);
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && o.optInt("id") == id) return o.optString("tone", ""); }
        return "";
    }

    /* ---------------- timer ---------------- */
    static long timerLeft(Context c) {
        SharedPreferences s = p(c);
        if (s.getBoolean("t_run", false)) return Math.max(0, s.getLong("t_end", 0) - System.currentTimeMillis());
        return s.getLong("t_left", 0);
    }

    static void timerCmd(Context c, String cmd, long arg) {
        SharedPreferences s = p(c);
        SharedPreferences.Editor e = s.edit();
        long now = System.currentTimeMillis();
        boolean run = s.getBoolean("t_run", false);
        switch (cmd) {
            case "start":
                if (arg <= 0) return;
                e.putLong("t_total", arg).putLong("t_end", now + arg).putBoolean("t_run", true).putLong("t_left", arg);
                break;
            case "toggle":
                if (run) e.putLong("t_left", Math.max(0, s.getLong("t_end", 0) - now)).putBoolean("t_run", false);
                else {
                    long left = s.getLong("t_left", 0);
                    if (left <= 0) left = s.getLong("t_total", 0);
                    if (left <= 0) return;
                    e.putLong("t_end", now + left).putBoolean("t_run", true);
                }
                break;
            case "pause":
                if (run) e.putLong("t_left", Math.max(0, s.getLong("t_end", 0) - now)).putBoolean("t_run", false);
                break;
            case "reset":
                e.putBoolean("t_run", false).putLong("t_left", s.getLong("t_total", 0));
                break;
            case "add":
                if (run) e.putLong("t_end", s.getLong("t_end", 0) + arg);
                else {
                    long left = s.getLong("t_left", 0) + arg;
                    e.putLong("t_left", left);
                    if (s.getLong("t_total", 0) <= 0) e.putLong("t_total", left);
                }
                break;
            case "done":
                e.putBoolean("t_run", false).putLong("t_left", s.getLong("t_total", 0));
                break;
            default: return;
        }
        e.apply();
        armTimer(c);
        WidgetRenderer.updateAll(c);
    }

    static void armTimer(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 7000, new Intent(c, ClockReceiver.class).setAction(A_TIMER),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (p(c).getBoolean("t_run", false)) AdhanScheduler.setClock(c, am, p(c).getLong("t_end", 0), pi);
        else am.cancel(pi);
    }

    /* ---------------- stopwatch ---------------- */
    static long swElapsed(Context c) {
        SharedPreferences s = p(c);
        long acc = s.getLong("s_acc", 0);
        return s.getBoolean("s_run", false) ? acc + System.currentTimeMillis() - s.getLong("s_start", 0) : acc;
    }

    static void swCmd(Context c, String cmd) {
        SharedPreferences s = p(c);
        SharedPreferences.Editor e = s.edit();
        long now = System.currentTimeMillis();
        boolean run = s.getBoolean("s_run", false);
        switch (cmd) {
            case "toggle":
                if (run) e.putLong("s_acc", swElapsed(c)).putBoolean("s_run", false);
                else e.putLong("s_start", now).putBoolean("s_run", true);
                break;
            case "reset":
                e.putBoolean("s_run", false).putLong("s_acc", 0).putString("s_laps", "[]");
                break;
            case "lap":
                if (!run) return;
                try {
                    JSONArray l = new JSONArray(s.getString("s_laps", "[]"));
                    l.put(swElapsed(c));
                    e.putString("s_laps", l.toString());
                } catch (Exception ignored) { }
                break;
            default: return;
        }
        e.apply();
        WidgetRenderer.updateAll(c);
    }

    static JSONArray laps(Context c) {
        try { return new JSONArray(p(c).getString("s_laps", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** Everything the app page needs, as JSON. */
    static String state(Context c) {
        SharedPreferences s = p(c);
        try {
            JSONObject o = new JSONObject();
            o.put("now", System.currentTimeMillis());
            o.put("alarms", alarms(c));
            JSONObject t = new JSONObject();
            t.put("run", s.getBoolean("t_run", false)).put("left", timerLeft(c)).put("total", s.getLong("t_total", 0)).put("tone", s.getString("t_tone", "")).put("toneN", s.getString("t_tone_n", ""));
            o.put("timer", t);
            JSONObject w = new JSONObject();
            w.put("run", s.getBoolean("s_run", false)).put("el", swElapsed(c)).put("laps", laps(c));
            o.put("sw", w);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }
}
