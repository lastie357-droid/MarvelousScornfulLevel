package com.a4455jkjh.apktool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends ThemedActivity {
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
