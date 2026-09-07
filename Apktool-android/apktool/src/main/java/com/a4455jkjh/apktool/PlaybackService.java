package com.a4455jkjh.apktool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;

/** Keeps music playback alive when the media screen is no longer visible. */
public class PlaybackService extends Service {
    public static final String ACTION_PLAY = "master.play";
    public static final String ACTION_PAUSE = "master.pause";
    public static final String ACTION_STOP = "master.stop";
    public static final String ACTION_NEXT = "master.next";
    public static final String ACTION_PREVIOUS = "master.previous";
    public static final String ACTION_SEEK = "master.seek";
    public static final String ACTION_STATE = "master.playback_state";
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_TITLE = "title";
    private static final String CHANNEL = "master_music";
    private static final int NOTIFICATION_ID = 301;
    private final ArrayList<String> playlist = new ArrayList<String>();
    private final ArrayList<String> titles = new ArrayList<String>();
    private MediaPlayer player;
    private MediaSession mediaSession;
    private int index;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override public void run() {
            if (player != null) {
                updatePlaybackState();
                progressHandler.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "Master App music");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { handleAction(ACTION_PAUSE); }
                @Override public void onPause() { handleAction(ACTION_PAUSE); }
                @Override public void onSkipToNext() { handleAction(ACTION_NEXT); }
                @Override public void onSkipToPrevious() { handleAction(ACTION_PREVIOUS); }
                @Override public void onStop() { handleAction(ACTION_STOP); }
                @Override public void onSeekTo(long position) {
                    if (player != null) {
                        player.seekTo((int) position);
                        updatePlaybackState();
                    }
                }
            });
            mediaSession.setActive(true);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("Master App music"));
        if (intent == null || intent.getAction() == null) return START_STICKY;
        handleAction(intent.getAction(), intent);
        return START_STICKY;
    }

    private void handleAction(String action) {
        handleAction(action, null);
    }

    private void handleAction(String action, Intent intent) {
        if (ACTION_PLAY.equals(action)) {
            ArrayList<String> incoming = intent == null ? null
                    : intent.getStringArrayListExtra(EXTRA_PLAYLIST);
            if (incoming != null) {
                playlist.clear();
                playlist.addAll(incoming);
            }
            ArrayList<String> incomingTitles = intent == null ? null
                    : intent.getStringArrayListExtra(EXTRA_TITLES);
            if (incomingTitles != null) {
                titles.clear();
                titles.addAll(incomingTitles);
            }
            index = intent == null ? 0 : intent.getIntExtra(EXTRA_INDEX, 0);
            if (mediaSession != null) {
                mediaSession.setActive(true);
            }
            playCurrent();
        } else if (ACTION_PAUSE.equals(action) && player != null) {
            if (player.isPlaying()) player.pause(); else player.start();
            updatePlaybackState();
        } else if (ACTION_NEXT.equals(action)) {
            index = playlist.isEmpty() ? 0 : (index + 1) % playlist.size();
            playCurrent();
        } else if (ACTION_PREVIOUS.equals(action)) {
            index = playlist.isEmpty() ? 0 : (index <= 0 ? playlist.size() - 1 : index - 1);
            playCurrent();
        } else if (ACTION_SEEK.equals(action) && player != null && intent != null) {
            player.seekTo(Math.max(0, Math.min(intent.getIntExtra(EXTRA_POSITION, 0),
                    player.getDuration())));
            updatePlaybackState();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
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
            updatePlaybackState();
            progressHandler.removeCallbacks(progressTicker);
            progressHandler.post(progressTicker);
        } catch (IOException | SecurityException exception) {
            stopPlayerOnly();
        }
    }

    private void stopPlayerOnly() {
        progressHandler.removeCallbacks(progressTicker);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    private void stopPlayback() {
        stopPlayerOnly();
        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
        stopForeground(true);
        stopSelf();
    }

    private String currentTitle() {
        if (index >= 0 && index < titles.size() && titles.get(index) != null
                && titles.get(index).length() > 0) {
            return titles.get(index);
        }
        return "Master App music";
    }

    private void updatePlaybackState() {
        if (player == null) {
            return;
        }
        boolean playing = player.isPlaying();
        int position = 0;
        int duration = 0;
        try {
            position = player.getCurrentPosition();
            duration = player.getDuration();
        } catch (IllegalStateException ignored) {
        }
        if (mediaSession != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_STOP;
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(playing ? PlaybackState.STATE_PLAYING
                            : PlaybackState.STATE_PAUSED, position, 1.0f)
                    .build());
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle())
                    .build());
        }
        sendBroadcast(new Intent(ACTION_STATE)
                .putExtra(EXTRA_POSITION, position)
                .putExtra(EXTRA_DURATION, duration)
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_TITLE, currentTitle()));
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(currentTitle()));
        }
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
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(title)
                .setContentText("Playback is active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pending).setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous",
                    serviceAction(ACTION_PREVIOUS)).build());
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_pause, "Pause",
                    serviceAction(ACTION_PAUSE)).build());
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next",
                    serviceAction(ACTION_NEXT)).build());
            if (mediaSession != null) {
                builder.setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
            }
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return builder.build();
    }

    private PendingIntent serviceAction(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    @Override public void onDestroy() {
        progressHandler.removeCallbacks(progressTicker);
        stopPlayerOnly();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}