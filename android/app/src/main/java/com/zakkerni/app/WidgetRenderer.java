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
    static final int T_NEXT = 0, T_DAY = 1, T_FULL = 2, T_LIST = 3, T_PLAYER = 4, T_PLAYER_S = 5, T_PLAYER_L = 6, T_ALARM = 7, T_TIMER = 8, T_SW = 9, T_CAL = 10, T_CLOCK = 11, T_STACK = 12;
    static final String WPREFS = "zakkerni_widgets";
    static final String ACTION_TICK = "com.zakkerni.app.WIDGET_TICK";

    static final String[] STYLES = {"grad", "purple", "orange", "dark", "light", "deep", "lav", "soft", "sunset", "auto"};
    static final String[] STYLE_NAMES = {"متدرّج", "بنفسجي", "برتقالي", "داكن", "فاتح", "ليلي", "لافندر", "مشمشي", "غروب", "أرابيسك"};
    static final String[] DESIGNS = {"كلاسيكي", "بطاقات", "مدمج"};
    static final Class<?>[] PROVIDERS = {WidgetNext.class, WidgetDay.class, PrayerWidget.class, WidgetList.class, PlayerWidget.class, PlayerWidgetSmall.class, PlayerWidgetLarge.class, WidgetAlarm.class, WidgetTimer.class, WidgetStopwatch.class, WidgetCalendar.class, WidgetClock.class, WidgetStack.class};
    static final int[] LAYOUTS = {R.layout.widget_next, R.layout.widget_day, R.layout.widget_prayer, R.layout.widget_list, R.layout.widget_player, R.layout.widget_player_s, R.layout.widget_player_l, R.layout.widget_alarm, R.layout.widget_timer, R.layout.widget_sw, R.layout.widget_cal, R.layout.widget_clock, R.layout.widget_stack};

    private static final int[] NAMES = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    private static final int[] TIMES = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};

    private WidgetRenderer() { }

    /** Per-widget settings. */
    static final class Cfg {
        int style = 0, opacity = 100, radius = 2, text = 0, font = 1, fmt = 0;
        boolean art = true; // player widgets: track art as background
        int design = 0;     // wide widget: 0 classic, 1 cards, 2 compact
        int wdp = 0;        // current widget width in dp (from the launcher)
        int hdp = 0;        // current widget height in dp (portrait)
        int hmin = 0;       // smaller reported height (some launchers over-report the portrait one)
        int id = 0;
        boolean cd = true, city = true, hijri = true, weather = false;

        static Cfg load(Context c, int id) {
            SharedPreferences p = c.getSharedPreferences(WPREFS, Context.MODE_PRIVATE);
            Cfg g = new Cfg();
            g.id = id;
            String k = "w" + id + "_";
            if (!p.contains(k + "style")) { int ty = typeOf(c, id); if (ty < T_PLAYER || ty >= T_ALARM) g.style = AUTO; if (ty == T_DAY) g.radius = 1; }
            try { android.os.Bundle ob = AppWidgetManager.getInstance(c).getAppWidgetOptions(id); g.wdp = ob.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0); g.hdp = ob.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0); g.hmin = ob.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0); } catch (Exception ignored) { }
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
            g.design = p.getInt(k + "design", g.design);
            return g;
        }

        void save(Context c, int id) {
            String k = "w" + id + "_";
            c.getSharedPreferences(WPREFS, Context.MODE_PRIVATE).edit()
                    .putInt(k + "style", style).putInt(k + "opacity", opacity).putInt(k + "radius", radius)
                    .putInt(k + "text", text).putInt(k + "font", font).putInt(k + "fmt", fmt)
                    .putBoolean(k + "cd", cd).putBoolean(k + "city", city).putBoolean(k + "hijri", hijri).putBoolean(k + "art", art).putInt(k + "design", design).putBoolean(k + "weather", weather).apply();
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

    static final long SINCE_MS = 30 * 60_000L;

    static String sinceText(Context c, long ms) {
        long m = Math.max(0, ms / 60_000);
        String s = m < 1 ? "حان الآن" : m == 1 ? "منذ دقيقة" : m == 2 ? "منذ دقيقتين" : "منذ " + m + (m <= 10 ? " دقائق" : " دقيقة");
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
                {R.drawable.wbg_light_0, R.drawable.wbg_light_1, R.drawable.wbg_light_2},
                {R.drawable.wbg_deep_0, R.drawable.wbg_deep_1, R.drawable.wbg_deep_2},
                {R.drawable.wbg_lav_0, R.drawable.wbg_lav_1, R.drawable.wbg_lav_2},
                {R.drawable.wbg_soft_0, R.drawable.wbg_soft_1, R.drawable.wbg_soft_2},
                {R.drawable.wbg_sunset_0, R.drawable.wbg_sunset_1, R.drawable.wbg_sunset_2},
                {R.drawable.wbg_auto_0, R.drawable.wbg_auto_1, R.drawable.wbg_auto_2}};
        return r[Math.max(0, Math.min(r.length - 1, style))][Math.max(0, Math.min(2, radius))];
    }

    static final int AUTO = 9; // app identity: orange + arabesque by day, night theme by night (follows the phone)
    static boolean lightBg(int style) { return style == 4 || style == 6 || style == 7; }
    /** orange-ish backgrounds get a purple highlight, everything else orange */
    static boolean orangeBg(int style) { return style == 2 || style == 7; }

    static void applyBg(RemoteViews v, Cfg g) {
        if (g.style == AUTO) { // drawn as the view background so the arabesque tiles instead of stretching
            v.setImageViewResource(R.id.wbg, android.R.color.transparent);
            v.setInt(R.id.wbg, "setBackgroundResource", bgRes(g.style, g.radius));
            v.setFloat(R.id.wbg, "setAlpha", g.opacity / 100f);
        } else {
            v.setInt(R.id.wbg, "setBackgroundResource", 0);
            v.setFloat(R.id.wbg, "setAlpha", 1f);
            v.setImageViewResource(R.id.wbg, bgRes(g.style, g.radius));
            v.setInt(R.id.wbg, "setImageAlpha", Math.round(255 * g.opacity / 100f));
        }
    }

    static boolean darkText(Cfg g) {
        if (g.text == 1) return false;
        if (g.text == 2) return true;
        return lightBg(g.style) || g.opacity < 35; // light styles, or nearly transparent
    }

    /* ---------- build ---------- */
    static RemoteViews build(Context c, int type, Cfg g) {
        if (type == T_STACK) return buildStack(c, g.id);
        if (type >= T_ALARM) return ClockWidgets.build(c, g, type, g.id);
        if (type >= T_PLAYER) return buildPlayer(c, g, type);
        int lay = LAYOUTS[type];
        if (type == T_DAY) lay = g.design == 1 ? R.layout.widget_day_cards : g.design == 2 ? R.layout.widget_day_compact : R.layout.widget_day;
        RemoteViews v = new RemoteViews(c.getPackageName(), lay);
        Intent open = new Intent(c, MainActivity.class).putExtra("go", "adhan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 50 + type, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        applyBg(v, g);
        boolean auto = g.style == AUTO;

        boolean dark = darkText(g);
        int main = dark ? 0xFF2A1F36 : 0xFFFFFFFF;
        int sub = dark ? 0xFF6B5E7B : 0xEEFFF0E2;
        int hi = auto ? 0xFFFFFFFF : orangeBg(g.style) ? (dark ? 0xFF7137A8 : 0xFF4A1C78) : (g.style == 6 ? 0xFF7137A8 : dark ? 0xFFC2580C : 0xFFFFB066);
        float f = g.font == 0 ? 0.85f : g.font == 2 ? 1.2f : 1f;

        JSONArray a = AdhanScheduler.list(c);
        long now = System.currentTimeMillis();
        JSONObject n = AdhanScheduler.next(a, now);
        // for 30 minutes after a prayer's time, show "الظهر منذ ١٢ د" instead of the next prayer
        boolean since = false;
        JSONObject prev = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optString("n").contains("الشروق")) continue;
            long t = o.optLong("t");
            if (t <= now + 1500 && now - t < SINCE_MS && (prev == null || t > prev.optLong("t"))) prev = o;
        }
        if (prev != null) { n = prev; since = true; }
        String city = AdhanScheduler.prefs(c).getString(AdhanScheduler.KEY_CITY, "");

        setText(v, R.id.w_label, since ? "الصلاة الحالية" : "الصلاة القادمة", sub, 11 * f);
        setText(v, R.id.w_city, city, sub, (type == T_FULL ? 12f : 10.5f) * f);
        if (type == T_FULL) v.setViewVisibility(R.id.w_city, city.isEmpty() ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.w_city, g.city && !city.isEmpty() ? View.VISIBLE : View.GONE);
        if (type == T_FULL) {
            // full widget: clock, weather, location and both dates
            String greg = digits(c, new java.text.SimpleDateFormat("EEEE d MMMM yyyy", new java.util.Locale("ar")).format(new java.util.Date()));
            setText(v, R.id.w_hijri, hijri(c), android.graphics.Color.parseColor("#FFB066"), 12f * f);
            setText(v, R.id.w_greg, greg, sub, 12f * f);
            v.setViewVisibility(R.id.w_hijri, View.VISIBLE);
            v.setTextViewTextSize(R.id.w_clock, TypedValue.COMPLEX_UNIT_DIP, 32 * f);
            v.setTextColor(R.id.w_clock, main);
            // tap targets: clock → clock page, weather → weather page, dates → calendar
            String[][] taps = {{"clock", "" + R.id.w_clock}, {"weather", "" + R.id.w_weather}, {"calendar", "" + R.id.w_hijri}, {"calendar", "" + R.id.w_greg}};
            for (int k = 0; k < taps.length; k++) {
                Intent it = new Intent(c, MainActivity.class).putExtra("go", taps[k][0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                v.setOnClickPendingIntent(Integer.parseInt(taps[k][1]), PendingIntent.getActivity(c, 90 + k, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        } else {
            setText(v, R.id.w_hijri, g.hijri ? hijri(c) : "", sub, 11 * f);
            v.setViewVisibility(R.id.w_hijri, g.hijri ? View.VISIBLE : View.GONE);
        }
        String wx = (g.weather || type == T_FULL) ? Weather.text(c) : "";
        setText(v, R.id.w_weather, wx, main, (type == T_FULL ? 13 : type == T_DAY && g.design != 2 ? 12 : 11) * f);
        v.setViewVisibility(R.id.w_weather, wx.isEmpty() ? View.GONE : View.VISIBLE);

        if (n == null) {
            setText(v, R.id.w_next, "افتح ذَكِّرني لتحديث المواقيت", main, 14 * f);
            v.setViewVisibility(R.id.w_cd, View.GONE);
            return v;
        }
        float bigSize = type == T_NEXT ? 20 : type == T_LIST ? 17 : type == T_FULL ? 17 : 18;
        if (type == T_FULL) setText(v, R.id.w_next, (since ? "الصلاة الحالية: " : "الصلاة القادمة: ") + n.optString("n"), main, 14.5f * f);
        else setText(v, R.id.w_next, n.optString("n") + "  " + fmtTime(c, n.optLong("t"), g.fmt), main, bigSize * f);
        setText(v, R.id.w_cd, since ? sinceText(c, now - n.optLong("t")) : countdown(c, n.optLong("t") - now), hi, (type == T_NEXT ? 15 : type == T_FULL ? 13 : 14) * f);
        v.setViewVisibility(R.id.w_cd, g.cd ? View.VISIBLE : View.GONE);
        if (type == T_FULL) { v.setTextColor(R.id.w_cd, 0xFFFFB066); v.setViewVisibility(R.id.w_cd, View.VISIBLE); }

        // sizes that always fit the widget width
        float colDp = (g.wdp > 0 ? g.wdp : 330) / 5f - (g.design == 1 ? 8 : 4);
        float timeDp = Math.min(19 * f, (colDp - 12) / 2.75f), nameDp = Math.min(14 * f, colDp / 3.4f);
        if (g.design == 2) { timeDp = Math.min(timeDp, 15 * f); nameDp = Math.min(nameDp, 12 * f); }
        if (type == T_DAY && g.hdp > 0) { // keep everything inside a short (1-row) widget
            int h = g.hmin > 0 ? (g.hmin + g.hdp) / 2 : g.hdp; // vivo: portrait height over-reported, landscape under-reported
            float head = g.design == 2 ? 0 : 24 * f, room = h - 17 /* launcher-frame insets */ - head - (g.design == 1 ? 14 : 8);
            float need = 1.4f * (nameDp + timeDp) + 4;
            if (room < need) { float k = Math.max(0.8f, room / need); nameDp *= k; timeDp *= k; }
        }
        if (type == T_DAY) {
            int[][] heads = {{R.drawable.whead_dark_0, R.drawable.whead_dark_1, R.drawable.whead_dark_2}, {R.drawable.whead_light_0, R.drawable.whead_light_1, R.drawable.whead_light_2}};
            if (g.design == 0) v.setInt(R.id.w_head, "setBackgroundResource", heads[dark ? 1 : 0][Math.max(0, Math.min(2, g.radius))]);
            setText(v, R.id.w_next, n.optString("n") + (g.design == 2 ? "" : since ? " منذ" : " بعد"), main, (g.design == 2 ? 18 : 14) * f);
            v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + (n.optLong("t") - now), null, true);
            if (android.os.Build.VERSION.SDK_INT >= 24) v.setChronometerCountDown(R.id.w_chrono, !since);
            v.setTextColor(R.id.w_chrono, g.design == 2 ? hi : main);
            v.setTextViewTextSize(R.id.w_chrono, TypedValue.COMPLEX_UNIT_DIP, (g.design == 2 ? 15 : 14) * f);
            v.setViewVisibility(R.id.w_chrono, g.cd ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.w_cd, View.GONE);
            v.setTextViewTextSize(R.id.w_hijri, TypedValue.COMPLEX_UNIT_DIP, (g.design == 2 ? 10.5f : 12) * f);
            v.setTextColor(R.id.w_hijri, g.design == 2 ? sub : main);
            if (g.design == 2) setText(v, R.id.w_label, since ? "الصلاة الحالية" : "الصلاة القادمة", sub, 11 * f);
            if (!g.cd && g.design != 2) setText(v, R.id.w_next, n.optString("n") + "  " + fmtTime(c, n.optLong("t"), g.fmt), main, 14 * f);
        }
        if (type != T_NEXT) {
            List<JSONObject> day = AdhanScheduler.dayOf(a, n);
            for (int i = 0; i < NAMES.length; i++) {
                if (i < day.size()) {
                    JSONObject o = day.get(i);
                    boolean isNext = o.optLong("t") == n.optLong("t");
                    int col = isNext ? (type == T_FULL ? 0xFFFFB066 : hi) : main;
                    float sz = (type == T_LIST ? 14.5f : 12f) * f;
                    if (type == T_DAY) {
                        boolean purplePill = orangeBg(g.style);
                        if (g.design == 1) {
                            int[] tiles = {R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4};
                            v.setInt(tiles[i], "setBackgroundResource", isNext ? (auto ? R.drawable.w_tile_auto : purplePill ? R.drawable.w_tile_on_p : R.drawable.w_tile_on) : (dark ? R.drawable.w_tile_d : R.drawable.w_tile_l));
                            tc(v, NAMES[i], o.optString("n"), isNext ? (auto ? 0xFF2A1440 : 0xFFFFFFFF) : main);
                            tc(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), isNext ? (auto ? 0xFF2A1440 : 0xFFFFFFFF) : main);
                            v.setInt(TIMES[i], "setBackgroundResource", 0);
                        } else {
                            if (g.design == 2) {
                                setText(v, NAMES[i], o.optString("n"), isNext ? hi : main, nameDp);
                                setText(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), isNext ? (auto ? 0xFF2A1440 : purplePill ? 0xFFFFFFFF : 0xFF1D1726) : main, timeDp);
                            } else { // classic: autosize fills the cell
                                tc(v, NAMES[i], o.optString("n"), isNext ? hi : main);
                                tc(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), isNext ? (auto ? 0xFF2A1440 : purplePill ? 0xFFFFFFFF : 0xFF1D1726) : main);
                            }
                            v.setInt(TIMES[i], "setBackgroundResource", isNext ? (auto ? R.drawable.w_pill_auto : purplePill ? R.drawable.w_pill_purple : R.drawable.w_pill) : 0);
                        }
                    } else {
                        float fit = type == T_LIST ? sz : Math.min(sz, Math.min(timeDp, (colDp - 6) / 3.3f));
                        setText(v, NAMES[i], o.optString("n"), col, fit);
                        setText(v, TIMES[i], fmtTime(c, o.optLong("t"), g.fmt), col, fit);
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
        int side = type == T_PLAYER_S ? 150 : 0; // small player fills its 2x2 frame (the launcher already sizes it like its own tiles)
        Intent open = new Intent(c, MainActivity.class).putExtra("go", "player").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 70, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        PlayerService p = PlayerService.inst;
        int alpha = Math.round(255 * g.opacity / 100f);
        boolean artBg = g.art;
        if (artBg) {
            android.graphics.Bitmap src = p != null && p.art != null ? p.art : fallbackArt(c);
            boolean real = p != null && p.art != null;
            int bw = type == T_PLAYER_S ? 360 : 720, bh = type == T_PLAYER_S ? 343 : type == T_PLAYER_L ? 380 : 200;
            float dpw = type == T_PLAYER_S ? 170f : 340f;
            if (side > 0) dpw = side;
            else if (g.wdp > 0 && g.hdp > 0) { // match the real cell shape so nothing is stretched
                bh = Math.max(120, Math.min(1200, Math.round(bw * g.hdp / (float) g.wdp)));
                dpw = g.wdp;
            }
            float[] radii = type == T_PLAYER_S ? new float[]{0, 11, 19} : new float[]{0, 16, 28};
            float rr = radii[Math.max(0, Math.min(2, g.radius))] * (bw / dpw);
            if (type == T_PLAYER_S) { // scrim baked into the bitmap so its corners match exactly
                if (src != null) {
                    android.graphics.Bitmap b = roundedCrop(src, bw, bh, rr);
                    new android.graphics.Canvas(b).drawColor(((real ? 128 : 56) << 24), android.graphics.PorterDuff.Mode.SRC_ATOP);
                    v.setImageViewBitmap(R.id.wbg, b);
                }
                v.setInt(R.id.wbg, "setImageAlpha", alpha);
                v.setViewVisibility(R.id.wscrim, View.GONE);
            } else {
            if (src != null) v.setImageViewBitmap(R.id.wbg, roundedCrop(src, bw, bh, rr));
            v.setInt(R.id.wbg, "setImageAlpha", alpha);
            v.setImageViewResource(R.id.wscrim, bgRes(3, g.radius));
            v.setInt(R.id.wscrim, "setImageAlpha", Math.round(alpha * (real ? 0.5f : 0.22f)));
            v.setViewVisibility(R.id.wscrim, View.VISIBLE);
            }
            v.setViewVisibility(R.id.p_art, View.GONE);
        } else {
            v.setImageViewResource(R.id.wbg, bgRes(g.style, g.radius));
            v.setInt(R.id.wbg, "setImageAlpha", alpha);
            v.setViewVisibility(R.id.wscrim, View.GONE);
            if (type != T_PLAYER_S) v.setViewVisibility(R.id.p_art, View.VISIBLE);
            if (p != null && p.art != null) v.setImageViewBitmap(R.id.p_art, android.graphics.Bitmap.createScaledBitmap(p.art, 144, 144, true));
            else v.setImageViewResource(R.id.p_art, R.mipmap.ic_zakkerni);
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
        float ts = type == T_PLAYER_L ? 17 : type == T_PLAYER_S ? 13 : 15;
        setText(v, R.id.p_title, title, main, ts * f);
        setText(v, R.id.p_artist, artist == null ? "" : artist, sub, (type == T_PLAYER_S ? 10.5f : 12.5f) * f);
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

    private static void tc(RemoteViews v, int id, String s, int color) {
        v.setTextViewText(id, s);
        v.setTextColor(id, color);
    }

    private static void setText(RemoteViews v, int id, String s, int color, float sp) {
        v.setTextViewText(id, s);
        v.setTextColor(id, color);
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, sp);
    }

    /* ---------- refresh ---------- */
    static RemoteViews buildStack(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_stack);
        Intent svc = new Intent(c, StackService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(android.net.Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.stack, svc);
        v.setEmptyView(R.id.stack, R.id.stack_empty);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        v.setPendingIntentTemplate(R.id.stack, PendingIntent.getActivity(c, 800, open, fl));
        return v;
    }

    static void updateAll(Context c) {
        boolean any = false;
        Weather.maybeRefresh(c);
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (int t = 0; t < PROVIDERS.length; t++) {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, PROVIDERS[t]));
                if (ids == null) continue;
                for (int id : ids) {
                    if (t == T_STACK) { m.notifyAppWidgetViewDataChanged(id, R.id.stack); any = true; continue; }
                    m.updateAppWidget(id, build(c, t, Cfg.load(c, id))); any = true;
                }
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
