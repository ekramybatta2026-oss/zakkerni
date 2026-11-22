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
        // dark night background (same as the app's dark mode) with the light arabesque
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#2A2236"), Color.parseColor("#1D1726"), Color.parseColor("#160F1F")});
        android.graphics.drawable.BitmapDrawable pat = new android.graphics.drawable.BitmapDrawable(getResources(),
                android.graphics.Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.adhan_pat_dark), dp(220), dp(220), true));
        pat.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{grad, pat}));
        getWindow().setStatusBarColor(Color.parseColor("#2A2236"));
        getWindow().setNavigationBarColor(Color.parseColor("#160F1F"));

        int or = Color.parseColor("#F28A32"), orL = Color.parseColor("#FFB066");
        ImageView word = new ImageView(this);
        word.setImageResource(R.drawable.adhan_word);
        word.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(word, new LinearLayout.LayoutParams(dp(200), dp(126)));

        TextView small = text("حان الآن موعد أذان", 19, false, orL);
        small.setPadding(0, dp(26), 0, 0);
        root.addView(small);
        root.addView(text(AdhanService.curName.isEmpty() ? "الصلاة" : AdhanService.curName, 52, true, or));

        TextView time = text(AdhanService.curTime, 32, true, orL);
        time.setPadding(dp(24), dp(2), dp(24), dp(2));
        time.setBackground(frame(dp(22)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(10);
        root.addView(time, tl);

        String city = AdhanScheduler.prefs(this).getString(AdhanScheduler.KEY_CITY, "");
        if (!city.isEmpty()) {
            TextView c = text(city, 19, false, orL);
            c.setPadding(dp(22), dp(4), dp(22), dp(4));
            c.setBackground(frame(dp(30)));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cl.topMargin = dp(14);
            root.addView(c, cl);
        }

        Button stop = new Button(this);
        stop.setText("إيقاف الأذان");
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        stop.setTypeface(font(true));
        stop.setTextColor(orL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#8A4BC4"), Color.parseColor("#5A2690")});
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), Color.parseColor("#66F28A32"));
        stop.setBackground(bg);
        stop.setElevation(dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(60));
        lp.topMargin = dp(40);
        root.addView(stop, lp);
        stop.setOnClickListener(v -> stopAdhan());

        TextView hint = text("أو اضغط زر رفع/خفض الصوت", 14, false, Color.parseColor("#B4A6C6"));
        hint.setPadding(0, dp(14), 0, 0);
        root.addView(hint);

        setContentView(root);

        IntentFilter f = new IntentFilter(AdhanService.ACTION_DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(done, f);
        if (!AdhanService.running) finish();
    }

    private void stopAdhan() {
        stopService(new Intent(this, AdhanService.class));
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

    private GradientDrawable frame(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#1FF28A32"));
        g.setStroke(dp(2), Color.parseColor("#B3F28A32"));
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
