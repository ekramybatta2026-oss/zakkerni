package com.zakkerni.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Current weather from Open-Meteo (free, no key), cached and refreshed at most every 30 minutes. */
final class Weather {
    static final String PREFS = "zakkerni_weather";
    private static final long EVERY = 30 * 60_000L, RETRY = 10 * 60_000L;
    private static volatile boolean busy = false;

    private Weather() { }

    static void setLocation(Context c, double lat, double lng) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean moved = Math.abs(p.getFloat("lat", 999) - lat) > 0.05 || Math.abs(p.getFloat("lng", 999) - lng) > 0.05;
        p.edit().putFloat("lat", (float) lat).putFloat("lng", (float) lng).apply();
        if (moved) { p.edit().putLong("try", 0).putLong("ts", 0).apply(); maybeRefresh(c); }
    }

    /** Fetch in the background if the cache is stale; widgets update when it arrives. */
    static void maybeRefresh(Context ctx) {
        final Context c = ctx.getApplicationContext();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat") || busy) return;
        long now = System.currentTimeMillis();
        if (now - p.getLong("ts", 0) < EVERY || now - p.getLong("try", 0) < RETRY) return;
        p.edit().putLong("try", now).apply();
        busy = true;
        new Thread(() -> {
            try {
                String u = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
                        p.getFloat("lat", 0), p.getFloat("lng", 0));
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream()))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
                JSONObject all = new JSONObject(sb.toString());
                JSONObject cur = all.getJSONObject("current");
                JSONObject daily = all.optJSONObject("daily");
                if (daily != null) p.edit().putFloat("max", (float) daily.getJSONArray("temperature_2m_max").getDouble(0)).putFloat("min", (float) daily.getJSONArray("temperature_2m_min").getDouble(0)).apply();
                p.edit().putFloat("temp", (float) cur.getDouble("temperature_2m")).putInt("code", cur.getInt("weather_code"))
                        .putBoolean("day", cur.optInt("is_day", 1) == 1).putLong("ts", System.currentTimeMillis()).apply();
                WidgetRenderer.updateAll(c);
            } catch (Exception ignored) {
            } finally { busy = false; }
        }).start();
    }

    /** e.g. "☀️ 24°" — empty when nothing usable is cached (older than 6 hours counts as stale). */
    static String text(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("ts") || System.currentTimeMillis() - p.getLong("ts", 0) > 6 * 3600_000L) return "";
        return icon(p.getInt("code", 0), p.getBoolean("day", true)) + " " + WidgetRenderer.digits(c, String.valueOf(Math.round(p.getFloat("temp", 0)))) + "°";
    }

    static boolean fresh(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.contains("ts") && System.currentTimeMillis() - p.getLong("ts", 0) < 6 * 3600_000L;
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String desc(int code) {
        if (code == 0) return "صافٍ";
        if (code == 1) return "صافٍ غالبًا";
        if (code == 2) return "غائم جزئيًا";
        if (code == 3) return "غائم";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 51 && code <= 57) return "رذاذ";
        if (code >= 61 && code <= 67) return "مطر";
        if (code >= 71 && code <= 77) return "ثلج";
        if (code >= 80 && code <= 82) return "زخات مطر";
        if (code == 85 || code == 86) return "زخات ثلج";
        if (code >= 95) return "عاصفة رعدية";
        return "";
    }

    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "🌤️" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 95) return "⛈️";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄️";
        if (code >= 80) return "🌦️";
        return "🌧️";
    }
}
