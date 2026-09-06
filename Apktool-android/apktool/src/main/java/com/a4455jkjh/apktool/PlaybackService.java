package com.a4455jkjh.apktool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import java.io.IOException;
import java.util.ArrayList;

/** Keeps music playback alive when the media screen is no longer visible. */
public class PlaybackService extends Service {
    public static final String ACTION_PLAY = "master.play";
    public static final String ACTION_PAUSE = "master.pause";
    public static final String ACTION_STOP = "master.stop";
    public static final String ACTION_NEXT = "master.next";
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_INDEX = "index";
    private static final String CHANNEL = "master_music";
    private static final int NOTIFICATION_ID = 301;
    private final ArrayList<String> playlist = new ArrayList<String>();
    private MediaPlayer player;
    private int index;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("Master App music"));
        if (intent == null || intent.getAction() == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            ArrayList<String> incoming = intent.getStringArrayListExtra(EXTRA_PLAYLIST);
            if (incoming != null) {
                playlist.clear();
                playlist.addAll(incoming);
            }
            index = intent.getIntExtra(EXTRA_INDEX, 0);
            playCurrent();
        } else if (ACTION_PAUSE.equals(action) && player != null) {
            if (player.isPlaying()) player.pause(); else player.start();
        } else if (ACTION_NEXT.equals(action)) {
            index = playlist.isEmpty() ? 0 : (index + 1) % playlist.size();
            playCurrent();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_STICKY;
    }

    private void playCurrent() {
        if (index < 0 || index >= playlist.size()) return;
        stopPlayerOnly();
        try {
            player = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build());
            }
            player.setDataSource(this, Uri.parse(playlist.get(index)));
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    if (!playlist.isEmpty()) {
                        index = (index + 1) % playlist.size();
                        playCurrent();
                    }
                }
            });
            player.prepare();
            player.start();
        } catch (IOException | SecurityException exception) {
            stopPlayerOnly();
        }
    }

    private void stopPlayerOnly() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    private void stopPlayback() {
        stopPlayerOnly();
        stopForeground(true);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Music playback",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    private Notification notification(String title) {
        Intent open = new Intent(this, MediaActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL).setContentTitle(title)
                    .setContentText("Playback is active")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentIntent(pending).setOngoing(true).build();
        }
        return new Notification.Builder(this).setContentTitle(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pending).setOngoing(true).build();
    }

    @Override public void onDestroy() {
        stopPlayerOnly();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}