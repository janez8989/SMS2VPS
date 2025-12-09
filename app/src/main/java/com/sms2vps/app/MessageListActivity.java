package com.sms2vps.app;

import android.os.Bundle;
import android.database.Cursor;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


public class MessageListActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_list);

        ListView messageListView = findViewById(R.id.message_list);
        DatabaseHelper dbHelper = new DatabaseHelper(MessageListActivity.this);

        Cursor cursor = null;
        try {
            cursor = dbHelper.getReadableDatabase().query(
                    "sms_messages",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            if (cursor.getCount() > 0) {
                String[] from = {"sender", "message", "timestamp"};
                int[] to = {R.id.sender_text, R.id.message_text, R.id.timestamp_text};
                SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                        MessageListActivity.this,
                        R.layout.message_row,
                        cursor,
                        from,
                        to,
                        0);
                messageListView.setAdapter(adapter);
            } else {
                Log.d("MessageListActivity", "No messages found in the database.");
                messageListView.setVisibility(View.GONE);
                TextView noMessagesTextView = findViewById(R.id.noMessagesTextView); // **REMOVED CASTING**
                if (noMessagesTextView != null) {
                    noMessagesTextView.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e("MessageListActivity", "Error fetching messages", e);
            messageListView.setVisibility(View.GONE);
            TextView errorTextView = findViewById(R.id.errorTextView); // **REMOVED CASTING**
            if (errorTextView != null) {
                errorTextView.setVisibility(View.VISIBLE);
                errorTextView.setText(R.string.fetch_error); // **MUUTETTU RESURSSIIN**
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }
}
