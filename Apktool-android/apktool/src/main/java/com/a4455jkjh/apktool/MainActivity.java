package com.a4455jkjh.apktool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.content.DialogInterface;
import android.os.Build;
import android.app.AlertDialog;
import android.widget.Toast;

public class MainActivity extends ThemedActivity {
    private static final int STORAGE_PERMISSION_REQUEST = 950;
    private boolean permissionRequestInProgress;
    private boolean permissionPromptShown;
    private boolean allFilesSettingsOpened;

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.master_home);

        findViewById(R.id.open_apktool).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openApktool(null);
            }
        });
        findViewById(R.id.open_browser).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, BrowserActivity.class));
            }
        });
        findViewById(R.id.open_media).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, MediaActivity.class));
            }
        });
        findViewById(R.id.open_tools).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ToolsActivity.class));
            }
        });
        findViewById(R.id.open_messenger).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, MessengerActivity.class));
            }
        });
        findViewById(R.id.open_phone_dialer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPhoneDialer();
            }
        });
        findViewById(R.id.set_default_browser).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserActivity.openDefaultBrowserSettings(MainActivity.this);
            }
        });
        findViewById(R.id.open_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingActivity.class));
            }
        });

        Uri data = getIntent().getData();
        if (data != null) {
            openApktool(data);
        }
        requestApktoolAccess();
    }

    private void openPhoneDialer() {
        Intent dialer = new Intent(Intent.ACTION_DIAL);
        dialer.setPackage("com.google.android.dialer");
        try {
            startActivity(dialer);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.phone_dialer_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allFilesSettingsOpened) {
            allFilesSettingsOpened = false;
            if (ApktoolPermissions.hasFileAccess(this)) {
                permissionPromptShown = false;
                requestApktoolAccess();
            }
            return;
        }
        if (!permissionRequestInProgress && !permissionPromptShown) {
            requestApktoolAccess();
        }
    }

    private void requestApktoolAccess() {
        String[] missing = ApktoolPermissions.missingRuntimePermissions(this);
        if (missing.length > 0) {
            permissionRequestInProgress = true;
            requestPermissions(missing, STORAGE_PERMISSION_REQUEST);
            return;
        }
        if (!ApktoolPermissions.hasFileAccess(this)) {
            permissionPromptShown = true;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.apktool_permissions_title)
                    .setMessage(R.string.apktool_all_files_message)
                    .setPositiveButton(R.string.open_all_files_settings,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    allFilesSettingsOpened = true;
                                    ApktoolPermissions.openAllFilesAccessSettings(MainActivity.this);
                                }
                            })
                    .setNegativeButton(R.string.later, null)
                    .show();
            return;
        }
        if (!ApktoolPermissions.canInstallPackages(this)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissionPromptShown = true;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.apktool_permissions_title)
                    .setMessage(R.string.apktool_install_permission_message)
                    .setPositiveButton(R.string.open_install_settings,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ApktoolPermissions.openInstallPackagesSettings(
                                            MainActivity.this);
                                }
                            })
                    .setNegativeButton(R.string.later, null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            permissionRequestInProgress = false;
            permissionPromptShown = false;
            requestApktoolAccess();
        }
    }

    private void openApktool(Uri data) {
        Intent intent = new Intent(this, ApktoolActivity.class);
        if (data != null) {
            intent.setData(data);
        }
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // Master App is a home surface: pressing Back must not close it.
        Toast.makeText(this, R.string.master_home_message, Toast.LENGTH_SHORT).show();
    }
}
