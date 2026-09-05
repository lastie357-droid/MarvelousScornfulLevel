---
name: Embedded browser engine
description: Browser engine boundary and feature strategy for Master App.
---

Master App intentionally uses Android System WebView rather than bundling a second Chromium engine. JavaScript, DOM storage, media, and WebRTC capabilities come from the device's WebView provider; the app supplies permissions, navigation, file handling, tabs, and policy features around it.

**Why:** A larger APK does not create a better browser. Bundling or replacing the engine is a major architectural change, while duplicating the system engine increases size and maintenance without adding capability.

**How to apply:** Prefer WebView integrations and explicit user controls. If a full independent engine is ever required, treat GeckoView/Chromium migration as a separate major architecture project with its own compatibility and licensing review.

The browser's website file-upload flow should remain inside Master App: use an in-app directory picker and return local file URIs to WebView instead of launching an external document picker.

**Why:** The product requirement is that upload and drag/drop selection do not leave the browser surface, while WebView still needs a normal file callback.

**How to apply:** Keep storage permission handling and the internal picker around WebChromeClient file chooser callbacks; preserve MIME/extension filtering and single/multiple selection.