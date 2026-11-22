package com.zakkerni.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (AdhanScheduler.ACTION_RESTORE.equals(action)) {
            AdhanScheduler.restoreRinger(c);
            return;
        }
        if (AdhanScheduler.ACTION_FIRE.equals(action) && intent.getStringExtra("name") != null) {
            String name = intent.getStringExtra("name");
            if (intent.getBooleanExtra("pre", false)) {
                AdhanScheduler.showPre(c, name, intent.getStringExtra("time"));
            } else if (intent.getBooleanExtra("adhan", true)) {
                Intent s = new Intent(c, AdhanService.class)
                        .putExtra("name", name)
                        .putExtra("time", intent.getStringExtra("time"))
                        .putExtra("file", intent.getStringExtra("file"));
                try {
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                    else c.startService(s);
                } catch (Exception ignored) { }
            }
        }
        // after any event, boot, time change or update: refresh notification/widget, arm the next event
        AdhanScheduler.updatePinned(c);
        AdhanScheduler.scheduleNext(c);
    }
}
