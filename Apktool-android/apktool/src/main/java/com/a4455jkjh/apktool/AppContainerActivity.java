package com.a4455jkjh.apktool;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the Android-managed profile used as the Master App container.
 *
 * This deliberately uses Android's profile boundary instead of trying to
 * execute another APK inside Master App's process. The OS keeps the cloned
 * package, UID, permissions, accounts and storage isolated.
 */
public class AppContainerActivity extends ThemedActivity {
    private static final int PROVISION_REQUEST = 810;

    private DevicePolicyManager devicePolicyManager;
    private PackageManager packageManager;
    private ComponentName adminComponent;
    private TextView status;
    private Button setup;
    private Button openProfile;
    private LinearLayout manager;
    private EditText packageId;
    private ListView apps;
    private ArrayAdapter<String> appsAdapter;
    private final ArrayList<String> clonedPackages = new ArrayList<String>();

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.app_container);
        if (getActionBar() != null) {
            getActionBar().setTitle(R.string.open_app_container);
        }

        devicePolicyManager =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        packageManager = getPackageManager();
        adminComponent = new ComponentName(this, MasterDeviceAdminReceiver.class);
        status = findViewById(R.id.app_container_status);
        setup = findViewById(R.id.app_container_setup);
        openProfile = findViewById(R.id.app_container_open_profile);
        manager = findViewById(R.id.app_container_manager);
        packageId = findViewById(R.id.app_container_package);
        apps = findViewById(R.id.app_container_apps);
        appsAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        apps.setAdapter(appsAdapter);

        setup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                provisionManagedProfile();
            }
        });
        openProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMasterInManagedProfile();
            }
        });
        findViewById(R.id.app_container_clone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clonePackage();
            }
        });
        apps.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position >= 0 && position < clonedPackages.size()) {
                    showPackageActions(clonedPackages.get(position));
                }
            }
        });
        findViewById(R.id.app_container_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                returnToMasterHome();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContainerState();
    }

    private boolean isProfileOwner() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && devicePolicyManager.isProfileOwnerApp(getPackageName());
    }

    private void refreshContainerState() {
        boolean profileOwner = isProfileOwner();
        manager.setVisibility(profileOwner ? View.VISIBLE : View.GONE);
        setup.setVisibility(profileOwner ? View.GONE : View.VISIBLE);
        openProfile.setVisibility(profileOwner ? View.GONE : View.VISIBLE);
        if (profileOwner) {
            status.setText(R.string.app_container_ready);
            refreshClonedApps();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            status.setText(R.string.app_container_not_supported);
        } else {
            status.setText(R.string.app_container_not_ready);
        }
    }

    private void provisionManagedProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, R.string.app_container_not_supported,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (isProfileOwner()) {
            refreshContainerState();
            return;
        }
        Intent provision = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        provision.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent);
        if (provision.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.app_container_not_available,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivityForResult(provision, PROVISION_REQUEST);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.app_container_not_available,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openMasterInManagedProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, R.string.app_container_profile_unavailable,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            android.content.pm.CrossProfileApps crossProfileApps =
                    (android.content.pm.CrossProfileApps)
                            getSystemService(Context.CROSS_PROFILE_APPS_SERVICE);
            List<UserHandle> profiles = crossProfileApps.getTargetUserProfiles();
            if (profiles == null || profiles.isEmpty()) {
                Toast.makeText(this, R.string.app_container_profile_unavailable,
                        Toast.LENGTH_LONG).show();
                return;
            }
            crossProfileApps.startMainActivity(
                    new ComponentName(this, AppContainerActivity.class), profiles.get(0));
        } catch (Exception exception) {
            Toast.makeText(this, R.string.app_container_profile_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void clonePackage() {
        if (!isProfileOwner() || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        String requestedPackage = packageId.getText().toString().trim();
        if (requestedPackage.length() == 0 || requestedPackage.indexOf('.') < 0) {
            packageId.setError(getString(R.string.app_container_package_hint));
            return;
        }
        try {
            boolean installed = devicePolicyManager.installExistingPackage(
                    adminComponent, requestedPackage);
            if (installed) {
                Toast.makeText(this, R.string.app_container_clone_success,
                        Toast.LENGTH_SHORT).show();
                refreshClonedApps();
            } else {
                Toast.makeText(this, R.string.app_container_clone_failed,
                        Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException exception) {
            Toast.makeText(this, R.string.app_container_clone_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshClonedApps() {
        clonedPackages.clear();
        try {
            List<ApplicationInfo> installed = packageManager.getInstalledApplications(0);
            for (ApplicationInfo info : installed) {
                if (!getPackageName().equals(info.packageName)
                        && (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    clonedPackages.add(info.packageName);
                }
            }
        } catch (Exception ignored) {
            // The profile can be briefly unavailable while Android finishes setup.
        }
        Collections.sort(clonedPackages, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return getAppLabel(left).compareToIgnoreCase(getAppLabel(right));
            }
        });
        appsAdapter.clear();
        for (String packageName : clonedPackages) {
            appsAdapter.add(getAppLabel(packageName) + "\n" + packageName);
        }
        appsAdapter.notifyDataSetChanged();
    }

    private String getAppLabel(String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return info.loadLabel(packageManager).toString();
        } catch (Exception exception) {
            return packageName;
        }
    }

    private void showPackageActions(final String packageName) {
        final String[] actions = new String[] {
                getString(R.string.app_container_launch),
                getString(R.string.app_container_uninstall)
        };
        new AlertDialog.Builder(this)
                .setTitle(getAppLabel(packageName))
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            launchPackage(packageName);
                        } else {
                            removePackage(packageName);
                        }
                    }
                })
                .show();
    }

    private void launchPackage(String packageName) {
        Intent launch = packageManager.getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, R.string.app_no_launch_activity,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(launch);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.app_container_launch_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void removePackage(String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            // DevicePolicyManager has no public uninstall-package API. The
            // supported path is Android's profile-scoped uninstall confirmation.
            Intent uninstall = new Intent(Intent.ACTION_DELETE);
            uninstall.setData(Uri.parse("package:" + packageName));
            startActivity(uninstall);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.app_container_remove_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void returnToMasterHome() {
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PROVISION_REQUEST) {
            refreshContainerState();
            if (resultCode == RESULT_OK && !isProfileOwner()) {
                openMasterInManagedProfile();
            }
        }
    }
}