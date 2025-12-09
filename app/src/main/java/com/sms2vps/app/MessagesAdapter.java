package com.sms2vps.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.util.Log;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> {
    private final List<SMSMessage> messages;
    private final SimpleDateFormat dateFormat;
    private final android.content.Context context;
    private float currentFontScale;



    public MessagesAdapter(android.content.Context context) {
        this.messages = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        this.context = context;
        this.currentFontScale = FontSizeManager.getInstance(context).getFontScale();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timestamp;
        private final ImageView cloudStatus;
        private final ImageView gmailStatus;
        private final ImageView driveStatus;
        private final ImageView xmppStatus;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.timestamp);
            messageText = itemView.findViewById(R.id.message_text);
            cloudStatus = itemView.findViewById(R.id.message_cloud_status);
            gmailStatus = itemView.findViewById(R.id.message_gmail_status);
            driveStatus = itemView.findViewById(R.id.message_drive_status);
            xmppStatus = itemView.findViewById(R.id.message_xmpp_status);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        SMSMessage message = messages.get(position);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(holder.itemView.getContext());
        boolean enableForwarding = sharedPreferences.getBoolean("enable_forwarding", false);
        boolean gmailEnabled = sharedPreferences.getBoolean("google_gmail_enabled", false);
        boolean driveEnabled = sharedPreferences.getBoolean("google_drive_enabled", false);
        boolean vpsSettingsSaved = sharedPreferences.getBoolean("vps_settings_saved", false);

        String formattedTime = dateFormat.format(new Date(message.timestamp()));
        holder.timestamp.setText(formattedTime);
        applyMessageStyling(holder.messageText, message.message());

        // Sovelletaan fonttikoko bindauksen yhteydessä
        holder.messageText.setTextSize(16 * currentFontScale);
        holder.timestamp.setTextSize(12 * currentFontScale);

        Log.d("VPSIconDebug", "Binding message: " + message + ", uploadedToVPS=" + message.uploadedToVPS() + ", uploadFailed=" + message.uploadFailed());
        if (enableForwarding && vpsSettingsSaved) {
            holder.cloudStatus.setVisibility(View.VISIBLE);
            if (message.uploadedToVPS()) {
                holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_success);
                Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_success");
            } else if (message.uploadFailed()) {
                holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_error);
                Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_error");
            } else {
                holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_progress);
                Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_progress");
            }
        } else {
            holder.cloudStatus.setVisibility(View.GONE);
            Log.d("VPSIconDebug", "Cloud status hidden");
        }

        if (gmailEnabled) {
            holder.gmailStatus.setVisibility(View.VISIBLE);
            if (message.googleSent()) {
                holder.gmailStatus.setImageResource(R.drawable.ic_gmail_sent);
            } else if (message.googleFailed()) {
                holder.gmailStatus.setImageResource(R.drawable.ic_gmail_failed);
            } else {
                holder.gmailStatus.setImageResource(R.drawable.ic_gmail_pending);
            }
        } else {
            holder.gmailStatus.setVisibility(View.GONE);
        }

        if (driveEnabled) {
            holder.driveStatus.setVisibility(View.VISIBLE);
            if (message.driveSent()) {
                holder.driveStatus.setVisibility(View.VISIBLE);
                holder.driveStatus.setImageResource(R.drawable.drive_success);
            } else if (message.driveFailed()) {
                holder.driveStatus.setVisibility(View.VISIBLE);
                holder.driveStatus.setImageResource(R.drawable.drive_failed);
            } else {
                holder.driveStatus.setVisibility(View.GONE);
            }
        } else {
            holder.driveStatus.setVisibility(View.GONE);
        }

        holder.xmppStatus.setVisibility(View.GONE);

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

    public List<SMSMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateMessages(List<SMSMessage> newMessages) {
        Log.d("MessagesAdapter", "Updating messages. Old size: " + messages.size() + ", New size: " + newMessages.size());

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
                return oldMessage.message().equals(newMessage.message()) &&
                        oldMessage.uploadedToVPS() == newMessage.uploadedToVPS() &&
                        oldMessage.uploadFailed() == newMessage.uploadFailed() &&
                        oldMessage.googleSent() == newMessage.googleSent() &&
                        oldMessage.googleFailed() == newMessage.googleFailed() &&
                        oldMessage.driveSent() == newMessage.driveSent() &&
                        oldMessage.driveFailed() == newMessage.driveFailed();
            }
        });

        messages.clear();
        messages.addAll(newMessages);
        diffResult.dispatchUpdatesTo(this);

        Log.d("MessagesAdapter", "Messages updated successfully");
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        return new MessageViewHolder(view);
    }

    private void applyMessageStyling(TextView textView, String message) {
        if (message == null) message = "";
        // Hyödynnetään utilia: linkittää URLit, tekee 4+ numerot klikattaviksi kopiointiin,
        // ja asettaa pitkän painalluksen kopioimaan koko viestin.
        TextCopyUtils.applyClickableNumberCopy(textView, message, textView.getContext());
    }





    public void setMessages(List<SMSMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        // Käytetään spesifisempää päivitysmetodia notifyItemRangeInserted
        notifyItemRangeInserted(0, newMessages.size());
    }

    public void updateFontSize() {
        float newScale = FontSizeManager.getInstance(context).getFontScale();
        if (Math.abs(newScale - currentFontScale) > 0.001f) {
            currentFontScale = newScale;
            notifyItemRangeChanged(0, messages.size());
        }
    }
}