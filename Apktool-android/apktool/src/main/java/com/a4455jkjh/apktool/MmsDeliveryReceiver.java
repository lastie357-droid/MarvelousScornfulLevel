package com.a4455jkjh.apktool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives MMS delivery broadcasts while Master App holds the SMS role. */
public class MmsDeliveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // MMS storage remains managed by Android's telephony provider.
    }
}