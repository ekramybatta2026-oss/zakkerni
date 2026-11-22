package com.zakkerni.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/** Cards of the stacked widget (swipe up/down like a smart stack). */
public class StackService extends RemoteViewsService {
    static final int[] CARDS = {WidgetRenderer.T_CLOCK, WidgetRenderer.T_NEXT, WidgetRenderer.T_PLAYER_S, WidgetRenderer.T_ALARM};
    static final String[] PAGES = {"clock", "adhan", "player", "alarm"};

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        final int wid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        final Context c = getApplicationContext();
        return new RemoteViewsFactory() {
            @Override public void onCreate() { }
            @Override public void onDataSetChanged() { }
            @Override public void onDestroy() { }
            @Override public int getCount() { return CARDS.length; }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return CARDS.length + 4; }
            @Override public long getItemId(int i) { return i; }
            @Override public boolean hasStableIds() { return true; }

            @Override
            public RemoteViews getViewAt(int i) {
                WidgetRenderer.Cfg g = WidgetRenderer.Cfg.load(c, wid);
                g.wdp = 0; g.hdp = 0; g.hmin = 0;
                RemoteViews v;
                try { v = WidgetRenderer.build(c, CARDS[i], g); }
                catch (Exception e) { v = new RemoteViews(c.getPackageName(), R.layout.widget_clock); }
                v.setViewPadding(R.id.widget_root, 0, 0, 0, 0); // the stack widget already sits inside the launcher frame
                v.setOnClickFillInIntent(R.id.widget_root, new Intent().putExtra("go", PAGES[i]));
                return v;
            }
        };
    }
}
