package com.a4455jkjh.apktool;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Dialog;
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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A self-contained browser surface. Ordinary pages stay inside the app's
 * WebView. Master App owns the browser engine, profile, tabs, history,
 * downloads, permissions and navigation without launching another browser.
 *
 * Android WebView uses the Chromium engine supplied by the device.
 */
public class BrowserActivity extends ThemedActivity {
    private static final String HOME_URL = "https://www.google.com";
    private static final String GMAIL_URL = "https://mail.google.com";
    private static final String PREFS = "master_browser";
    private static final String HISTORY_KEY = "history";
    private static final String DOWNLOADS_KEY = "downloads";
    private static final String TABS_KEY = "tabs";
    private static final String CURRENT_TAB_KEY = "current_tab";
    private static final String AUTOFILL_ONBOARDING_KEY = "autofill_onboarding_shown";
    private static final String AD_BLOCKING_KEY = "ad_blocking_enabled";
    private static final int WEB_PERMISSION_REQUEST_CODE = 701;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 702;
    private static final int DOWNLOAD_PERMISSION_REQUEST_CODE = 703;
    private static final int FILE_CHOOSER_REQUEST_CODE = 704;
    private static final int MAX_HISTORY = 100;
    private static final int MAX_DOWNLOADS = 50;
    private static final int DEFAULT_TEXT_ZOOM = 85;
    private static final int MIN_TEXT_ZOOM = 50;
    private static final int MAX_TEXT_ZOOM = 200;

    private final ArrayList<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private EditText addressBar;
    private FrameLayout webViewContainer;
    private int currentTab = -1;
    private PermissionRequest pendingPermissionRequest;
    private String[] pendingPermissionResources;
    private ValueCallback<Uri[]> pendingFileCallback;
    private WebChromeClient.FileChooserParams pendingFileChooserParams;
    private Dialog filePickerDialog;
    private File filePickerDirectory;
    private ArrayList<File> filePickerEntries = new ArrayList<File>();
    private HashSet<File> selectedFiles = new HashSet<File>();
    private ListView filePickerList;
    private TextView filePickerPath;
    private TextView filePickerSelect;
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

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
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        addressBar = findViewById(R.id.browser_address);
        webViewContainer = findViewById(R.id.browser_webview_container);
        AutoCompleteTextView addressSuggestions =
                (AutoCompleteTextView) findViewById(R.id.browser_address);
        addressSuggestions.setThreshold(1);
        addressSuggestions.setAdapter(createHistorySuggestions());
        addressSuggestions.setSelectAllOnFocus(true);
        addressSuggestions.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    ((AutoCompleteTextView) view).showDropDown();
                }
            }
        });
        addressSuggestions.setOnItemClickListener(
                new android.widget.AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                            int position, long id) {
                        String selected = String.valueOf(parent.getItemAtPosition(position));
                        int separator = selected.indexOf('\n');
                        navigate(separator >= 0 ? selected.substring(separator + 1) : selected);
                    }
                });

        findViewById(R.id.browser_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebView current = getCurrentWebView();
                if (current != null) {
                    loadLinkInsideApp(current, HOME_URL);
                }
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
        findViewById(R.id.browser_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebView current = getCurrentWebView();
                if (current != null && current.canGoBack()) {
                    current.goBack();
                }
            }
        });
        findViewById(R.id.browser_forward).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebView current = getCurrentWebView();
                if (current != null && current.canGoForward()) {
                    current.goForward();
                }
            }
        });
        findViewById(R.id.browser_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebView current = getCurrentWebView();
                if (current != null) {
                    current.reload();
                }
            }
        });
        findViewById(R.id.browser_zoom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showZoomControls();
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
        restoreTabs(incoming == null ? null : incoming.toString());
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
        loadLinkInsideApp(tab.webView, initialUrl == null ? HOME_URL : initialUrl);
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
        settings.setTextZoom(DEFAULT_TEXT_ZOOM);
        webView.setInitialScale(75);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        applyBrowserSettings(settings);

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
                improvePasswordFieldAutofill(view);
                rememberHistory(tab);
                saveTabs();
                updateCurrentChrome(tab);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                int failedIndex = tabs.indexOf(tab);
                if (failedIndex >= 0) {
                    removeTab(failedIndex);
                    Toast.makeText(BrowserActivity.this,
                            R.string.browser_renderer_restarted, Toast.LENGTH_LONG).show();
                }
                return true;
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
                requestOrStartDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
        return webView;
    }

    private void applyBrowserSettings(WebSettings settings) {
        boolean desktop = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean("browser_desktop_mode", true);
        settings.setUserAgentString(desktop ? getDesktopUserAgent() : null);
    }

    private String getDesktopUserAgent() {
        // Do not only remove "Mobile" from the Android WebView UA. Many sites
        // still classify that UA as a phone because it contains Android.
        return "Mozilla/5.0 (X11; Linux x86_64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0.0.0 Safari/537.36";
    }

    private void improvePasswordFieldAutofill(final WebView webView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        webView.evaluateJavascript(
                "(function(){"
                        + "var p=document.querySelectorAll('input[type=password]');"
                        + "for(var i=0;i<p.length;i++){p[i].setAttribute('autocomplete','current-password');"
                        + "var f=p[i].form;if(f){var u=f.querySelector('input:not([type=password])');"
                        + "if(u&&!u.getAttribute('autocomplete'))u.setAttribute('autocomplete','username');}}"
                        + "})();", null);
    }

    private void restoreTabs(String incomingUrl) {
        String encoded = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TABS_KEY, "");
        int restoredCurrent = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(CURRENT_TAB_KEY, 0);
        boolean restored = false;
        if (encoded.length() > 0 && incomingUrl == null) {
            try {
                JSONArray savedTabs = new JSONArray(encoded);
                for (int i = 0; i < savedTabs.length(); i++) {
                    String url = savedTabs.optString(i, HOME_URL);
                    if (url.length() > 0) {
                        createTab(url);
                        restored = true;
                    }
                }
            } catch (Exception ignored) {
                // Fall through to a clean home tab if local tab state is corrupt.
            }
            if (restored && restoredCurrent >= 0 && restoredCurrent < tabs.size()) {
                switchToTab(restoredCurrent);
            }
        }
        if (!restored) {
            createTab(incomingUrl == null ? null : incomingUrl);
        } else if (incomingUrl != null) {
            navigate(incomingUrl);
        }
    }

    private void saveTabs() {
        JSONArray savedTabs = new JSONArray();
        for (BrowserTab tab : tabs) {
            String url = tab.url;
            if (url != null && url.length() > 0) {
                savedTabs.put(url);
            }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(TABS_KEY, savedTabs.toString())
                .putInt(CURRENT_TAB_KEY, Math.max(0, currentTab))
                .apply();
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
        pendingFileChooserParams = params;
        try {
            // Use Android's document provider so the user can pick from Files,
            // Drive, photos, and other installed providers. Do not convert the
            // result into file://; WebView upload forms need the granted
            // content:// URI returned by the system picker.
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType(resolveFileChooserType(params));
            if (params != null && params.getAcceptTypes() != null
                    && params.getAcceptTypes().length > 1) {
                picker.putExtra(Intent.EXTRA_MIME_TYPES, params.getAcceptTypes());
            }
            if (params != null
                    && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(picker, FILE_CHOOSER_REQUEST_CODE);
        } catch (Exception exception) {
            pendingFileCallback = null;
            pendingFileChooserParams = null;
            callback.onReceiveValue(null);
            Toast.makeText(this, R.string.browser_file_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String resolveFileChooserType(WebChromeClient.FileChooserParams params) {
        if (params == null || params.getAcceptTypes() == null) {
            return "*/*";
        }
        String[] acceptTypes = params.getAcceptTypes();
        for (String acceptType : acceptTypes) {
            if (acceptType != null && acceptType.trim().length() > 0) {
                return acceptType.trim();
            }
        }
        return "*/*";
    }

    private void showInternalFilePicker() {
        filePickerDirectory = initialFilePickerDirectory();
        selectedFiles.clear();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 12);

        filePickerPath = new TextView(this);
        filePickerPath.setTextSize(13);
        root.addView(filePickerPath, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filePickerList = new ListView(this);
        boolean multiple = pendingFileChooserParams != null
                && pendingFileChooserParams.getMode()
                == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        filePickerList.setChoiceMode(multiple
                ? ListView.CHOICE_MODE_MULTIPLE
                : ListView.CHOICE_MODE_SINGLE);
        root.addView(filePickerList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button up = new Button(this);
        up.setText(R.string.browser_file_picker_up);
        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (filePickerDirectory != null && filePickerDirectory.getParentFile() != null) {
                    loadFilePickerDirectory(filePickerDirectory.getParentFile());
                }
            }
        });
        actions.addView(up, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button cancel = new Button(this);
        cancel.setText(R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFileSelection(false);
            }
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        filePickerSelect = new Button(this);
        filePickerSelect.setText(R.string.browser_file_picker_select);
        filePickerSelect.setEnabled(false);
        filePickerSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFileSelection(true);
            }
        });
        actions.addView(filePickerSelect, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filePickerList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position < 0 || position >= filePickerEntries.size()) {
                    return;
                }
                File selected = filePickerEntries.get(position);
                if (selected.isDirectory()) {
                    loadFilePickerDirectory(selected);
                    return;
                }
                boolean multipleSelection = pendingFileChooserParams != null
                        && pendingFileChooserParams.getMode()
                        == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
                if (multipleSelection) {
                    if (filePickerList.isItemChecked(position)) {
                        selectedFiles.add(selected);
                    } else {
                        selectedFiles.remove(selected);
                    }
                } else {
                    selectedFiles.clear();
                    selectedFiles.add(selected);
                }
                filePickerSelect.setEnabled(!selectedFiles.isEmpty());
            }
        });

        filePickerDialog = new Dialog(this);
        filePickerDialog.setTitle(R.string.browser_file_picker_title);
        filePickerDialog.setContentView(root);
        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
        windowParams.copyFrom(filePickerDialog.getWindow().getAttributes());
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        filePickerDialog.show();
        filePickerDialog.getWindow().setAttributes(windowParams);
        loadFilePickerDirectory(filePickerDirectory);
    }

    private File initialFilePickerDirectory() {
        File sharedStorage = Environment.getExternalStorageDirectory();
        if (sharedStorage.exists() && sharedStorage.canRead()) {
            return sharedStorage;
        }
        return getFilesDir();
    }

    private void loadFilePickerDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()
                || !directory.canRead()) {
            Toast.makeText(this, R.string.browser_file_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        filePickerDirectory = directory;
        filePickerPath.setText(directory.getAbsolutePath());
        filePickerEntries.clear();
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.canRead() && (child.isDirectory() || acceptsFile(child))) {
                    filePickerEntries.add(child);
                }
            }
        }
        Collections.sort(filePickerEntries, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        ArrayList<String> labels = new ArrayList<String>();
        for (File child : filePickerEntries) {
            labels.add((child.isDirectory() ? "📁 " : "") + child.getName());
        }
        filePickerList.setAdapter(new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_multiple_choice, labels));
        selectedFiles.clear();
        filePickerSelect.setEnabled(false);
    }

    private boolean acceptsFile(File file) {
        if (pendingFileChooserParams == null) {
            return true;
        }
        String[] acceptTypes = pendingFileChooserParams.getAcceptTypes();
        if (acceptTypes == null || acceptTypes.length == 0) {
            return true;
        }
        String extension = "";
        int extensionStart = file.getName().lastIndexOf('.');
        if (extensionStart >= 0) {
            extension = file.getName().substring(extensionStart).toLowerCase(Locale.US);
        }
        String mime = extension.length() == 0 ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.substring(1));
        for (String rawType : acceptTypes) {
            if (rawType == null) {
                continue;
            }
            for (String rawPart : rawType.split(",")) {
                String type = rawPart.trim().toLowerCase(Locale.US);
                if (type.length() == 0 || "*/*".equals(type)) {
                    return true;
                }
                if (type.startsWith(".") && type.equals(extension)) {
                    return true;
                }
                if (mime != null && type.endsWith("/*")
                        && mime.startsWith(type.substring(0, type.length() - 1))) {
                    return true;
                }
                if (mime != null && type.equals(mime.toLowerCase(Locale.US))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void finishFileSelection(boolean accepted) {
        if (filePickerDialog != null) {
            filePickerDialog.dismiss();
            filePickerDialog = null;
        }
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        pendingFileChooserParams = null;
        if (callback == null) {
            return;
        }
        if (!accepted || selectedFiles.isEmpty()) {
            callback.onReceiveValue(null);
            return;
        }
        ArrayList<Uri> selectedUris = new ArrayList<Uri>();
        for (File entry : filePickerEntries) {
            if (selectedFiles.contains(entry)) {
                selectedUris.add(Uri.fromFile(entry));
            }
        }
        callback.onReceiveValue(selectedUris.toArray(new Uri[selectedUris.size()]));
        selectedFiles.clear();
    }

    /**
     * Resolve browser links without leaving Master App. This deliberately
     * keeps identity-provider pages in the app's WebView as requested.
     */
    private void loadLinkInsideApp(WebView view, String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        if (isWebUrl(url) || isFileUrl(url)) {
            view.loadUrl(url);
            return;
        }
        String scheme = getUrlScheme(url);
        if ("mailto".equalsIgnoreCase(scheme)) {
            openExternalLink(url);
            return;
        }
        if ("intent".equalsIgnoreCase(scheme)) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (isWebUrl(fallback)) {
                    loadLinkInsideApp(view, fallback);
                    return;
                }
                Uri data = intent.getData();
                if (data != null && isWebUrl(data.toString())) {
                    loadLinkInsideApp(view, data.toString());
                    return;
                }
                openExternalIntent(intent);
                return;
            } catch (Exception ignored) {
                // Fall through to the normal external-link handler.
            }
        }
        String wrappedWebUrl = extractWrappedWebUrl(url);
        if (wrappedWebUrl != null) {
            loadLinkInsideApp(view, wrappedWebUrl);
            return;
        }
        openExternalLink(url);
    }

    private void openExternalLink(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            openExternalIntent(intent);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.browser_external_link_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternalIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.browser_external_link_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isWebUrl(String url) {
        String scheme = getUrlScheme(url);
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isFileUrl(String url) {
        return "file".equalsIgnoreCase(getUrlScheme(url));
    }

    private String getUrlScheme(String url) {
        if (url == null) {
            return null;
        }
        try {
            return Uri.parse(url).getScheme();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Some websites wrap a normal web URL in a browser-specific custom scheme.
     * Extract only the web destination; never launch the custom-scheme handler.
     */
    private String extractWrappedWebUrl(String url) {
        try {
            Uri parsed = Uri.parse(url);
            String scheme = parsed.getScheme();
            if (scheme == null) {
                return null;
            }
            if ("googlechrome".equalsIgnoreCase(scheme)
                    || "googlechrome-x-callback".equalsIgnoreCase(scheme)
                    || "browser".equalsIgnoreCase(scheme)) {
                String destination = parsed.getQueryParameter("url");
                return isWebUrl(destination) ? destination : null;
            }
        } catch (Exception ignored) {
            // The custom scheme is blocked when it has no web destination.
        }
        return null;
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
        if (currentTab < 0 || currentTab >= tabs.size()) {
            return null;
        }
        return tabs.get(currentTab).webView;
    }

    private void updateCurrentChrome(BrowserTab tab) {
        if (currentTab < 0 || currentTab >= tabs.size() || tabs.get(currentTab) != tab) {
            return;
        }
        addressBar.setText(tab.url);
        if (!addressBar.hasFocus()) {
            addressBar.setSelection(0, addressBar.length());
        }
        refreshAddressSuggestions();
        ((TextView) findViewById(R.id.browser_tab_count))
                .setText(String.valueOf(tabs.size()));
        findViewById(R.id.browser_back).setEnabled(tab.webView.canGoBack());
        findViewById(R.id.browser_forward).setEnabled(tab.webView.canGoForward());
    }

    private void showZoomControls() {
        final String[] options = new String[] {
                getString(R.string.browser_zoom_in),
                getString(R.string.browser_zoom_out),
                getString(R.string.browser_zoom_reset)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_zoom)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        WebView view = getCurrentWebView();
                        if (view == null) {
                            return;
                        }
                        if (which == 0) {
                            int zoom = view.getSettings().getTextZoom();
                            view.getSettings().setTextZoom(
                                    Math.min(MAX_TEXT_ZOOM, zoom + 10));
                        } else if (which == 1) {
                            int zoom = view.getSettings().getTextZoom();
                            view.getSettings().setTextZoom(
                                    Math.max(MIN_TEXT_ZOOM, zoom - 10));
                        } else {
                            view.getSettings().setTextZoom(DEFAULT_TEXT_ZOOM);
                            view.reload();
                        }
                    }
                })
                .show();
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
        WebView view = getCurrentWebView();
        if (view != null) {
            loadLinkInsideApp(view, query);
        }
    }

    private android.widget.ArrayAdapter<String> createHistorySuggestions() {
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<BrowserRecord> records = readRecords(HISTORY_KEY);
        for (BrowserRecord record : records) {
            String label = record.title == null || record.title.length() == 0
                    ? record.url
                    : record.title + "\n" + record.url;
            if (label.length() > 0 && !labels.contains(label)) {
                labels.add(label);
            }
        }
        return new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_dropdown_item_1line, labels);
    }

    private void refreshAddressSuggestions() {
        AutoCompleteTextView suggestions =
                (AutoCompleteTextView) findViewById(R.id.browser_address);
        if (suggestions != null) {
            suggestions.setAdapter(createHistorySuggestions());
        }
    }

    private void showTabs() {
        final Dialog dialog = new Dialog(this);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(18, 12, 18, 12);
        TextView heading = new TextView(this);
        heading.setText(getString(R.string.browser_tabs, tabs.size()));
        heading.setTextSize(20);
        heading.setTextColor(getResources().getColor(R.color.master_text));
        outer.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(true);
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            final BrowserTab tab = tabs.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 10, 12, 10);
            card.setBackgroundResource(R.drawable.browser_button);
            TextView title = new TextView(this);
            title.setText((i + 1) + "  "
                    + (tab.title.length() == 0 ? getString(R.string.browser) : tab.title));
            title.setTextColor(getResources().getColor(R.color.master_text));
            title.setTextSize(15);
            title.setMaxLines(2);
            card.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView url = new TextView(this);
            url.setText(tab.url);
            url.setTextColor(getResources().getColor(R.color.master_muted));
            url.setTextSize(11);
            url.setMaxLines(2);
            card.addView(url, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Button close = new Button(this);
            close.setText("×");
            close.setTextSize(16);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeTab(tabIndex);
                    dialog.dismiss();
                    saveTabs();
                }
            });
            card.addView(close, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 38));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    switchToTab(tabIndex);
                    dialog.dismiss();
                }
            });
            GridLayout.Spec row = GridLayout.spec(i / 2);
            GridLayout.Spec column = GridLayout.spec(i % 2, 1f);
            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams(row, column);
            cardParams.width = 0;
            cardParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            grid.addView(card, cardParams);
        }
        scroll.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button newTab = new Button(this);
        newTab.setText(R.string.browser_new_tab);
        newTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTab(null);
                dialog.dismiss();
            }
        });
        outer.addView(newTab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setTitle(R.string.browser_tabs);
        dialog.setContentView(outer);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(dialog.getWindow().getAttributes());
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        dialog.show();
        dialog.getWindow().setAttributes(params);
    }

    private void showBrowserMenu() {
        final String[] options = new String[] {
                getString(R.string.browser_exit),
                getString(R.string.browser_history),
                getString(R.string.browser_downloads),
                getString(R.string.set_default_browser),
                getString(R.string.browser_clear_data),
                getString(R.string.browser_sign_in_note),
                getString(R.string.browser_password_autofill),
                getString(R.string.browser_desktop_mode)
                        + " ("
                        + getString(isDesktopMode()
                        ? R.string.browser_desktop_mode_on
                        : R.string.browser_desktop_mode_off)
                        + ")",
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
                            returnToMasterHome();
                        } else if (which == 1) {
                            showHistory();
                        } else if (which == 2) {
                            showDownloads();
                        } else if (which == 3) {
                            openDefaultBrowserSettings(BrowserActivity.this);
                        } else if (which == 4) {
                            clearBrowsingData();
                        } else if (which == 5) {
                            showGoogleSignInNotice();
                        } else if (which == 6) {
                            openAutofillSettings();
                        } else if (which == 7) {
                            toggleDesktopMode();
                        } else {
                            toggleAdBlocking();
                        }
                    }
                })
                .show();
    }

    private boolean isDesktopMode() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean("browser_desktop_mode", true);
    }

    private void toggleDesktopMode() {
        boolean enabled = !isDesktopMode();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("browser_desktop_mode", enabled)
                .apply();
        Toast.makeText(this, enabled ? R.string.browser_desktop_mode_on
                : R.string.browser_desktop_mode_off, Toast.LENGTH_SHORT).show();
        WebView current = getCurrentWebView();
        if (current != null) {
            current.getSettings().setUserAgentString(
                    enabled ? getDesktopUserAgent() : null);
            current.reload();
        }
    }

    private void returnToMasterHome() {
        saveTabs();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void removeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        BrowserTab removed = tabs.remove(index);
        webViewContainer.removeView(removed.webView);
        removed.webView.stopLoading();
        removed.webView.setWebChromeClient(null);
        removed.webView.setWebViewClient(null);
        removed.webView.destroy();
        if (tabs.isEmpty()) {
            currentTab = -1;
            createTab(null);
            return;
        }
        if (currentTab >= tabs.size()) {
            currentTab = tabs.size() - 1;
        } else if (index < currentTab) {
            currentTab--;
        }
        switchToTab(currentTab);
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
        WebView current = getCurrentWebView();
        if (current != null) {
            current.reload();
        }
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
                .setPositiveButton(R.string.browser_secure_sign_in,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        WebView current = getCurrentWebView();
                        if (current != null) {
                            current.loadUrl(GMAIL_URL);
                        }
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
        refreshAddressSuggestions();
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

    private void requestOrStartDownload(String url, String userAgent,
                                        String contentDisposition, String mimetype) {
        if (!ApktoolPermissions.hasFileAccess(this)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingDownloadUrl = url;
            pendingDownloadUserAgent = userAgent;
            pendingDownloadContentDisposition = contentDisposition;
            pendingDownloadMimeType = mimetype;
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    DOWNLOAD_PERMISSION_REQUEST_CODE);
            return;
        }
        startDownload(url, userAgent, contentDisposition, mimetype);
    }

    private void startDownload(String url, String userAgent, String contentDisposition,
                               String mimetype) {
        try {
            Uri downloadUri = Uri.parse(url);
            if (!isWebUrl(url) || downloadUri.getHost() == null) {
                Toast.makeText(this, R.string.browser_download_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            String safeMimeType = mimetype == null || mimetype.trim().length() == 0
                    ? "application/octet-stream" : mimetype;
            String fileName = safeDownloadFileName(
                    URLUtil.guessFileName(url, contentDisposition, safeMimeType));
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.setMimeType(safeMimeType);
            request.setTitle(fileName);
            request.setDescription(url);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (ApktoolPermissions.hasFileAccess(this)) {
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Safe fallback if the user has not granted broad file access.
                request.setDestinationInExternalFilesDir(
                        this, Environment.DIRECTORY_DOWNLOADS, fileName);
            } else {
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(this, R.string.browser_download_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            manager.enqueue(request);

            ArrayList<BrowserRecord> records = readRecords(DOWNLOADS_KEY);
            ArrayList<BrowserRecord> updated = new ArrayList<BrowserRecord>();
            updated.add(new BrowserRecord(
                    fileName, url,
                    getString(R.string.browser_download_file,
                            fileName)));
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
            Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String safeDownloadFileName(String suggestedName) {
        String name = suggestedName == null ? "" : suggestedName.trim();
        if (name.length() == 0) {
            name = "download";
        }
        name = new File(name).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() == 0 || ".".equals(name) || "..".equals(name)) {
            return "download";
        }
        return name.length() > 180 ? name.substring(0, 180) : name;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            return;
        }
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        pendingFileChooserParams = null;
        if (callback == null) {
            return;
        }

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            } else if (data.getData() != null) {
                results = new Uri[] {data.getData()};
            }
            if (results != null) {
                for (Uri result : results) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                result, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                        // Some document providers grant only a temporary read URI.
                    }
                }
            }
        }
        callback.onReceiveValue(results);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                granted &= result == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                showInternalFilePicker();
            } else {
                finishFileSelection(false);
            }
            return;
        }
        if (requestCode == DOWNLOAD_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            String url = pendingDownloadUrl;
            String userAgent = pendingDownloadUserAgent;
            String disposition = pendingDownloadContentDisposition;
            String mimetype = pendingDownloadMimeType;
            pendingDownloadUrl = null;
            pendingDownloadUserAgent = null;
            pendingDownloadContentDisposition = null;
            pendingDownloadMimeType = null;
            if (granted && url != null) {
                startDownload(url, userAgent, disposition, mimetype);
            } else {
                Toast.makeText(this, R.string.browser_download_permission_needed,
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
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
        WebView current = getCurrentWebView();
        if (current != null && current.canGoBack()) {
            current.goBack();
            return;
        }
        if (tabs.size() > 1) {
            removeTab(currentTab);
            saveTabs();
            return;
        }
        returnToMasterHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (BrowserTab tab : tabs) {
            applyBrowserSettings(tab.webView.getSettings());
        }
    }

    @Override
    protected void onPause() {
        saveTabs();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        saveTabs();
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