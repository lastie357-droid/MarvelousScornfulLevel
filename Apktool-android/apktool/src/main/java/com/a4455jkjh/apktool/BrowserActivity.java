package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A self-contained browser surface. Pages stay inside the app's WebView and
 * are never handed to an external browser application.
 *
 * Android WebView uses the Chromium engine supplied by the device. This class
 * owns the browser profile, tabs, history and download records for Master App.
 */
public class BrowserActivity extends ThemedActivity {
    private static final String HOME_URL = "https://www.google.com";
    private static final String GMAIL_URL = "https://mail.google.com";
    private static final String PREFS = "master_browser";
    private static final String HISTORY_KEY = "history";
    private static final String DOWNLOADS_KEY = "downloads";
    private static final String AUTOFILL_ONBOARDING_KEY = "autofill_onboarding_shown";
    private static final String AD_BLOCKING_KEY = "ad_blocking_enabled";
    private static final int WEB_PERMISSION_REQUEST_CODE = 701;
    private static final int FILE_CHOOSER_REQUEST_CODE = 702;
    private static final int MAX_HISTORY = 100;
    private static final int MAX_DOWNLOADS = 50;

    private final ArrayList<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private EditText addressBar;
    private FrameLayout webViewContainer;
    private int currentTab = -1;
    private PermissionRequest pendingPermissionRequest;
    private String[] pendingPermissionResources;
    private ValueCallback<Uri[]> pendingFileCallback;

    private static class BrowserTab {
        private WebView webView;
        private String title = "";
        private String url = "";
    }

    private static class BrowserRecord {
        private final String title;
        private final String url;
        private final String detail;

        private BrowserRecord(String title, String url, String detail) {
            this.title = title;
            this.url = url;
            this.detail = detail;
        }
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        setContentView(R.layout.browser);
        getActionBar().setTitle(R.string.browser);

        addressBar = findViewById(R.id.browser_address);
        webViewContainer = findViewById(R.id.browser_webview_container);

        findViewById(R.id.browser_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getCurrentWebView().canGoBack()) {
                    getCurrentWebView().goBack();
                }
            }
        });
        findViewById(R.id.browser_forward).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getCurrentWebView().canGoForward()) {
                    getCurrentWebView().goForward();
                }
            }
        });
        findViewById(R.id.browser_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getCurrentWebView().reload();
            }
        });
        findViewById(R.id.browser_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getCurrentWebView().loadUrl(HOME_URL);
            }
        });
        findViewById(R.id.browser_gmail).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getCurrentWebView().loadUrl(GMAIL_URL);
            }
        });
        findViewById(R.id.browser_tab_count).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTabs();
            }
        });
        findViewById(R.id.browser_new_tab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTab(null);
            }
        });
        findViewById(R.id.browser_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBrowserMenu();
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
        createTab(incoming == null ? null : incoming.toString());
        showAutofillOnboardingIfNeeded();
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

    private BrowserTab createTab(String initialUrl) {
        final BrowserTab tab = new BrowserTab();
        tab.webView = buildWebView(tab);
        tabs.add(tab);
        webViewContainer.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        switchToTab(tabs.size() - 1);
        tab.webView.loadUrl(initialUrl == null ? HOME_URL : initialUrl);
        return tab;
    }

    private WebView buildWebView(final BrowserTab tab) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Let the device's configured password provider, such as Google
            // Password Manager, offer credentials to login forms in this WebView.
            // Master App never reads or stores those credentials.
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                loadLinkInsideApp(view, url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame()) {
                    loadLinkInsideApp(view, request.getUrl().toString());
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (isAdBlockingEnabled() && BrowserAdBlocker.shouldBlock(url)) {
                    return emptyBlockedResponse();
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()
                        && isAdBlockingEnabled()
                        && BrowserAdBlocker.shouldBlock(request.getUrl().toString())) {
                    return emptyBlockedResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                tab.url = url;
                updateCurrentChrome(tab);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tab.url = url;
                rememberHistory(tab);
                updateCurrentChrome(tab);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                tab.title = title == null ? "" : title;
                updateCurrentChrome(tab);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleWebPermissionRequest(request);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                openFileChooser(filePathCallback, fileChooserParams);
                return true;
            }

            @Override
            public boolean onCreateWindow(
                    WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                BrowserTab newTab = createTab(null);
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newTab.webView);
                resultMsg.sendToTarget();
                return true;
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                startDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
        return webView;
    }

    private WebResourceResponse emptyBlockedResponse() {
        return new WebResourceResponse(
                "text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private boolean isAdBlockingEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(AD_BLOCKING_KEY, true);
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            request.deny();
            return;
        }

        ArrayList<String> supportedResources = new ArrayList<String>();
        ArrayList<String> missingPermissions = new ArrayList<String>();
        String[] resources = request.getResources();
        for (String resource : resources) {
            String permission = null;
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                permission = Manifest.permission.CAMERA;
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                permission = Manifest.permission.RECORD_AUDIO;
            }
            if (permission == null) {
                continue;
            }
            supportedResources.add(resource);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                    && !missingPermissions.contains(permission)) {
                missingPermissions.add(permission);
            }
        }

        if (supportedResources.size() == 0) {
            request.deny();
            return;
        }

        String[] resourcesToGrant = supportedResources.toArray(
                new String[supportedResources.size()]);
        if (missingPermissions.size() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingPermissionRequest = request;
            pendingPermissionResources = resourcesToGrant;
            requestPermissions(
                    missingPermissions.toArray(new String[missingPermissions.size()]),
                    WEB_PERMISSION_REQUEST_CODE);
            return;
        }
        confirmWebPermission(request, resourcesToGrant);
    }

    private void confirmWebPermission(
            final PermissionRequest request, final String[] resources) {
        String origin = request.getOrigin() == null
                ? getString(R.string.browser_unknown_site)
                : request.getOrigin().toString();
        boolean camera = false;
        boolean microphone = false;
        for (String resource : resources) {
            camera |= PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource);
            microphone |= PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource);
        }
        String capability;
        if (camera && microphone) {
            capability = getString(R.string.browser_camera_and_microphone);
        } else if (camera) {
            capability = getString(R.string.browser_camera);
        } else {
            capability = getString(R.string.browser_microphone);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_site_permission_title)
                .setMessage(getString(R.string.browser_site_permission_message,
                        origin, capability))
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            request.grant(resources);
                        } catch (Exception ignored) {
                            request.deny();
                        }
                    }
                })
                .setNegativeButton(R.string.deny, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.deny();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        request.deny();
                    }
                })
                .show();
    }

    private void openFileChooser(
            ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
        }
        pendingFileCallback = callback;
        try {
            Intent intent = params.createIntent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
        } catch (Exception exception) {
            pendingFileCallback = null;
            callback.onReceiveValue(null);
            Toast.makeText(this, R.string.browser_file_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Keep navigation in Master App. In particular, never call ACTION_VIEW or
     * another package for a web link, login redirect, mail link, or intent URL.
     */
    private void loadLinkInsideApp(WebView view, String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        if (isWebUrl(url) || url.startsWith("file://")) {
            view.loadUrl(url);
            return;
        }
        if (url.startsWith("mailto:")) {
            String address = url.substring("mailto:".length());
            int queryStart = address.indexOf('?');
            if (queryStart >= 0) {
                address = address.substring(0, queryStart);
            }
            view.loadUrl(GMAIL_URL + "/mail/u/0/?view=cm&to=" + Uri.encode(address));
            return;
        }
        if (url.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (isWebUrl(fallback)) {
                    view.loadUrl(fallback);
                    return;
                }
                Uri data = intent.getData();
                if (data != null && isWebUrl(data.toString())) {
                    view.loadUrl(data.toString());
                    return;
                }
            } catch (Exception ignored) {
                // The URL is intentionally blocked below when no web fallback exists.
            }
        }
        Toast.makeText(this, R.string.browser_link_blocked, Toast.LENGTH_SHORT).show();
    }

    private boolean isWebUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        currentTab = index;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).webView.setVisibility(i == currentTab ? View.VISIBLE : View.GONE);
        }
        updateCurrentChrome(tabs.get(currentTab));
    }

    private WebView getCurrentWebView() {
        return tabs.get(currentTab).webView;
    }

    private void updateCurrentChrome(BrowserTab tab) {
        if (currentTab < 0 || tabs.get(currentTab) != tab) {
            return;
        }
        addressBar.setText(tab.url);
        addressBar.setSelection(addressBar.length());
        String title = tab.title.length() == 0 ? getString(R.string.browser) : tab.title;
        getActionBar().setTitle(title);
        ((TextView) findViewById(R.id.browser_tab_count))
                .setText(getString(R.string.browser_tabs, tabs.size()));
        findViewById(R.id.browser_back).setEnabled(tab.webView.canGoBack());
        findViewById(R.id.browser_forward).setEnabled(tab.webView.canGoForward());
    }

    private void navigate(String value) {
        String query = value == null ? "" : value.trim();
        if (query.length() == 0 || currentTab < 0) {
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
        getCurrentWebView().loadUrl(query);
    }

    private void showTabs() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            labels[i] = (i + 1) + ". "
                    + (tab.title.length() == 0 ? getString(R.string.browser) : tab.title);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.browser_tabs, tabs.size()))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switchToTab(which);
                    }
                })
                .setPositiveButton(R.string.browser_new_tab, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createTab(null);
                    }
                })
                .show();
    }

    private void showBrowserMenu() {
        final String[] options = new String[] {
                getString(R.string.browser_history),
                getString(R.string.browser_downloads),
                getString(R.string.set_default_browser),
                getString(R.string.browser_clear_data),
                getString(R.string.browser_sign_in_note),
                getString(R.string.browser_password_autofill),
                getString(R.string.browser_ad_blocker)
                        + " ("
                        + getString(isAdBlockingEnabled()
                        ? R.string.browser_ad_blocker_on
                        : R.string.browser_ad_blocker_off)
                        + ")"
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_menu)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showHistory();
                        } else if (which == 1) {
                            showDownloads();
                        } else if (which == 2) {
                            openDefaultBrowserSettings(BrowserActivity.this);
                        } else if (which == 3) {
                            clearBrowsingData();
                        } else if (which == 4) {
                            showGoogleSignInNotice();
                        } else if (which == 5) {
                            openAutofillSettings();
                        } else {
                            toggleAdBlocking();
                        }
                    }
                })
                .show();
    }

    private void toggleAdBlocking() {
        boolean enabled = !isAdBlockingEnabled();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(AD_BLOCKING_KEY, enabled)
                .apply();
        Toast.makeText(this,
                enabled ? R.string.browser_ad_blocker_enabled
                        : R.string.browser_ad_blocker_disabled,
                Toast.LENGTH_SHORT).show();
        getCurrentWebView().reload();
    }

    private void showAutofillOnboardingIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(AUTOFILL_ONBOARDING_KEY, false)) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(AUTOFILL_ONBOARDING_KEY, true)
                .apply();
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_autofill_title)
                .setMessage(R.string.browser_autofill_message)
                .setPositiveButton(R.string.browser_open_autofill_settings,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openAutofillSettings();
                            }
                        })
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void openAutofillSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showMessage(R.string.browser_password_autofill,
                    R.string.browser_autofill_unavailable);
            return;
        }
        try {
            // The public Settings class exposes the request-to-set-service
            // action, but not the general Autofill settings action on all
            // compile SDK stubs. Use the stable Android settings action here
            // so the user can choose Google Password Manager.
            startActivity(new Intent("android.settings.AUTOFILL_SETTINGS"));
        } catch (Exception exception) {
            showMessage(R.string.browser_password_autofill,
                    R.string.browser_autofill_settings_unavailable);
        }
    }

    private void showGoogleSignInNotice() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_gmail)
                .setMessage(R.string.browser_sign_in_note)
                .setPositiveButton(R.string.browser_gmail, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getCurrentWebView().loadUrl(GMAIL_URL);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void rememberHistory(BrowserTab tab) {
        if (tab.url.length() == 0 || (!tab.url.startsWith("http://")
                && !tab.url.startsWith("https://"))) {
            return;
        }
        ArrayList<BrowserRecord> records = readRecords(HISTORY_KEY);
        ArrayList<BrowserRecord> updated = new ArrayList<BrowserRecord>();
        updated.add(new BrowserRecord(
                tab.title.length() == 0 ? tab.url : tab.title, tab.url, ""));
        for (BrowserRecord record : records) {
            if (!record.url.equals(tab.url)) {
                updated.add(record);
            }
            if (updated.size() >= MAX_HISTORY) {
                break;
            }
        }
        writeRecords(HISTORY_KEY, updated);
    }

    private void showHistory() {
        final ArrayList<BrowserRecord> records = readRecords(HISTORY_KEY);
        if (records.size() == 0) {
            showMessage(R.string.browser_history, R.string.browser_no_history);
            return;
        }
        String[] labels = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            labels[i] = records.get(i).title + "\n" + records.get(i).url;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigate(records.get(which).url);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startDownload(String url, String userAgent, String contentDisposition,
                               String mimetype) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
            request.setDescription(url);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype));
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);

            ArrayList<BrowserRecord> records = readRecords(DOWNLOADS_KEY);
            ArrayList<BrowserRecord> updated = new ArrayList<BrowserRecord>();
            updated.add(new BrowserRecord(
                    URLUtil.guessFileName(url, contentDisposition, mimetype), url,
                    getString(R.string.browser_download_file,
                            URLUtil.guessFileName(url, contentDisposition, mimetype))));
            for (BrowserRecord record : records) {
                if (!record.url.equals(url)) {
                    updated.add(record);
                }
                if (updated.size() >= MAX_DOWNLOADS) {
                    break;
                }
            }
            writeRecords(DOWNLOADS_KEY, updated);
            Toast.makeText(this, R.string.browser_download_started, Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDownloads() {
        final ArrayList<BrowserRecord> records = readRecords(DOWNLOADS_KEY);
        if (records.size() == 0) {
            showMessage(R.string.browser_downloads, R.string.browser_no_downloads);
            return;
        }
        String[] labels = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            labels[i] = records.get(i).title + "\n" + records.get(i).url;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_downloads)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigate(records.get(which).url);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearBrowsingData() {
        for (BrowserTab tab : tabs) {
            tab.webView.clearHistory();
            tab.webView.clearCache(true);
        }
        CookieManager.getInstance().removeAllCookie();
        WebStorage.getInstance().deleteAllData();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(HISTORY_KEY)
                .remove(DOWNLOADS_KEY)
                .apply();
        Toast.makeText(this, R.string.browser_data_cleared, Toast.LENGTH_LONG).show();
    }

    private void showMessage(int title, int message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != WEB_PERMISSION_REQUEST_CODE) {
            return;
        }
        PermissionRequest request = pendingPermissionRequest;
        String[] resources = pendingPermissionResources;
        pendingPermissionRequest = null;
        pendingPermissionResources = null;
        if (request == null || resources == null) {
            return;
        }
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            granted &= result == PackageManager.PERMISSION_GRANTED;
        }
        if (granted) {
            confirmWebPermission(request, resources);
        } else {
            request.deny();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST_CODE || pendingFileCallback == null) {
            return;
        }
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        Uri[] results = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        }
        callback.onReceiveValue(results);
    }

    private ArrayList<BrowserRecord> readRecords(String key) {
        ArrayList<BrowserRecord> records = new ArrayList<BrowserRecord>();
        String encoded = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "");
        if (encoded.length() == 0) {
            return records;
        }
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                records.add(new BrowserRecord(
                        item.optString("title"),
                        item.optString("url"),
                        item.optString("detail")));
            }
        } catch (Exception ignored) {
            // An invalid local record should not stop the browser from opening.
        }
        return records;
    }

    private void writeRecords(String key, ArrayList<BrowserRecord> records) {
        JSONArray array = new JSONArray();
        for (BrowserRecord record : records) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", record.title);
                item.put("url", record.url);
                item.put("detail", record.detail);
                array.put(item);
            } catch (Exception ignored) {
                // Ignore one malformed record and keep the rest usable.
            }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(key, array.toString())
                .apply();
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
        if (currentTab >= 0 && getCurrentWebView().canGoBack()) {
            getCurrentWebView().goBack();
            return;
        }
        if (tabs.size() > 1) {
            BrowserTab tab = tabs.remove(currentTab);
            webViewContainer.removeView(tab.webView);
            tab.webView.destroy();
            switchToTab(Math.max(0, currentTab - 1));
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab tab : tabs) {
            tab.webView.stopLoading();
            tab.webView.setWebChromeClient(null);
            tab.webView.setWebViewClient(null);
            tab.webView.destroy();
        }
        tabs.clear();
        super.onDestroy();
    }
}