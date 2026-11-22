package com.zakkerni.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Full widget (also the base class of the other widget types). */
public class PrayerWidget extends AppWidgetProvider {
    int type() { return WidgetRenderer.T_FULL; }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, WidgetRenderer.build(c, type(), WidgetRenderer.Cfg.load(c, id)));
        WidgetRenderer.scheduleTick(c, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        m.updateAppWidget(id, WidgetRenderer.build(c, type(), WidgetRenderer.Cfg.load(c, id)));
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetRenderer.Cfg.delete(c, id);
    }

    static void updateAll(Context c) { WidgetRenderer.updateAll(c); }
}
