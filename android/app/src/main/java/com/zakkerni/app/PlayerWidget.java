package com.zakkerni.app;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

/** Interactive player widget (play / pause / next / previous, sleep-timer indicator). */
public class PlayerWidget extends PrayerWidget {
    @Override int type() { return WidgetRenderer.T_PLAYER; }

    static void update(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, PlayerWidget.class));
            if (ids == null) return;
            for (int id : ids) m.updateAppWidget(id, WidgetRenderer.build(c, WidgetRenderer.T_PLAYER, WidgetRenderer.Cfg.load(c, id)));
        } catch (Exception ignored) { }
    }
}
