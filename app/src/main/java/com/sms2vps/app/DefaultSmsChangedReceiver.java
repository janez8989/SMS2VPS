package com.sms2vps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

public class DefaultSmsChangedReceiver extends BroadcastReceiver {
    private static final String TAG = "DefaultSmsChangedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action.");
            return;
        }

        // Korjaus: tämä on tapahtuma, jota tulee kuunnella
        if ("android.provider.Telephony.SMS_DEFAULT_PACKAGE_CHANGED".equals(intent.getAction())) {
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context);
            String myPackageName = context.getPackageName();

            Log.d(TAG, "Default SMS app changed. Current default: " + defaultSmsApp);
            Log.d(TAG, "My package name: " + myPackageName);

            if (myPackageName.equals(defaultSmsApp)) {
                Log.i(TAG, "This app is now the default SMS app!");
                Toast.makeText(context, "Nyt oletus-SMS-sovellus", Toast.LENGTH_SHORT).show();

                // Ilmoita MainActivity:lle
                Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
                updateIntent.setPackage(context.getPackageName());
                context.sendBroadcast(updateIntent);
            } else {
                Log.d(TAG, "This app is NOT the default SMS app.");
            }
        } else {
            Log.w(TAG, "Unknown intent action received: " + intent.getAction());
        }
    }
}