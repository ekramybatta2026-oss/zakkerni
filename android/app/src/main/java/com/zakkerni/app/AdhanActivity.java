package com.zakkerni.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen adhan screen shown over the lock screen. */
public class AdhanActivity extends Activity {
    private final BroadcastReceiver done = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finish(); }
    };

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        boolean preview = getIntent() != null && getIntent().getBooleanExtra("preview", false);
        this.preview = preview;
        String theme = AdhanScheduler.prefs(this).getString("adhan_bg", "night");
        boolean light = "orange".equals(theme);
        applyBackground(root, theme);

        int or = Color.parseColor(light ? "#4A1C78" : "#F28A32"), orL = Color.parseColor(light ? "#4A1C78" : "#FFB066");
        int frameCol = Color.parseColor(light ? "#B34A1C78" : "#B3F28A32"), frameFill = Color.parseColor(light ? "#1F4A1C78" : "#1FF28A32");
        ImageView word = new ImageView(this);
        word.setImageResource(light ? R.drawable.adhan_word_purple : R.drawable.adhan_word);
        word.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(word, new LinearLayout.LayoutParams(dp(200), dp(126)));

        TextView small = text("حان الآن موعد أذان", 19, false, orL);
        small.setPadding(0, dp(30), 0, dp(8));
        root.addView(small);
        String name = AdhanService.curName.isEmpty() || preview ? (preview ? "المغرب" : "الصلاة") : AdhanService.curName;
        root.addView(text(name, 52, true, or));

        String tm = preview ? WidgetRenderer.fmtTime(this, System.currentTimeMillis(), 1) : AdhanService.curTime;
        TextView time = text(tm, 32, true, orL);
        time.setPadding(dp(36), dp(8), dp(36), dp(8));
        time.setBackground(frame(dp(26), frameFill, 0));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(18);
        root.addView(time, tl);

        String city = AdhanScheduler.prefs(this).getString(AdhanScheduler.KEY_CITY, "");
        if (!city.isEmpty()) {
            String wx = Weather.text(this);
            TextView c = text(city + (wx.isEmpty() ? "" : "   " + wx), 19, false, orL);
            c.setPadding(dp(22), dp(6), dp(22), dp(6));
            c.setBackground(frame(dp(30), frameFill, 0));
            android.graphics.drawable.Drawable pin = getDrawable(R.drawable.ic_pin_m);
            if (pin != null) { pin.setTint(orL); c.setCompoundDrawablesRelativeWithIntrinsicBounds(pin, null, null, null); c.setCompoundDrawablePadding(dp(6)); }
            c.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cl.topMargin = dp(18);
            root.addView(c, cl);
        }

        Button stop = new Button(this);
        stop.setText("إيقاف الأذان");
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        stop.setTypeface(font(true));
        stop.setTextColor(orL);
        stop.setBackground(frame(dp(30), frameFill, 0));   // same soft panel as the time, no border
        stop.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(60));
        lp.topMargin = dp(46);
        root.addView(stop, lp);
        stop.setOnClickListener(v -> stopAdhan());

        TextView hint = text(preview ? "معاينة — اضغط للإغلاق" : "أو اضغط زر رفع/خفض الصوت", 14, false, Color.parseColor(light ? "#5A2690" : "#B4A6C6"));
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint);

        setContentView(root);

        IntentFilter f = new IntentFilter(AdhanService.ACTION_DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(done, f);
        if (!AdhanService.running && !preview) finish();
    }

    private boolean preview = false;

    /** Background presets for the adhan screen, or the user's own picture. */
    private void applyBackground(LinearLayout root, String theme) {
        android.graphics.drawable.Drawable base; int top, bottom; int patRes = R.drawable.adhan_pat_dark; int patAlpha = 255;
        switch (theme) {
            case "orange": top = Color.parseColor("#FFAA5C"); bottom = Color.parseColor("#E8761E"); patRes = R.drawable.adhan_pat; patAlpha = 150; break;
            case "purple": top = Color.parseColor("#7137A8"); bottom = Color.parseColor("#3A1660"); patAlpha = 200; break;
            case "green": top = Color.parseColor("#1F3B33"); bottom = Color.parseColor("#0F221C"); break;
            default: top = Color.parseColor("#2A2236"); bottom = Color.parseColor("#160F1F");
        }
        base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        android.graphics.drawable.Drawable layer2 = null;
        if ("custom".equals(theme)) {
            try {
                java.io.File f = new java.io.File(getFilesDir(), "adhan_bg.jpg");
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bm != null) {
                    ImageView iv = new ImageView(this);
                    android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), bm);
                    base = new CenterCrop(bd);
                    layer2 = new android.graphics.drawable.ColorDrawable(Color.parseColor("#80000000"));
                }
            } catch (Exception ignored) { }
        }
        if (layer2 == null && !"custom".equals(theme)) {
            android.graphics.drawable.BitmapDrawable pat = new android.graphics.drawable.BitmapDrawable(getResources(),
                    android.graphics.Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeResource(getResources(), patRes), dp(220), dp(220), true));
            pat.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
            pat.setAlpha(patAlpha);
            layer2 = pat;
        }
        root.setBackground(layer2 == null ? base : new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, layer2}));
        getWindow().setStatusBarColor("custom".equals(theme) ? Color.BLACK : top);
        getWindow().setNavigationBarColor("custom".equals(theme) ? Color.BLACK : bottom);
    }

    /** Draws a bitmap scaled to cover the whole screen without stretching. */
    static class CenterCrop extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.BitmapDrawable d;
        CenterCrop(android.graphics.drawable.BitmapDrawable d) { this.d = d; }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds(); android.graphics.Bitmap bm = d.getBitmap();
            float s = Math.max(b.width() / (float) bm.getWidth(), b.height() / (float) bm.getHeight());
            int w = Math.round(bm.getWidth() * s), h = Math.round(bm.getHeight() * s);
            int x = b.left + (b.width() - w) / 2, y = b.top + (b.height() - h) / 2;
            c.drawBitmap(bm, null, new android.graphics.Rect(x, y, x + w, y + h), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    private void stopAdhan() {
        if (!preview) stopService(new Intent(this, AdhanService.class));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { stopAdhan(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(done); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(font(bold));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    private Typeface font(boolean bold) {
        try { if (Build.VERSION.SDK_INT >= 26) return getResources().getFont(bold ? R.font.cairo_bold : R.font.cairo_semibold); }
        catch (Exception ignored) { }
        return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    private GradientDrawable frame(int radius, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); if (stroke != 0) g.setStroke(dp(2), stroke); g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable frame(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#1FF28A32"));
        g.setStroke(dp(2), Color.parseColor("#B3F28A32"));
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
