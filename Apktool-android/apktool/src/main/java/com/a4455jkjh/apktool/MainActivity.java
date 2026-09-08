package com.a4455jkjh.apktool;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * Master Launcher home screen.
 *
 * The launcher itself does not embed or recreate other apps. It discovers
 * launchable packages and hands Android their normal launch intents.
 */
public class MainActivity extends ThemedActivity {
    private static final int DEFAULT_HOME_REQUEST = 701;
    private static final String PREFS = "master_launcher";
    private static final String DEFAULT_PROMPT_SHOWN = "default_prompt_shown";

    private LauncherApps launcherApps;
    private Button defaultButton;

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.master_launcher);
        showSystemBars();

        GridLayout grid = findViewById(R.id.launcher_grid);
        TextView empty = findViewById(R.id.launcher_empty);
        TextView appCount = findViewById(R.id.launcher_count);
        EditText search = findViewById(R.id.launcher_search);
        launcherApps = new LauncherApps(this, grid, empty, appCount, search, false);

        defaultButton = findViewById(R.id.launcher_default);
        defaultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestDefaultLauncher();
            }
        });

        findViewById(R.id.launcher_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLauncherMenu(view);
            }
        });

        launcherApps.refresh();
        maybeExplainDefaultLauncher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultButton();
        if (launcherApps != null) {
            launcherApps.refresh();
        }
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.launcher_background));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.launcher_background));
    }

    private void showLauncherMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.launcher, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_install_app) {
                startActivity(new Intent(MainActivity.this, InstallApkActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.menu_hidden_apps) {
                startActivity(new Intent(MainActivity.this, AppDrawerActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.menu_default_launcher) {
                requestDefaultLauncher();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void updateDefaultButton() {
        if (defaultButton == null) {
            return;
        }
        if (isDefaultLauncher()) {
            defaultButton.setText(R.string.launcher_default_active);
            defaultButton.setEnabled(false);
            defaultButton.setAlpha(0.7f);
        } else {
            defaultButton.setText(R.string.launcher_default);
            defaultButton.setEnabled(true);
            defaultButton.setAlpha(1f);
        }
    }

    private boolean isDefaultLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(
                home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null
                && resolved.activityInfo != null
                && getPackageName().equals(resolved.activityInfo.packageName);
    }

    private void requestDefaultLauncher() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = getSystemService(RoleManager.class);
                if (roleManager != null
                        && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                            DEFAULT_HOME_REQUEST);
                    return;
                }
            }

            Intent settings = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivityForResult(settings, DEFAULT_HOME_REQUEST);
        } catch (Exception exception) {
            try {
                Intent chooser = new Intent(Intent.ACTION_MAIN);
                chooser.addCategory(Intent.CATEGORY_HOME);
                chooser.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(chooser);
            } catch (Exception ignored) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.launcher_default_unavailable)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
    }

    private void maybeExplainDefaultLauncher() {
        if (isDefaultLauncher()
                || getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(DEFAULT_PROMPT_SHOWN, false)) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(DEFAULT_PROMPT_SHOWN, true)
                .apply();
        new AlertDialog.Builder(this)
                .setTitle(R.string.launcher_default)
                .setMessage(R.string.launcher_default_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.launcher_default,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestDefaultLauncher();
                            }
                        })
                .show();
    }

    @Override
    public void onBackPressed() {
        // A home app remains in place when Back is pressed.
    }
}