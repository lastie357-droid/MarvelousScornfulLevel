package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private int currentMusic = -1;
    private boolean shuffle;
    private int repeatMode;

    private static class MediaEntry {
        final long id;
        final String name;
        final Uri uri;
        final boolean playable;

        MediaEntry(long id, String name, Uri uri, boolean playable) {
            this.id = id;
            this.name = name;
            this.uri = uri;
            this.playable = playable;
        }
    }

    @Override
    protected void init(Bundle savedInstanceState) {
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
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), true));
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
                        false));
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
        content.addView(label(getString(video ? R.string.media_videos : R.string.media_images),
                20, Color.WHITE));
        if (entries.isEmpty()) {
            content.addView(label(getString(R.string.media_no_files), 16, Color.LTGRAY));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label(entries.get(i).name, 16, Color.WHITE);
            name.setPadding(8, 12, 8, 12);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button open = button(getString(R.string.media_open));
            open.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, entries.get(index).uri);
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception exception) {
                        Toast.makeText(MediaActivity.this, R.string.media_open_failed,
                                Toast.LENGTH_SHORT).show();
                    }
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
}