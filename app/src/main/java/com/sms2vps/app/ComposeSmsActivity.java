package com.sms2vps.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Tämä aktiviteetti käsittelee sms:// ja smsto:// URI skeemojen avauksia.
 * Tämä on pakollinen oletustekstiviestisovellukselle.
 */
public class ComposeSmsActivity extends BaseActivity {
    private static final String TAG = "ComposeSmsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        
        Log.d(TAG, "Intent received: " + action + ", data: " + data);
        
        if (data != null) {
            String recipient = data.getSchemeSpecificPart();
            if (recipient.startsWith("//")) {
                recipient = recipient.substring(2);
            }
            
            // Avaa NewConversationActivity tai ConversationActivity määritellyn vastaanottajan kanssa
            Intent newIntent = new Intent(this, NewConversationActivity.class);
            newIntent.putExtra("recipient", recipient);
            startActivity(newIntent);
        } else {
            // Jos ei ole vastaanottajaa, avaa tavallinen NewConversationActivity
            Intent newIntent = new Intent(this, NewConversationActivity.class);
            startActivity(newIntent);
        }
        
        // Suljetaan tämä aktiviteetti, koska se on vain välittäjä
        finish();
    }
}