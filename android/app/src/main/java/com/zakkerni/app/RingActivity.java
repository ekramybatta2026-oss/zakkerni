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

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!fire(getIntent())) { finish(); return; }
        boolean timer = "timer".equals(RingService.kind);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#33284A"), Color.parseColor("#251D33"), Color.parseColor("#1D1726")}));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_c_alarm);
        icon.setColorFilter(Color.parseColor("#F28A32"));
        root.addView(icon, new LinearLayout.LayoutParams(dp(84), dp(84)));
        root.addView(text(WidgetRenderer.fmtTime(this, System.currentTimeMillis(), 1), 54, true, Color.WHITE));
        root.addView(text(timer ? "انتهى المؤقت" : (RingService.label.isEmpty() ? "المنبه" : RingService.label), 22, true, Color.parseColor("#FFB066")));

        Button stop = button("إيقاف", new int[]{Color.parseColor("#FFB066"), Color.parseColor("#F28A32")});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(60));
        lp.topMargin = dp(48);
        root.addView(stop, lp);
        stop.setOnClickListener(v -> { stopService(new Intent(this, RingService.class)); finish(); });
        if (!timer) {
            Button sn = button("غفوة ١٠ دقائق", new int[]{Color.parseColor("#7137A8"), Color.parseColor("#5A2690")});
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(dp(240), dp(54));
            lp2.topMargin = dp(14);
            root.addView(sn, lp2);
            sn.setOnClickListener(v -> { startService(new Intent(this, RingService.class).setAction(RingService.SNOOZE)); finish(); });
        }
        TextView hint = text(timer ? "أو اضغط زر الصوت للإيقاف" : "زر الصوت = غفوة", 14, false, Color.parseColor("#E6D6F5"));
        hint.setPadding(0, dp(14), 0, 0);
        root.addView(hint);
        setContentView(root);

        IntentFilter f = new IntentFilter(RingService.DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(done, f);
    }

    /** Launched by AlarmManager at ring time: update state and start the sound. False = nothing to ring. */
    private boolean fire(Intent i) {
        String f = i == null ? null : i.getStringExtra("fire");
        if (f == null) return RingService.running;
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
            if ("timer".equals(RingService.kind)) stopService(new Intent(this, RingService.class));
            else startService(new Intent(this, RingService.class).setAction(RingService.SNOOZE));
            finish();
            return true;
        }
        return super.onKeyDown(k, e);
    }

    @Override protected void onDestroy() { try { unregisterReceiver(done); } catch (Exception ignored) { } super.onDestroy(); }


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
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
