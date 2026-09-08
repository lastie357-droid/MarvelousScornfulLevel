---
name: Third-party app hosting from a launcher
description: Android task and process boundary for launching arbitrary installed apps.
---

A normal Android launcher can start an exported third-party launcher activity and remain the caller/return surface, but it cannot host that activity inside the launcher's own activity, process, or view hierarchy.

**Why:** Each installed app owns its process and window/task lifecycle. A launcher does not have a supported API to re-parent arbitrary external activities or catch every external crash.

**How to apply:** Use the target app's exported MAIN/LAUNCHER activity, avoid forcing a new task when a return-to-caller flow is wanted, and let Android return to the launcher after finish or process death. Only use a real embedded surface when the content is designed for embedding, such as a WebView page or an app-specific SDK.