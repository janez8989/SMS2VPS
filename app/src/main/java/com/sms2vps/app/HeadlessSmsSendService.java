package com.sms2vps.app;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // **KÄYTÖSSÄ**
import android.Manifest;

public class HeadlessSmsSendService extends Service {
    private static final String TAG = "HeadlessSmsSendService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        // **Tarkistetaan lupan saamisen ennen toimintojen suorittamista**
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted. Cannot send SMS.");
            return START_NOT_STICKY;
        }

        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String message = extras.getString("message");
                String recipient = extras.getString("recipient");

                if (message != null && recipient != null) {
                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(recipient, null, message, null, null);
                        Log.d(TAG, "SMS sent successfully to " + recipient);
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending SMS", e);
                        handleSendErrorException(e);
                    }
                } else {
                    Log.w(TAG, "Missing message or recipient in intent extras.");
                }
            } else {
                Log.w(TAG, "Intent extras are null.");
            }
        } else {
            Log.w(TAG, "Received null intent.");
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleSendErrorException(Exception e) {
        Log.e(TAG, "SMS Sending Error", e);
        // Lisää tämän funktioon sovelluksesi vianhallintastrategia
    }
}
