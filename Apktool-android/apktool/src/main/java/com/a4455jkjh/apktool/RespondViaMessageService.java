package com.a4455jkjh.apktool;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.SmsManager;

/**
 * Required by Android's default SMS role for quick replies from the dialer,
 * assistant, and other system surfaces.
 */
public class RespondViaMessageService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null
                && checkSelfPermission(android.Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            Uri data = intent.getData();
            String address = data == null ? null : data.getSchemeSpecificPart();
            String body = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (address != null && address.length() > 0
                    && body != null && body.trim().length() > 0) {
                try {
                    SmsManager.getDefault().sendTextMessage(
                            address, null, body, null, null);
                } catch (Exception ignored) {
                    // The caller owns the reply UI; do not keep the service alive
                    // if the telephony provider rejects the send.
                }
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}