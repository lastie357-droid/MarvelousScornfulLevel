package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native media hub for Master App. MediaStore is used so the app can show
 * device files without copying them into the app sandbox.
 */
public class MediaActivity extends ThemedActivity {
    private static final int MEDIA_PERMISSION_REQUEST = 810;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 811;
    private final ArrayList<MediaEntry> music = new ArrayList<MediaEntry>();
    private final ArrayList<MediaEntry> images = new ArrayList<MediaEntry>();
    private final ArrayList<MediaEntry> videos = new ArrayList<MediaEntry>();
    private LinearLayout content;
    private TextView nowPlaying;
    private TextView playbackTime;
    private SeekBar playbackSeekBar;
    private int currentMusic = -1;
    private boolean shuffle;
    private int repeatMode;
    private boolean galleryGrid = true;
    private boolean changingSeekBar;
    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            if (!PlaybackService.ACTION_STATE.equals(intent.getAction())) {
                return;
            }
            int duration = intent.getIntExtra(PlaybackService.EXTRA_DURATION, 0);
            int position = intent.getIntExtra(PlaybackService.EXTRA_POSITION, 0);
            boolean playing = intent.getBooleanExtra(PlaybackService.EXTRA_PLAYING, false);
            String title = intent.getStringExtra(PlaybackService.EXTRA_TITLE);
            if (title != null && title.length() > 0) {
                nowPlaying.setText(getString(R.string.media_now_playing, title));
            }
            if (playbackSeekBar != null && duration > 0) {
                changingSeekBar = true;
                playbackSeekBar.setMax(duration);
                playbackSeekBar.setProgress(Math.min(position, duration));
                changingSeekBar = false;
            }
            if (playbackTime != null) {
                playbackTime.setText(formatTime(position) + " / " + formatTime(duration)
                        + (playing ? "" : "  •  Paused"));
            }
        }
    };

    private static class MediaEntry {
        final long id;
        final String name;
        final Uri uri;
        final boolean playable;
        final boolean video;

        MediaEntry(long id, String name, Uri uri, boolean playable, boolean video) {
            this.id = id;
            this.name = name;
            this.uri = uri;
            this.playable = playable;
            this.video = video;
        }
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        IntentFilter playbackFilter = new IntentFilter(PlaybackService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playbackReceiver, playbackFilter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playbackReceiver, playbackFilter);
        }
        requestNotificationPermission();
        buildScreen();
        if (!hasMediaPermission()) {
            requestPermissions(mediaPermissions(), MEDIA_PERMISSION_REQUEST);
        } else {
            loadMedia();
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(Color.rgb(16, 21, 31));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(getString(R.string.media_hub), 24, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button close = button(getString(R.string.close_cur));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        nowPlaying = label(getString(R.string.media_nothing_playing), 14,
                Color.LTGRAY);
        nowPlaying.setPadding(0, 10, 0, 10);
        root.addView(nowPlaying);

        LinearLayout tabs = new LinearLayout(this);
        addSectionButton(tabs, R.string.media_music, new View.OnClickListener() {
            @Override public void onClick(View view) { showMusic(); }
        });
        addSectionButton(tabs, R.string.media_images, new View.OnClickListener() {
            @Override public void onClick(View view) { showGallery(images, false); }
        });
        addSectionButton(tabs, R.string.media_videos, new View.OnClickListener() {
            @Override public void onClick(View view) { showGallery(videos, true); }
        });
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 12, 0, 12);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        showMusic();
    }

    private void addSectionButton(LinearLayout parent, int text,
                                  View.OnClickListener listener) {
        Button view = button(getString(text));
        view.setOnClickListener(listener);
        parent.addView(view, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(48);
        return button;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String[] mediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[] {
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        }
        return new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (String permission : mediaPermissions()) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void loadMedia() {
        music.clear();
        images.clear();
        videos.clear();
        queryAudio();
        queryImages();
        queryVideos();
        showMusic();
    }

    private void queryAudio() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media.DISPLAY_NAME,
                            MediaStore.Audio.Media.TITLE },
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null,
                    MediaStore.Audio.Media.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (cursor == null) return;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                if (name == null || name.length() == 0) {
                    name = titleIndex >= 0 ? cursor.getString(titleIndex) : "Audio";
                }
                music.add(new MediaEntry(id, name,
                        ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), true, false));
            }
        } catch (Exception ignored) {
            // A missing provider or denied permission leaves an empty section.
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void queryImages() {
        queryVisual(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, images);
    }

    private void queryVideos() {
        queryVisual(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videos);
    }

    private void queryVisual(Uri collection, ArrayList<MediaEntry> target) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(collection,
                    new String[] { MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME },
                    null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (cursor == null) return;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "Media";
                target.add(new MediaEntry(id, name, ContentUris.withAppendedId(collection, id),
                        false, collection.equals(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void showMusic() {
        content.removeAllViews();
        addPlaybackControls();
        if (music.isEmpty()) {
            content.addView(label(getString(R.string.media_no_music), 16, Color.LTGRAY));
            return;
        }
        for (int i = 0; i < music.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label(music.get(i).name, 16, Color.WHITE);
            name.setPadding(8, 12, 8, 12);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button play = button(getString(R.string.media_play));
            play.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { playMusic(index); }
            });
            row.addView(play);
            Button delete = button(getString(R.string.delete));
            delete.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { deleteEntry(music, index); }
            });
            row.addView(delete);
            content.addView(row);
        }
    }

    private void addPlaybackControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        addControl(controls, "‹", new View.OnClickListener() {
            @Override public void onClick(View view) { previousMusic(); }
        });
        addControl(controls, "Pause", new View.OnClickListener() {
            @Override public void onClick(View view) {
                sendPlayerAction(PlaybackService.ACTION_PAUSE);
            }
        });
        addControl(controls, "Next ›", new View.OnClickListener() {
            @Override public void onClick(View view) { nextMusic(); }
        });
        addControl(controls, "Repeat", new View.OnClickListener() {
            @Override public void onClick(View view) {
                repeatMode = (repeatMode + 1) % 3;
                Toast.makeText(MediaActivity.this,
                        repeatMode == 0 ? "Repeat off"
                                : repeatMode == 1 ? "Repeat one" : "Repeat all",
                        Toast.LENGTH_SHORT).show();
            }
        });
        addControl(controls, "Shuffle", new View.OnClickListener() {
            @Override public void onClick(View view) {
                shuffle = !shuffle;
                Toast.makeText(MediaActivity.this, shuffle ? "Shuffle on" : "Shuffle off",
                        Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(controls);

        playbackSeekBar = new SeekBar(this);
        playbackSeekBar.setMax(1);
        playbackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && !changingSeekBar && playbackTime != null) {
                    playbackTime.setText(formatTime(progress) + " / "
                            + formatTime(bar.getMax()));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                Intent seek = new Intent(MediaActivity.this, PlaybackService.class);
                seek.setAction(PlaybackService.ACTION_SEEK);
                seek.putExtra(PlaybackService.EXTRA_POSITION, bar.getProgress());
                startService(seek);
            }
        });
        content.addView(playbackSeekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playbackTime = label("0:00 / 0:00", 12, Color.LTGRAY);
        playbackTime.setGravity(Gravity.CENTER);
        content.addView(playbackTime);
    }

    private void addControl(LinearLayout parent, String text, View.OnClickListener listener) {
        Button view = button(text);
        view.setOnClickListener(listener);
        parent.addView(view, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void playMusic(int index) {
        if (index < 0 || index >= music.size()) return;
        currentMusic = index;
        ArrayList<String> playlist = new ArrayList<String>();
        for (MediaEntry entry : music) playlist.add(entry.uri.toString());
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putStringArrayListExtra(PlaybackService.EXTRA_PLAYLIST, playlist);
        ArrayList<String> titles = new ArrayList<String>();
        for (MediaEntry entry : music) titles.add(entry.name);
        intent.putStringArrayListExtra(PlaybackService.EXTRA_TITLES, titles);
        intent.putExtra(PlaybackService.EXTRA_INDEX, index);
        startService(intent);
        nowPlaying.setText(getString(R.string.media_now_playing, music.get(index).name));
    }

    private void nextMusic() {
        if (music.isEmpty()) return;
        int next = shuffle ? (int) (Math.random() * music.size())
                : (currentMusic + 1) % music.size();
        playMusic(next);
    }

    private void previousMusic() {
        if (music.isEmpty()) return;
        int previous = currentMusic <= 0 ? music.size() - 1 : currentMusic - 1;
        playMusic(previous);
    }

    private void sendPlayerAction(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void showGallery(final ArrayList<MediaEntry> entries, final boolean video) {
        content.removeAllViews();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label(getString(video ? R.string.media_videos : R.string.media_images),
                20, Color.WHITE), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button grid = button(getString(R.string.media_grid));
        grid.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                galleryGrid = true;
                showGallery(entries, video);
            }
        });
        heading.addView(grid);
        Button list = button(getString(R.string.media_list));
        list.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                galleryGrid = false;
                showGallery(entries, video);
            }
        });
        heading.addView(list);
        content.addView(heading);
        if (entries.isEmpty()) {
            content.addView(label(getString(R.string.media_no_files), 16, Color.LTGRAY));
            return;
        }
        if (galleryGrid) {
            GridLayout gridLayout = new GridLayout(this);
            gridLayout.setColumnCount(2);
            for (int i = 0; i < entries.size(); i++) {
                final int index = i;
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(6, 6, 6, 6);
                ImageView preview = thumbnail(entries.get(i));
                tile.addView(preview, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
                TextView name = label(entries.get(i).name, 12, Color.WHITE);
                name.setMaxLines(2);
                tile.addView(name);
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        openMedia(entries.get(index));
                    }
                });
                GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f));
                tileParams.width = 0;
                tileParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                gridLayout.addView(tile, tileParams);
            }
            content.addView(gridLayout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(thumbnail(entries.get(i)), new LinearLayout.LayoutParams(dp(84), dp(84)));
            TextView name = label(entries.get(i).name, 16, Color.WHITE);
            name.setPadding(8, 12, 8, 12);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button open = button(getString(R.string.media_open));
            open.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    openMedia(entries.get(index));
                }
            });
            row.addView(open);
            Button delete = button(getString(R.string.delete));
            delete.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { deleteEntry(entries, index); }
            });
            row.addView(delete);
            content.addView(row);
        }
    }

    private ImageView thumbnail(MediaEntry entry) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = null;
        try {
            if (entry.video) {
                bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        getContentResolver(), entry.id,
                        MediaStore.Video.Thumbnails.MINI_KIND, null);
            } else {
                bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                        getContentResolver(), entry.id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
        } catch (Exception ignored) {
        }
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        } else {
            image.setImageResource(entry.video
                    ? android.R.drawable.ic_media_play : android.R.drawable.ic_menu_gallery);
            image.setBackgroundColor(Color.rgb(45, 52, 64));
        }
        return image;
    }

    private void openMedia(MediaEntry entry) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, entry.uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.media_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = Math.max(0, milliseconds) / 1000;
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }

    private void deleteEntry(final ArrayList<MediaEntry> entries, final int index) {
        if (index < 0 || index >= entries.size()) return;
        final MediaEntry entry = entries.get(index);
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.media_delete_confirm, entry.name))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (getContentResolver().delete(entry.uri, null, null) > 0) {
                                entries.remove(index);
                                if (entries == music) {
                                    showMusic();
                                } else {
                                    showGallery(entries, entries == videos);
                                }
                            }
                        } catch (SecurityException exception) {
                            Toast.makeText(MediaActivity.this,
                                    R.string.media_delete_denied, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST) {
            if (hasMediaPermission()) loadMedia();
            else Toast.makeText(this, R.string.media_permission_needed,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(playbackReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}