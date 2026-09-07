package com.a4455jkjh.apktool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Declares Master App's SMS delivery endpoint for Android's default SMS role.
 * Android's SMS provider persists delivered messages; the inbox reads them
 * through the provider after the user grants access.
 */
public class SmsDeliveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // The system SMS provider owns persistence. Keep this receiver short so
        // delivery is acknowledged without starting an external application.
    }
}