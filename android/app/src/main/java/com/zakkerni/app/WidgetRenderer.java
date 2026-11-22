package com.zakkerni.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Draws every Zakkerni widget type from the saved prayer times and each widget's own settings. */
final class WidgetRenderer {
    static final int T_NEXT = 0, T_DAY = 1, T_FULL = 2, T_LIST = 3, T_PLAYER = 4, T_PLAYER_S = 5, T_PLAYER_L = 6;
    static final String WPREFS = "zakkerni_widgets";
    static final String ACTION_TICK = "com.zakkerni.app.WIDGET_TICK";

    static final String[] STYLES = {"grad", "purple", "orange", "dark", "light"};
    static final String[] STYLE_NAMES = {"متدرّج", "بنفسجي", "برتقالي", "داكن", "فاتح"};
    static final Class<?>[] PROVIDERS = {WidgetNext.class, WidgetDay.class, PrayerWidget.class, WidgetList.class, PlayerWidget.class, PlayerWidgetSmall.class, PlayerWidgetLarge.class};
    static final int[] LAYOUTS = {R.layout.widget_next, R.layout.widget_day, R.layout.widget_prayer, R.layout.widget_list, R.layout.widget_player, R.layout.widget_player_s, R.layout.widget_player_l};

    private static final int[] NAMES = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    private static final int[] TIMES = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};

    private WidgetRenderer() { }

    /** Per-widget settings. */
    static final class Cfg {
        int style = 0, opacity = 100, radius = 2, text = 0, font = 1, fmt = 0;
        boolean art = true; // player widgets: track art as background
        boolean cd = true, city = true, hijri = true;

        static Cfg load(Context c, int id) {
            SharedPreferences p = c.getSharedPreferences(WPREFS, Context.MODE_PRIVATE);
            Cfg g = new Cfg();
            String k = "w" + id + "_";
            if (!p.contains(k + "style") && typeOf(c, id) == T_DAY) { g.style = 3; g.opacity = 85; g.radius = 1; } // dark, like classic prayer widgets
            g.style = p.getInt(k + "style", g.style);
            g.opacity = p.getInt(k + "opacity", g.opacity);
            g.radius = p.getInt(k + "radius", g.radius);
            g.text = p.getInt(k + "text", g.text);
            g.font = p.getInt(k + "font", g.font);
            g.fmt = p.getInt(k + "fmt", g.fmt);
            g.cd = p.getBoolean(k + "cd", g.cd);
            g.city = p.getBoolean(k + "city", g.city);
            g.hijri = p.getBoolean(k + "hijri", g.hijri);
            g.art = p.getBoolean(k + "art", g.art);
            return g;
        }

        void save(Context c, int id) {
            String k = "w" + id + "_";
            c.getSharedPreferences(WPREFS, Context.MODE_PRIVATE).edit()
                    .putInt(k + "style", style).putInt(k + "opacity", opacity).putInt(k + "radius", radius)
                    .putInt(k + "text", text).putInt(k + "font", font).putInt(k + "fmt", fmt)
                    .putBoolean(k + "cd", cd).putBoolean(k + "city", city).putBoolean(k + "hijri", hijri).putBoolean(k + "art", art).apply();
        }

        static void delete(Context c, int id) {
            SharedPreferences p = c.getSharedPreferences(WPREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor e = p.edit();
            for (String key : p.getAll().keySet()) if (key.startsWith("w" + id + "_")) e.remove(key);
            e.apply();
        }
    }

    /* ---------- digits ---------- */
    static String digits(Context c, String s) {
        if (s == null) return "";
        String mode = AdhanScheduler.prefs(c).getString("dig", "ar");
        String set = "en".equals(mode) ? "0123456789" : "hi".equals(mode) ? "۰۱۲۳۴۵۶۷۸۹" : "٠١٢٣٤٥٦٧٨٩";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            int v = -1;
            if (ch >= '0' && ch <= '9') v = ch - '0';
            else if (ch >= '٠' && ch <= '٩') v = ch - '٠';
            else if (ch >= '۰' && ch <= '۹') v = ch - '۰';
            b.append(v >= 0 ? set.charAt(v) : ch);
        }
        return b.toString();
    }

    static String hijri(Context c) {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura");
            android.icu.text.DateFormat df = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, loc);
            return digits(c, df.format(new java.util.Date()));
        } catch (Throwable t) { return ""; }
    }

    /** fmt 0: 05:19   1: 5:19 ص   2: 17:19 (24h) — in the prayer city's time zone. */
    static String fmtTime(Context c, long t, int fmt) {
        String tz = AdhanScheduler.prefs(c).getString("tz", "");
        java.util.Calendar cal = java.util.Calendar.getInstance(tz.isEmpty() ? java.util.TimeZone.getDefault() : java.util.TimeZone.getTimeZone(tz));
        cal.setTimeInMillis(t);
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY), m = cal.get(java.util.Calendar.MINUTE), h12 = h % 12 == 0 ? 12 : h % 12;
        String s = fmt == 2 ? String.format(java.util.Locale.US, "%02d:%02d", h, m)
                : fmt == 1 ? String.format(java.util.Locale.US, "%d:%02d %s", h12, m, h < 12 ? "ص" : "م")
                : String.format(java.util.Locale.US, "%02d:%02d", h12, m);
        return digits(c, s);
    }

    static String countdown(Context c, long ms) {
        long min = Math.max(0, (ms + 59_999) / 60_000);
        long h = min / 60, m = min % 60;
        String s = h > 0 ? ("بعد " + h + " س " + m + " د") : ("بعد " + m + " دقيقة");
        return digits(c, s);
    }

    /* ---------- colours ---------- */
    static int bgRes(int style, int radius) {
        int[][] r = {
                {R.drawable.wbg_grad_0, R.drawable.wbg_grad_1, R.drawable.wbg_grad_2},
                {R.drawable.wbg_purple_0, R.drawable.wbg_purple_1, R.drawable.wbg_purple_2},
                {R.drawable.wbg_orange_0, R.drawable.wbg_orange_1, R.drawable.wbg_orange_2},
                {R.drawable.wbg_dark_0, R.drawable.wbg_dark_1, R.drawable.wbg_dark_2},
                {R.drawable.wbg_light_0, R.drawable.wbg_light_1, R.drawable.wbg_light_2}};
        return r[Math.max(0, Math.min(4, style))][Math.max(0, Math.min(2, radius))];
    }

    static boolean darkText(Cfg g) {
        if (g.text == 1) return false;
        if (g.text == 2) return true;
        return g.style == 4 || g.opacity < 35; // light style, or nearly transparent
    }

    /* ---------- build ---------- */
    static RemoteViews build(Context c, int type, Cfg g) {
        if (type >= T_PLAYER) return buildPlayer(c, g, type);
        RemoteViews v = new RemoteViews(c.getPackageName(), LAYOUTS[type]);
        Intent open = new Intent(c, MainActivity.class).putExtra("go", "adhan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 50 + type, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        v.setImageViewResource(R.id.wbg, bgRes(g.style, g.radius));
        v.setInt(R.id.wbg, "setImageAlpha", Math.round(255 * g.opacity / 100f));

        boolean dark = darkText(g);
        int main = dark ? 0xFF2A1F36 : 0xFFFFFFFF;
        int sub = dark ? 0xFF6B5E7B : 0xDDF1E8F8;
        int hi = g.style == 2 ? (dark ? 0xFF5A2690 : 0xFF4A1C78) : (dark ? 0xFFD06A12 : 0xFFFFB066);
        float f = g.font == 0 ? 0.85f : g.font == 2 ? 1.2f : 1f;

        JSONArray a = AdhanScheduler.list(c);
        long now = System.currentTimeMillis();
        JSONObject n = AdhanScheduler.next(a, now);
        String city = AdhanScheduler.prefs(c).getString(AdhanScheduler.KEY_CITY, "");

        setText(v, R.id.w_label, "الصلاة القادمة", sub, 11 * f);
        setText(v, R.id.w_city, city, sub, 10.5f * f);
        v.setViewVisibility(R.id.w_city, g.city && !city.isEmpty() ? View.VISIBLE : View.GONE);
        setText(v, R.id.w_hijri, g.hijri ? hijri(c) : "", sub, 11 * f);
        v.setViewVisibility(R.id.w_hijri, g.hijri ? View.VISIBLE : View.GONE);

        if (n == null) {
            setText(v, R.id.w_next, "افتح ذَكِّرني لتحديث المواقيت", main, 14 * f);
            v.setViewVisibility(R.id.w_cd, View.GONE);
            return v;
        }
        float bigSize = type == T_NEXT ? 20 : type == T_LIST ? 17 : 18;
        setText(v, R.id.w_next, n.optString("n") + "  " + fmtTime(c, n.optLong("t"), g.fmt), main, bigSize * f);
        setText(v, R.id.w_cd, countdown(c, n.optLong("t") - now), hi, (type == T_NEXT ? 15 : 14) * f);
        v.setViewVisibility(R.id.w_cd, g.cd ? View.VISIBLE : View.GONE);

        if (type == T_DAY) {
            int[][] heads = {{R.drawable.whead_dark_0, R.drawable.whead_dark_1, R.drawable.whead_dark_2}, {R.drawable.whead_light_0, R.drawable.whead_light_1, R.drawable.whead_light_2}};
            v.setInt(R.id.w_head, "setBackgroundResource", heads[dark ? 1 : 0][Math.max(0, Math.min(2, g.radius))]);
            setText(v, R.id.w_next, n.optString("n") + " بعد", main, 17 * f);
            v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + (n.optLong("t") - now), null, true);
            if (android.os.Build.VERSION.SDK_INT >= 24) v.setChronometerCountDown(R.id.w_chrono, true);
            v.setTextColor(R.id.w_chrono, main);
            v.setTextViewTextSize(R.id.w_chrono, TypedValue.COMPLEX_UNIT_SP, 17 * f);
            v.setViewVisibility(R.id.w_chrono, g.cd ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.w_cd, View.GONE);
            v.setTextViewTextSize(R.id.w_hijri, TypedValue.COMPLEX_UNIT_SP, 15 * f);
            v.setTextColor(R.id.w_hijri, main);
            if (!g.cd) setText(v, R.id.w_next, n.optString("n") + "  " + fmtTime(c, n.optLong("t"), g.fmt), main, 17 * f);
        }
        if (type != T_NEXT) {
            List<JSONObject> day = AdhanScheduler.dayOf(a, n);
            for (int i = 0; i < NAMES.length; i++) {
                if (i < day.size()) {
                    JSONObject o = day.get(i);
                    boolean isNext = o.optLong("t") == n.optLong("t");
                    int col = isNext ? hi : main;
                    float sz = (type == T_LIST ? 14.5f : 12f) * f;
                    if (type == T_DAY) {
                        setText(v, NAMES[i], o.optString("n"), isNext ? hi : main, 14 * f);
                        setText(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), isNext ? 0xFF1D1726 : main, 19 * f);
                        v.setInt(TIMES[i], "setBackgroundResource", isNext ? R.drawable.w_pill : 0);
                    } else {
                        setText(v, NAMES[i], o.optString("n"), col, sz);
                        setText(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), col, sz);
                    }
                } else {
                    v.setTextViewText(NAMES[i], "");
                    v.setTextViewText(TIMES[i], "");
                }
            }
        }
        return v;
    }

    /* ---------- player widget ---------- */
    static PendingIntent playerAction(Context c, String a, int code) {
        Intent i = new Intent(c, PlayerService.class).setAction(a).putExtra("fg", true);
        return android.os.Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static android.graphics.Bitmap fallbackBg;
    static android.graphics.Bitmap fallbackArt(Context c) {
        if (fallbackBg == null) {
            try { fallbackBg = android.graphics.BitmapFactory.decodeResource(c.getResources(), R.drawable.player_bg); } catch (Exception ignored) { }
        }
        return fallbackBg;
    }

    /** Centre-crop to w×h and round the corners (so the picture follows the widget's corner style). */
    static android.graphics.Bitmap roundedCrop(android.graphics.Bitmap src, int w, int h, float r) {
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        sh.setLocalMatrix(m);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setShader(sh);
        cv.drawRoundRect(new android.graphics.RectF(0, 0, w, h), r, r, p);
        return out;
    }

    static RemoteViews buildPlayer(Context c, Cfg g, int type) {
        int layout = type == T_PLAYER_S ? R.layout.widget_player_s : type == T_PLAYER_L ? R.layout.widget_player_l : R.layout.widget_player;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        Intent open = new Intent(c, MainActivity.class).putExtra("go", "player").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 70, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        PlayerService p = PlayerService.inst;
        int alpha = Math.round(255 * g.opacity / 100f);
        boolean artBg = g.art;
        if (artBg) {
            android.graphics.Bitmap src = p != null && p.art != null ? p.art : fallbackArt(c);
            boolean real = p != null && p.art != null;
            int bw = type == T_PLAYER_S ? 360 : 720, bh = type == T_PLAYER_S ? 360 : type == T_PLAYER_L ? 380 : 200;
            float rr = new float[]{0, 16, 28}[Math.max(0, Math.min(2, g.radius))] * (type == T_PLAYER_S ? bw / 170f : bw / 340f);
            if (src != null) v.setImageViewBitmap(R.id.wbg, roundedCrop(src, bw, bh, rr));
            v.setInt(R.id.wbg, "setImageAlpha", alpha);
            v.setImageViewResource(R.id.wscrim, bgRes(3, g.radius));
            v.setInt(R.id.wscrim, "setImageAlpha", Math.round(alpha * (real ? 0.5f : 0.22f)));
            v.setViewVisibility(R.id.wscrim, View.VISIBLE);
            v.setViewVisibility(R.id.p_art, View.GONE);
        } else {
            v.setImageViewResource(R.id.wbg, bgRes(g.style, g.radius));
            v.setInt(R.id.wbg, "setImageAlpha", alpha);
            v.setViewVisibility(R.id.wscrim, View.GONE);
            if (type != T_PLAYER_S) v.setViewVisibility(R.id.p_art, View.VISIBLE);
            if (p != null && p.art != null) v.setImageViewBitmap(R.id.p_art, android.graphics.Bitmap.createScaledBitmap(p.art, 144, 144, true));
            else v.setImageViewResource(R.id.p_art, R.mipmap.ic_launcher);
        }
        boolean dark = !artBg && darkText(g);
        int main = dark ? 0xFF2A1F36 : 0xFFFFFFFF, sub = dark ? 0xFF6B5E7B : 0xDDF1E8F8;
        int hi = dark ? 0xFFD06A12 : 0xFFFFB066;
        float f = g.font == 0 ? 0.85f : g.font == 2 ? 1.2f : 1f;

        android.content.SharedPreferences sp = c.getSharedPreferences(PlayerService.PREFS, Context.MODE_PRIVATE);
        String title = p != null ? p.title : sp.getString("title", "");
        String artist = p != null ? p.artist : sp.getString("artist", "");
        boolean playing = p != null && p.isPlaying();
        if (title == null || title.isEmpty()) { title = "مشغّل ذَكِّرني"; artist = "اضغط لاختيار الملفات"; }
        float ts = type == T_PLAYER_L ? 17 : type == T_PLAYER_S ? 14 : 15;
        setText(v, R.id.p_title, title, main, ts * f);
        setText(v, R.id.p_artist, artist == null ? "" : artist, sub, (type == T_PLAYER_S ? 11 : 12.5f) * f);
        String sl = "";
        if (p != null && p.sleepEnd) sl = "⏱ يتوقف بعد هذا المقطع";
        else if (p != null && p.sleepAt > 0) sl = digits(c, "⏱ يتوقف بعد " + Math.max(1, (p.sleepAt - System.currentTimeMillis() + 59_999) / 60_000) + " د");
        setText(v, R.id.p_sleep, sl, hi, 11 * f);
        v.setViewVisibility(R.id.p_sleep, sl.isEmpty() ? View.GONE : View.VISIBLE);
        v.setImageViewResource(R.id.p_toggle, playing ? R.drawable.ic_p_pause : R.drawable.ic_p_play);
        for (int id : new int[]{R.id.p_prev, R.id.p_toggle, R.id.p_next}) v.setInt(id, "setColorFilter", main);
        v.setOnClickPendingIntent(R.id.p_prev, playerAction(c, PlayerService.A_PREV, 71));
        v.setOnClickPendingIntent(R.id.p_toggle, playerAction(c, PlayerService.A_TOGGLE, 72));
        v.setOnClickPendingIntent(R.id.p_next, playerAction(c, PlayerService.A_NEXT, 73));
        if (type == T_PLAYER_L) {
            boolean sh = p != null ? p.shuffle : sp.getBoolean("shuffle", false);
            int rep = p != null ? p.repeat : sp.getInt("repeat", 0);
            boolean timer = p != null && (p.sleepAt > 0 || p.sleepEnd);
            v.setInt(R.id.p_shuf, "setColorFilter", sh ? hi : (main & 0x99FFFFFF));
            v.setInt(R.id.p_rep, "setColorFilter", rep > 0 ? hi : (main & 0x99FFFFFF));
            v.setInt(R.id.p_timer, "setColorFilter", timer ? hi : (main & 0x99FFFFFF));
            v.setOnClickPendingIntent(R.id.p_shuf, playerAction(c, PlayerService.A_SHUFFLE_T, 74));
            v.setOnClickPendingIntent(R.id.p_rep, playerAction(c, PlayerService.A_REPEAT_C, 75));
            v.setOnClickPendingIntent(R.id.p_timer, playerAction(c, PlayerService.A_SLEEP_C, 76));
            long dur = p != null ? p.durationMs() : 0, pos = p != null ? p.positionMs() : 0;
            v.setProgressBar(R.id.p_prog, 1000, dur > 0 ? (int) (pos * 1000 / dur) : 0, false);
        }
        return v;
    }

    private static void setText(RemoteViews v, int id, String s, int color, float sp) {
        v.setTextViewText(id, s);
        v.setTextColor(id, color);
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /* ---------- refresh ---------- */
    static void updateAll(Context c) {
        boolean any = false;
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (int t = 0; t < PROVIDERS.length; t++) {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, PROVIDERS[t]));
                if (ids == null) continue;
                for (int id : ids) { m.updateAppWidget(id, build(c, t, Cfg.load(c, id))); any = true; }
            }
        } catch (Exception ignored) { }
        scheduleTick(c, any);
    }

    /** Refresh the countdown each minute while the screen is on (non-waking alarm, battery friendly). */
    static void scheduleTick(Context c, boolean on) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1003, new Intent(c, AdhanReceiver.class).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!on) { am.cancel(pi); return; }
        long next = SystemClock.elapsedRealtime() + (60_000 - System.currentTimeMillis() % 60_000) + 500;
        am.set(AlarmManager.ELAPSED_REALTIME, next, pi);
    }

    static int typeOf(Context c, int widgetId) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        try {
            ComponentName cn = m.getAppWidgetInfo(widgetId).provider;
            for (int t = 0; t < PROVIDERS.length; t++) if (PROVIDERS[t].getName().equals(cn.getClassName())) return t;
        } catch (Exception ignored) { }
        return T_FULL;
    }
}
