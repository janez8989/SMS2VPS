package com.sms2vps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SmsMmsDeliveredReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsMmsDeliveredReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "SMS_DELIVERED action received");
        // Tässä voisi käsitellä toimitetut viestit tarvittaessa
    }
}