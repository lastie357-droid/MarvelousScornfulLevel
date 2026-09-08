package com.a4455jkjh.apktool;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * Built-in wallpaper picker. The designs are generated locally, so the
 * launcher does not need network access or bundled image files.
 */
public class WallpaperActivity extends ThemedActivity {
    private static final String[] NAMES = {
            "Obsidian", "Steel", "Carbon", "Night Grid", "Midnight Ridge",
            "Graphite Lines", "Cobalt Fade", "Forest Slate", "Sandstone Night",
            "Iron Wave", "Deep Space", "Blueprint", "Smoke", "Ocean Night",
            "Black Gold", "Storm", "Concrete", "Indigo", "Raven", "Minimal Dark"
    };

    private static final int[][] PALETTES = {
            {0xff080b10, 0xff1a202b, 0xff718096},
            {0xff18212a, 0xff40505b, 0xffb0bec5},
            {0xff090909, 0xff262626, 0xff6d747c},
            {0xff0a1823, 0xff152e42, 0xff3c91bd},
            {0xff101421, 0xff24344b, 0xff7187a3},
            {0xff121212, 0xff30343a, 0xff9ba5ae},
            {0xff071526, 0xff123f68, 0xff4fa3d1},
            {0xff101b1b, 0xff29413c, 0xff789b82},
            {0xff211b18, 0xff4a3b32, 0xffb2916f},
            {0xff161b21, 0xff3d4b55, 0xff93a7ad},
            {0xff040615, 0xff171c42, 0xff625fd0},
            {0xff091a2b, 0xff17466d, 0xff82b5d6},
            {0xff0b0d10, 0xff32373d, 0xff7c858d},
            {0xff06131b, 0xff12384a, 0xff4f9ab1},
            {0xff100d0a, 0xff3d2d18, 0xffc19a55},
            {0xff111820, 0xff35465a, 0xffb5c6d8},
            {0xff171717, 0xff424242, 0xffa5a5a5},
            {0xff0c0920, 0xff241d55, 0xff786ed1},
            {0xff05070b, 0xff151a22, 0xff4d5663},
            {0xff080a0e, 0xff111820, 0xff2f3b49}
    };

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.wallpaper_picker);
        showSystemBars();

        findViewById(R.id.wallpaper_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        GridLayout grid = findViewById(R.id.wallpaper_grid);
        for (int index = 0; index < NAMES.length; index++) {
            addWallpaperCard(grid, index);
        }
    }

    private void addWallpaperCard(GridLayout grid, final int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setBackgroundColor(Color.TRANSPARENT);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyWallpaper(index);
            }
        });

        ImageView preview = new ImageView(this);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(178)));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageBitmap(createWallpaper(index, 360, 560));
        preview.setContentDescription(NAMES[index]);
        card.addView(preview);

        TextView label = new TextView(this);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        label.setGravity(Gravity.CENTER);
        label.setText(NAMES[index]);
        label.setTextColor(getResources().getColor(R.color.launcher_text));
        label.setTextSize(11);
        card.addView(label);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(212);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(card, params);
    }

    private void applyWallpaper(int index) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        WallpaperManager manager = WallpaperManager.getInstance(this);
        try {
            manager.setBitmap(createWallpaper(index, Math.max(width, 720),
                    Math.max(height, 1280)));
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException exception) {
            Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createWallpaper(int index, int width, int height) {
        int[] palette = PALETTES[index];
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width, height,
                palette[0], palette[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        paint.setStrokeWidth(Math.max(2, width / 240f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(withAlpha(palette[2], 42));
        int pattern = index % 5;
        if (pattern == 0) {
            for (int x = -height; x < width + height; x += Math.max(40, width / 7)) {
                canvas.drawLine(x, 0, x + height, height, paint);
            }
        } else if (pattern == 1) {
            int step = Math.max(45, width / 6);
            for (int x = 0; x < width; x += step) {
                canvas.drawLine(x, 0, x, height, paint);
            }
            for (int y = 0; y < height; y += step) {
                canvas.drawLine(0, y, width, y, paint);
            }
        } else if (pattern == 2) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(palette[2], 32));
            canvas.drawCircle(width * 0.18f, height * 0.28f, width * 0.28f, paint);
            canvas.drawCircle(width * 0.85f, height * 0.72f, width * 0.42f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(withAlpha(Color.WHITE, 22));
            canvas.drawCircle(width * 0.18f, height * 0.28f, width * 0.32f, paint);
            canvas.drawCircle(width * 0.85f, height * 0.72f, width * 0.48f, paint);
        } else if (pattern == 3) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(palette[2], 45));
            Path ridge = new Path();
            ridge.moveTo(0, height * 0.67f);
            ridge.lineTo(width * 0.28f, height * 0.4f);
            ridge.lineTo(width * 0.5f, height * 0.62f);
            ridge.lineTo(width * 0.73f, height * 0.3f);
            ridge.lineTo(width, height * 0.58f);
            ridge.lineTo(width, height);
            ridge.lineTo(0, height);
            ridge.close();
            canvas.drawPath(ridge, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(withAlpha(palette[2], 52));
            for (int radius = width / 5; radius < width * 2; radius += width / 5) {
                canvas.drawOval(-radius / 2f, height * 0.2f - radius / 3f,
                        width + radius / 2f, height * 0.2f + radius / 3f, paint);
            }
        }
        return bitmap;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.launcher_background));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.launcher_background));
    }
}