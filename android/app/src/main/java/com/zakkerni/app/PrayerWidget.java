package com.zakkerni.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Home-screen widget: next prayer with live countdown + the day's five prayer times. */
public class PrayerWidget extends AppWidgetProvider {
    private static final int[] NAMES = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    private static final int[] TIMES = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, PrayerWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews v = build(c);
            for (int id : ids) m.updateAppWidget(id, v);
        } catch (Exception ignored) { }
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_prayer);
        PendingIntent open = PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, open);

        JSONArray a = AdhanScheduler.list(c);
        long now = System.currentTimeMillis();
        JSONObject n = AdhanScheduler.next(a, now);
        String city = AdhanScheduler.prefs(c).getString(AdhanScheduler.KEY_CITY, "");
        v.setTextViewText(R.id.w_city, city);

        if (n == null) {
            v.setTextViewText(R.id.w_next, "افتح ذَكِّرني لتحديث المواقيت");
            v.setViewVisibility(R.id.w_cd, View.GONE);
            return v;
        }
        v.setTextViewText(R.id.w_next, n.optString("n") + "  " + n.optString("s"));
        v.setViewVisibility(R.id.w_cd, View.VISIBLE);
        long base = SystemClock.elapsedRealtime() + (n.optLong("t") - now);
        v.setChronometer(R.id.w_cd, base, null, true);
        if (Build.VERSION.SDK_INT >= 24) v.setChronometerCountDown(R.id.w_cd, true);

        List<JSONObject> day = AdhanScheduler.dayOf(a, n);
        for (int i = 0; i < NAMES.length; i++) {
            if (i < day.size()) {
                JSONObject o = day.get(i);
                boolean isNext = o.optLong("t") == n.optLong("t");
                v.setTextViewText(NAMES[i], o.optString("n"));
                v.setTextViewText(TIMES[i], o.optString("s"));
                int col = isNext ? 0xFFFFB066 : 0xFFFFFFFF;
                v.setTextColor(NAMES[i], col);
                v.setTextColor(TIMES[i], col);
            } else {
                v.setTextViewText(NAMES[i], "");
                v.setTextViewText(TIMES[i], "");
            }
        }
        return v;
    }
}
