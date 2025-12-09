package com.sms2vps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null or invalid intent.");
            return;
        }

        String action = intent.getAction();
        // Käsitellään vain SMS_RECEIVED_ACTION, ei molempia
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            Log.w(TAG, "Unexpected intent action: " + action);
            return;
        }

        Log.d(TAG, "SMS action received: " + action);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Google-palvelut toimivat itsenäisesti riippumatta VPS-synkronoinnin tilasta
        // Tarkista Google Settings -näkymästä asetukset
        boolean googleServicesEnabled = prefs.getBoolean("google_drive_enabled", false) ||
                prefs.getBoolean("google_gmail_enabled", false);
        
        Log.d(TAG, "Google services enabled: " + googleServicesEnabled);

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            Log.w(TAG, "No SMS messages found in intent.");
            showToast(context, "No SMS message found");
            return;
        }

        String sender = getSenderFromMessages(messages);
        String messageBody = getMessageBodyFromMessages(messages);
        long timestamp = messages[0].getTimestampMillis();

        if (sender == null || messageBody.isEmpty()) {
            Log.w(TAG, "Could not extract sender or message body.");
            showToast(context, "Failed to parse SMS");
            return;
        }

        Log.d(TAG, "Processing SMS from: " + sender + ", Message: " + messageBody + ", Timestamp: " + timestamp);

        // Tallennetaan viesti tietokantaan
        // saveMessageToAppDatabase(context, sender, messageBody, timestamp);



        // Näytetään ilmoitus (sisältää äänen)
        // showNotification(context, sender, messageBody);

        // Käynnistetään välityspalvelu käsittelemään saapunut viesti


        Intent forwardIntent = new Intent(context, SmsSynchronizationService.class);
        forwardIntent.putExtra("sender", sender);
        forwardIntent.putExtra("message", messageBody);
        forwardIntent.putExtra("timestamp", timestamp);

        try {
            if (context.getPackageManager().resolveService(forwardIntent, 0) != null) {
                Log.d(TAG, "Starting SmsSynchronizationService...");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(forwardIntent);
                    Log.d(TAG, "Started SmsSynchronizationService in foreground mode");
                } else {
                    context.startService(forwardIntent);
                    Log.d(TAG, "Started SmsSynchronizationService in normal mode");
                }
            } else {
                Log.e(TAG, "SmsSynchronizationService not found in package manager");
                showToast(context, "Error: Sync service not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SmsSynchronizationService", e);
            showToast(context, "Error: Could not start SmsSynchronizationService");
        }
    }

    private void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private String getSenderFromMessages(SmsMessage[] messages) {
        return (messages.length == 0) ? null : messages[0].getDisplayOriginatingAddress();
    }

    private String getMessageBodyFromMessages(SmsMessage[] messages) {
        if (messages.length == 0) return "";
        StringBuilder messageBody = new StringBuilder();
        for (SmsMessage message : messages) {
            if (message != null) {
                messageBody.append(message.getMessageBody());
            }
        }
        return messageBody.toString();
    }
}