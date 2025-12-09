package com.sms2vps.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import com.jcraft.jsch.*;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.LoadAdError;

public class VPSStorageActivity extends BaseActivity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private VPSMessageAdapter adapter;
    private final List<SMSMessage> cachedMessages = new ArrayList<>();
    private boolean isLoading = false;
    private boolean isDeletingAll = false;
    private static final String CACHED_MESSAGES_KEY = "vps_cached_messages";
    private ProgressBar loadingProgressBar;
    private TextView filesDetectedTextView;
    private RecyclerView recyclerView;
    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vps_storage);

        // AdMob-bannerin alustus ja lataus
        MobileAds.initialize(this, initializationStatus -> {
            Log.d("VPSStorageActivity", "MobileAds initialized");
            loadAd();
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("VPS Storage");
        }

        recyclerView = findViewById(R.id.vpsMessagesRecyclerView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        filesDetectedTextView = findViewById(R.id.filesDetectedTextView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setClipToPadding(false);
        adapter = new VPSMessageAdapter(this);
        recyclerView.setAdapter(adapter);

        // Sovita listan ala-padding järjestelmäpalkin ja mainosbannerin mukaan
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int adHeight = (adView != null) ? adView.getHeight() : 0;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom + adHeight);
            return insets;
        });

        // Päivitä padding, kun mainos on ladattu ja sen korkeus tiedossa
        adView = findViewById(R.id.adView);
        if (adView != null) {
            adView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int adHeight = adView.getHeight();
                WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(recyclerView);
                Insets systemBars = rootInsets != null
                        ? rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        : Insets.of(0, 0, 0, 0);
                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        systemBars.bottom + adHeight
                );
            });
        }

        // Add swipe-to-delete functionality using the new callback class
        new ItemTouchHelper(new VPSStorageSwipeCallback(adapter, this)).attachToRecyclerView(recyclerView);

        // Lataa välimuistissa olevat viestit
        loadCachedMessages();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vps_storage_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            loadMessagesFromVPS();
            return true;
        } else if (item.getItemId() == R.id.action_delete_all) {
            showDeleteAllConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteAllConfirmationDialog() {
        // Luo custom layout checkboxeilla
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Pääkysymys
        TextView questionText = new TextView(this);
        questionText.setText(R.string.delete_all_messages_question);
        questionText.setTextSize(16);
        questionText.setPadding(0, 0, 0, 30);
        layout.addView(questionText);

        // Checkbox sovelluksen välimuistin poistamiseen
        CheckBox appCacheCheckbox = new CheckBox(this);
        appCacheCheckbox.setText(R.string.delete_app_cache_option);
        appCacheCheckbox.setChecked(true); // Oletuksena valittu
        appCacheCheckbox.setPadding(0, 10, 0, 10);
        layout.addView(appCacheCheckbox);

        // Checkbox VPS-viestien poistamiseen
        CheckBox vpsMessagesCheckbox = new CheckBox(this);
        vpsMessagesCheckbox.setText(R.string.delete_vps_messages_option);
        vpsMessagesCheckbox.setChecked(false); // Oletuksena ei valittu
        vpsMessagesCheckbox.setPadding(0, 10, 0, 20);
        layout.addView(vpsMessagesCheckbox);

        // Varoitusteksti
        TextView warningText = new TextView(this);
        warningText.setText(R.string.delete_action_irreversible);
        warningText.setTextSize(14);
        warningText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        warningText.setPadding(0, 10, 0, 0);
        layout.addView(warningText);

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_messages_title)
                .setView(layout)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    boolean deleteAppCache = appCacheCheckbox.isChecked();
                    boolean deleteVpsMessages = vpsMessagesCheckbox.isChecked();

                    if (deleteAppCache || deleteVpsMessages) {
                        performDeletion(deleteAppCache, deleteVpsMessages);
                    } else {
                        Toast.makeText(this, "No deletion option selected", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDeletion(boolean deleteAppCache, boolean deleteVpsMessages) {
        if (isLoading || isDeletingAll) {
            return;
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean uploadsInProgress = sharedPrefs.getBoolean("uploads_in_progress", false);

        if (uploadsInProgress && deleteVpsMessages) {
            mainHandler.post(() ->
                    Toast.makeText(this, "Cannot delete VPS messages while uploads are in progress", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        isDeletingAll = true;

        // Näytä sopiva viesti sen mukaan, mitä poistetaan
        String progressMessage;
        if (deleteAppCache && deleteVpsMessages) {
            progressMessage = getString(R.string.deleting_vps_and_cache);
        } else if (deleteVpsMessages) {
            progressMessage = getString(R.string.deleting_all_messages);
        } else {
            progressMessage = getString(R.string.deleting_app_cache);
        }

        mainHandler.post(() -> {
            loadingProgressBar.setVisibility(ProgressBar.VISIBLE);
            filesDetectedTextView.setText(progressMessage);
            filesDetectedTextView.setVisibility(TextView.VISIBLE);
            Toast.makeText(this, progressMessage, Toast.LENGTH_SHORT).show();
        });

        // Jos vain sovelluksen välimuisti poistetaan
        if (deleteAppCache && !deleteVpsMessages) {
            clearAppCacheOnly();
            return;
        }

        // Jos VPS-viestit poistetaan (ja mahdollisesti myös välimuisti)
        if (deleteVpsMessages) {
            deleteFromVPS(deleteAppCache);
        }
    }

    private void clearAppCacheOnly() {
        executorService.execute(() -> {
            try {
                // Odota hetki simuloimaan prosessointia
                Thread.sleep(500);

                mainHandler.post(() -> {
                    // Tyhjennä välimuisti
                    cachedMessages.clear();
                    if (adapter != null) {
                        adapter.updateMessages(cachedMessages);
                    }
                    saveMessagesToPersistentCache(cachedMessages);

                    loadingProgressBar.setVisibility(ProgressBar.GONE);
                    filesDetectedTextView.setVisibility(TextView.GONE);
                    Toast.makeText(VPSStorageActivity.this, getString(R.string.app_cache_cleared), Toast.LENGTH_SHORT).show();
                    isDeletingAll = false;
                });
            } catch (InterruptedException e) {
                mainHandler.post(() -> {
                    loadingProgressBar.setVisibility(ProgressBar.GONE);
                    filesDetectedTextView.setVisibility(TextView.GONE);
                    isDeletingAll = false;
                });
            }
        });
    }

    private void deleteFromVPS(boolean alsoClearAppCache) {
        String vpsIp = PreferenceManager.getDefaultSharedPreferences(this).getString("vps_ip", "");
        String username = PreferenceManager.getDefaultSharedPreferences(this).getString("vps_username", "");
        String password = PreferenceManager.getDefaultSharedPreferences(this).getString("vps_password", "");
        String storagePath = PreferenceManager.getDefaultSharedPreferences(this).getString("vps_storage_path", "");
        String port = PreferenceManager.getDefaultSharedPreferences(this).getString("vps_port", "22");

        Log.d("VPSStorageActivity", "Deleting from VPS - IP: " + vpsIp + ", Username: " + username + ", Path: " + storagePath);

        executorService.execute(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, vpsIp, Integer.parseInt(port));
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect();

                try {
                    channel.cd(storagePath);
                    Log.d("VPSStorageActivity", "Connected to VPS and changed directory to: " + storagePath);

                    @SuppressWarnings("unchecked")
                    Vector<ChannelSftp.LsEntry> list = (Vector<ChannelSftp.LsEntry>) channel.ls("*");
                    Log.d("VPSStorageActivity", "Found " + list.size() + " files in directory for deletion");

                    int deletedCount = 0;
                    for (ChannelSftp.LsEntry entry : list) {
                        if (!entry.getAttrs().isDir()) {
                            try {
                                channel.rm(entry.getFilename());
                                deletedCount++;
                                Log.d("VPSStorageActivity", "Deleted file: " + entry.getFilename());
                            } catch (Exception e) {
                                Log.e("VPSStorageActivity", "Error deleting file " + entry.getFilename() + ": " + e.getMessage());
                            }
                        }
                    }

                    Log.d("VPSStorageActivity", "Total files deleted: " + deletedCount);

                    mainHandler.post(() -> {
                        // Tyhjennä välimuisti jos pyydetty
                        if (alsoClearAppCache) {
                            cachedMessages.clear();
                            if (adapter != null) {
                                adapter.updateMessages(cachedMessages);
                            }
                            saveMessagesToPersistentCache(cachedMessages);
                        }

                        loadingProgressBar.setVisibility(ProgressBar.GONE);
                        filesDetectedTextView.setVisibility(TextView.GONE);

                        String successMessage = alsoClearAppCache ?
                                getString(R.string.both_deleted_successfully) :
                                "VPS messages deleted successfully";

                        Toast.makeText(VPSStorageActivity.this, successMessage, Toast.LENGTH_SHORT).show();
                        isDeletingAll = false;
                    });

                } finally {
                    channel.disconnect();
                    session.disconnect();
                }

            } catch (Exception e) {
                Log.e("VPSStorageActivity", "Error in deleteFromVPS: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    loadingProgressBar.setVisibility(ProgressBar.GONE);
                    filesDetectedTextView.setVisibility(TextView.GONE);
                    Toast.makeText(VPSStorageActivity.this,
                            "Error deleting messages from VPS: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    isDeletingAll = false;
                });
            }
        });
    }

    private void loadCachedMessages() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String cached = prefs.getString(CACHED_MESSAGES_KEY, "");
        List<SMSMessage> messages = new ArrayList<>();
        if (!cached.isEmpty()) {
            String[] rows = cached.split("\n");
            for (String row : rows) {
                String[] parts = row.split("\\|", 3);
                if (parts.length == 3) {
                    try {
                        String sender = unescape(parts[0]);
                        long timestamp = Long.parseLong(parts[1]);
                        String message = unescape(parts[2]);
                        messages.add(new SMSMessage(sender, message, timestamp, true, false, false, false, false, false, false));
                    } catch (Exception ignored) {}
                }
            }
        }
        // Järjestetään viestit uusimmasta vanhimpaan
        messages.sort((m1, m2) -> Long.compare(m2.timestamp(), m1.timestamp()));
        cachedMessages.clear();
        cachedMessages.addAll(messages);
        if (adapter != null) {
            adapter.updateMessages(cachedMessages);
            scrollToTop();
        }
    }

    private void scrollToTop() {
        if (recyclerView != null) {
            recyclerView.smoothScrollToPosition(0);
        }
    }

    private void loadMessagesFromVPS() {
        if (isLoading || isDeletingAll) {
            if (isDeletingAll) {
                Toast.makeText(this, "Cannot fetch messages while deletion is in progress", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean uploadsInProgress = sharedPrefs.getBoolean("uploads_in_progress", false);

        if (uploadsInProgress) {
            mainHandler.post(() ->
                    Toast.makeText(this, "Cannot fetch messages while uploads are in progress", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        isLoading = true;
        mainHandler.post(() -> {
            loadingProgressBar.setVisibility(ProgressBar.VISIBLE);
            filesDetectedTextView.setVisibility(TextView.VISIBLE);
            Toast.makeText(this, "Fetching SMS content", Toast.LENGTH_SHORT).show();
        });

        String vpsIp = sharedPrefs.getString("vps_ip", "");
        String username = sharedPrefs.getString("vps_username", "");
        String password = sharedPrefs.getString("vps_password", "");
        String storagePath = sharedPrefs.getString("vps_storage_path", "");
        String port = sharedPrefs.getString("vps_port", "22");

        Log.d("VPSStorageActivity", "VPS Settings - IP: " + vpsIp + ", Username: " + username + ", Path: " + storagePath);

        executorService.execute(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, vpsIp, Integer.parseInt(port));
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect();

                List<SMSMessage> messages = new ArrayList<>();
                try {
                    // Check if SMS folder exists
                    try {
                        channel.cd(storagePath);
                        Log.d("VPSStorageActivity", "SMS folder found: " + storagePath);
                    } catch (SftpException e) {
                        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                            Log.d("VPSStorageActivity", "SMS folder not found: " + storagePath);
                            mainHandler.post(() -> showSmsFolderNotFoundDialog(channel, storagePath));
                            return;
                        } else {
                            throw e;
                        }
                    }
                    
                    Log.d("VPSStorageActivity", "Connected to VPS and changed directory to: " + storagePath);

                    @SuppressWarnings("unchecked")
                    Vector<ChannelSftp.LsEntry> list = (Vector<ChannelSftp.LsEntry>) channel.ls("*");
                    Log.d("VPSStorageActivity", "Found " + list.size() + " files in directory");

                    // Count actual files (not directories)
                    int fileCount = 0;
                    for (ChannelSftp.LsEntry entry : list) {
                        if (!entry.getAttrs().isDir()) {
                            fileCount++;
                        }
                    }

                    // Update the UI with the detected file count
                    final int detectedFiles = fileCount;
                    mainHandler.post(() ->
                            filesDetectedTextView.setText(getResources().getQuantityString(
                                    R.plurals.files_detected, detectedFiles, detectedFiles)));

                    for (ChannelSftp.LsEntry entry : list) {
                        if (!entry.getAttrs().isDir()) {
                            Log.d("VPSStorageActivity", "Processing file: " + entry.getFilename());
                            try {
                                InputStream is = channel.get(entry.getFilename());
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                                StringBuilder content = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    content.append(line).append('\n');
                                }
                                reader.close();
                                is.close();

                                String messageContent = content.toString().trim();
                                Log.d("VPSStorageActivity", "File content: " + messageContent);

                                // Parse the message content
                                String sender = "";
                                long timestamp = 0;
                                String message = "";

                                // Extract sender
                                Pattern senderPattern = Pattern.compile("From: (.*?)(?=\\n|$)");
                                Matcher senderMatcher = senderPattern.matcher(messageContent);
                                if (senderMatcher.find()) {
                                    String matchedSender = senderMatcher.group(1);
                                    sender = matchedSender != null ? matchedSender.trim() : "";
                                }

                                // Extract timestamp
                                Pattern timePattern = Pattern.compile("Time: (.*?)(?=\\n|$)");
                                Matcher timeMatcher = timePattern.matcher(messageContent);
                                if (timeMatcher.find()) {
                                    String timeStr = timeMatcher.group(1);
                                    if (timeStr != null) {
                                        timeStr = timeStr.trim();
                                        try {
                                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                                            Date parsedDate = dateFormat.parse(timeStr);
                                            if (parsedDate != null) {
                                                timestamp = parsedDate.getTime();
                                            }
                                        } catch (Exception e) {
                                            Log.e("VPSStorageActivity", "Error parsing time: " + timeStr);
                                            // Try to get timestamp from filename as fallback
                                            String[] filenameParts = entry.getFilename().split("_");
                                            if (filenameParts.length >= 2) {
                                                try {
                                                    timestamp = Long.parseLong(filenameParts[1].replace(".txt", ""));
                                                } catch (NumberFormatException ex) {
                                                    Log.e("VPSStorageActivity", "Error parsing timestamp from filename: " + entry.getFilename());
                                                }
                                            }
                                        }
                                    }
                                }

                                // Extract message
                                Pattern messagePattern = Pattern.compile("Message: (.*?)(?=\\n|$)", Pattern.DOTALL);
                                Matcher messageMatcher = messagePattern.matcher(messageContent);
                                if (messageMatcher.find()) {
                                    String matchedMessage = messageMatcher.group(1);
                                    message = matchedMessage != null ? matchedMessage.trim() : "";
                                }

                                if (!sender.isEmpty() && timestamp > 0 && !message.isEmpty()) {
                                    messages.add(new SMSMessage(
                                            sender,
                                            message,
                                            timestamp,
                                            true,   // uploadedToVPS = true
                                            false,  // uploadFailed = false
                                            false,  // isRead = false
                                            false,  // xmppSent = false
                                            false,  // xmppFailed = false
                                            false,  // driveSent
                                            false   // driveFailed
                                    ));
                                    Log.d("VPSStorageActivity", "Successfully added message from " + sender);
                                }
                            } catch (Exception e) {
                                Log.e("VPSStorageActivity", "Error reading file " + entry.getFilename() + ": " + e.getMessage());
                            }
                        }
                    }
                } finally {
                    channel.disconnect();
                    session.disconnect();
                }

                // Järjestetään viestit uusimmasta vanhimpaan muokkauspäivän mukaan
                messages.sort((m1, m2) -> Long.compare(m2.timestamp(), m1.timestamp()));

                Log.d("VPSStorageActivity", "Total messages loaded: " + messages.size());
                mainHandler.post(() -> {
                    cachedMessages.clear();
                    cachedMessages.addAll(messages);
                    if (adapter != null) {
                        adapter.updateMessages(cachedMessages);
                        Log.d("VPSStorageActivity", "Messages updated in adapter");
                        scrollToTop();
                    }
                    saveMessagesToPersistentCache(cachedMessages);
                    loadingProgressBar.setVisibility(ProgressBar.GONE);
                    filesDetectedTextView.setVisibility(TextView.GONE);
                    Toast.makeText(VPSStorageActivity.this, "Import complete", Toast.LENGTH_SHORT).show();
                    isLoading = false;
                });

            } catch (Exception e) {
                Log.e("VPSStorageActivity", "Error in loadMessagesFromVPS: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    loadingProgressBar.setVisibility(ProgressBar.GONE);
                    filesDetectedTextView.setVisibility(TextView.GONE);
                    Toast.makeText(VPSStorageActivity.this,
                            "Error loading messages from VPS: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
                isLoading = false;
            }
        });
    }

    // New method to remove message from view and cache only
    public void removeMessageFromView(SMSMessage message) {
        mainHandler.post(() -> {
            cachedMessages.remove(message);
            saveMessagesToPersistentCache(cachedMessages);
        });
    }

    // Tallennetaan viestit pysyvästi SharedPreferencesiin yksinkertaisella serialisoinnilla
    private void saveMessagesToPersistentCache(List<SMSMessage> messages) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        StringBuilder sb = new StringBuilder();
        for (SMSMessage msg : messages) {
            // Yksinkertainen serialisointi: sender|timestamp|message\n
            sb.append(escape(msg.sender()))
                    .append("|")
                    .append(msg.timestamp())
                    .append("|")
                    .append(escape(msg.message()))
                    .append("\n");
        }
        prefs.edit().putString(CACHED_MESSAGES_KEY, sb.toString()).apply();
    }

    // Apumetodit serialisointiin
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
    }

    private void loadAd() {
        adView = findViewById(R.id.adView);
        if (adView != null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    super.onAdLoaded();
                    Log.d("VPSStorageActivity", "Ad loaded successfully");
                }
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    super.onAdFailedToLoad(adError);
                    Log.e("VPSStorageActivity", "Ad failed to load: " + adError.getMessage() + ", Code: " + adError.getCode());
                }
                @Override
                public void onAdOpened() {
                    super.onAdOpened();
                    Log.d("VPSStorageActivity", "Ad opened");
                }
                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    Log.d("VPSStorageActivity", "Ad clicked");
                }
                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                    Log.d("VPSStorageActivity", "Ad closed");
                }
            });
            adView.loadAd(adRequest);
        } else {
            Log.e("VPSStorageActivity", "AdView not found in layout");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adView != null) {
            adView.resume();
        }
        if (adapter != null) {
            adapter.updateFontSize();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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

    private void showSmsFolderNotFoundDialog(ChannelSftp channel, String storagePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SMS Folder Not Found")
               .setMessage("The SMS folder '" + storagePath + "' was not found on the VPS. Would you like to create it?")
               .setPositiveButton("Create Folder", (dialog, which) -> executorService.execute(() -> {
                   try {
                       channel.mkdir(storagePath);
                       Log.d("VPSStorageActivity", "Created SMS folder: " + storagePath);
                       mainHandler.post(() -> {
                           Toast.makeText(this, "SMS folder created successfully", Toast.LENGTH_SHORT).show();
                           // Retry loading messages
                           loadMessagesFromVPS();
                       });
                   } catch (SftpException e) {
                       Log.e("VPSStorageActivity", "Error creating SMS folder", e);
                       mainHandler.post(() -> {
                           Toast.makeText(this, "Error creating SMS folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
                           loadingProgressBar.setVisibility(ProgressBar.GONE);
                           filesDetectedTextView.setVisibility(TextView.GONE);
                           isLoading = false;
                       });
                   }
               }))
               .setNegativeButton("Cancel", (dialog, which) -> {
                   loadingProgressBar.setVisibility(ProgressBar.GONE);
                   filesDetectedTextView.setVisibility(TextView.GONE);
                   isLoading = false;
               })
               .setCancelable(false)
               .show();
    }
}
