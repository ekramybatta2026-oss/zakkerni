package com.zakkerni.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Widget customisation: colour, transparency, corners, text colour & size, visible elements — with live preview. */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int type;
    private WidgetRenderer.Cfg cfg;
    private FrameLayout preview;
    private boolean dark;
    private int ink, muted, card;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Bundle ex = getIntent().getExtras();
        if (ex != null) widgetId = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        type = WidgetRenderer.typeOf(this, widgetId);
        cfg = WidgetRenderer.Cfg.load(this, widgetId);

        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = Color.parseColor(dark ? "#1D1726" : "#FAF6FD");
        ink = Color.parseColor(dark ? "#F3ECFA" : "#2A1F36");
        muted = Color.parseColor(dark ? "#B4A6C6" : "#7A6E88");
        card = Color.parseColor(dark ? "#2A2236" : "#FFFFFF");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        sv.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.setPadding(dp(16), dp(20), dp(16), dp(24));
        sv.addView(col);

        TextView title = label("تخصيص الويدجت", 22, true, Color.parseColor(dark ? "#D9B8F5" : "#7137A8"));
        col.addView(title);
        col.addView(label("التغييرات تظهر في المعاينة مباشرة", 13, false, muted));

        // preview on a sample "wallpaper" so transparency is visible
        FrameLayout wall = new FrameLayout(this);
        GradientDrawable wp = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#3A6073"), Color.parseColor("#16222A"), Color.parseColor("#7B4397")});
        wp.setCornerRadius(dp(20));
        wall.setBackground(wp);
        wall.setPadding(dp(18), dp(18), dp(18), dp(18));
        preview = new FrameLayout(this);
        int[] ph = {150, 120, 170, 300, 86, 160, 180};
        int[] pw = {170, -1, -1, 190, -1, 160, -1};
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(pw[type] < 0 ? -1 : dp(pw[type]), dp(ph[type]), Gravity.CENTER);
        wall.addView(preview, plp);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = dp(14);
        col.addView(wall, wl);

        LinearLayout box = section(col);
        if (type == WidgetRenderer.T_DAY) {
            box.addView(head("التصميم"));
            box.addView(segment(WidgetRenderer.DESIGNS, cfg.design, i -> cfg.design = i, false));
        }
        box.addView(head("اللون"));
        colourGrid(box);

        box.addView(head("الشفافية"));
        TextView opL = label(digits(cfg.opacity + "٪ ظاهر"), 13, false, muted);
        box.addView(opL);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(cfg.opacity);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { cfg.opacity = p; opL.setText(digits(p + "٪ ظاهر")); render(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(sb);

        box.addView(head("الزوايا"));
        box.addView(segment(new String[]{"حادة", "مستديرة", "دائرية"}, cfg.radius, i -> cfg.radius = i, false));
        box.addView(head("لون النص"));
        box.addView(segment(new String[]{"تلقائي", "أبيض", "داكن"}, cfg.text, i -> cfg.text = i, false));
        if (type < WidgetRenderer.T_PLAYER) {
            box.addView(head("صيغة الوقت"));
            box.addView(segment(new String[]{digits("05:19"), digits("5:19 ص"), digits("17:19")}, cfg.fmt, i -> cfg.fmt = i, false));
        }
        box.addView(head("حجم الخط"));
        box.addView(segment(new String[]{"صغير", "عادي", "كبير"}, cfg.font, i -> cfg.font = i, false));

        if (type >= WidgetRenderer.T_PLAYER) {
            LinearLayout box3 = section(col);
            box3.addView(toggle("صورة الملف الصوتي كخلفية (والأرابيسك إن لم توجد صورة)", cfg.art, v -> cfg.art = v));
        } else {
            LinearLayout box2 = section(col);
            box2.addView(toggle("العدّ التنازلي للصلاة القادمة", cfg.cd, v -> cfg.cd = v));
            box2.addView(toggle("اسم المدينة", cfg.city, v -> cfg.city = v));
            box2.addView(toggle("التاريخ الهجري", cfg.hijri, v -> cfg.hijri = v));
        }

        Button save = new Button(this);
        save.setText("حفظ الويدجت");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#FFB066"), Color.parseColor("#F28A32")});
        sbg.setCornerRadius(dp(16));
        save.setBackground(sbg);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(56));
        sl.topMargin = dp(18);
        col.addView(save, sl);
        save.setOnClickListener(v -> {
            cfg.save(this, widgetId);
            AppWidgetManager.getInstance(this).updateAppWidget(widgetId, WidgetRenderer.build(this, type, cfg));
            WidgetRenderer.scheduleTick(this, true);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            finish();
        });

        setContentView(sv);
        render();
    }

    private void render() {
        preview.removeAllViews();
        try {
            View v = WidgetRenderer.build(this, type, cfg).apply(this, preview);
            preview.addView(v, new FrameLayout.LayoutParams(-1, -1));
        } catch (Exception ignored) { }
    }

    /* ---------- small UI helpers ---------- */
    interface IntPick { void pick(int i); }
    interface BoolPick { void pick(boolean b); }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(card);
        g.setCornerRadius(dp(20));
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        parent.addView(box, lp);
        return box;
    }

    private TextView head(String s) {
        TextView t = label(s, 15, true, ink);
        t.setPadding(0, dp(12), 0, dp(6));
        return t;
    }

    private LinearLayout segment(String[] items, int sel, IntPick onPick, boolean colours) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button[] bs = new Button[items.length];
        int[] sw = {0xFF7137A8, 0xFF7137A8, 0xFFE5761F, 0xFF1D1726, 0xFFFFFFFF};
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(items[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setPadding(0, 0, 0, 0);
            bs[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            lp.setMarginStart(i == 0 ? 0 : dp(6));
            row.addView(b, lp);
            b.setOnClickListener(v -> { onPick.pick(idx); paint(bs, idx, colours, sw); render(); });
        }
        paint(bs, sel, colours, sw);
        return row;
    }

    private void paint(Button[] bs, int sel, boolean colours, int[] sw) {
        for (int i = 0; i < bs.length; i++) {
            GradientDrawable g;
            if (colours && i == 0) g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF7137A8, 0xFFB04E0A});
            else { g = new GradientDrawable(); g.setColor(colours ? sw[i] : (i == sel ? 0xFF7137A8 : (dark ? 0xFF352A45 : 0xFFF1E8F8))); }
            g.setCornerRadius(dp(12));
            if (i == sel) g.setStroke(dp(3), 0xFFF28A32); else if (colours && i == 4) g.setStroke(dp(1), 0x33000000);
            bs[i].setBackground(g);
            int tc = colours ? (i == 4 ? 0xFF2A1F36 : 0xFFFFFFFF) : (i == sel ? 0xFFFFFFFF : ink);
            bs[i].setTextColor(tc);
            bs[i].setTypeface(i == sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    /** 9 colour swatches in rows of 5, previewing each style's real colours. */
    private void colourGrid(LinearLayout box) {
        String[] n = WidgetRenderer.STYLE_NAMES;
        Button[] bs = new Button[n.length];
        LinearLayout row = null;
        for (int i = 0; i < n.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = i == 0 ? 0 : dp(6);
                box.addView(row, rl);
            }
            final int idx = i;
            Button b = new Button(this);
            b.setText(n[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setPadding(0, 0, 0, 0);
            bs[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            lp.setMarginStart(i % 5 == 0 ? 0 : dp(6));
            row.addView(b, lp);
            b.setOnClickListener(v -> { cfg.style = idx; paintSwatches(bs); render(); });
        }
        if (n.length % 5 != 0) for (int k = n.length % 5; k < 5; k++) { View sp = new View(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1); lp.setMarginStart(dp(6)); row.addView(sp, lp); }
        paintSwatches(bs);
    }

    private void paintSwatches(Button[] bs) {
        for (int i = 0; i < bs.length; i++) {
            bs[i].setBackgroundResource(WidgetRenderer.bgRes(i, 1));
            android.graphics.drawable.Drawable d = bs[i].getBackground();
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(12));
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(i == cfg.style ? dp(3) : dp(1), i == cfg.style ? 0xFFF28A32 : 0x33000000);
            bs[i].setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{d, ring}));
            bs[i].setTextColor(WidgetRenderer.lightBg(i) ? 0xFF2A1F36 : 0xFFFFFFFF);
            bs[i].setTypeface(i == cfg.style ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private View toggle(String text, boolean on, BoolPick onPick) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(ink);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setChecked(on);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setOnCheckedChangeListener((c, v) -> { onPick.pick(v); render(); });
        s.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        return s;
    }

    private TextView label(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private String digits(String s) { return WidgetRenderer.digits(this, s); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
