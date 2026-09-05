---
name: Browser password autofill
description: Constraint and implementation choice for saved-password behavior in the embedded browser.
---

Master App must use Android's Autofill Framework for saved-password suggestions inside its WebView. A Google account sign-in inside the app does not grant access to Chrome's synced password vault, and Google does not expose that vault through a public API for third-party browsers.

**Why:** The requested Chrome-like password sync cannot be implemented by importing Chrome credentials without violating platform boundaries. Android Autofill lets the user's configured provider, such as Google Password Manager, offer credentials while keeping them outside Master App.

**How to apply:** Keep WebView eligible for Autofill, provide a route to Android Autofill settings, and do not add app-side password capture, storage, or claims that Gmail sign-in imports Chrome passwords.