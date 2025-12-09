package com.sms2vps.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;


import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.widget.Button;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.webkit.WebView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import android.app.role.RoleManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends BaseActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final String ACTIVITY_TAG = "MainActivity";
    private static final String PREFS = "AppPrefs";
    private static final String KEY_TERMS_ACCEPTED = "termsAccepted";

    private DatabaseHelper dbHelper;
    private ConversationsAdapter conversationsAdapter;
    private TextView noConversationsText;
    private ActivityResultLauncher<Intent> defaultSmsAppLauncher;

    private SMSConversation recentlySwipedConversation = null;
    private int recentlySwipedPosition = -1;

    private boolean isWaitingForDefaultSmsResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Log.d(ACTIVITY_TAG, "onCreate started");
        dbHelper = new DatabaseHelper(this);

        // Initialize RecyclerView first
        RecyclerView recyclerView = findViewById(R.id.conversationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setClipToPadding(false);

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        conversationsAdapter = new ConversationsAdapter(
                (position, conversation) -> {
                    if (conversation != null) {
                        Intent intent = new Intent(this, ConversationActivity.class);
                        intent.putExtra("phone_number", conversation.phoneNumber());
                        startActivity(intent);
                    }
                }
        );

        recyclerView.setAdapter(conversationsAdapter);

        // Add swipe-to-delete functionality
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (conversationsAdapter != null && position < conversationsAdapter.getItemCount()) {
                        recentlySwipedConversation = conversationsAdapter.getConversationAt(position);
                        recentlySwipedPosition = position;
                        showDeleteConfirmationDialog(recentlySwipedConversation);
                        // Poista näkyvistä väliaikaisesti
                        conversationsAdapter.removeConversationAt(position);
                    }
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);

        noConversationsText = findViewById(R.id.no_conversations_text);

        defaultSmsAppLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isWaitingForDefaultSmsResult = false;
                    if (isDefaultSmsApp()) {
                        Toast.makeText(this, "Set as the default text messaging app.", Toast.LENGTH_SHORT).show();
                        // SMS-sovellus on nyt asetettu oletukseksi, voi jatkaa normaalisti
                        checkPermissionsAfterDefaultSms();
                    } else {
                        // Käyttäjä ei asettanut sovellusta oletukseksi, palaa tervetuloikkunaan
                        showWelcomeDialogAfterSmsDecline();
                    }
                }
        );

        setupToolbar();
        setupFab();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean termsAccepted = prefs.getBoolean(KEY_TERMS_ACCEPTED, false);
        if (!isDefaultSmsApp() || !termsAccepted) {
            // Näytä tervetuloikkuna kunnes sekä oletus-SMS että ehdot on hyväksytty
            showWelcomeDialog();
        } else {
            // Molemmat ehdot täyttyvät → ei tervetuloa eikä oletus-SMS pyyntöjä
            checkPermissions();
        }

        registerSmsUpdateReceiver();
        updateFontSize();

        // Rekisteröidään VPS_STATUS_UPDATED vastaanotin (Android 13+/14 vaatii flägit)
        IntentFilter vpsFilter = new IntentFilter("VPS_STATUS_UPDATED");
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                vpsStatusReceiver,
                vpsFilter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );

        Log.d(ACTIVITY_TAG, "onCreate finished");
    }

    private void showWelcomeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.welcome_dialog, null);
        builder.setView(dialogView);

        WebView webView = dialogView.findViewById(R.id.welcome_webview);
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        Button acceptButton = dialogView.findViewById(R.id.accept_button);

        // Load HTML content
        try {
            InputStream is = getAssets().open("welcome_content.html");
            int size = is.available();
            byte[] buffer = new byte[size];
            int bytesRead = is.read(buffer);
            if (bytesRead != size) {
                Log.w(ACTIVITY_TAG, "Incomplete read from welcome_content.html");
            }
            is.close();
            String html = new String(buffer, StandardCharsets.UTF_8);
            String styledHtml = "<html><head><style>body{color: #333; background-color: #fff; font-family: sans-serif; margin: 16px;} h2{color: #2c3e50;}</style></head><body>" + html + "</body></html>";
            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Log.e(ACTIVITY_TAG, "Error loading welcome content", e);
            Toast.makeText(this, "Error loading terms.", Toast.LENGTH_SHORT).show();
        }

        acceptButton.setEnabled(false);

        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (!v.canScrollVertically(1)) { // Reached the bottom
                acceptButton.setEnabled(true);
            }
        });

        AlertDialog dialog = builder.create();

        acceptButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_TERMS_ACCEPTED, true);
            editor.apply();
            dialog.dismiss();
            // Jos ei vielä oletus-SMS, ohjaa asettamaan; muuten jatka oikeuksiin/keskusteluihin
            if (!isDefaultSmsApp()) {
                showDefaultSmsRequest();
            } else {
                checkPermissionsAfterDefaultSms();
            }
        });

        dialog.setCancelable(false);
        dialog.show();
    }

    private void showWelcomeDialogAfterSmsDecline() {
        // Palaa tervetuloon kunnes ehdot hyväksytty ja oletus-SMS asetettu
        showWelcomeDialog();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem defaultAppItem = menu.findItem(R.id.action_set_default);
        if (defaultAppItem != null) {
            defaultAppItem.setVisible(!isDefaultSmsApp()); // Simplified if-statement
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_SMS);
            }
            return false;
        } else {
            String defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(this);
            return getPackageName().equals(defaultSmsPackage);
        }
    }

    private void showDefaultSmsRequest() {
        if (isWaitingForDefaultSmsResult) {
            return; // Vältetään useita samanaikaisia pyyntöjä
        }

        String myPackageName = getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    Log.d(ACTIVITY_TAG, "Requesting ROLE_SMS via RoleManager");
                    isWaitingForDefaultSmsResult = true;
                    try {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                        defaultSmsAppLauncher.launch(intent);
                    } catch (Exception e) {
                        Log.e(ACTIVITY_TAG, "Error launching RoleManager request", e);
                        Toast.makeText(this, "Unable to request default SMS role.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                } else {
                    Log.d(ACTIVITY_TAG, "Already holding ROLE_SMS");
                    checkPermissionsAfterDefaultSms();
                    return;
                }
            }
        }

        // Android 9 ja aiemmat - fallback
        String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
        Log.d(ACTIVITY_TAG, "Current default SMS app: " + (defaultSmsApp != null ? defaultSmsApp : "null"));
        Log.d(ACTIVITY_TAG, "My package name: " + myPackageName);
        if (!myPackageName.equals(defaultSmsApp)) {
            Log.d(ACTIVITY_TAG, "Requesting to become default SMS app (legacy)");
            isWaitingForDefaultSmsResult = true;
            try {
                Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName);
                defaultSmsAppLauncher.launch(intent);
            } catch (Exception e) {
                Log.e(ACTIVITY_TAG, "Error launching default SMS app intent", e);
                Toast.makeText(this, "Unable to open default SMS dialog.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(ACTIVITY_TAG, "Already the default SMS app (legacy)");
            checkPermissionsAfterDefaultSms();
        }
    }

    private void requestDefaultSmsApp() {
        showDefaultSmsRequest();
    }

    private void checkPermissionsAfterDefaultSms() {
        // Tämä kutsutaan kun SMS-sovellus on asetettu oletukseksi
        List<String> permissionsToRequest = new ArrayList<>();

        String[] requiredPermissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(ACTIVITY_TAG, "Requesting permission: " + permission);
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        } else {
            Log.d(ACTIVITY_TAG, "All required permissions already granted.");
            loadConversations();
        }
    }

    private void checkPermissions() {
        // Tarkista ensin onko oletus SMS-sovellus
        if (!isDefaultSmsApp()) {
            showDefaultSmsRequest();
            return;
        }

        // Tämä on vanha checkPermissions-metodi, käytetään kun sovellus on jo oletus SMS-sovellus
        checkPermissionsAfterDefaultSms();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    // At least one permission not granted, do nothing special
                    break;
                }
            }
            // Riippumatta luvista, lataa keskustelut jos sovellus on oletus SMS-sovellus
            if (isDefaultSmsApp()) {
                loadConversations();
            }
        }
    }

    private void loadConversations() {
        Log.d(ACTIVITY_TAG, "loadConversations started");
        if (conversationsAdapter != null) {
            List<SMSConversation> conversations = dbHelper.getAllConversations();
            conversationsAdapter.updateConversations(conversations);
            Log.d(ACTIVITY_TAG, "Adapter updated with " + conversations.size() + " conversations.");
        } else {
            Log.e(ACTIVITY_TAG, "conversationsAdapter is null, cannot load conversations.");
        }

        if (noConversationsText != null) {
            if (conversationsAdapter != null && conversationsAdapter.getItemCount() == 0) {
                noConversationsText.setVisibility(View.VISIBLE);
            } else {
                noConversationsText.setVisibility(View.GONE);
            }
        }
        Log.d(ACTIVITY_TAG, "loadConversations finished");
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        // Asetetaan toolbar käyttämään popup-tyyliä
        toolbar.setPopupTheme(R.style.PopupMenuStyle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Korostetaan valittu fonttikoko
        float currentScale = FontSizeManager.getInstance(this).getFontScale();

        MenuItem smallItem = menu.findItem(R.id.font_size_small);
        MenuItem mediumItem = menu.findItem(R.id.font_size_medium);
        MenuItem largeItem = menu.findItem(R.id.font_size_large);

        if (currentScale == 1.2f && smallItem != null) {
            smallItem.setActionView(R.layout.selected_menu_item);
        } else if (currentScale == 1.4f && mediumItem != null) {
            mediumItem.setActionView(R.layout.selected_menu_item);
        } else if (currentScale == 1.6f && largeItem != null) {
            largeItem.setActionView(R.layout.selected_menu_item);
        }

        // Päivitetään pudotusvalikkojen fonttikoko
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.hasSubMenu()) {
                Menu subMenu = item.getSubMenu();
                if (subMenu != null) {
                    updateSubMenuFontSize(subMenu);
                }
            }
        }

        return true;
    }

    private void updateSubMenuFontSize(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.hasSubMenu()) {
                Menu subMenu = item.getSubMenu();
                if (subMenu != null) {
                    updateSubMenuFontSize(subMenu);
                }
            }
            View actionView = item.getActionView();
            if (actionView instanceof TextView) {
                FontSizeManager.getInstance(this).applyFontSize((TextView) actionView);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_vps_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_vps_storage) {
            Intent intent = new Intent(this, VPSStorageActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_google_settings) {
            Intent intent = new Intent(this, GoogleSettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_terms_of_use) {
            Intent intent = new Intent(this, TermsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_set_default) {
            requestDefaultSmsApp();
            return true;
        } else if (itemId == R.id.font_size_small) {
            setFontSize(0.8f);
            return true;
        } else if (itemId == R.id.font_size_medium) {
            setFontSize(1.0f);
            return true;
        } else if (itemId == R.id.font_size_large) {
            setFontSize(1.6f);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setFontSize(float scale) {
        FontSizeManager.getInstance(this).setFontScale(scale);
        // Always update font size and refresh menu, even if scale is the same
        updateFontSize();
        invalidateOptionsMenu(); // Force menu to refresh highlight
    }

    @Override
    protected void updateFontSize() {
        float scale = FontSizeManager.getInstance(this).getFontScale();

        // Update RecyclerView adapter
        if (conversationsAdapter != null) {
            conversationsAdapter.setFontScale(scale);
            conversationsAdapter.notifyItemRangeChanged(0, conversationsAdapter.getItemCount());
        }

        // Call parent class method to update all TextViews
        super.updateFontSize();
    }

    /**
     * Luo uuden keskustelun valikko käyttäen PopupWindow:ia FAB-painikkeen yläpuolelle
     */
    private void setupFab() {
        View fab = findViewById(R.id.newConversationFab);
        fab.setOnClickListener(v -> showNewConversationPopup(fab));
    }

    private void showNewConversationPopup(View anchorView) {
        // Luo popup-ikkuna layout - korjattu null view root ongelma
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        View popupView = inflater.inflate(R.layout.new_conversation_popup, container, false);

        // Luo PopupWindow
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // Aseta tausta ja animaatio
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.popup_background));
        popupWindow.setElevation(8);
        popupWindow.setAnimationStyle(R.style.PopupAnimation);

        // Hae painikkeet
        Button newNumberButton = popupView.findViewById(R.id.btn_new_number);
        Button selectContactButton = popupView.findViewById(R.id.btn_select_contact);
        TextView titleText = popupView.findViewById(R.id.popup_title);

        // Sovella fonttikoko - päivitetty tekstin värin käsittely
        float fontScale = FontSizeManager.getInstance(this).getFontScale();
        titleText.setTextSize(16 * fontScale);
        newNumberButton.setTextSize(14 * fontScale);
        selectContactButton.setTextSize(14 * fontScale);

        // Aseta painikkeiden toiminnot
        newNumberButton.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(this, NewConversationActivity.class);
            intent.putExtra("mode", "number");
            startActivity(intent);
        });

        selectContactButton.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(this, NewConversationActivity.class);
            intent.putExtra("mode", "contact");
            startActivity(intent);
        });

        // Laske popup-ikkunan sijainti FAB-painikkeen yläpuolelle
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        // Mittaa popup-ikkunan koko
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        // Laske sijainti: keskitä vaakasuunnassa FAB:n kanssa ja aseta pystysuunnassa yläpuolelle
        int xOffset = (anchorView.getWidth() - popupWidth) / 2;
        int yOffset = -(popupHeight + 16); // 16dp väli FAB:n ja popup:n väliin

        // Näytä popup
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset, Gravity.NO_GRAVITY);
    }

    private void registerSmsUpdateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.sms2vps.UPDATE_CONVERSATIONS");
        filter.addAction("com.example.sms2vps.CONVERSATION_EMPTIED");
        filter.addAction("com.example.sms2vps.GOOGLE_STATUS_CHANGED");
        filter.addAction("com.example.sms2vps.DRIVE_UPLOAD_SUCCESS");
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                smsUpdateReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private final BroadcastReceiver smsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case "com.example.sms2vps.UPDATE_CONVERSATIONS":
                    Log.d(ACTIVITY_TAG, "SMS update received, refreshing UI.");
                    loadConversations();
                    break;
                case "com.example.sms2vps.CONVERSATION_EMPTIED":
                    Log.d(ACTIVITY_TAG, "Conversation emptied, refreshing conversations.");
                    loadConversations();
                    break;
                case "com.example.sms2vps.GOOGLE_STATUS_CHANGED":
                    Log.d(ACTIVITY_TAG, "Google status changed, invalidating options menu.");
                    invalidateOptionsMenu();
                    break;
                case "com.example.sms2vps.DRIVE_UPLOAD_SUCCESS":
                    Log.d(ACTIVITY_TAG, "DRIVE_UPLOAD_SUCCESS broadcast received, invalidating options menu and reloading conversations");
                    invalidateOptionsMenu();
                    loadConversations();
                    break;
            }
        }
    };

    private final BroadcastReceiver vpsStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("VPSIconDebug", "VPS_STATUS_UPDATED broadcast received, reloading conversations");
            loadConversations();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        // Tarkista oletus SMS-sovelluksen tila onResume:ssa (Android 10+ varten)
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean termsAccepted = prefs.getBoolean(KEY_TERMS_ACCEPTED, false);
        boolean isDefault = isDefaultSmsApp();

        if (isWaitingForDefaultSmsResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isWaitingForDefaultSmsResult = false;
            if (isDefault) {
                Toast.makeText(this, "Set as the default text messaging app.", Toast.LENGTH_SHORT).show();
                checkPermissionsAfterDefaultSms();
            } else {
                showWelcomeDialog();
            }
        } else {
            if (!isDefault || !termsAccepted) {
                showWelcomeDialog();
            } else {
                loadConversations();
            }
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsUpdateReceiver);
        unregisterReceiver(vpsStatusReceiver);
    }

    private void showDeleteConfirmationDialog(SMSConversation conversation) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Conversation")
                .setMessage("Are you sure you want to delete all messages from " + conversation.phoneNumber() + "? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        dbHelper.deleteConversation(conversation.phoneNumber());
                        loadConversations();
                        Toast.makeText(this, "Conversation deleted.", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(ACTIVITY_TAG, "Error deleting conversation", e);
                        Toast.makeText(this, "Error deleting conversation.", Toast.LENGTH_SHORT).show();
                    }
                    recentlySwipedConversation = null;
                    recentlySwipedPosition = -1;
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Restore the conversation visually
                    if (recentlySwipedConversation != null && recentlySwipedPosition >= 0) {
                        if (conversationsAdapter != null) {
                            conversationsAdapter.restoreConversationAt(recentlySwipedPosition, recentlySwipedConversation);
                        }
                    }
                    recentlySwipedConversation = null;
                    recentlySwipedPosition = -1;
                })
                .setCancelable(false)
                .show();
    }

}
