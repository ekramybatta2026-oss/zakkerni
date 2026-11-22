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
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#7137A8"), Color.parseColor("#5A2690"), Color.parseColor("#B04E0A")}));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_zakkerni);
        root.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView small = text("حان الآن موعد أذان", 18, false, Color.parseColor("#F1E8F8"));
        small.setPadding(0, dp(28), 0, 0);
        root.addView(small);
        root.addView(text(AdhanService.curName.isEmpty() ? "الصلاة" : AdhanService.curName, 48, true, Color.WHITE));
        TextView time = text(AdhanService.curTime, 20, true, Color.parseColor("#FFB066"));
        root.addView(time);
        String city = AdhanScheduler.prefs(this).getString(AdhanScheduler.KEY_CITY, "");
        if (!city.isEmpty()) root.addView(text(city, 15, false, Color.parseColor("#F1E8F8")));

        Button stop = new Button(this);
        stop.setText("إيقاف الأذان");
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        stop.setTypeface(Typeface.DEFAULT_BOLD);
        stop.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#FFB066"), Color.parseColor("#F28A32")});
        bg.setCornerRadius(dp(30));
        stop.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(60));
        lp.topMargin = dp(44);
        root.addView(stop, lp);
        stop.setOnClickListener(v -> stopAdhan());

        TextView hint = text("أو اضغط زر رفع/خفض الصوت", 14, false, Color.parseColor("#E6D6F5"));
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
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
