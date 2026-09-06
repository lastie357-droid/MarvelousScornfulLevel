---
name: Embedded browser engine
description: Browser engine boundary and feature strategy for Master App.
---

Master App uses Android System WebView as its browser engine. The device's WebView provider supplies the Chromium runtime; the app supplies permissions, navigation, file handling, tabs, downloads, and policy features around it.

**Why:** The product requirement is an in-app browser surface with a dependable APK size and no bundled third-party browser engine.

**How to apply:** Prefer WebView/WebSettings/WebViewClient/WebChromeClient APIs and explicit user controls. Keep navigation, tabs, downloads, permissions, and file uploads inside BrowserActivity.

The browser's website file-upload flow should remain inside Master App: use WebChromeClient's file chooser and return selected local URIs to WebView.

**Why:** The product requirement is that upload selection does not launch another browser, while WebView provides a normal file callback.

**How to apply:** Preserve the internal directory picker, MIME/extension filtering, and single/multiple selection around WebChromeClient callbacks.

Identity providers may reject embedded WebViews even when their user-agent is changed to resemble Chrome. Master App intentionally keeps sign-in pages in its WebView because external-browser routing is not acceptable for this product.

**Why:** The product prioritizes a self-contained browser surface over provider-specific embedded-login restrictions.

**How to apply:** Keep ordinary pages and identity-provider pages in WebView. Do not restore Custom Tab or ACTION_VIEW sign-in routing unless the product requirement explicitly changes.