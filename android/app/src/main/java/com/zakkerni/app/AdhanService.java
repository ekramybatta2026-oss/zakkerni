package com.zakkerni.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Plays the adhan. Locked phone → full-screen AdhanActivity (screen turns on);
 * phone in use → heads-up banner. Volume keys or the stop button end it.
 * Afterwards: optional silent mode for the chosen minutes.
 */
public class AdhanService extends Service {
    static final String ACTION_STOP = "com.zakkerni.app.STOP_ADHAN";
    static final String ACTION_DONE = "com.zakkerni.app.ADHAN_DONE";
    private static final int NOTIF_ID = 77;

    static volatile boolean running = false;
    static String curName = "", curTime = "";

    private MediaPlayer player;
    private MediaSession session;
    private PowerManager.WakeLock screenLock;
    private int savedAlarmVol = -1;
    private boolean finished = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String name = intent != null ? intent.getStringExtra("name") : null;
        String time = intent != null ? intent.getStringExtra("time") : null;
        String file = intent != null ? intent.getStringExtra("file") : null;
        if (name == null) name = "الصلاة";
        if (time == null) time = "";
        if (file == null || file.contains("..")) file = "adhan.mp3";
        curName = name; curTime = time; running = true;

        AdhanScheduler.createChannels(this);
        // auto: full screen when locked, banner when in use · always: full screen both ways · banner: never full screen
        String mode = AdhanScheduler.prefs(this).getString("fullmode", AdhanScheduler.prefs(this).getBoolean(AdhanScheduler.KEY_FULL, true) ? "auto" : "banner");
        boolean full = !"banner".equals(mode);

        Intent fs = new Intent(this, AdhanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(this, 4, fs, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AdhanService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, AdhanScheduler.CHANNEL)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_MAX);
        b.setSmallIcon(R.drawable.ic_stat_zal)
                .setColor(Color.parseColor("#7137A8"))
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText((time.isEmpty() ? "" : time + " — ") + "اضغط إيقاف أو زر الصوت لإسكات الأذان")
                .setContentIntent(fullPi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "إيقاف الأذان", stop).build());
        if (full) b.setFullScreenIntent(fullPi, true); // locked: full screen; in use: heads-up banner
        Notification n = b.build();
        if ("always".equals(mode)) {
            try {
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km == null || !km.isKeyguardLocked()) startActivity(fs);   // phone in use: open the full screen too
            } catch (Exception ignored) { }
        }

        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);

        wakeScreen();
        startVolumeKeyStop();
        play(file);
        return START_NOT_STICKY;
    }

    /** Turn the screen on for the adhan. */
    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            screenLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, "zakkerni:adhan");
            screenLock.acquire(15_000);
        } catch (Exception ignored) { }
    }

    /** Volume up / down stops the adhan (screen on or off, phone locked or not). */
    private void startVolumeKeyStop() {
        try {
            session = new MediaSession(this, "zakkerni-adhan");
            session.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override public void onAdjustVolume(int direction) { if (direction != 0) stopSelf(); }
            });
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPause() { stopSelf(); }
                @Override public void onStop() { stopSelf(); }
            });
            session.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
            session.setActive(true);
        } catch (Exception ignored) { }
    }

    private void play(String file) {
        release();
        try {
            AudioManager au = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int pct = AdhanScheduler.prefs(this).getInt(AdhanScheduler.KEY_VOL, 80);
            if (au != null) {
                savedAlarmVol = au.getStreamVolume(AudioManager.STREAM_ALARM);
                int max = au.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                try { au.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, Math.round(max * pct / 100f)), 0); } catch (Exception ignored) { }
            }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            AssetFileDescriptor afd;
            try { afd = getAssets().openFd("www/" + file); }
            catch (Exception missing) { afd = getAssets().openFd("www/adhan.mp3"); }
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(mp -> stopSelf());
            player.prepare();
            player.start();
            AdhanLog.add(this, "بدأ تشغيل الأذان (" + file + ")");
        } catch (Exception e) {
            AdhanLog.add(this, "فشل تشغيل الأذان: " + e.getClass().getSimpleName() + " " + e.getMessage());
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
        if (!finished) {
            finished = true;
            release();
            if (session != null) { try { session.setActive(false); session.release(); } catch (Exception ignored) { } }
            if (screenLock != null && screenLock.isHeld()) { try { screenLock.release(); } catch (Exception ignored) { } }
            AudioManager au = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (au != null && savedAlarmVol >= 0) { try { au.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0); } catch (Exception ignored) { } }
            running = false;
            sendBroadcast(new Intent(ACTION_DONE).setPackage(getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
