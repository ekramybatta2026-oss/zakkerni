package com.zakkerni.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.io.File;
import java.util.Arrays;

/** Photo frame widget: the user's own pictures, rounded like the other widgets; tap for the next one, changes by itself every half hour. */
public class PhotoWidget extends AppWidgetProvider {
    static final String NEXT = "com.zakkerni.app.PHOTO_NEXT";

    static File dir(Context c, int id) { return new File(c.getFilesDir(), "photos/" + id); }

    static File[] photos(Context c, int id) {
        File[] f = dir(c, id).listFiles((d, n) -> n.endsWith(".jpg"));
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) { advance(c, id, 1); draw(c, m, id); }
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { draw(c, m, id); }

    @Override public void onDeleted(Context c, int[] ids) {
        for (int id : ids) {
            File[] f = dir(c, id).listFiles(); if (f != null) for (File x : f) x.delete();
            dir(c, id).delete();
        }
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (NEXT.equals(i.getAction())) {
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            advance(c, id, 1);
            draw(c, AppWidgetManager.getInstance(c), id);
        }
    }

    static void advance(Context c, int id, int by) {
        int n = photos(c, id).length; if (n == 0) return;
        android.content.SharedPreferences p = c.getSharedPreferences("photo_w", Context.MODE_PRIVATE);
        p.edit().putInt("i" + id, (p.getInt("i" + id, 0) + by) % n).apply();
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_photo);
        File[] ph = photos(c, id);
        float dp = c.getResources().getDisplayMetrics().density;
        if (ph.length == 0) {
            v.setViewVisibility(R.id.ph_img, android.view.View.GONE);
            v.setViewVisibility(R.id.ph_empty, android.view.View.VISIBLE);
            Intent cfg = new Intent(c, PhotoPickActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.ph_root, PendingIntent.getActivity(c, 7000 + id, cfg, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            int idx = c.getSharedPreferences("photo_w", Context.MODE_PRIVATE).getInt("i" + id, 0) % ph.length;
            Bundle o = m.getAppWidgetOptions(id);
            int wdp = Math.max(110, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 170));
            int hdp = Math.max(110, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 170));
            Bitmap b = rounded(ph[idx], wdp, hdp, 20);
            if (b != null) v.setImageViewBitmap(R.id.ph_img, b);
            v.setViewVisibility(R.id.ph_img, android.view.View.VISIBLE);
            v.setViewVisibility(R.id.ph_empty, android.view.View.GONE);
            Intent nx = new Intent(c, PhotoWidget.class).setAction(NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            v.setOnClickPendingIntent(R.id.ph_root, PendingIntent.getBroadcast(c, 7000 + id, nx, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        m.updateAppWidget(id, v);
    }

    /** Centre-crop to the widget shape and round the corners (kept under the RemoteViews size limit). */
    static Bitmap rounded(File f, int wdp, int hdp, int rdp) {
        try {
            Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (src == null) return null;
            float scale = Math.min(1f, 720f / Math.max(wdp, hdp));
            int W = Math.round(wdp * 2.2f * scale), H = Math.round(hdp * 2.2f * scale);
            W = Math.min(W, 720); H = Math.min(H, 720);
            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(W / (float) src.getWidth(), H / (float) src.getHeight());
            Matrix mx = new Matrix(); mx.setScale(s, s);
            mx.postTranslate((W - src.getWidth() * s) / 2f, (H - src.getHeight() * s) / 2f);
            sh.setLocalMatrix(mx);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); p.setShader(sh);
            float r = rdp * W / (float) wdp;
            new Canvas(out).drawRoundRect(new RectF(0, 0, W, H), r, r, p);
            return out;
        } catch (Throwable t) { return null; }
    }

    static void refresh(Context c, int id) { draw(c, AppWidgetManager.getInstance(c), id); }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, PhotoWidget.class))) draw(c, m, id);
    }
}
