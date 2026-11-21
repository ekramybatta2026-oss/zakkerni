package com.zakkerni.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Plays the official Zakkerni adhan with a notification that has a stop button. */
public class AdhanService extends Service {
    static final String ACTION_STOP = "com.zakkerni.app.STOP_ADHAN";
    private static final int NOTIF_ID = 77;
    private MediaPlayer player;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String name = intent != null ? intent.getStringExtra("name") : null;
        if (name == null) name = "الصلاة";

        AdhanScheduler.createChannel(this);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AdhanService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, AdhanScheduler.CHANNEL)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH);
        b.setSmallIcon(R.drawable.ic_stat_mosque)
                .setColor(Color.parseColor("#7137A8"))
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText("ذَكِّرني — اضغط إيقاف لإسكات الأذان")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(new Notification.Action.Builder(null, "إيقاف الأذان", stop).build());
        Notification n = b.build();

        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);

        play();
        return START_NOT_STICKY;
    }

    private void play() {
        release();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor afd = getAssets().openFd("www/adhan.mp3");
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(mp -> stopSelf());
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        release();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
