package com.zakkerni.app;

import android.app.Activity;
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

/** Full-screen alarm / timer screen over the lock screen. */
public class RingActivity extends Activity {
    private final BroadcastReceiver done = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finish(); }
    };
    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (System.currentTimeMillis() - shownAt > 3000) stopRing(); }
    };
    private long shownAt = System.currentTimeMillis();

    private void stopRing() { stopService(new Intent(this, RingService.class)); finish(); }

    private void snooze(int min) {
        startService(new Intent(this, RingService.class).setAction(RingService.SNOOZE).putExtra("min", min));
        android.widget.Toast.makeText(this, "غفوة " + toAr(min) + (min <= 10 ? " دقائق" : " دقيقة"), android.widget.Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String toAr(int n) {
        String s = String.valueOf(n), d = "٠١٢٣٤٥٦٧٨٩"; StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(d.charAt(ch - '0'));
        return b.toString();
    }

    private Typeface font(boolean bold) {
        try { if (Build.VERSION.SDK_INT >= 26) return getResources().getFont(bold ? R.font.cairo_bold : R.font.cairo_semibold); } catch (Exception ignored) { }
        return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    private GradientDrawable frame(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#1FF28A32"));
        g.setStroke(dp(2), Color.parseColor("#B3F28A32"));
        g.setCornerRadius(radius);
        return g;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!fire(getIntent())) { finish(); return; }
        AdhanLog.add(this, "ظهرت شاشة المنبه");
        boolean timer = "timer".equals(RingService.kind);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        // same look as the adhan screen: night background with the light arabesque
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#2A2236"), Color.parseColor("#1D1726"), Color.parseColor("#160F1F")});
        android.graphics.drawable.BitmapDrawable pat = new android.graphics.drawable.BitmapDrawable(getResources(),
                android.graphics.Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.adhan_pat_dark), dp(220), dp(220), true));
        pat.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{grad, pat}));
        getWindow().setStatusBarColor(Color.parseColor("#2A2236"));
        getWindow().setNavigationBarColor(Color.parseColor("#160F1F"));
        int or = Color.parseColor("#F28A32"), orL = Color.parseColor("#FFB066"), deep = Color.parseColor("#4A1C78");

        ImageView word = new ImageView(this);
        word.setImageResource(R.drawable.adhan_word);
        word.setScaleType(ImageView.ScaleType.FIT_CENTER);
        word.setAlpha(.9f);
        root.addView(word, new LinearLayout.LayoutParams(dp(150), dp(95)));

        TextView kindT = text(timer ? "انتهى المؤقت" : "المنبه", 19, false, orL);
        kindT.setPadding(0, dp(22), 0, 0);
        root.addView(kindT);
        TextView time = text(WidgetRenderer.fmtTime(this, System.currentTimeMillis(), 1), 40, true, orL);
        time.setPadding(dp(34), dp(6), dp(34), dp(6));
        time.setBackground(frame(dp(26)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(12);
        root.addView(time, tl);
        if (!timer && !RingService.label.isEmpty()) {
            TextView lbl = text(RingService.label, 22, true, or);
            lbl.setPadding(0, dp(14), 0, 0);
            root.addView(lbl);
        }

        Button stop = new Button(this);
        stop.setText("إيقاف"); stop.setAllCaps(false); stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        stop.setTypeface(font(true)); stop.setTextColor(deep);
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{orL, or});
        sg.setCornerRadius(dp(34));
        stop.setBackground(sg); stop.setElevation(dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(68));
        lp.topMargin = dp(44);
        root.addView(stop, lp);
        stop.setOnClickListener(v -> stopRing());

        if (!timer) {
            TextView sn = text("غفوة", 14, false, Color.parseColor("#B4A6C6"));
            sn.setPadding(0, dp(22), 0, dp(8));
            root.addView(sn);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            int[][] opts = {{5}, {10}, {15}};
            for (int[] o : opts) {
                int m = o[0];
                Button c = new Button(this);
                c.setText(toAr(m) + " د"); c.setAllCaps(false); c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                c.setTypeface(font(true)); c.setTextColor(m == 10 ? Color.WHITE : orL);
                GradientDrawable cg = m == 10 ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#8A4BC4"), Color.parseColor("#5A2690")}) : new GradientDrawable();
                cg.setCornerRadius(dp(22));
                if (m != 10) cg.setStroke(dp(1), Color.parseColor("#99FFB066"));
                c.setBackground(cg);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(76), dp(44));
                cl.setMarginStart(dp(5)); cl.setMarginEnd(dp(5));
                row.addView(c, cl);
                c.setOnClickListener(v -> snooze(m));
            }
            root.addView(row);
        }
        TextView hint = text(timer ? "زر الصوت أو زر التشغيل = إيقاف" : "زر الصوت = غفوة ١٠ د  ·  زر التشغيل = إيقاف", 13, false, Color.parseColor("#B4A6C6"));
        hint.setPadding(0, dp(22), 0, 0);
        root.addView(hint);
        setContentView(root);

        // power button: the screen is kept on while this is shown, so a screen-off means the user pressed it
        IntentFilter off = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(power, off, Context.RECEIVER_EXPORTED); else registerReceiver(power, off);

        IntentFilter f = new IntentFilter(RingService.DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(done, f);
    }

    /** Launched by AlarmManager at ring time: update state and start the sound. False = nothing to ring. */
    private boolean fire(Intent i) {
        String f = i == null ? null : i.getStringExtra("fire");
        if (f == null) return true; // opened by the ringing service / notification
        i.removeExtra("fire");
        if ("timer".equals(f)) {
            boolean was = ClockStore.p(this).getBoolean("t_run", false);
            long late = System.currentTimeMillis() - ClockStore.p(this).getLong("t_end", 0);
            ClockStore.timerCmd(this, "done", 0);
            if (!was || late > 10 * 60_000L) return false;
            RingService.kind = "timer"; RingService.label = "";
        } else {
            int id = i.getIntExtra("id", 0);
            ClockStore.afterRing(this, id);
            WidgetRenderer.updateAll(this);
            RingService.kind = "alarm"; RingService.alarmId = id; RingService.label = i.getStringExtra("label") == null ? "" : i.getStringExtra("label");
        }
        Intent s = new Intent(this, RingService.class).putExtra("kind", RingService.kind).putExtra("id", RingService.alarmId).putExtra("label", RingService.label);
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s); } catch (Exception e) { return false; }
        RingService.running = true;
        return true;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getStringExtra("fire") != null) { setIntent(i); recreate(); }
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if ("timer".equals(RingService.kind)) stopRing(); else snooze(10);
            return true;
        }
        return super.onKeyDown(k, e);
    }

    @Override protected void onDestroy() { try { unregisterReceiver(done); } catch (Exception ignored) { } try { unregisterReceiver(power); } catch (Exception ignored) { } super.onDestroy(); }


    private Button button(String s, int[] cols) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols);
        g.setCornerRadius(dp(30));
        b.setBackground(g);
        return b;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        t.setTypeface(font(bold)); t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
