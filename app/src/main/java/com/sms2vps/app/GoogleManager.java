package com.sms2vps.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.util.Base64;

@SuppressWarnings("deprecation")
public class GoogleManager {
    private static final String TAG = "GoogleManager";
    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final ExecutorService executorService;
    private final SimpleDateFormat dateFormat;
    private final DatabaseHelper dbHelper;

    private static final List<String> GOOGLE_SCOPES = Arrays.asList(
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/gmail.send"
    );

    public GoogleManager(Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        this.dbHelper = new DatabaseHelper(context);
    }

    private GoogleAccountCredential getCredential() {
        String email = sharedPreferences.getString("google_account_email", null);
        if (email == null || email.isEmpty()) return null;
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
            context, GOOGLE_SCOPES
        );
        credential.setSelectedAccountName(email);
        return credential;
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean("google_logged_in", false);
    }

    public boolean isDriveEnabled() {
        return sharedPreferences.getBoolean("google_drive_enabled", false);
    }

    public boolean isGmailEnabled() {
        return sharedPreferences.getBoolean("google_gmail_enabled", false);
    }

    public boolean forwardSms(String sender, String message, long timestamp) {
        if (!isLoggedIn()) {
            Log.d(TAG, "Google not logged in, skipping forwarding");
            return false;
        }

        executorService.execute(() -> {
            boolean driveSuccess = false;
            boolean gmailSuccess = false;

            if (isDriveEnabled()) {
                driveSuccess = forwardSmsToDrive(sender, message, timestamp);
            }

            if (isGmailEnabled()) {
                gmailSuccess = forwardSmsToGmail(sender, message, timestamp);
            }

            // Tulos käsitellään asynkronisesti, joten emme tarvitse result-taulukkoa
            Log.d(TAG, "Google forwarding completed. Drive: " + driveSuccess + ", Gmail: " + gmailSuccess);
        });
        // Always return true for now, as the result is async. Consider using a callback for real result.
        return true;
    }

    private boolean forwardSmsToDrive(String sender, String message, long timestamp) {
        try {
            GoogleAccountCredential credential = getCredential();
            if (credential == null) {
                Log.e(TAG, "No Google account available");
                return false;
            }

            Drive driveService = new Drive.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName("SMS2VPS")
                    .build();

            String formattedTime = dateFormat.format(new Date(timestamp));
            String content = "From: " + sender + "\nTime: " + formattedTime + "\nMessage: " + message;
            String fileName = "sms_" + timestamp + ".txt";

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setMimeType("text/plain");

            com.google.api.client.http.ByteArrayContent mediaContent = 
                new com.google.api.client.http.ByteArrayContent("text/plain", content.getBytes(StandardCharsets.UTF_8));

            File file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            Log.i(TAG, "SMS forwarded to Google Drive: " + file.getId());
            dbHelper.updateDriveUploadStatus(sender, timestamp, true, false);
            Intent intent = new Intent("com.example.sms2vps.DRIVE_UPLOAD_SUCCESS");
            intent.putExtra("driveUploadSuccess", true);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error forwarding SMS to Google Drive", e);
            dbHelper.updateDriveUploadStatus(sender, timestamp, false, true);
            Intent intent = new Intent("com.example.sms2vps.DRIVE_UPLOAD_SUCCESS");
            intent.putExtra("driveUploadSuccess", false);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
            return false;
        }
    }

    private boolean forwardSmsToGmail(String sender, String message, long timestamp) {
        try {
            GoogleAccountCredential credential = getCredential();
            if (credential == null) {
                Log.e(TAG, "No Google account available");
                return false;
            }

            Gmail gmailService = new Gmail.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName("SMS2VPS")
                    .build();

            String formattedTime = dateFormat.format(new Date(timestamp));
            String subject = "SMS from " + sender + " - " + formattedTime;
            String body = "From: " + sender + "\nTime: " + formattedTime + "\nMessage: " + message;

            // Create a simple MIME message manually
            String mimeMessage = "From: " + credential.getSelectedAccountName() + "\r\n" +
                               "To: " + credential.getSelectedAccountName() + "\r\n" +
                               "Subject: " + subject + "\r\n" +
                               "MIME-Version: 1.0\r\n" +
                               "Content-Type: text/plain; charset=UTF-8\r\n" +
                               "\r\n" +
                               body;

            // Encode the message using Android Base64
            String encodedEmail = Base64.encodeToString(mimeMessage.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            Message gmailMessage = new Message();
            gmailMessage.setRaw(encodedEmail);

            gmailService.users().messages().send("me", gmailMessage).execute();

            Log.i(TAG, "SMS forwarded to Gmail successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error forwarding SMS to Gmail", e);
            return false;
        }
    }

}