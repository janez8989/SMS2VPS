package com.sms2vps.app;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.io.InputStream;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SettingsActivity extends BaseActivity {
    private static final int REQUEST_CODE_SELECT_SSH_KEY = 1001;
    private EditText vpsIpAddressEdit;
    private EditText vpsUsernameEdit;
    private EditText vpsStoragePathEdit;
    private EditText vpsPasswordEdit;
    private EditText vpsPortEdit;
    private SwitchCompat enableSyncSwitch;
    private View connectionStatusIndicator;
    private Button pingButton;
    private TextView pingResultText;
    private TextView sshStatusText;
    private SharedPreferences preferences;
    private RadioGroup authMethodRadioGroup;
    private RadioButton authPasswordRadio;
    private RadioButton authKeyRadio;
    private RadioButton authMultiFactorRadio;
    private View sshKeyContainer;
    private Button selectSshKeyButton;
    private ImageButton clearSshKeyButton;
    private TextView sshKeyInfoText;
    private String sshPrivateKeyContent;
    private String sshPrivateKeyPath;

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
        
        // SSH Key authentication setup
        authMethodRadioGroup = findViewById(R.id.authMethodRadioGroup);
        authPasswordRadio = findViewById(R.id.authPasswordRadio);
        authKeyRadio = findViewById(R.id.authKeyRadio);
        authMultiFactorRadio = findViewById(R.id.authMultiFactorRadio);
        sshKeyContainer = findViewById(R.id.sshKeyContainer);
        selectSshKeyButton = findViewById(R.id.selectSshKeyButton);
        sshKeyInfoText = findViewById(R.id.sshKeyInfoText);
        
        // Lisää poistopainike SSH-avaimelle
        clearSshKeyButton = findViewById(R.id.clearSshKeyButton);
        clearSshKeyButton.setOnClickListener(v -> clearSshKey());
        
        authMethodRadioGroup.setOnCheckedChangeListener((group, checkedId) -> updateAuthMethodUi());
        
        selectSshKeyButton.setOnClickListener(v -> selectSshKeyFile());
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
        
        // Load saved authentication method
        loadAuthSettings();
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
        
        // Lataa SSH-avaimen asetukset
        String authMethod = preferences.getString("vps_auth_method", "password");
        if ("ssh_key".equals(authMethod)) {
            authKeyRadio.setChecked(true);
        } else if ("multi_factor".equals(authMethod)) {
            authMultiFactorRadio.setChecked(true);
        } else {
            authPasswordRadio.setChecked(true);
        }
        
        // Näytä valitun SSH-avaimen tiedot
        sshPrivateKeyContent = preferences.getString("vps_ssh_key_content", "");
        updateSshKeyInfo();
    }
    
    private void updateSshKeyInfo() {
        if (sshPrivateKeyContent != null && !sshPrivateKeyContent.isEmpty()) {
            // Näytä SSH-avaimen tiedot (lyhyt nimi)
            String keyInfo = "SSH key selected: " + sshPrivateKeyContent.substring(0, Math.min(20, sshPrivateKeyContent.length())) + "...";
            sshKeyInfoText.setText(keyInfo);
            // Näytä poistopainike kun SSH-avain on valittu
            if (clearSshKeyButton != null) {
                clearSshKeyButton.setVisibility(View.VISIBLE);
            }
        } else {
            sshKeyInfoText.setText(R.string.no_ssh_key_selected);
            // Piilota poistopainike kun SSH-avainta ei ole valittu
            if (clearSshKeyButton != null) {
                clearSshKeyButton.setVisibility(View.GONE);
            }
        }
    }
    
    private void clearSshKey() {
        // Tyhjennä SSH-avaimen tiedot
        sshPrivateKeyContent = "";
        sshPrivateKeyPath = null;
        
        // Päivitä käyttöliittymä
        updateSshKeyInfo();
        
        // Poista SSH-avaimen asetukset
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("vps_ssh_key_content");
        editor.remove("ssh_key_content");
        editor.remove("ssh_key_path");
        editor.apply();
        
        Toast.makeText(this, R.string.ssh_key_cleared, Toast.LENGTH_SHORT).show();
    }

    private void saveSettings() {
        String ipAddress = vpsIpAddressEdit.getText().toString();
        String username = vpsUsernameEdit.getText().toString();
        String storagePath = vpsStoragePathEdit.getText().toString();
        String password = vpsPasswordEdit.getText().toString();
        String port = vpsPortEdit.getText().toString();
        boolean enableSync = enableSyncSwitch != null && enableSyncSwitch.isChecked();

        // Validate authentication settings
        if (!validateAuthSettings()) {
            return;
        }

        if (ipAddress.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            return;
        }

        // For password auth, password is required
        if (authPasswordRadio.isChecked() && password.isEmpty()) {
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
        
        // Save authentication settings
        saveAuthSettings();
        
        editor.apply();

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        if (connectionStatusIndicator == null) return;

        final String vpsIp = vpsIpAddressEdit.getText().toString();
        final String username = vpsUsernameEdit.getText().toString();
        final String password = vpsPasswordEdit.getText().toString();
        final String portStr = vpsPortEdit.getText().toString();

        if (vpsIp.isEmpty() || username.isEmpty()) return;
        
        // Validate authentication settings for test
        if (authPasswordRadio.isChecked() && password.isEmpty()) {
            Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (authKeyRadio.isChecked() && 
            (sshPrivateKeyContent == null || sshPrivateKeyContent.isEmpty())) {
            Toast.makeText(this, R.string.ssh_key_required, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (authMultiFactorRadio.isChecked() && 
            (password.isEmpty() || sshPrivateKeyContent == null || sshPrivateKeyContent.isEmpty())) {
            Toast.makeText(this, R.string.both_auth_methods_required, Toast.LENGTH_SHORT).show();
            return;
        }

        int tempPort = 22; // Default SSH port
        try {
            tempPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            // tempPort remains 22
        }
        final int port = tempPort;

        final SharedPreferences finalPreferences = preferences;
        new Thread(() -> {
            try {
                Session session = createSshSession(vpsIp, username, password, port);
                session.connect(5000);
                session.disconnect();
                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(R.drawable.green_circle_background);
                    sshStatusText.setText(R.string.ssh_connection_success);
                    sshStatusText.setTextColor(ContextCompat.getColor(SettingsActivity.this, R.color.green));
                    // Mark settings as saved on successful SSH test
                    SharedPreferences.Editor editor = finalPreferences.edit();
                    editor.putBoolean("vps_settings_saved", true);
                    editor.apply();
                });
            } catch (Exception e) {
                final String errorMessage = e.getMessage();
                runOnUiThread(() -> {
                    connectionStatusIndicator.setBackgroundResource(R.drawable.circle_background);
                    sshStatusText.setText(String.format("%s: %s", getString(R.string.ssh_connection_failed), errorMessage));
                    sshStatusText.setTextColor(ContextCompat.getColor(SettingsActivity.this, R.color.red));
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

    private void updateAuthMethodUi() {
        if (authKeyRadio.isChecked() || authMultiFactorRadio.isChecked()) {
            sshKeyContainer.setVisibility(View.VISIBLE);
        } else {
            sshKeyContainer.setVisibility(View.GONE);
        }
    }

    private void loadAuthSettings() {
        String authMethod = preferences.getString("auth_method", "password");
        String sshKeyPath = preferences.getString("ssh_key_path", "");
        String sshKeyContent = preferences.getString("ssh_key_content", "");

        switch (authMethod) {
            case "key":
                authKeyRadio.setChecked(true);
                break;
            case "multi_factor":
                authMultiFactorRadio.setChecked(true);
                break;
            default:
                authPasswordRadio.setChecked(true);
        }

        if (!sshKeyPath.isEmpty()) {
            sshPrivateKeyPath = sshKeyPath;
            sshKeyInfoText.setText(getString(R.string.ssh_key_selected, 
                sshKeyPath.substring(sshKeyPath.lastIndexOf("/") + 1)));
        }
        
        if (!sshKeyContent.isEmpty()) {
            sshPrivateKeyContent = sshKeyContent;
        }

        updateAuthMethodUi();
    }

    private void selectSshKeyFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(Intent.createChooser(intent, "Select SSH Key"), 
                REQUEST_CODE_SELECT_SSH_KEY);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No file manager available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_SELECT_SSH_KEY && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) {
                        throw new IOException("Failed to open input stream");
                    }
                    int available = inputStream.available();
                    if (available <= 0) {
                        throw new IOException("Empty or invalid input stream");
                    }
                    byte[] buffer = new byte[available];
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead != available) {
                        throw new IOException("Failed to read complete SSH key");
                    }
                    sshPrivateKeyContent = new String(buffer);
                    
                    // Päivitä SSH-avaimen tiedot ja näytä poistopainike
                    updateSshKeyInfo();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.ssh_key_load_error, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void saveAuthSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        
        if (authPasswordRadio.isChecked()) {
            editor.putString("vps_auth_method", "password");
        } else if (authKeyRadio.isChecked()) {
            editor.putString("vps_auth_method", "ssh_key");
        } else if (authMultiFactorRadio.isChecked()) {
            editor.putString("vps_auth_method", "multi_factor");
        }
        
        if (sshPrivateKeyPath != null) {
            editor.putString("ssh_key_path", sshPrivateKeyPath);
        }
        
        if (sshPrivateKeyContent != null) {
            editor.putString("vps_ssh_key_content", sshPrivateKeyContent);
        }
        
        editor.apply();
    }

    private boolean validateAuthSettings() {
        if (authKeyRadio.isChecked() && 
            (sshPrivateKeyContent == null || sshPrivateKeyContent.isEmpty())) {
            Toast.makeText(this, R.string.ssh_key_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (authMultiFactorRadio.isChecked()) {
            String password = vpsPasswordEdit.getText().toString();
            if (password.isEmpty() || 
                (sshPrivateKeyContent == null || sshPrivateKeyContent.isEmpty())) {
                Toast.makeText(this, R.string.both_auth_methods_required, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        
        return true;
    }

    private Session createSshSession(String host, String username, String password, int port) 
        throws Exception {
        
        JSch jsch = new JSch();
        
        if (authKeyRadio.isChecked() || authMultiFactorRadio.isChecked()) {
            // Use SSH key (with optional passphrase)
            jsch.addIdentity("ssh_key", sshPrivateKeyContent.getBytes(), null, null);
        }
        
        Session session = jsch.getSession(username, host, port);
        
        if (authPasswordRadio.isChecked() || authMultiFactorRadio.isChecked()) {
            // Use password (for password-only or multi-factor)
            session.setPassword(password);
        }
        
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
    }
}