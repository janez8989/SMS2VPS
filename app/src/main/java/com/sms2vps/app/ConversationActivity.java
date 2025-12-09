package com.sms2vps.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.util.List;

public class ConversationActivity extends BaseActivity {
    private DatabaseHelper dbHelper;
    private MessagesAdapter messagesAdapter;
    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Status barin värin asetus
        android.view.Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        dbHelper = new DatabaseHelper(this);

        // Aseta työkalupalkki
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            android.graphics.drawable.Drawable navigationIcon = toolbar.getNavigationIcon();
            if (navigationIcon != null) {
                navigationIcon.setTint(getResources().getColor(android.R.color.white, getTheme()));
            }
        }

        // Hae puhelinnumero intentistä
        phoneNumber = getIntent().getStringExtra("phone_number");
        String contactName = getIntent().getStringExtra("contact_name");
        if (phoneNumber == null) {
            Toast.makeText(this, "Error: Phone number is missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Aseta työkalupalkin otsikko
        TextView conversationTitle = findViewById(R.id.toolbar_title);
        if (contactName != null && !contactName.isEmpty()) {
            conversationTitle.setText(getString(R.string.conversation_title_format, contactName, phoneNumber));
        } else {
            conversationTitle.setText(phoneNumber);
        }

        // Alusta viestilista ja adapteri
        List<SMSMessage> messages = dbHelper.getMessagesForPhoneNumber(phoneNumber);
        messagesAdapter = new MessagesAdapter(this);
        messagesAdapter.updateMessages(messages);

        // Alusta RecyclerView
        RecyclerView messagesRecyclerView = findViewById(R.id.messages_recycler_view);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messagesAdapter);
        messagesRecyclerView.setClipToPadding(false);

        // Lisää padding, joka ottaa huomioon sekä järjestelmäpalkin/IME:n että syöttökentän korkeuden
        android.view.View inputContainer = findViewById(R.id.message_input_container);
        ViewCompat.setOnApplyWindowInsetsListener(messagesRecyclerView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int inputHeight = (inputContainer != null) ? inputContainer.getHeight() : 0;
            int bottomSafeInset = Math.max(systemBars.bottom, imeInsets.bottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomSafeInset + inputHeight);
            return insets;
        });
        // Päivitä padding heti kun näkymät ovat mitattu (aiempi getHeight() saattoi olla 0)
        messagesRecyclerView.post(() -> {
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(messagesRecyclerView);
            int bottomSafeInset = 0;
            if (rootInsets != null) {
                Insets systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets imeInsets = rootInsets.getInsets(WindowInsetsCompat.Type.ime());
                bottomSafeInset = Math.max(systemBars.bottom, imeInsets.bottom);
            }
            int inputHeight = (inputContainer != null) ? inputContainer.getHeight() : 0;
            messagesRecyclerView.setPadding(
                    messagesRecyclerView.getPaddingLeft(),
                    messagesRecyclerView.getPaddingTop(),
                    messagesRecyclerView.getPaddingRight(),
                    bottomSafeInset + inputHeight
            );
        });
        if (inputContainer != null) {
            // Lisätään myös sisennykset syöttökenttään, jottei se jää järjestelmäpalkin alle
            ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
            // Reagoi syöttökentän korkeuden muutoksiin (esim. kun IME avautuu/sulkeutuu)
            inputContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int inputHeight = bottom - top;
                WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(messagesRecyclerView);
                int bottomSafeInset = 0;
                if (rootInsets != null) {
                    Insets systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets imeInsets = rootInsets.getInsets(WindowInsetsCompat.Type.ime());
                    bottomSafeInset = Math.max(systemBars.bottom, imeInsets.bottom);
                }
                messagesRecyclerView.setPadding(
                        messagesRecyclerView.getPaddingLeft(),
                        messagesRecyclerView.getPaddingTop(),
                        messagesRecyclerView.getPaddingRight(),
                        bottomSafeInset + inputHeight
                );
            });
        }

        // Lisää pyyhkäisyeleen käsittelijä
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    SMSMessage message = messagesAdapter.getMessages().get(position);
                    dbHelper.deleteMessage(message.sender(), message.timestamp());
                    messagesAdapter.removeMessage(position);
                    Toast.makeText(ConversationActivity.this, "Message deleted.", Toast.LENGTH_SHORT).show();

                    // Tarkista onko tämä viimeinen viesti keskustelussa
                    if (messagesAdapter.getItemCount() == 0 && !dbHelper.hasMessages(phoneNumber)) {
                        // Lähetä broadcast ilmoittamaan että keskustelu on tyhjä
                        Intent intent = new Intent("com.example.sms2vps.CONVERSATION_EMPTIED");
                        intent.putExtra("phone_number", phoneNumber);
                        intent.setPackage(getPackageName());
                        sendBroadcast(intent);
                    }
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(messagesRecyclerView);

        // Alusta viestikenttä ja lähetä-painike
        EditText messageInput = findViewById(R.id.message_input);
        ImageButton sendButton = findViewById(R.id.send_button);

        sendButton.setOnClickListener(v -> sendMessage(phoneNumber, messages, messageInput));

        // Rekisteröi vastaanotin viestien päivityksiä varten
        IntentFilter filter = new IntentFilter("com.example.sms2vps.UPDATE_CONVERSATIONS");
        filter.addAction("com.example.sms2vps.DRIVE_UPLOAD_SUCCESS");
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                smsUpdateReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation_menu, menu);
        MenuItem driveStatusItem = menu.findItem(R.id.drive_status);
        if (driveStatusItem != null) {
            driveStatusItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendMessage(String phoneNumber, List<SMSMessage> messages, EditText messageInput) {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            // Tarkista, onko sovellus oletus-SMS-sovellus
            if (!getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this))) {
                Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                startActivity(intent);
                Toast.makeText(this, "Please set this app as your default SMS app to send messages.", Toast.LENGTH_LONG).show();
                return;
            }

            long timestamp = System.currentTimeMillis();

            if (sendSms(phoneNumber, message)) {
                // Lisää viesti listaan ja päivitä UI
                messages.add(new SMSMessage(
                        phoneNumber,  // sender
                        message,      // message
                        timestamp,    // timestamp
                        true,         // isRead
                        false,        // uploadedToVPS
                        false,        // uploadFailed
                        false,        // googleSent
                        false,        // googleFailed
                        false,        // driveSent
                        false         // driveFailed
                ));

                messagesAdapter.notifyItemInserted(messages.size() - 1);
                messageInput.setText("");

                // Käynnistä SmsForwardingService ja välitä viestitiedot
                Intent serviceIntent = new Intent(this, SmsSynchronizationService.class);
                serviceIntent.putExtra("sender", phoneNumber);
                serviceIntent.putExtra("message", message);
                serviceIntent.putExtra("timestamp", timestamp);
                startService(serviceIntent);
            } else {
                Toast.makeText(this, "Error sending message.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "The message cannot be empty.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean sendSms(String phoneNumber, String message) {
        try {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(android.net.Uri.parse("smsto:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);
            if (smsIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(smsIntent);
                return true;
            } else {
                Toast.makeText(this, "No SMS application installed.", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            Log.e("ConversationActivity", "Error sending SMS: " + e.getMessage());
            return false;
        }
    }

    // Vastaanotin UI-päivityksiä varten
    private final BroadcastReceiver smsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case "com.example.sms2vps.UPDATE_CONVERSATIONS":
                    Log.d("ConversationActivity", "SMS update received, refreshing UI.");
                    refreshMessages();
                    break;
                case "com.example.sms2vps.DRIVE_UPLOAD_SUCCESS":
                    Log.d("ConversationActivity", "Drive upload status received, updating UI.");
                    refreshMessages();
                    invalidateOptionsMenu();
                    break;
            }
        }
    };

    private void refreshMessages() {
        String phoneNumber = getIntent().getStringExtra("phone_number");
        if (phoneNumber != null) {
            List<SMSMessage> messages = dbHelper.getMessagesForPhoneNumber(phoneNumber);
            messagesAdapter.updateMessages(messages);
        }
    }

    @Override
    protected void updateFontSize() {
        if (messagesAdapter != null) {
            messagesAdapter.updateFontSize();
        }
        super.updateFontSize();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsUpdateReceiver);
    }
}