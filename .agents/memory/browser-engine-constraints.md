---
name: Embedded browser engine
description: Browser engine boundary and feature strategy for Master App.
---

Master App uses an embedded GeckoView engine rather than Android System WebView. GeckoView supplies the independent browser runtime; the app supplies permissions, navigation, file handling, tabs, and policy features around it.

**Why:** The product requirement is a self-contained browser: identity-provider pages and ordinary links must remain inside Master App instead of being handed to Chrome, Firefox, or another installed browser.

**How to apply:** Prefer GeckoSession/GeckoView APIs and explicit user controls. Keep GeckoView version, Android minimum SDK, native libraries, and APK-size impact in mind for future browser changes.

The browser's website file-upload flow should remain inside Master App: use GeckoSession's file prompt and return selected URIs to GeckoView.

**Why:** The product requirement is that upload selection does not launch another browser; GeckoView exposes a prompt callback instead of WebView's file chooser callback.

**How to apply:** Preserve MIME filtering and single/multiple selection when extending the GeckoView document-picker flow.

Identity providers may reject embedded WebViews even when their user-agent is changed to resemble Chrome. Master App now uses GeckoView as its browser surface, so secure account flows stay in-app without external-browser routing.

**Why:** Google and similar providers use embedded-browser detection beyond the user-agent; GeckoView provides a real independent browser engine while preserving the product's self-contained behavior.

**How to apply:** Keep ordinary pages and identity-provider pages in GeckoView. Never restore the old Custom Tab or ACTION_VIEW sign-in fallback unless the product requirement explicitly changes.