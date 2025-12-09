package com.sms2vps.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.os.CancellationSignal;

import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
// Removed unused Google API imports after migrating to Credential Manager
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.LoadAdError;
import java.util.concurrent.Executor;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import androidx.annotation.NonNull;

public class GoogleSettingsActivity extends BaseActivity {
    private static final String TAG = "GoogleSettingsActivity";

    private SwitchCompat driveSwitch;
    private SwitchCompat gmailSwitch;
    private Button signInButton;
    private Button signOutButton;
    private TextView statusText;
    private SharedPreferences sharedPreferences;
    private CredentialManager credentialManager;
    private AdView adView;

    // Credential Manager -kirjautuminen ei käytä ActivityResultLauncheria; käytämme async-callbackia.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_settings);

        // AdMob-bannerin alustus ja lataus
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "MobileAds initialized");
            loadAd();
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Alustetaan Credential Manager Google Sign-Inille
        credentialManager = CredentialManager.create(this);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.google_settings_title);
        }
        // Set navigation icon color to white
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(getResources().getColor(android.R.color.white, getTheme()));
        }
        // Korjataan nuolen toiminta
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(GoogleSettingsActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Initialize views
        driveSwitch = findViewById(R.id.driveSwitch);
        gmailSwitch = findViewById(R.id.gmailSwitch);
        signInButton = findViewById(R.id.signInButton);
        signOutButton = findViewById(R.id.signOutButton);
        statusText = findViewById(R.id.statusText);

        // Load saved preferences
        loadPreferences();

        // Setup click listeners
        signInButton.setOnClickListener(v -> signIn());
        signOutButton.setOnClickListener(v -> signOut());

        // Update UI based on sign-in status
        updateUI();
    }

    private void loadAd() {
        adView = findViewById(R.id.adView);
        if (adView != null) {
            AdRequest adRequest = new AdRequest.Builder()
                    .build();

            // Lisää AdListener debugging-tarkoituksiin
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    super.onAdLoaded();
                    Log.d(TAG, "Ad loaded successfully");
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    super.onAdFailedToLoad(adError);
                    Log.e(TAG, "Ad failed to load: " + adError.getMessage()
                            + ", Code: " + adError.getCode());
                }

                @Override
                public void onAdOpened() {
                    super.onAdOpened();
                    Log.d(TAG, "Ad opened");
                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    Log.d(TAG, "Ad clicked");
                }

                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                    Log.d(TAG, "Ad closed");
                }
            });

            adView.loadAd(adRequest);
        } else {
            Log.e(TAG, "AdView not found in layout");
        }
    }

    private void loadPreferences() {
        driveSwitch.setChecked(sharedPreferences.getBoolean("google_drive_enabled", false));
        gmailSwitch.setChecked(sharedPreferences.getBoolean("google_gmail_enabled", false));
    }

    private void signIn() {
        Log.d(TAG, "Starting Google Sign-In process");
        try {
            String serverClientId = getString(R.string.google_client_id);

            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    // Salli tilin valinta myös ilman aiempaa autorisointia
                    .setFilterByAuthorizedAccounts(false)
                    .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

            Executor executor = ContextCompat.getMainExecutor(this);
            CancellationSignal cancellationSignal = new CancellationSignal();
            credentialManager.getCredentialAsync(
                    this,
                    request,
                    cancellationSignal,
                    executor,
                    new androidx.credentials.CredentialManagerCallback<>() {
                        @Override
                        public void onResult(@NonNull GetCredentialResponse result) {
                            try {
                                Credential credential = result.getCredential();
                                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                                String idToken = googleIdTokenCredential.getIdToken();
                                String accountId = googleIdTokenCredential.getId();
                                String email = extractEmailFromIdToken(idToken);

                                sharedPreferences.edit()
                                        .putBoolean("google_logged_in", true)
                                        .putBoolean("google_online", true)
                                        .putString("google_account_email", email != null ? email : "Unknown")
                                        .putString("google_account_id", accountId)
                                        .apply();

                                updateUI();
                                Intent statusIntent = new Intent("com.example.sms2vps.GOOGLE_STATUS_CHANGED");
                                statusIntent.setPackage(getPackageName());
                                sendBroadcast(statusIntent);
                                Toast.makeText(GoogleSettingsActivity.this, getString(R.string.google_sign_in_success, email != null ? email : "Unknown"), Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing credential", e);
                                Toast.makeText(GoogleSettingsActivity.this, "Sign-in processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            Log.e(TAG, "Sign-in failed via CredentialManager", e);
                            String errorMessage;
                            if (e instanceof androidx.credentials.exceptions.NoCredentialException) {
                                errorMessage = "No Google account or no authorized credentials available.";
                            } else {
                                errorMessage = "Sign in failed";
                            }
                            Toast.makeText(GoogleSettingsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error starting CredentialManager sign-in", e);
            Toast.makeText(this, "Error starting sign-in: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Credential Manager hoitaa sign-in -palautteen suoraan signIn()-metodissa.

    private String extractEmailFromIdToken(String idToken) {
        if (idToken == null) return null;
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) return null;
            String payload = parts[1]
                    .replace('-', '+')
                    .replace('_', '/');
            int pad = (4 - (payload.length() % 4)) % 4;
            StringBuilder sb = new StringBuilder(payload);
            for (int i = 0; i < pad; i++) sb.append('=');
            byte[] decoded = android.util.Base64.decode(sb.toString(), android.util.Base64.DEFAULT);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            String email = obj.optString("email", "");
            if (email.isEmpty()) {
                // Some tokens may store email under different claim names
                email = obj.optString("upn", "");
            }
            return email.isEmpty() ? null : email;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse email from ID token", e);
            return null;
        }
    }

    private void signOut() {
        sharedPreferences.edit()
                .putBoolean("google_logged_in", false)
                .putBoolean("google_online", false)
                .remove("google_account_email")
                .remove("google_account_id")
                .apply();
        updateUI();
        Intent statusIntent = new Intent("com.example.sms2vps.GOOGLE_STATUS_CHANGED");
        statusIntent.setPackage(getPackageName());
        sendBroadcast(statusIntent);
        Toast.makeText(this, R.string.google_sign_out_success, Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        boolean isSignedIn = sharedPreferences.getBoolean("google_logged_in", false);

        if (isSignedIn) {
            String email = sharedPreferences.getString("google_account_email", "Unknown");
            statusText.setText(getString(R.string.google_logged_in_online, email));
            signInButton.setVisibility(View.GONE);
            signOutButton.setVisibility(View.VISIBLE);
            driveSwitch.setEnabled(true);
            gmailSwitch.setEnabled(true);
        } else {
            statusText.setText(R.string.google_not_logged_in);
            signInButton.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.GONE);
            driveSwitch.setEnabled(false);
            gmailSwitch.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFontSize();
        if (adView != null) {
            adView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save preferences
        sharedPreferences.edit()
                .putBoolean("google_drive_enabled", driveSwitch.isChecked())
                .putBoolean("google_gmail_enabled", gmailSwitch.isChecked())
                .apply();

        if (adView != null) {
            adView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (adView != null) {
            adView.destroy();
        }
        super.onDestroy();
    }
}