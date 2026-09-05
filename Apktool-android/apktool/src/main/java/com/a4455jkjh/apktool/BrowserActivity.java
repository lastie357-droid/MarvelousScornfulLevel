package com.a4455jkjh.apktool;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A self-contained browser surface. Pages stay inside the app's WebView and
 * are never handed to an external browser application.
 */
public class BrowserActivity extends ThemedActivity {
    private WebView webView;
    private EditText addressBar;

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.browser);
        getActionBar().setTitle(R.string.browser);

        addressBar = findViewById(R.id.browser_address);
        webView = findViewById(R.id.browser_webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                addressBar.setText(url);
                addressBar.selectAll();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addressBar.setText(url);
                addressBar.setSelection(addressBar.length());
                updateNavigationButtons();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && title.length() > 0) {
                    getActionBar().setTitle(title);
                }
            }
        });

        findViewById(R.id.browser_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (webView.canGoBack()) {
                    webView.goBack();
                }
            }
        });
        findViewById(R.id.browser_forward).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (webView.canGoForward()) {
                    webView.goForward();
                }
            }
        });
        findViewById(R.id.browser_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                webView.reload();
            }
        });
        findViewById(R.id.browser_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                webView.loadUrl("https://www.google.com");
            }
        });
        findViewById(R.id.browser_default).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDefaultBrowserSettings(BrowserActivity.this);
            }
        });
        addressBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    navigate(addressBar.getText().toString());
                    return true;
                }
                return false;
            }
        });
        addressBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    navigate(addressBar.getText().toString());
                    return true;
                }
                return false;
            }
        });

        Uri incoming = getIntent().getData();
        if (incoming != null) {
            navigate(incoming.toString());
        } else {
            webView.loadUrl("https://www.google.com");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri incoming = intent.getData();
        if (incoming != null) {
            navigate(incoming.toString());
        }
    }

    private void navigate(String value) {
        String query = value == null ? "" : value.trim();
        if (query.length() == 0) {
            return;
        }
        if (!query.startsWith("http://") && !query.startsWith("https://")
                && !query.startsWith("file://")) {
            if (query.indexOf(' ') >= 0 || query.indexOf('.') < 0) {
                query = "https://www.google.com/search?q=" + Uri.encode(query);
            } else {
                query = "https://" + query;
            }
        }
        webView.loadUrl(query);
    }

    private void updateNavigationButtons() {
        findViewById(R.id.browser_back).setEnabled(webView.canGoBack());
        findViewById(R.id.browser_forward).setEnabled(webView.canGoForward());
    }

    /**
     * Android requires the user to choose a default browser in system UI.
     * The app cannot silently take that role.
     */
    public static void openDefaultBrowserSettings(Context context) {
        Intent intent = new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
        try {
            context.startActivity(intent);
        } catch (Exception exception) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
        Toast.makeText(context, R.string.default_browser_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}