package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.util.ArrayList;

/**
 * Android permission and special-access checks used by the Apktool workspace.
 *
 * Broad file access is a special Android setting on Android 11 and newer; it
 * cannot be obtained with requestPermissions().
 */
public final class ApktoolPermissions {
    private ApktoolPermissions() {
    }

    public static boolean hasFileAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static String[] missingRuntimePermissions(Context context) {
        ArrayList<String> missing = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return missing.toArray(new String[missing.size()]);
    }

    public static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    public static void openAllFilesAccessSettings(Activity activity) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception exception) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
                // The device does not expose the special-access settings page.
            }
        }
    }

    public static void openInstallPackagesSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception ignored) {
            // The device does not expose this special-access settings page.
        }
    }
}