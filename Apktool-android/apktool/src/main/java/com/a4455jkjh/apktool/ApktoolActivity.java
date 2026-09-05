package com.a4455jkjh.apktool;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.fragment.EditorFragment;
import com.a4455jkjh.apktool.fragment.FilesFragment;
import com.a4455jkjh.apktool.fragment.editor.EditorPagerAdapter;
import com.a4455jkjh.apktool.util.Settings;

/**
 * The original Apktool workspace. It is opened from the Master App home screen
 * and remains available as a standalone deep-link target for APK files.
 */
public class ApktoolActivity extends ThemedActivity implements DrawerLayout.DrawerListener {
    private DrawerLayout drawer;
    private EditorFragment editor;
    private FilesFragment files;

    public boolean dismissFiles() {
        if (drawer.isDrawerOpen(Gravity.LEFT)) {
            drawer.closeDrawer(Gravity.LEFT);
            return true;
        }
        return false;
    }

    public void showFiles(int idx) {
        files.setPage(idx);
        drawer.openDrawer(Gravity.LEFT);
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.main);
        drawer = findViewById(R.id.drawer);
        drawer.addDrawerListener(this);
        getActionBar().setDisplayOptions(16);
        getActionBar().setCustomView(R.layout.title);
        init(getSupportFragmentManager());
    }

    public void init() {
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        editor.open(data);
        dismissFiles();
    }

    private void init(FragmentManager supportFragmentManager) {
        Fragment fragment = supportFragmentManager.findFragmentById(R.id.editor);
        FilesFragment files;
        if (fragment == null) {
            EditorFragment editor = new EditorFragment();
            files = new FilesFragment();
            supportFragmentManager.beginTransaction()
                    .add(R.id.editor, editor)
                    .add(R.id.leftView, files)
                    .commit();
            this.editor = editor;
        } else {
            editor = (EditorFragment) fragment;
            files = (FilesFragment) supportFragmentManager.findFragmentById(R.id.leftView);
        }
        files.bind(editor);
        this.files = files;
    }

    @Override
    public void onBackPressed() {
        // Back is intentionally non-exiting. Close transient Apktool UI first,
        // then leave the workspace open rather than terminating the Master App.
        if (dismissFiles() || editor.collapseItem()) {
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.isFontSizeChanged) {
            Settings.isFontSizeChanged = false;
            EditorPagerAdapter.INSTANCE.setFontSize();
        }
    }

    @Override
    public void finish() {
        EditorPagerAdapter.INSTANCE.exit();
        super.finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        changeLeft();
    }

    private void changeLeft() {
        if (drawer == null) {
            return;
        }
        View parent = drawer;
        View child = parent.findViewById(R.id.leftView);
        int width = parent.getWidth() / 5 * 4;
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.width = width;
        child.setLayoutParams(params);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeLeft();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (editor != null) {
            editor.save(true, false);
        }
    }

    @Override
    public void onDrawerSlide(View view, float offset) {
        // Drawer listener callback.
    }

    @Override
    public void onDrawerOpened(View view) {
        files.focus();
        drawer.requestDisallowInterceptTouchEvent(false);
    }

    @Override
    public void onDrawerClosed(View view) {
        editor.focus();
        drawer.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public void onDrawerStateChanged(int state) {
        // Drawer listener callback.
    }
}