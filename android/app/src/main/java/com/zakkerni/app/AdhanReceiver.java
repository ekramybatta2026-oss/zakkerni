package com.zakkerni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (AdhanScheduler.ACTION_FIRE.equals(intent.getAction()) && intent.getBooleanExtra("adhan", true)
                && intent.getStringExtra("name") != null) {
            Intent s = new Intent(c, AdhanService.class);
            s.putExtra("name", intent.getStringExtra("name"));
            s.putExtra("file", intent.getStringExtra("file"));
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            } catch (Exception ignored) { }
        }
        // after a prayer time, boot, time change or app update: refresh the notification, arm the next one
        AdhanScheduler.updatePinned(c);
        AdhanScheduler.scheduleNext(c);
    }
}
