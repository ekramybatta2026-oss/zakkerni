package com.zakkerni.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small rolling diagnostic log of adhan scheduling, kept on the device only. */
final class AdhanLog {
    private AdhanLog() { }
    private static final int MAX = 60;

    static synchronized void add(Context c, String msg) {
        try {
            SharedPreferences p = c.getSharedPreferences("adhan_log", Context.MODE_PRIVATE);
            String now = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date());
            String all = now + "  " + msg + "\n" + p.getString("log", "");
            String[] lines = all.split("\n");
            if (lines.length > MAX) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < MAX; i++) b.append(lines[i]).append('\n');
                all = b.toString();
            }
            p.edit().putString("log", all).apply();
        } catch (Exception ignored) { }
    }

    static String get(Context c) { return c.getSharedPreferences("adhan_log", Context.MODE_PRIVATE).getString("log", ""); }

    static String hm(long t) { return new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(t)); }
}
