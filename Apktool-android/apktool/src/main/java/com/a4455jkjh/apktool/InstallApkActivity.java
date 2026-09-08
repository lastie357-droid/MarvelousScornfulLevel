package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Small APK file browser used by the install action.
 *
 * It intentionally sorts by last-modified time instead of delegating the
 * directory to a generic picker, so the newest received APK is always first.
 */
public class InstallApkActivity extends ThemedActivity {
    private static final int STORAGE_PERMISSION_REQUEST = 810;

    private LinearLayout browserContent;
    private Button permissionButton;
    private TextView subtitle;
    private TextView pathView;
    private TextView fileCountView;
    private LinearLayout fileList;
    private File currentDirectory;
    private File pendingInstall;
    private boolean allFilesSettingsOpened;
    private boolean installSettingsOpened;

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.install_apk);
        showSystemBars();
        currentDirectory = Environment.getExternalStorageDirectory();

        subtitle = findViewById(R.id.install_subtitle);
        permissionButton = findViewById(R.id.install_permission);
        browserContent = findViewById(R.id.install_browser_content);
        pathView = findViewById(R.id.install_path);
        fileCountView = findViewById(R.id.install_file_count);
        fileList = findViewById(R.id.install_file_list);

        findViewById(R.id.install_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.install_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentDirectory != null && currentDirectory.getParentFile() != null) {
                    currentDirectory = currentDirectory.getParentFile();
                    refreshFileList();
                }
            }
        });
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestFileAccess();
            }
        });

        updateAccessState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allFilesSettingsOpened) {
            allFilesSettingsOpened = false;
            updateAccessState();
        } else if (installSettingsOpened) {
            installSettingsOpened = false;
            if (canInstallPackages() && pendingInstall != null) {
                launchInstaller(pendingInstall);
            }
        }
    }

    private void updateAccessState() {
        if (hasFileAccess()) {
            browserContent.setVisibility(View.VISIBLE);
            permissionButton.setVisibility(View.GONE);
            subtitle.setText(R.string.install_apk_subtitle);
            refreshFileList();
        } else {
            browserContent.setVisibility(View.GONE);
            permissionButton.setVisibility(View.VISIBLE);
            permissionButton.setText(R.string.open_all_files_settings);
            subtitle.setText(R.string.install_storage_needed);
        }
    }

    private boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allFilesSettingsOpened = true;
            try {
                Intent access = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                access.setData(Uri.parse("package:" + getPackageName()));
                startActivity(access);
            } catch (Exception exception) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            updateAccessState();
        }
    }

    private void refreshFileList() {
        pathView.setText(getString(R.string.install_current_path,
                currentDirectory == null ? "/" : currentDirectory.getAbsolutePath()));
        fileList.removeAllViews();

        File[] children;
        try {
            children = currentDirectory == null ? null : currentDirectory.listFiles();
        } catch (SecurityException exception) {
            children = null;
        }
        if (children == null) {
            fileCountView.setText(R.string.install_empty_folder);
            return;
        }

        List<File> entries = new ArrayList<File>();
        int apkCount = 0;
        for (File child : children) {
            if (child.isDirectory() || isApk(child)) {
                entries.add(child);
                if (isApk(child)) {
                    apkCount++;
                }
            }
        }
        Collections.sort(entries, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                int byDate = Long.compare(right.lastModified(), left.lastModified());
                return byDate != 0
                        ? byDate
                        : left.getName().compareToIgnoreCase(right.getName());
            }
        });
        fileCountView.setText(getString(R.string.install_file_count, apkCount));

        for (File entry : entries) {
            addFileRow(entry);
        }
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.install_empty_folder);
            empty.setTextColor(getResources().getColor(R.color.launcher_muted));
            empty.setTextSize(15);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            fileList.addView(empty);
        }
    }

    private void addFileRow(final File entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackgroundResource(R.drawable.launcher_secondary_button);

        TextView name = new TextView(this);
        name.setText((entry.isDirectory() ? "▸  " : "▣  ") + entry.getName());
        name.setTextColor(getResources().getColor(R.color.launcher_text));
        name.setTextSize(15);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView metadata = new TextView(this);
        String date = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.lastModified()));
        metadata.setText(entry.isDirectory() ? date : date + "  •  " + readableSize(entry.length()));
        metadata.setTextColor(getResources().getColor(R.color.launcher_muted));
        metadata.setTextSize(12);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(4);
        row.addView(metadata, metaParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        fileList.addView(row, rowParams);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (entry.isDirectory()) {
                    currentDirectory = entry;
                    refreshFileList();
                } else {
                    installApk(entry);
                }
            }
        });
    }

    private boolean isApk(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".apk");
    }

    private void installApk(File file) {
        pendingInstall = file;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            installSettingsOpened = true;
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settings.setData(Uri.parse("package:" + getPackageName()));
                startActivity(settings);
            } catch (Exception exception) {
                Toast.makeText(this, R.string.install_unknown_needed, Toast.LENGTH_LONG).show();
            }
            return;
        }
        launchInstaller(file);
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private void launchInstaller(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        String encoded = Base64.encodeToString(
                file.getAbsolutePath().getBytes(Charset.forName("UTF-8")),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        Uri fileUri = Uri.parse("content://com.a4455jkjh.fileprovider/" + encoded);
        Intent installer = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installer.setDataAndType(fileUri, "application/vnd.android.package-archive");
        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installer.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            startActivityForResult(installer, 811);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.install_unknown_needed, Toast.LENGTH_LONG).show();
        }
    }

    private String readableSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return (size / (1024 * 1024)) + " MB";
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