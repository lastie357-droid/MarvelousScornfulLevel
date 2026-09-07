---
name: SMS role requirements
description: Android platform limits for Master App's built-in SMS/MMS Messenger.
---

Master App's Messenger must request SMS permissions for inbox access and sending, but Android may require Master App to hold the default SMS role for deletion and complete delivery handling.

**Why:** SMS provider writes and delivery broadcasts are protected more strictly than ordinary message reads, and the role is user-controlled by Android.

**How to apply:** Keep permission prompts and the default-SMS role request explicit. Treat deletion, MMS parts, and incoming-message handling as device-tested capabilities rather than assuming READ_SMS alone is sufficient.