package com.zakkerni.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Alarm, timer, stopwatch and calendar widgets. */
final class ClockWidgets {
    private ClockWidgets() { }

    static RemoteViews build(Context c, WidgetRenderer.Cfg g, int type, int widgetId) {
        if (type == WidgetRenderer.T_CLOCK) return clockFace(c, g);
        int lay = type == WidgetRenderer.T_ALARM ? R.layout.widget_alarm : type == WidgetRenderer.T_TIMER ? R.layout.widget_timer
                : type == WidgetRenderer.T_SW ? R.layout.widget_sw : R.layout.widget_cal;
        RemoteViews v = new RemoteViews(c.getPackageName(), lay);
        WidgetRenderer.applyBg(v, g);
        boolean dark = WidgetRenderer.darkText(g);
        int main = dark ? 0xFF2A1F36 : 0xFFFFFFFF, sub = dark ? 0xFF6B5E7B : 0xEEFFF0E2, hi = dark ? 0xFFC2580C : 0xFFFFB066;
        float f = g.font == 0 ? 0.85f : g.font == 2 ? 1.2f : 1f;
        String go = type == WidgetRenderer.T_ALARM ? "alarm" : type == WidgetRenderer.T_TIMER ? "timer" : type == WidgetRenderer.T_SW ? "stopwatch" : "calendar";
        v.setOnClickPendingIntent(R.id.widget_root, open(c, go, 90 + type));

        if (type == WidgetRenderer.T_ALARM) {
            v.setInt(R.id.c_icon, "setColorFilter", hi);
            Object[] n = ClockStore.nextAlarm(c);
            txt(v, R.id.c_label, "المنبه القادم", sub, 12 * f);
            if (n == null) {
                txt(v, R.id.c_big, "--:--", main, 30 * f);
                txt(v, R.id.c_sub, "لا يوجد منبه مفعّل", sub, 11 * f);
            } else {
                long t = (Long) n[0];
                JSONObject a = (JSONObject) n[1];
                txt(v, R.id.c_big, WidgetRenderer.fmtTime(c, t, g.fmt == 0 ? 1 : g.fmt), main, 30 * f);
                String lbl = a.optString("label");
                txt(v, R.id.c_sub, dayWord(t) + (lbl.isEmpty() ? "" : " · " + lbl), sub, 11 * f);
            }
            return v;
        }

        if (type == WidgetRenderer.T_TIMER || type == WidgetRenderer.T_SW) {
            boolean timer = type == WidgetRenderer.T_TIMER;
            android.content.SharedPreferences p = ClockStore.p(c);
            boolean run = p.getBoolean(timer ? "t_run" : "s_run", false);
            long val = timer ? ClockStore.timerLeft(c) : ClockStore.swElapsed(c);
            txt(v, R.id.c_label, timer ? "المؤقت" : "ساعة الإيقاف", sub, 12 * f);
            long base = timer ? SystemClock.elapsedRealtime() + val : SystemClock.elapsedRealtime() - val;
            v.setChronometer(R.id.c_chrono, base, null, run);
            if (timer) v.setChronometerCountDown(R.id.c_chrono, true);
            v.setTextColor(R.id.c_chrono, main);
            v.setTextViewTextSize(R.id.c_chrono, TypedValue.COMPLEX_UNIT_DIP, 30 * f);
            String sub2 = "";
            if (!timer) {
                JSONArray l = ClockStore.laps(c);
                if (l.length() > 0) sub2 = "لفة " + l.length() + " · " + clock(l.optLong(l.length() - 1));
            } else if (!run && p.getLong("t_total", 0) > 0 && val > 0 && val < p.getLong("t_total", 0)) sub2 = "متوقف مؤقتًا";
            txt(v, R.id.c_sub, WidgetRenderer.digits(c, sub2), hi, 11 * f);
            v.setViewVisibility(R.id.c_sub, sub2.isEmpty() ? View.GONE : View.VISIBLE);
            v.setImageViewResource(R.id.c_b2, run ? R.drawable.ic_p_pause : R.drawable.ic_p_play);
            v.setInt(R.id.c_b2, "setBackgroundResource", R.drawable.w_btn_on);
            for (int id : new int[]{R.id.c_b1, R.id.c_b3}) { v.setInt(id, "setColorFilter", main); v.setInt(id, "setBackgroundResource", dark ? R.drawable.w_tile_d : R.drawable.w_btn); }
            v.setInt(R.id.c_b2, "setColorFilter", 0xFFFFFFFF);
            String w = timer ? "timer" : "sw";
            v.setOnClickPendingIntent(R.id.c_b1, cmd(c, w, "reset", 0, 110 + type * 10));
            if (timer && p.getLong("t_total", 0) <= 0 && val <= 0) v.setOnClickPendingIntent(R.id.c_b2, open(c, go, 95));
            else v.setOnClickPendingIntent(R.id.c_b2, cmd(c, w, "toggle", 0, 111 + type * 10));
            v.setOnClickPendingIntent(R.id.c_b3, timer ? cmd(c, w, "add", 60_000L, 112 + type * 10) : cmd(c, w, "lap", 0, 112 + type * 10));
            return v;
        }

        // calendar
        int off = ClockStore.p(c).getInt("cal_" + widgetId, 0);
        Calendar now = Calendar.getInstance();
        Calendar m = (Calendar) now.clone();
        m.set(Calendar.DAY_OF_MONTH, 1);
        m.add(Calendar.MONTH, off);
        String[] gm = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
        txt(v, R.id.c_big, WidgetRenderer.digits(c, gm[m.get(Calendar.MONTH)] + " " + m.get(Calendar.YEAR)), main, 15 * f);
        int days = m.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar last = (Calendar) m.clone();
        last.set(Calendar.DAY_OF_MONTH, days);
        String h1 = hMonth(m), h2 = hMonth(last);
        txt(v, R.id.c_sub, WidgetRenderer.digits(c, h1.equals(h2) ? h1 : (h1.replaceAll(" \\d+$", "").replaceAll(" [٠-٩]+$", "") + " – " + h2)), hi, 11 * f);
        int[] dayIds = {R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6};
        for (int i = 0; i < 7; i++) v.setTextColor(dayIds[i], i == 5 ? hi : sub);
        int lead = m.get(Calendar.DAY_OF_WEEK) - 1; // Sunday first
        int rows = (lead + days + 6) / 7;
        Calendar d = (Calendar) m.clone();
        for (int i = 0; i < 42; i++) {
            int k = id(c, "k" + i), gi = id(c, "g" + i), hiId = id(c, "h" + i);
            int day = i - lead + 1;
            if (i >= rows * 7) { v.setViewVisibility(k, View.GONE); continue; }
            v.setViewVisibility(k, View.VISIBLE);
            if (day < 1 || day > days) { v.setTextViewText(gi, ""); v.setTextViewText(hiId, ""); v.setInt(k, "setBackgroundResource", 0); continue; }
            d.set(Calendar.DAY_OF_MONTH, day);
            boolean today = off == 0 && day == now.get(Calendar.DAY_OF_MONTH);
            txt(v, gi, WidgetRenderer.digits(c, String.valueOf(day)), today ? 0xFFFFFFFF : (i % 7 == 5 ? hi : main), 13 * f);
            txt(v, hiId, WidgetRenderer.digits(c, hDay(d)), today ? 0xFFFFFFFF : sub, 9 * f);
            v.setInt(k, "setBackgroundResource", today ? R.drawable.w_today : 0);
        }
        for (int r = 0; r < 6; r++) v.setViewVisibility(id(c, "r" + r), r < rows ? View.VISIBLE : View.GONE);
        v.setOnClickPendingIntent(R.id.c_prev, cmdW(c, "prev", widgetId, 1));
        v.setOnClickPendingIntent(R.id.c_next, cmdW(c, "next", widgetId, 2));
        v.setOnClickPendingIntent(R.id.c_head, cmdW(c, "today", widgetId, 3));
        v.setInt(R.id.c_prev, "setColorFilter", main);
        v.setInt(R.id.c_next, "setColorFilter", main);
        return v;
    }

    private static RemoteViews clockFace(Context c, WidgetRenderer.Cfg g) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        WidgetRenderer.applyBg(v, g);
        boolean dark = WidgetRenderer.darkText(g);
        int main = dark ? 0xFF2A1F36 : 0xFFFFFFFF, hi = dark ? 0xFFC2580C : 0xFFFFB066;
        float f = g.font == 0 ? 0.85f : g.font == 2 ? 1.2f : 1f;
        boolean h24 = g.fmt == 2;
        v.setCharSequence(R.id.k_time, "setFormat12Hour", h24 ? "HH:mm" : "h:mm");
        v.setCharSequence(R.id.k_time, "setFormat24Hour", h24 ? "HH:mm" : "h:mm");
        v.setTextColor(R.id.k_time, main);
        v.setTextViewTextSize(R.id.k_time, TypedValue.COMPLEX_UNIT_DIP, 40 * f);
        Calendar now = Calendar.getInstance();
        txt(v, R.id.k_ampm, h24 ? "" : (now.get(Calendar.HOUR_OF_DAY) < 12 ? "ص" : "م"), hi, 14 * f);
        v.setViewVisibility(R.id.k_ampm, h24 ? View.GONE : View.VISIBLE);
        String[] days = {"الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
        String[] gm = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
        txt(v, R.id.k_day, WidgetRenderer.digits(c, days[now.get(Calendar.DAY_OF_WEEK) - 1] + " " + now.get(Calendar.DAY_OF_MONTH) + " " + gm[now.get(Calendar.MONTH)]), main, 12.5f * f);
        String hj = WidgetRenderer.hijri(c).replaceAll("\\s*[0-9٠-٩۰-۹]{4}.*$", "").trim(); // day + month only
        txt(v, R.id.k_hijri, hj, hi, 11.5f * f);
        android.content.SharedPreferences wp = Weather.prefs(c);
        if (Weather.fresh(c)) {
            int code = wp.getInt("code", 0);
            v.setViewVisibility(R.id.k_wx, View.VISIBLE);
            txt(v, R.id.k_wicon, Weather.icon(code, wp.getBoolean("day", true)), main, 18 * f);
            txt(v, R.id.k_temp, WidgetRenderer.digits(c, Math.round(wp.getFloat("temp", 0)) + "°"), main, 18 * f);
            String hl = wp.contains("max") ? WidgetRenderer.digits(c, Math.round(wp.getFloat("max", 0)) + "°/" + Math.round(wp.getFloat("min", 0)) + "°") : "";
            txt(v, R.id.k_hl, hl, hi, 11 * f);
            v.setOnClickPendingIntent(R.id.k_wx, open(c, "weather", 98));
        } else {
            v.setViewVisibility(R.id.k_wx, View.GONE);
        }
        v.setOnClickPendingIntent(R.id.widget_root, open(c, "clock", 99));
        return v;
    }

    private static int id(Context c, String n) { return c.getResources().getIdentifier(n, "id", c.getPackageName()); }

    private static void txt(RemoteViews v, int id, String s, int col, float dp) {
        v.setTextViewText(id, s);
        v.setTextColor(id, col);
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dp);
    }

    static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static String dayWord(long t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(t);
        int diff = (int) Math.round((startOfDay(b) - startOfDay(a)) / 86_400_000.0);
        if (diff == 0) return "اليوم";
        if (diff == 1) return "غدًا";
        String[] n = {"الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
        return n[b.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static long startOfDay(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 12); x.set(Calendar.MINUTE, 0); x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    private static String hMonth(Calendar g) {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura");
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("MMMM y", loc);
            return f.format(g.getTime()).replace(" هـ", "");
        } catch (Throwable t) { return ""; }
    }

    private static String hDay(Calendar g) {
        try {
            android.icu.util.Calendar ic = android.icu.util.Calendar.getInstance(new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura"));
            ic.setTime(g.getTime());
            return String.valueOf(ic.get(android.icu.util.Calendar.DAY_OF_MONTH));
        } catch (Throwable t) { return ""; }
    }

    static PendingIntent open(Context c, String go, int code) {
        Intent i = new Intent(c, MainActivity.class).putExtra("go", go).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 300 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cmd(Context c, String w, String cmd, long arg, int code) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(ClockStore.A_CMD).putExtra("w", w).putExtra("c", cmd).putExtra("arg", arg)
                .setData(android.net.Uri.parse("zk://" + w + "/" + cmd));
        return PendingIntent.getBroadcast(c, 400 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cmdW(Context c, String cmd, int wid, int code) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(ClockStore.A_CMD).putExtra("w", "cal").putExtra("c", cmd).putExtra("wid", wid)
                .setData(android.net.Uri.parse("zk://cal/" + wid + "/" + cmd));
        return PendingIntent.getBroadcast(c, 600 + (wid % 1000) * 4 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
