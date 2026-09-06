package com.a4455jkjh.apktool;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Quick device controls available from Master App without opening full Settings. */
public class ToolsActivity extends ThemedActivity {
    private static final int BLUETOOTH_PERMISSION = 820;
    private static final int NOTIFICATION_PERMISSION = 821;

    @Override
    protected void init(Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(getResources().getColor(R.color.master_background));

        TextView title = new TextView(this);
        title.setText(R.string.tools_title);
        title.setTextSize(26);
        title.setTextColor(getResources().getColor(R.color.master_text));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText(R.string.tools_note);
        note.setTextSize(14);
        note.setTextColor(getResources().getColor(R.color.master_muted));
        note.setPadding(0, 8, 0, 18);
        root.addView(note);

        addButton(root, R.string.tools_bluetooth, new View.OnClickListener() {
            @Override public void onClick(View view) { enableBluetooth(); }
        });
        addButton(root, R.string.tools_wifi, new View.OnClickListener() {
            @Override public void onClick(View view) { openWifiPanel(); }
        });
        addButton(root, R.string.media_hub, new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(ToolsActivity.this, MediaActivity.class));
            }
        });
        addButton(root, R.string.tools_notifications, new View.OnClickListener() {
            @Override public void onClick(View view) { requestNotificationPermission(); }
        });
        addButton(root, R.string.close_cur, new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        setContentView(root);
    }

    private void addButton(LinearLayout root, int text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(58);
        button.setOnClickListener(listener);
        root.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void enableBluetooth() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    BLUETOOTH_PERMISSION);
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, R.string.tools_bluetooth_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (adapter.isEnabled()) {
            Toast.makeText(this, R.string.tools_bluetooth_already_on,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.tools_bluetooth_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openWifiPanel() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
            }
        } catch (Exception exception) {
            Toast.makeText(this, R.string.tools_wifi_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION);
        } else {
            Toast.makeText(this, R.string.tools_notifications_ready,
                    Toast.LENGTH_SHORT).show();
        }
    }
}