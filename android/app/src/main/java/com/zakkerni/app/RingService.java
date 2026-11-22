package com.zakkerni.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Rings for an alarm or a finished timer: full screen when locked, heads-up when in use. */
public class RingService extends Service {
    static final String CHANNEL = "clock_ring";
    static final String STOP = "com.zakkerni.app.RING_STOP", SNOOZE = "com.zakkerni.app.RING_SNOOZE";
    static final String DONE = "com.zakkerni.app.RING_DONE";
    static volatile String kind = "alarm", label = "";
    static volatile int alarmId = 0;
    static volatile boolean running = false;

    private MediaPlayer player;
    private Vibrator vib;
    private MediaSession session;
    private PowerManager.WakeLock lock;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done = false;

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String act = i == null ? null : i.getAction();
        if (STOP.equals(act)) { stopSelf(); return START_NOT_STICKY; }
        if (SNOOZE.equals(act)) { ClockStore.snooze(this, alarmId); WidgetRenderer.updateAll(this); stopSelf(); return START_NOT_STICKY; }
        kind = i != null && i.getStringExtra("kind") != null ? i.getStringExtra("kind") : "alarm";
        alarmId = i != null ? i.getIntExtra("id", 0) : 0;
        label = i != null && i.getStringExtra("label") != null ? i.getStringExtra("label") : "";
        running = true;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "المنبه والمؤقت", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        boolean timer = "timer".equals(kind);
        String title = timer ? "انتهى المؤقت" : (label.isEmpty() ? "المنبه" : label);
        String text = timer ? "اضغط إيقاف" : WidgetRenderer.fmtTime(this, System.currentTimeMillis(), 1);
        PendingIntent full = PendingIntent.getActivity(this, 8, new Intent(this, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 9, new Intent(this, RingService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_MAX);
        b.setSmallIcon(R.drawable.ic_stat_zal).setColor(Color.parseColor("#7137A8"))
                .setContentTitle(title).setContentText(text)
                .setContentIntent(full).setFullScreenIntent(full, true)
                .setOngoing(true).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "إيقاف", stop).build());
        if (!timer) {
            PendingIntent sn = PendingIntent.getService(this, 10, new Intent(this, RingService.class).setAction(SNOOZE), PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null, "غفوة ١٠ د", sn).build());
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(78, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(78, b.build());

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "zakkerni:ring");
            lock.acquire(15_000);
        } catch (Exception ignored) { }
        startSound();
        startVibration();
        volumeKeys(timer);
        h.postDelayed(this::stopSelf, timer ? 2 * 60_000L : 5 * 60_000L); // don't ring forever
        return START_NOT_STICKY;
    }

    /** "" = default alarm tone, "asset:file.mp3" = app sound, otherwise a content:// uri. */
    static MediaPlayer player(Context c, String tone, boolean loop) throws Exception {
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        if (tone != null && tone.startsWith("asset:")) {
            String f = tone.substring(6);
            if (f.contains("..")) f = "adhan.mp3";
            android.content.res.AssetFileDescriptor afd = c.getAssets().openFd("www/" + f);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } else {
            Uri u = tone == null || tone.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(tone);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mp.setDataSource(c, u);
        }
        mp.setLooping(loop);
        mp.prepare();
        return mp;
    }

    private void startSound() {
        String tone = ClockStore.toneFor(this, kind, alarmId);
        try { player = player(this, tone, true); player.start(); }
        catch (Exception e) {
            try { player = player(this, "", true); player.start(); } catch (Exception ignored) { }
        }
    }

    private void startVibration() {
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pat = {0, 600, 500};
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pat, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            else vib.vibrate(pat, 0);
        } catch (Exception ignored) { }
    }

    /** Volume keys: snooze an alarm, stop a timer. */
    private void volumeKeys(boolean timer) {
        try {
            session = new MediaSession(this, "zakkerni-ring");
            session.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override public void onAdjustVolume(int d) {
                    if (d == 0) return;
                    if (!timer) ClockStore.snooze(RingService.this, alarmId);
                    stopSelf();
                }
            });
            session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_STOP)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
            session.setActive(true);
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        if (!done) {
            done = true;
            h.removeCallbacksAndMessages(null);
            if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
            if (vib != null) { try { vib.cancel(); } catch (Exception ignored) { } }
            if (session != null) { try { session.setActive(false); session.release(); } catch (Exception ignored) { } }
            if (lock != null && lock.isHeld()) { try { lock.release(); } catch (Exception ignored) { } }
            running = false;
            sendBroadcast(new Intent(DONE).setPackage(getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
