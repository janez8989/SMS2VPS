package com.sms2vps.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.core.content.ContextCompat;

public class MessagesActivity extends BaseActivity {
    private EditText messageInput;
    private ImageButton sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        
        setupViews();
    }

    private void setupViews() {
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        // Aseta alkuperäinen väri
        sendButton.setColorFilter(ContextCompat.getColor(this, R.color.white));

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                messageInput.setText("");
            }
        });

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Muutetaan nuolen väri tekstin pituuden mukaan
                if (s.length() > 0) {
                    sendButton.setColorFilter(ContextCompat.getColor(MessagesActivity.this, R.color.send_button_active));
                } else {
                    sendButton.setColorFilter(ContextCompat.getColor(MessagesActivity.this, R.color.white));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}