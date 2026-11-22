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
            Class<?>[] cls = {PlayerWidget.class, PlayerWidgetSmall.class, PlayerWidgetLarge.class};
            int[] types = {WidgetRenderer.T_PLAYER, WidgetRenderer.T_PLAYER_S, WidgetRenderer.T_PLAYER_L};
            for (int k = 0; k < cls.length; k++) {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, cls[k]));
                if (ids == null) continue;
                for (int id : ids) m.updateAppWidget(id, WidgetRenderer.build(c, types[k], WidgetRenderer.Cfg.load(c, id)));
            }
        } catch (Exception ignored) { }
    }
}
