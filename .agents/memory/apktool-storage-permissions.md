---
name: Android storage access
description: Android version differences that affect Apktool file browsing and installed-app enumeration.
---

On Android 11 and newer, `WRITE_EXTERNAL_STORAGE` is not the permission that grants broad filesystem access. The Apktool workspace must use the special All files access setting, while older Android versions use the legacy runtime storage permission. Installed-app enumeration also requires explicit package visibility.

**Why:** The old initialization gate checked only `WRITE_EXTERNAL_STORAGE`, so the file drawer never initialized on newer Android versions even when the user expected storage access. Package visibility independently affects the Applications page.

**How to apply:** Centralize the version check, route API 30+ users to the app-specific All files access screen, and declare only the package visibility needed to list installed applications.