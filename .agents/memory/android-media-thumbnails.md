---
name: Android media thumbnails
description: Compatibility guidance for rendering MediaStore image and video previews.
---

Use the MediaStore image/video Thumbnails.getThumbnail API for gallery previews when supporting the app's current Android SDK. Do not assume the cursor-based queryMiniThumbnail helper exists for every media collection.

**Why:** The current SDK exposes different thumbnail helpers for image and video collections, and attempting to share the cursor helper across both caused the Android build to fail.

**How to apply:** Keep thumbnail loading behind a small helper and retain a placeholder image when a provider cannot generate a thumbnail.