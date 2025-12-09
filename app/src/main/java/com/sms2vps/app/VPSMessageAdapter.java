package com.sms2vps.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.TextPaint;
import java.util.regex.Matcher;
import android.util.Patterns;
import android.graphics.Color;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.content.Context;

public class VPSMessageAdapter extends RecyclerView.Adapter<VPSMessageAdapter.MessageViewHolder> {
    private final List<SMSMessage> messages;
    private final SimpleDateFormat dateFormat;
    private final Context context;
    private float currentFontScale;

    public VPSMessageAdapter(Context context) {
        this.messages = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        this.context = context;
        this.currentFontScale = FontSizeManager.getInstance(context).getFontScale();
    }

    public void updateFontSize() {
        float newScale = FontSizeManager.getInstance(context).getFontScale();
        if (newScale != currentFontScale) {
            currentFontScale = newScale;
            // Käytetään spesifisempää päivitysmetodia notifyItemRangeChanged
            // notifyDataSetChanged() sijaan tehokkuuden parantamiseksi
            notifyItemRangeChanged(0, messages.size());
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestamp;
        TextView phoneNumber;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.vps_message_text);
            timestamp = itemView.findViewById(R.id.vps_timestamp);
            phoneNumber = itemView.findViewById(R.id.vps_phone_number);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final MessageViewHolder holder, final int position) {
        SMSMessage message = messages.get(position);
        Log.d("VPSMessageAdapter", "Binding message at position " + position + ": " + message.message());
        setMessageText(holder.messageText, message.message());
        holder.messageText.setTextSize(16 * currentFontScale);
        holder.timestamp.setTextSize(12 * currentFontScale);
        holder.phoneNumber.setTextSize(12 * currentFontScale);
        String formattedTime = dateFormat.format(new Date(message.timestamp()));
        holder.timestamp.setText(formattedTime);
        holder.phoneNumber.setText(message.sender());
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                SMSMessage clickedMessage = messages.get(adapterPosition);
                String messageText = clickedMessage.message();
                if (messageText != null && !messageText.isEmpty()) {
                    Toast.makeText(v.getContext(), messageText, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<SMSMessage> newMessages) {
        Log.d("VPSMessageAdapter", "Updating messages. Old size: " + messages.size() + ", New size: " + newMessages.size());
        
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return messages.size();
            }

            @Override
            public int getNewListSize() {
                return newMessages.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                SMSMessage oldMessage = messages.get(oldItemPosition);
                SMSMessage newMessage = newMessages.get(newItemPosition);
                return oldMessage.timestamp() == newMessage.timestamp() &&
                        oldMessage.sender().equals(newMessage.sender());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SMSMessage oldMessage = messages.get(oldItemPosition);
                SMSMessage newMessage = newMessages.get(newItemPosition);
                return oldMessage.message().equals(newMessage.message());
            }
        });

        messages.clear();
        messages.addAll(newMessages);
        diffResult.dispatchUpdatesTo(this);
        
        Log.d("VPSMessageAdapter", "Messages updated successfully");
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.vps_message_item, parent, false);
        return new MessageViewHolder(view);
    }

    private void setMessageText(TextView textView, String message) {
        if (message == null || message.isEmpty()) {
            textView.setText("");
            return;
        }

        SpannableString spannableString = new SpannableString(message);
        Matcher matcher = Patterns.WEB_URL.matcher(message);

        while (matcher.find()) {
            final String url = matcher.group();
            spannableString.setSpan(
                createUrlClickableSpan(url),
                matcher.start(),
                matcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        textView.setText(spannableString);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private ClickableSpan createUrlClickableSpan(final String url) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.BLUE);
                ds.setUnderlineText(true);
            }
        };
    }

    public List<SMSMessage> getMessages() {
        return messages;
    }

    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }
}