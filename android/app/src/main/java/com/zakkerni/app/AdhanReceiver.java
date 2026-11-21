package com.zakkerni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (AdhanScheduler.ACTION_FIRE.equals(intent.getAction())) {
            Intent s = new Intent(c, AdhanService.class);
            s.putExtra("name", intent.getStringExtra("name"));
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            } catch (Exception ignored) { }
        }
        // after boot, time change, update, or a fired alarm: arm the next adhan
        AdhanScheduler.scheduleNext(c);
    }
}
