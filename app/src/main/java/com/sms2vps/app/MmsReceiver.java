package com.sms2vps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * MMS-viestien vastaanotin. Android vaatii oletustekstiviestisovelluksilta MMS-receiverin.
 */
public class MmsReceiver extends BroadcastReceiver {
    private static final String TAG = "MmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "MMS action received: " + (intent != null ? intent.getAction() : "null"));

        // Tässä voidaan lisätä MMS-viestien käsittely jos tarvitaan
        // Tämä simppeli toteutus riittää oletustekstiviestisovellukseksi rekisteröitymiseen
    }
}