package com.sms2vps.app;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/* LocalBroadcastManager is deprecated; use Context.sendBroadcast with dynamic receivers */

public class SmsSynchronizationService extends Service {
    private static final String TAG = "SmsForwardingService";
    private static final String CHANNEL_ID = "sms_forwarding_channel";
    private static final int NOTIFICATION_ID = 1;
    private ExecutorService executorService;
    private SimpleDateFormat dateFormat;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        executorService = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private Notification createNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Luodaan NotificationChannel vain API 26 ja uudemmille versioille
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "SMS Synchronization Service",
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
            }
        }

        // Käytetään NotificationCompat.Builder-luokkaa taaksepäin yhteensopivuuden vuoksi
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Synchronization Service")
                .setContentText("Service is running...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "SmsSyncService started");
    
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());

        if (intent != null) {
            final String sender = intent.getStringExtra("sender");
            final String message = intent.getStringExtra("message");
            final long timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis());

            executorService.execute(() -> {
                try {
                    // Tallennetaan viesti ja näytetään ilmoitus aina, kun viesti saapuu.
                    saveMessageToAppDatabase(this, sender, message, timestamp);
                    showNotification(this, sender, message);

                    // Tarkistetaan, pitääkö viesti myös välittää eteenpäin.
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    boolean forwardingEnabled = prefs.getBoolean("enable_sync", false);

                    if (forwardingEnabled) {
                        Log.i(TAG, "Välitys on käytössä. Jatketaan välitystä.");
                        // VPS:llä on suurin prioriteetti
                        SyncSmsToVps(sender, message, timestamp);
                        
                        // Google forwarding on toissijainen
                        forwardSmsToGoogle(sender, message, timestamp);
                    } else {
                        Log.i(TAG, "Ohitetaan välitys: synkronointi pois päältä tai sovellus ei ole oletussovellus.");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "An error occurred during SMS processing thread.", e);
                } finally {
                    Log.i(TAG, "All synchronization tasks are complete. Stopping service.");
                    stopForeground(true);
                    stopSelf();
                }
            });
        } else {
            Log.w(TAG, "Intent is null, stopping service.");
            stopForeground(true);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Palvelu ei tue sitomista
        return null;
    }

    private void SyncSmsToVps(String sender, String message, long timestamp) {
        Log.i(TAG, "Sync SMS to VPS: " + sender + " - " + message);

        // Haetaan käyttäjän määrittelemät asetukset
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String vpsIpAddress = prefs.getString("vps_ip", "");
        String vpsUsername = prefs.getString("vps_username", "");
        String vpsPassword = prefs.getString("vps_password", "");
        String vpsDirectory = prefs.getString("vps_storage_path", "/root/sms"); // Lisätään tämä rivi
        boolean forwardingEnabled = prefs.getBoolean("enable_sync", false);

        if (!forwardingEnabled) {
            Log.i(TAG, "SMS sync is disabled in settings");
            return;
        }

        if (vpsIpAddress.isEmpty() || vpsUsername.isEmpty() || vpsPassword.isEmpty()) {
            Log.e(TAG, "VPS settings are not configured");
            return;
        }

        if (sender == null || message == null || message.isEmpty()) {
            Log.e(TAG, "Invalid SMS data, cannot sync.");
            return;
        }

        if (!testVpsConnection(vpsIpAddress, vpsUsername, vpsPassword)) {
            Log.e(TAG, "VPS is unreachable. SMS synchronization aborted.");
            return;
        }

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(vpsUsername, vpsIpAddress, getVpsPort());
            session.setPassword(vpsPassword);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(5000);

            if (!session.isConnected()) {
                Log.e(TAG, "The SSH connection failed. Unable to send an SMS to the VPS.");
                return;
            }

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            ensureVpsDirectoryExists(channel, vpsDirectory);  // Korjattu metodin kutsu

            String fileName = "sms_" + timestamp + ".txt";
            String filePath = vpsDirectory + "/" + fileName;

            String formattedTime = dateFormat.format(new Date(timestamp));
            String content = "From: " + sender + "\nTime: " + formattedTime + "\nMessage: " + message;
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());

            try {
                Log.i(TAG, "Attempting to sync to: " + filePath);
                channel.put(inputStream, filePath);
                Log.i(TAG, "SMS synchronized successfully to VPS: " + filePath);
                // Päivitetään onnistunut lataus tietokantaan
                try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                    String normalizedSender = DatabaseHelper.normalizePhoneNumber(sender);
                    dbHelper.updateVpsUploadStatus(normalizedSender, timestamp, true, false);
                    Intent vpsStatusIntent = new Intent("VPS_STATUS_UPDATED");
                    vpsStatusIntent.putExtra("phoneNumber", normalizedSender);
                    vpsStatusIntent.putExtra("timestamp", timestamp);
                    vpsStatusIntent.setPackage(getPackageName());
                    sendBroadcast(vpsStatusIntent);
                }
                // Lähetetään broadcast UI:n päivitystä varten
                Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
                updateIntent.setPackage(getPackageName());
                sendBroadcast(updateIntent);
            } catch (SftpException e) {
                Log.e(TAG, "SFTP upload failed.", e);
                // Päivitetään epäonnistunut lataus tietokantaan
                try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                    String normalizedSender = DatabaseHelper.normalizePhoneNumber(sender);
                    dbHelper.updateVpsUploadStatus(normalizedSender, timestamp, false, true);
                    Intent vpsStatusIntentFail = new Intent("VPS_STATUS_UPDATED");
                    vpsStatusIntentFail.putExtra("phoneNumber", normalizedSender);
                    vpsStatusIntentFail.putExtra("timestamp", timestamp);
                    vpsStatusIntentFail.setPackage(getPackageName());
                    sendBroadcast(vpsStatusIntentFail);
                }
                // Lähetetään broadcast UI:n päivitystä varten
                Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
                updateIntent.setPackage(getPackageName());
                sendBroadcast(updateIntent);
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error sync SMS to VPS", e);
            // Päivitetään epäonnistunut lataus tietokantaan
            try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                String normalizedSender = DatabaseHelper.normalizePhoneNumber(sender);
                dbHelper.updateVpsUploadStatus(normalizedSender, timestamp, false, true);
            }
        }
    }

    private boolean testVpsConnection(String vpsAddress, String username, String password) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, vpsAddress, getVpsPort());
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
    
            try {
                Log.d(TAG, "Testing SSH connection to " + vpsAddress);
                session.connect(5000); // 5 sekunnin timeout
                boolean isConnected = session.isConnected();
                session.disconnect();
                Log.d(TAG, "SSH connection test result: " + isConnected);
                return isConnected;
            } catch (Exception e) {
                Log.e(TAG, "SSH connection failed during test", e);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection test failed due to JSch setup error", e);
            return false;
        }
    }

    private void ensureVpsDirectoryExists(ChannelSftp channelSftp, String directory) {
        try {
            channelSftp.cd(directory);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                try {
                    channelSftp.mkdir(directory);
                    Log.i(TAG, "Created directory: " + directory);
                    channelSftp.cd(directory);
                } catch (SftpException mkdirException) {
                    Log.e(TAG, "Failed to create or access directory: " + directory, mkdirException);
                }
            }
        }
    }

    private int getVpsPort() {
        String portStr = preferences.getString("vps_port", "22");
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return 22; // jos portin muunnos epäonnistuu, käytetään oletusporttia
        }
    }

    private void saveMessageToAppDatabase(Context context, String sender, String messageBody, long timestamp) {
        // Käytetään try-with-resources, jotta DatabaseHelper suljetaan automaattisesti
        try (DatabaseHelper dbHelper = new DatabaseHelper(context)) {
            dbHelper.insertMessage(sender, messageBody, timestamp);
            Log.d(TAG, "saveMessageToAppDatabase: saved SMS from " + sender + " at " + timestamp);

            // Lähetetään broadcast UI:n päivitystä varten, kun viesti on tallennettu
            Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
            updateIntent.putExtra("phone_number", sender);
            updateIntent.putExtra("message", messageBody);
            updateIntent.putExtra("timestamp", timestamp);
            updateIntent.setPackage(getPackageName());
            context.sendBroadcast(updateIntent);
            Log.d(TAG, "Sent broadcast to update UI after saving message.");

        } catch (Exception e) {
            Log.e(TAG, "Error saving message to database", e);
        }
    }

    private static int notificationId = 0;

    private void showNotification(Context context, String sender, String messageBody) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.e(TAG, "NotificationManager is null");
            return;
        }

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (soundUri == null) {
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            Log.d(TAG, "Using ringtone as fallback sound");
        }

        // Luo PendingIntent, joka avaa MainActivityn
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder;

        String channelId = "sms_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Creating an existing notification channel with its original values performs
            // no operation, so it's safe to call this code repeatedly.
            NotificationChannel channel = new NotificationChannel(
                    channelId, "SMS Channel", NotificationManager.IMPORTANCE_HIGH);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.enableLights(true);

            nm.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created/updated with sound: " + soundUri);

            builder = new NotificationCompat.Builder(context, channelId);
        } else {
            // channelId is not used for older versions
            builder = new NotificationCompat.Builder(context, channelId);
            builder.setSound(soundUri)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(new long[]{0, 500, 200, 500});
        }

        builder.setContentTitle("New SMS: " + sender)
                .setContentText(messageBody)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        Notification notification = builder.build();

        // Use an incrementing notification ID instead of timestamp
        nm.notify(notificationId++, notification);
        Log.d(TAG, "Notification sent with sound: " + soundUri);
    }

    private void forwardSmsToGoogle(String sender, String message, long timestamp) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean googleEnabled = prefs.getBoolean("google_logged_in", false);
        
        if (!googleEnabled) {
            Log.i(TAG, "Google forwarding not configured or enabled");
            return;
        }
        
        if (sender == null || message == null || message.isEmpty()) {
            Log.e(TAG, "Invalid SMS data, cannot forward via Google.");
            return;
        }
        
        try {
            GoogleManager googleManager = new GoogleManager(this);
            boolean success = googleManager.forwardSms(sender, message, timestamp);
            
            if (success) {
                Log.i(TAG, "SMS forwarded to Google successfully");
                // Update database with Google upload status
                try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                    dbHelper.updateGoogleUploadStatus(sender, timestamp, true, false);
                }
                // Send broadcast for UI update
                Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
                sendBroadcast(updateIntent);
            } else {
                Log.w(TAG, "Google forwarding failed");
                // Update database with failed Google upload status
                try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                    dbHelper.updateGoogleUploadStatus(sender, timestamp, false, true);
                }
                // Send broadcast for UI update
                Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
                sendBroadcast(updateIntent);
            }
            
            return;
        } catch (Exception e) {
            Log.e(TAG, "Error in Google forwarding", e);
            // Update database with failed Google upload status
            try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
                dbHelper.updateGoogleUploadStatus(sender, timestamp, false, true);
            }
            // Send broadcast for UI update
            Intent updateIntent = new Intent("com.example.sms2vps.UPDATE_CONVERSATIONS");
            sendBroadcast(updateIntent);
            return;
        }
    }
}
