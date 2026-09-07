package com.a4455jkjh.apktool;

import android.os.Bundle;
import com.a4455jkjh.apktool.fragment.files.ApplicationsPage;

/**
 * Full-screen installed-app drawer opened from the Apktool workspace.
 */
public class ApplicationsActivity extends ThemedActivity {
    private ApplicationsPage applicationsPage;

    @Override
    protected void init(Bundle savedInstanceState) {
        applicationsPage = new ApplicationsPage(this, true);
        setContentView(applicationsPage.getView());
        applicationsPage.init(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (applicationsPage != null) {
            applicationsPage.refresh();
        }
    }
}