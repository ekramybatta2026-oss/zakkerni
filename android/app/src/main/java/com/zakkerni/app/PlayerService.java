package com.zakkerni.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Background music/audio player for the phone's own audio files. */
public class PlayerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    static final String A_QUEUE = "z.player.QUEUE", A_TOGGLE = "z.player.TOGGLE", A_PLAY = "z.player.PLAY", A_PAUSE = "z.player.PAUSE",
            A_NEXT = "z.player.NEXT", A_PREV = "z.player.PREV", A_STOP = "z.player.STOP", A_SEEK = "z.player.SEEK",
            A_SHUFFLE = "z.player.SHUFFLE", A_REPEAT = "z.player.REPEAT", A_SPEED = "z.player.SPEED", A_SLEEP = "z.player.SLEEP",
            A_EQ = "z.player.EQ", A_JUMP = "z.player.JUMP";
    static final String PREFS = "zakkerni_player", CH = "player";
    static final int NID = 90;

    static PlayerService inst;

    private MediaPlayer mp;
    private MediaSession ms;
    private Equalizer eq;
    private AudioManager am;
    private AudioFocusRequest focusReq;
    private final Handler h = new Handler(Looper.getMainLooper());

    private long[] queue = new long[0];
    private List<Integer> order = new ArrayList<>();
    private int idx = 0, repeat = 0, eqPreset = -1;
    private boolean shuffle = false, prepared = false, wantPlay = false, foreground = false;
    private float speed = 1f;
    private long pendingSeek = 0, sleepAt = 0;
    boolean sleepEnd = false;
    String title = "", artist = "";
    private long curId = -1, duration = 0;
    Bitmap art;
    private String artB64 = "";

    /* ---------- lifecycle ---------- */
    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        ms = new MediaSession(this, "zakkerni-player");
        ms.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(false); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long pos) { seek(pos); }
            @Override public void onStop() { stopAll(); }
        });
        ms.setActive(true);
        restore();
        h.post(ticker);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i != null ? i.getAction() : null;
        if (i != null && i.getBooleanExtra("fg", false)) goForeground(); // started from widget/notification
        if (a == null) a = "";
        switch (a) {
            case A_QUEUE: setQueue(i.getLongArrayExtra("ids"), i.getIntExtra("start", 0)); break;
            case A_JUMP: jumpTo(i.getIntExtra("pos", 0)); break;
            case A_TOGGLE: if (mp.isPlaying()) pause(); else play(); break;
            case A_PLAY: play(); break;
            case A_PAUSE: pause(); break;
            case A_NEXT: next(false); break;
            case A_PREV: prev(); break;
            case A_SEEK: seek(i.getLongExtra("ms", 0)); break;
            case A_SHUFFLE: setShuffle(i.getBooleanExtra("on", false)); break;
            case A_REPEAT: repeat = i.getIntExtra("mode", 0); changed(false); break;
            case A_SPEED: setSpeed(i.getFloatExtra("v", 1f)); break;
            case A_SLEEP: setSleep(i.getIntExtra("min", 0), i.getBooleanExtra("end", false)); break;
            case A_EQ: setEq(i.getIntExtra("preset", -1)); break;
            case A_STOP: stopAll(); return START_NOT_STICKY;
        }
        if (queue.length == 0 && foreground && !mp.isPlaying()) { stopFg(true); stopSelf(); }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        save();
        h.removeCallbacksAndMessages(null);
        try { if (eq != null) eq.release(); } catch (Exception ignored) { }
        try { mp.release(); } catch (Exception ignored) { }
        try { ms.release(); } catch (Exception ignored) { }
        abandonFocus();
        inst = null;
        pushState();
        PlayerWidget.update(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /* ---------- queue ---------- */
    private void setQueue(long[] ids, int start) {
        if (ids == null || ids.length == 0) return;
        queue = ids;
        buildOrder(Math.max(0, Math.min(start, ids.length - 1)));
        pendingSeek = 0;
        load(true);
    }

    private void buildOrder(int startTrack) {
        order = new ArrayList<>();
        for (int k = 0; k < queue.length; k++) order.add(k);
        if (shuffle) {
            order.remove(Integer.valueOf(startTrack));
            Collections.shuffle(order);
            order.add(0, startTrack);
            idx = 0;
        } else idx = startTrack;
    }

    private void setShuffle(boolean on) {
        int cur = order.isEmpty() ? 0 : order.get(Math.min(idx, order.size() - 1));
        shuffle = on;
        if (queue.length > 0) buildOrder(cur);
        changed(false);
    }

    private void jumpTo(int queuePos) {
        if (queue.length == 0) return;
        buildOrder(Math.max(0, Math.min(queuePos, queue.length - 1)));
        pendingSeek = 0;
        load(true);
    }

    /* ---------- playback ---------- */
    private void load(boolean play) {
        if (queue.length == 0 || order.isEmpty()) return;
        idx = Math.max(0, Math.min(idx, order.size() - 1));
        curId = queue[order.get(idx)];
        prepared = false;
        wantPlay = play;
        readMeta(curId);
        try {
            mp.reset();
            mp.setDataSource(this, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, curId));
            mp.prepareAsync();
        } catch (Exception e) { h.postDelayed(() -> next(true), 300); }
        changed(true);
    }

    @Override
    public void onPrepared(MediaPlayer p) {
        prepared = true;
        duration = p.getDuration();
        applyEq();
        if (pendingSeek > 0 && pendingSeek < duration) p.seekTo((int) pendingSeek);
        pendingSeek = 0;
        if (wantPlay) play(); else changed(false);
    }

    @Override
    public void onCompletion(MediaPlayer p) {
        if (sleepEnd) { sleepEnd = false; sleepAt = 0; pause(); p.seekTo(0); return; }
        if (repeat == 2) { p.seekTo(0); p.start(); return; }
        next(true);
    }

    @Override
    public boolean onError(MediaPlayer p, int what, int extra) {
        h.postDelayed(() -> next(true), 500);
        return true;
    }

    void play() {
        if (queue.length == 0) return;
        if (!prepared) { wantPlay = true; if (curId < 0) load(true); return; }
        if (!requestFocus()) return;
        try {
            if (Build.VERSION.SDK_INT >= 23 && Math.abs(speed - 1f) > 0.01f) mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));
            mp.setVolume(1f, 1f);
            mp.start();
        } catch (Exception ignored) { }
        goForeground();
        changed(false);
    }

    void pause() {
        try { if (mp.isPlaying()) mp.pause(); } catch (Exception ignored) { }
        wantPlay = false;
        stopFg(false);
        changed(false);
        save();
    }

    void next(boolean auto) {
        if (queue.length == 0) return;
        if (idx + 1 < order.size()) idx++;
        else if (repeat == 1 || !auto) { if (shuffle) buildOrder(order.get(0)); idx = 0; }
        else { pause(); try { mp.seekTo(0); } catch (Exception ignored) { } return; }
        pendingSeek = 0;
        load(true);
    }

    void prev() {
        if (queue.length == 0) return;
        if (prepared && mp.getCurrentPosition() > 3000) { seek(0); return; }
        idx = idx > 0 ? idx - 1 : (repeat == 1 ? order.size() - 1 : 0);
        pendingSeek = 0;
        load(true);
    }

    void seek(long ms) {
        if (prepared) { try { mp.seekTo((int) ms); } catch (Exception ignored) { } }
        else pendingSeek = ms;
        changed(false);
    }

    private void setSpeed(float v) {
        speed = Math.max(0.5f, Math.min(2f, v));
        if (prepared && Build.VERSION.SDK_INT >= 23) {
            boolean was = mp.isPlaying();
            try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed)); } catch (Exception ignored) { }
            if (!was) try { mp.pause(); } catch (Exception ignored) { }
        }
        changed(false);
    }

    /* ---------- sleep timer (fades out over the last 15 s) ---------- */
    private void setSleep(int min, boolean endOfTrack) {
        sleepEnd = endOfTrack;
        sleepAt = min > 0 ? System.currentTimeMillis() + min * 60_000L : 0;
        try { mp.setVolume(1f, 1f); } catch (Exception ignored) { }
        changed(false);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (sleepAt > 0) {
                long left = sleepAt - System.currentTimeMillis();
                if (left <= 0) {
                    sleepAt = 0;
                    pause();
                    try { mp.setVolume(1f, 1f); } catch (Exception ignored) { }
                } else if (left < 15_000 && mp.isPlaying()) {
                    float v = left / 15_000f;
                    try { mp.setVolume(v, v); } catch (Exception ignored) { }
                }
            }
            if (mp != null && mp.isPlaying()) { pushState(); updateSessionState(); }
            h.postDelayed(this, 1000);
        }
    };

    /* ---------- equalizer ---------- */
    private void setEq(int preset) {
        eqPreset = preset;
        applyEq();
        save();
        changed(false);
    }

    private void applyEq() {
        try {
            if (eqPreset < 0) { if (eq != null) eq.setEnabled(false); return; }
            if (eq == null) eq = new Equalizer(0, mp.getAudioSessionId());
            if (eqPreset < eq.getNumberOfPresets()) { eq.usePreset((short) eqPreset); eq.setEnabled(true); }
        } catch (Exception ignored) { }
    }

    static String eqPresets(Context c) {
        StringBuilder b = new StringBuilder("[");
        Equalizer e = null;
        MediaPlayer tmp = null;
        try {
            int session = inst != null ? inst.mp.getAudioSessionId() : (tmp = new MediaPlayer()).getAudioSessionId();
            e = new Equalizer(0, session);
            for (short i = 0; i < e.getNumberOfPresets(); i++) {
                if (i > 0) b.append(',');
                b.append(JSONObject.quote(e.getPresetName(i)));
            }
        } catch (Exception ignored) { }
        finally { if (e != null && inst == null) e.release(); if (tmp != null) tmp.release(); }
        return b.append(']').toString();
    }

    /* ---------- audio focus ---------- */
    private final AudioManager.OnAudioFocusChangeListener focusL = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { try { mp.setVolume(0.3f, 0.3f); } catch (Exception ignored) { } }
        else if (change == AudioManager.AUDIOFOCUS_GAIN) { try { mp.setVolume(1f, 1f); } catch (Exception ignored) { } }
    };

    private boolean requestFocus() {
        if (am == null) return true;
        if (Build.VERSION.SDK_INT >= 26) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener(focusL).build();
            return am.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return am.requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusReq != null) am.abandonAudioFocusRequest(focusReq);
        else am.abandonAudioFocus(focusL);
    }

    private void stopAll() {
        pause();
        sleepAt = 0;
        stopFg(true);
        stopSelf();
    }

    /* ---------- metadata ---------- */
    private void readMeta(long id) {
        title = ""; artist = ""; art = null; artB64 = "";
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID},
                MediaStore.Audio.Media._ID + "=?", new String[]{String.valueOf(id)}, null)) {
            if (c != null && c.moveToFirst()) {
                title = c.getString(0);
                artist = c.getString(1);
                duration = c.getLong(2);
                if (artist == null || artist.startsWith("<")) artist = "";
                art = loadArt(this, id, c.getLong(3), 300);
            }
        } catch (Exception ignored) { }
        if (art != null) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            Bitmap small = Bitmap.createScaledBitmap(art, 160, 160, true);
            small.compress(Bitmap.CompressFormat.JPEG, 80, o);
            artB64 = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
        }
    }

    static Bitmap loadArt(Context c, long id, long albumId, int px) {
        try {
            if (Build.VERSION.SDK_INT >= 29)
                return c.getContentResolver().loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), new Size(px, px), null);
            Uri u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
            return BitmapFactory.decodeStream(c.getContentResolver().openInputStream(u));
        } catch (Exception e) { return null; }
    }

    /* ---------- state out (app, widget, notification, lock screen) ---------- */
    private void changed(boolean trackChanged) {
        if (trackChanged) {
            MediaMetadata.Builder m = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
            if (art != null) m.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
            ms.setMetadata(m.build());
        }
        updateSessionState();
        if (foreground || mp.isPlaying()) postNotification();
        pushState();
        PlayerWidget.update(this);
        save();
    }

    private void updateSessionState() {
        long pos = 0;
        try { pos = prepared ? mp.getCurrentPosition() : pendingSeek; } catch (Exception ignored) { }
        ms.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, isPlaying() ? speed : 0f).build());
    }

    boolean isPlaying() { try { return mp != null && mp.isPlaying(); } catch (Exception e) { return false; } }

    String stateJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", curId); o.put("title", title); o.put("artist", artist);
            o.put("pos", prepared ? mp.getCurrentPosition() : pendingSeek); o.put("dur", duration);
            o.put("playing", isPlaying()); o.put("shuffle", shuffle); o.put("repeat", repeat); o.put("speed", speed);
            o.put("sleepAt", sleepAt); o.put("sleepEnd", sleepEnd); o.put("eq", eqPreset);
            o.put("index", idx); o.put("count", order.size()); o.put("art", artB64);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    private String lastPushedArt = null;
    private void pushState() {
        MainActivity a = MainActivity.current;
        if (a == null) return;
        String js = stateJson();
        if (artB64.equals(lastPushedArt)) js = js.replace("\"art\":\"" + artB64 + "\"", "\"art\":\"=\""); // send art only when it changes
        else lastPushedArt = artB64;
        final String s = js;
        a.runOnUiThread(() -> a.pushPlayer(s));
    }

    /* ---------- notification ---------- */
    private Notification buildNotification() {
        createChannel(this);
        PendingIntent open = PendingIntent.getActivity(this, 60, new Intent(this, MainActivity.class).putExtra("go", "player")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_play)
                .setColor(Color.parseColor("#7137A8"))
                .setContentTitle(title.isEmpty() ? "مشغّل ذَكِّرني" : title)
                .setContentText(artist)
                .setLargeIcon(art)
                .setContentIntent(open)
                .setDeleteIntent(action(A_STOP, 66))
                .setOngoing(isPlaying())
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "السابق", action(A_PREV, 61)).build())
                .addAction(new Notification.Action.Builder(null, isPlaying() ? "إيقاف مؤقت" : "تشغيل", action(A_TOGGLE, 62)).build())
                .addAction(new Notification.Action.Builder(null, "التالي", action(A_NEXT, 63)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    PendingIntent action(String a, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(a).putExtra("fg", true);
        return Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void postNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try { if (nm != null) nm.notify(NID, buildNotification()); } catch (SecurityException ignored) { }
    }

    private void goForeground() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NID, n);
            foreground = true;
        } catch (Exception ignored) { }
    }

    private void stopFg(boolean remove) {
        if (!foreground) return;
        foreground = false;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        else stopForeground(remove);
        if (!remove) postNotification();
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CH) != null) return;
        NotificationChannel ch = new NotificationChannel(CH, "المشغّل", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    /* ---------- persistence ---------- */
    private void save() {
        StringBuilder q = new StringBuilder();
        for (int k = 0; k < queue.length; k++) { if (k > 0) q.append(','); q.append(queue[k]); }
        long pos = 0;
        try { pos = prepared ? mp.getCurrentPosition() : pendingSeek; } catch (Exception ignored) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("queue", q.toString()).putInt("track", order.isEmpty() ? 0 : order.get(Math.min(idx, order.size() - 1)))
                .putLong("pos", pos).putBoolean("shuffle", shuffle).putInt("repeat", repeat).putFloat("speed", speed)
                .putInt("eq", eqPreset).putString("title", title).putString("artist", artist).putBoolean("playing", isPlaying()).apply();
    }

    private void restore() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        shuffle = p.getBoolean("shuffle", false);
        repeat = p.getInt("repeat", 0);
        speed = p.getFloat("speed", 1f);
        eqPreset = p.getInt("eq", -1);
        String q = p.getString("queue", "");
        if (q.isEmpty()) return;
        String[] parts = q.split(",");
        queue = new long[parts.length];
        try { for (int k = 0; k < parts.length; k++) queue[k] = Long.parseLong(parts[k]); } catch (Exception e) { queue = new long[0]; return; }
        buildOrder(Math.min(p.getInt("track", 0), queue.length - 1));
        pendingSeek = p.getLong("pos", 0);
        load(false);
    }
}
