package com.a4455jkjh.apktool;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

/** Displays apps intentionally hidden from the main launcher grid. */
public class AppDrawerActivity extends ThemedActivity {
    private LauncherApps launcherApps;

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.hidden_apps);
        showSystemBars();
        findViewById(R.id.hidden_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        launcherApps = new LauncherApps(
                this,
                (GridLayout) findViewById(R.id.hidden_grid),
                (TextView) findViewById(R.id.hidden_empty),
                (TextView) findViewById(R.id.hidden_count),
                (EditText) findViewById(R.id.hidden_search),
                true);
        launcherApps.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launcherApps != null) {
            launcherApps.refresh();
        }
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.launcher_background));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.launcher_background));
    }
}