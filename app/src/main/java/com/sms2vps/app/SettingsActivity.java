package com.sms2vps.app;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.widget.ImageView;
import android.net.Uri;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SettingsActivity extends BaseActivity {
    private EditText vpsIpAddressEdit;
    private EditText vpsUsernameEdit;
    private EditText vpsStoragePathEdit;
    private EditText vpsPasswordEdit;
    private EditText vpsPortEdit;
    private SwitchCompat enableSyncSwitch;
    private SharedPreferences preferences;
    private View connectionStatusIndicator;
    private Button pingButton;
    private TextView pingResultText;
    private TextView sshStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize preferences with default values if not set
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains("enable_sync")) {
            editor.putBoolean("enable_sync", false);
        }
        // Mirror flag used by UI adapters for icon visibility
        if (!preferences.contains("enable_forwarding")) {
            editor.putBoolean("enable_forwarding", false);
        }
        if (!preferences.contains("vps_settings_saved")) {
            editor.putBoolean("vps_settings_saved", false);
        }
        editor.apply();

        setupViews();
        loadSettings();
    }

    private void setupViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.vps_settings);
        }

        vpsIpAddressEdit = findViewById(R.id.vpsIpAddressEdit);
        vpsUsernameEdit = findViewById(R.id.vpsUsernameEdit);
        vpsStoragePathEdit = findViewById(R.id.vpsStoragePathEdit);
        vpsPasswordEdit = findViewById(R.id.vpsPasswordEdit);
        vpsPortEdit = findViewById(R.id.vpsPortEdit);
        enableSyncSwitch = findViewById(R.id.enableSyncSwitch);
        enableSyncSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Tarkista että VPS-asetukset ovat täydelliset ennen kuin sallitaan pois kytkeminen
            if (!isChecked) {
                String hostname = vpsIpAddressEdit.getText().toString().trim();
                String username = vpsUsernameEdit.getText().toString().trim();
                String password = vpsPasswordEdit.getText().toString().trim();
                String port = vpsPortEdit.getText().toString().trim();
                String directory = vpsStoragePathEdit.getText().toString().trim();
                
                if (hostname.isEmpty() || username.isEmpty() || password.isEmpty() || port.isEmpty() || directory.isEmpty()) {
                    // Estetään pois kytkeminen jos VPS-asetukset eivät ole täydelliset
                    enableSyncSwitch.setChecked(true);
                    Toast.makeText(SettingsActivity.this, "Cannot disable sync - VPS settings are incomplete", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // Tallenna asetus välittömästi (mirror both keys)
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("enable_sync", isChecked);
            editor.putBoolean("enable_forwarding", isChecked);
            editor.apply();
            
            if (isChecked) {
                Toast.makeText(SettingsActivity.this, "SMS sync enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SettingsActivity.this, "SMS sync disabled", Toast.LENGTH_SHORT).show();
            }
        });
        connectionStatusIndicator = findViewById(R.id.connectionStatusIndicator);
        sshStatusText = findViewById(R.id.sshStatusText);
        if (sshStatusText != null) {
            sshStatusText.setText(""); // Tyhjennetään teksti sivun avautuessa
        }
        pingButton = findViewById(R.id.pingButton);
        pingResultText = findViewById(R.id.pingResultText);

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            saveSettings();
            testConnection();
        });

        vpsIpAddressEdit.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveSettings();
                testConnection();
                return true;
            }
            return false;
        });

        ImageView racknerdBanner = findViewById(R.id.racknerdBanner);
        if (racknerdBanner != null) {
            racknerdBanner.setOnClickListener(v -> {
                String url = "https://my.racknerd.com/aff.php?aff=14379";
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            });
        }

        pingButton.setOnClickListener(v -> {
            String vpsIp = vpsIpAddressEdit.getText().toString();
            if (!vpsIp.isEmpty()) {
                performPingTest(vpsIp);
            } else {
                Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performPingTest(String vpsIp) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 " + vpsIp);
                int status = process.waitFor();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                final String pingResult = output.toString();
                final boolean isSuccess = status == 0;

                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(
                            isSuccess ? R.drawable.green_circle_background : R.drawable.circle_background
                    );

                    if (isSuccess) {
                        // Etsitään viive millisekunteina
                        String[] lines = pingResult.split("\n");
                        for (String l : lines) {
                            if (l.contains("time=")) {
                                String timeStr = l.substring(l.indexOf("time=") + 5);
                                timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                                pingResultText.setText(getString(R.string.ping_time_format, timeStr));
                                break;
                            }
                        }
                    } else {
                        pingResultText.setText(R.string.ping_failed);
                    }
                    pingResultText.setVisibility(View.VISIBLE);
                });
            } catch (IOException | InterruptedException e) {
                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(R.drawable.circle_background);
                    pingResultText.setText(R.string.ping_failed);
                    pingResultText.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void loadSettings() {
        vpsIpAddressEdit.setText(preferences.getString("vps_ip", ""));
        vpsUsernameEdit.setText(preferences.getString("vps_username", ""));
        vpsStoragePathEdit.setText(preferences.getString("vps_storage_path", "/root/sms"));
        vpsPasswordEdit.setText(preferences.getString("vps_password", ""));
        vpsPortEdit.setText(preferences.getString("vps_port", "22"));
        if (enableSyncSwitch != null) {
            enableSyncSwitch.setChecked(preferences.getBoolean("enable_sync", false));
        }
    }

    private void saveSettings() {
        String ipAddress = vpsIpAddressEdit.getText().toString();
        String username = vpsUsernameEdit.getText().toString();
        String storagePath = vpsStoragePathEdit.getText().toString();
        String password = vpsPasswordEdit.getText().toString();
        String port = vpsPortEdit.getText().toString();
        boolean enableSync = enableSyncSwitch != null && enableSyncSwitch.isChecked();

        if (ipAddress.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("vps_ip", ipAddress);
        editor.putString("vps_username", username);
        editor.putString("vps_storage_path", storagePath);
        editor.putString("vps_password", password);
        editor.putString("vps_port", port);
        // Mirror both keys so UI adapters and services stay consistent
        editor.putBoolean("enable_sync", enableSync);
        editor.putBoolean("enable_forwarding", enableSync);
        editor.putBoolean("vps_settings_saved", true);
        editor.apply();

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        if (connectionStatusIndicator == null) return;

        String vpsIp = vpsIpAddressEdit.getText().toString();
        String username = vpsUsernameEdit.getText().toString();
        String password = vpsPasswordEdit.getText().toString();
        String port = vpsPortEdit.getText().toString();

        if (vpsIp.isEmpty() || username.isEmpty() || password.isEmpty()) return;

        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, vpsIp, Integer.parseInt(port));
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(5000);
                session.disconnect();

                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(R.drawable.green_circle_background);
                    sshStatusText.setText(R.string.ssh_connection_success);
                    sshStatusText.setTextColor(ContextCompat.getColor(this, R.color.green));
                    // Mark settings as saved on successful SSH test
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("vps_settings_saved", true);
                    editor.apply();
                });
            } catch (Exception e) {
                final String errorMessage = e.getMessage();
                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(R.drawable.circle_background);
                    sshStatusText.setText(String.format("%s: %s", getString(R.string.ssh_connection_failed), errorMessage));
                    sshStatusText.setTextColor(ContextCompat.getColor(this, R.color.red));
                });
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}