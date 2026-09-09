package com.a4455jkjh.apktool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared installed-app grid for the launcher and its hidden-app screen.
 *
 * Every app action intentionally goes through Android's public intents.
 * The grid contains installed application packages that Android can launch.
 * Apps without a launch activity are intentionally left out of the grid.
 */
public final class LauncherApps {
    private static final String PREFS = "master_launcher";
    private static final String HIDDEN_PACKAGES = "hidden_packages";
    private static final String RECENT_PACKAGES = "recent_packages";

    private final Activity activity;
    private final GridLayout grid;
    private final TextView empty;
    private final TextView count;
    private final EditText search;
    private final boolean hiddenOnly;
    private final PackageManager packageManager;
    private final SharedPreferences preferences;
    private final LayoutInflater inflater;
    private final List<AppEntry> allEntries = new ArrayList<AppEntry>();

    public LauncherApps(Activity activity, GridLayout grid, TextView empty,
                        TextView count, EditText search, boolean hiddenOnly) {
        this.activity = activity;
        this.grid = grid;
        this.empty = empty;
        this.count = count;
        this.search = search;
        this.hiddenOnly = hiddenOnly;
        packageManager = activity.getPackageManager();
        preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        inflater = LayoutInflater.from(activity);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void refresh() {
        loadApps();
        render(search.getText() == null ? "" : search.getText().toString());
    }

    private void loadApps() {
        allEntries.clear();
        Set<String> hiddenPackages = hiddenPackages();
        List<ApplicationInfo> results =
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo info : results) {
            if (info == null) {
                continue;
            }
            String packageName = info.packageName;
            if (packageName == null || packageName.equals(activity.getPackageName())) {
                continue;
            }
            Intent target = packageManager.getLaunchIntentForPackage(packageName);
            if (target == null) {
                continue;
            }
            if (hiddenOnly != hiddenPackages.contains(packageName)) {
                continue;
            }

            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                allEntries.add(new AppEntry(
                        packageName,
                        info.loadLabel(packageManager),
                        info.loadIcon(packageManager),
                        (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                        packageInfo.lastUpdateTime,
                        target));
            } catch (PackageManager.NameNotFoundException ignored) {
                // The package may have been removed while the launcher refreshed.
            }
        }

        final Collator collator = Collator.getInstance();
        final List<String> recentPackages = recentPackages();
        Collections.sort(allEntries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                int leftRecent = recentPackages.indexOf(left.packageName);
                int rightRecent = recentPackages.indexOf(right.packageName);
                if (leftRecent >= 0 || rightRecent >= 0) {
                    if (leftRecent < 0) {
                        return 1;
                    }
                    if (rightRecent < 0) {
                        return -1;
                    }
                    if (leftRecent != rightRecent) {
                        return leftRecent - rightRecent;
                    }
                }
                int result = collator.compare(left.label.toString(), right.label.toString());
                return result != 0 ? result : left.packageName.compareTo(right.packageName);
            }
        });
        if (count != null) {
            count.setText(activity.getString(R.string.launcher_app_count, allEntries.size()));
        }
    }

    private void render(String query) {
        grid.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase();
        int shown = 0;
        for (AppEntry entry : allEntries) {
            if (normalized.length() > 0
                    && !entry.label.toString().toLowerCase().contains(normalized)
                    && !entry.packageName.toLowerCase().contains(normalized)) {
                continue;
            }
            addCard(entry);
            shown++;
        }

        if (shown == 0) {
            empty.setText(normalized.length() == 0
                    ? (hiddenOnly ? R.string.hidden_apps_empty : R.string.launcher_no_apps)
                    : R.string.launcher_no_search_results);
            empty.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
        }
    }

    private void addCard(final AppEntry entry) {
        View card = inflater.inflate(R.layout.app_card, grid, false);
        ImageView icon = card.findViewById(R.id.app_icon);
        TextView label = card.findViewById(R.id.app_label);
        TextView more = card.findViewById(R.id.app_more);
        icon.setImageDrawable(entry.icon);
        label.setText(entry.label);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(entry);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showActions(view, entry);
                return true;
            }
        });
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showActions(view, entry);
            }
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(106);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, 0, 0);
        grid.addView(card, params);
    }

    private void showActions(View anchor, final AppEntry entry) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenuInflater().inflate(R.menu.app_actions, menu.getMenu());
        menu.getMenu().findItem(R.id.action_hide).setTitle(
                hiddenOnly ? R.string.unhide_app : R.string.hide_app);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_launch) {
                launch(entry);
                return true;
            }
            if (id == R.id.action_details) {
                showDetails(entry);
                return true;
            }
            if (id == R.id.action_hide) {
                toggleHidden(entry);
                return true;
            }
            if (id == R.id.action_uninstall) {
                uninstall(entry);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void launch(AppEntry entry) {
        Intent launch = new Intent(entry.launchIntent);
        launch.setFlags(0);
        ComponentName component = launch.getComponent();

        try {
            /*
             * When available, let Android's launcher service start the
             * package's own main activity. This is the platform path intended
             * for launchers and lets Android reuse or bring forward the
             * target app's task.
             *
             * The app does not add NEW_TASK, NEW_DOCUMENT, MULTIPLE_TASK, or
             * any other task/activity flags. A third-party package still owns
             * its Activity and process; Android cannot embed arbitrary APK
             * Activities inside this launcher's view hierarchy.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && component != null
                    && launchWithLauncherService(component)) {
                rememberRecent(entry.packageName);
                return;
            }

            // Compatibility fallback for older Android versions or devices
            // that reject the launcher-service handoff.
            activity.startActivity(launch);
            rememberRecent(entry.packageName);
        } catch (Exception exception) {
            Toast.makeText(activity, R.string.app_no_launch_activity, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean launchWithLauncherService(ComponentName component) {
        try {
            android.content.pm.LauncherApps launcherService =
                    (android.content.pm.LauncherApps) activity.getSystemService(
                            Context.LAUNCHER_APPS_SERVICE);
            if (launcherService == null) {
                return false;
            }
            launcherService.startMainActivity(
                    component, android.os.Process.myUserHandle(), null, null);
            return true;
        } catch (SecurityException exception) {
            // The service can reject the call when this app is not the active
            // home app. The explicit intent fallback still works normally.
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private List<String> recentPackages() {
        String saved = preferences.getString(RECENT_PACKAGES, "");
        List<String> recent = new ArrayList<String>();
        if (saved == null || saved.length() == 0) {
            return recent;
        }
        String[] packages = saved.split(",");
        for (String packageName : packages) {
            if (packageName != null && packageName.length() > 0
                    && !recent.contains(packageName)) {
                recent.add(packageName);
            }
        }
        return recent;
    }

    private void rememberRecent(String packageName) {
        List<String> recent = recentPackages();
        recent.remove(packageName);
        recent.add(0, packageName);
        while (recent.size() > 5) {
            recent.remove(recent.size() - 1);
        }
        StringBuilder saved = new StringBuilder();
        for (String recentPackage : recent) {
            if (saved.length() > 0) {
                saved.append(',');
            }
            saved.append(recentPackage);
        }
        preferences.edit().putString(RECENT_PACKAGES, saved.toString()).apply();
    }

    private void showDetails(AppEntry entry) {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + entry.packageName));
        try {
            activity.startActivity(details);
        } catch (Exception exception) {
            Toast.makeText(activity, R.string.app_details, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleHidden(AppEntry entry) {
        Set<String> updated = hiddenPackages();
        if (hiddenOnly) {
            updated.remove(entry.packageName);
        } else {
            updated.add(entry.packageName);
        }
        preferences.edit().putStringSet(HIDDEN_PACKAGES, updated).apply();
        refresh();
    }

    private void uninstall(final AppEntry entry) {
        if (entry.systemApp) {
            Toast.makeText(activity, R.string.app_system_uninstall_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.uninstall_app)
                .setMessage(activity.getString(R.string.app_uninstall_confirm, entry.label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.uninstall_app, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent uninstall = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        uninstall.setData(Uri.parse("package:" + entry.packageName));
                        uninstall.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                        try {
                            activity.startActivity(uninstall);
                        } catch (Exception exception) {
                            try {
                                Intent fallback = new Intent(Intent.ACTION_DELETE);
                                fallback.setData(Uri.parse("package:" + entry.packageName));
                                activity.startActivity(fallback);
                            } catch (Exception ignored) {
                                Toast.makeText(activity, R.string.app_uninstall_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .show();
    }

    private Set<String> hiddenPackages() {
        return new HashSet<String>(preferences.getStringSet(
                HIDDEN_PACKAGES, Collections.<String>emptySet()));
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppEntry {
        final String packageName;
        final CharSequence label;
        final android.graphics.drawable.Drawable icon;
        final boolean systemApp;
        final long lastUpdateTime;
        final Intent launchIntent;

        AppEntry(String packageName, CharSequence label,
                 android.graphics.drawable.Drawable icon, boolean systemApp,
                 long lastUpdateTime, Intent launchIntent) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.systemApp = systemApp;
            this.lastUpdateTime = lastUpdateTime;
            this.launchIntent = launchIntent;
        }
    }
}