package com.sms2vps.app;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.widget.ImageView;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder> {
    private final List<SMSConversation> conversations = new ArrayList<>();
    private final ConversationClickListener clickListener;
    private final SimpleDateFormat dateFormat;
    private int selectedPosition = -1;
    private float fontScale = 1.0f; // Lisätään fonttikoon skaalauskerroin

    public interface ConversationClickListener {
        void onConversationClick(int position, SMSConversation conversation);
    }

    public ConversationsAdapter(ConversationClickListener clickListener) {
        this.clickListener = clickListener;
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    public void setFontScale(float scale) {
        this.fontScale = scale;
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Log.d("VPSIconDebug", "onBindViewHolder: position=" + position);
        int adapterPosition = holder.getAbsoluteAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) return;
        
        SMSConversation conversation = conversations.get(adapterPosition);
        holder.contactName.setText(conversation.phoneNumber());
        // Etusivulla EI tehdä kopiointilinkityksiä; näytetään pelkkä teksti
        holder.lastMessage.setText(conversation.lastMessage());
        
        try {
            long timestamp = Long.parseLong(conversation.timestamp());
            String formattedTime = dateFormat.format(new Date(timestamp));
            holder.timestamp.setText(formattedTime);
        } catch (NumberFormatException e) {
            holder.timestamp.setText(conversation.timestamp());
        }

        holder.unreadBadge.setVisibility(View.VISIBLE);
        if (conversation.unreadCount() > 0) {
            holder.unreadBadge.setText(String.valueOf(conversation.unreadCount()));
            holder.unreadBadge.setTextColor(0xFFFFFFFF);
            holder.unreadBadge.setBackgroundResource(R.drawable.badge_background);
        } else {
            holder.unreadBadge.setBackgroundResource(R.drawable.ic_circle_check);
            holder.unreadBadge.setText("");
            holder.unreadBadge.setTextColor(0xFF00008B);
        }

        // Pilvisymbolin tila asetetaan myöhemmin viimeisimmän viestin perusteella
        try (DatabaseHelper dbHelper = new DatabaseHelper(holder.itemView.getContext())) {
            SMSMessage latestMessage = dbHelper.getLatestMessage(conversations.get(position).phoneNumber());
            Log.d("VPSIconDebug", "Bind: phoneNumber=" + conversations.get(position).phoneNumber() + ", latestMessage.uploadedToVPS=" + (latestMessage != null ? latestMessage.uploadedToVPS() : "null"));
            if (latestMessage != null) {
                String logMessage = "Binding conversation: " + latestMessage + ", uploadedToVPS=" + latestMessage.uploadedToVPS() + ", uploadFailed=" + latestMessage.uploadFailed();
                Log.d("VPSIconDebug", logMessage);
                // VPS-tilakuvake - näytetään vain jos forwarding päällä ja asetukset tallennettu
                SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(holder.itemView.getContext());
                boolean vpsForwardingEnabled = prefs.getBoolean("enable_forwarding", false);
                boolean vpsSettingsSaved = prefs.getBoolean("vps_settings_saved", false);

                if (!(vpsForwardingEnabled && vpsSettingsSaved)) {
                    holder.cloudStatus.setVisibility(View.GONE);
                } else {
                    Log.d("VPSIconDebug", logMessage);
                    holder.cloudStatus.setVisibility(View.VISIBLE); // Varmistetaan että kuvake on näkyvissä
                    if (latestMessage.uploadedToVPS()) {
                        holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_success);
                        Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_success");
                    } else if (latestMessage.uploadFailed()) {
                        holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_error);
                        Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_error");
                    } else {
                        holder.cloudStatus.setImageResource(R.drawable.ic_cloud_upload_progress);
                        Log.d("VPSIconDebug", "Set icon: ic_cloud_upload_progress");
                    }
                }

                // Gmail-tilakuvake
                boolean gmailForwardingEnabled = prefs.getBoolean("google_gmail_enabled", false);
                if (!gmailForwardingEnabled) {
                    holder.gmailStatus.setVisibility(View.GONE); // Piilota, jos Gmail-lähetys ei ole käytössä
                } else {
                    if (latestMessage.googleSent()) {
                        holder.gmailStatus.setImageResource(R.drawable.ic_gmail_sent);
                    } else if (latestMessage.googleFailed()) {
                        holder.gmailStatus.setImageResource(R.drawable.ic_gmail_failed);
                    } else {
                        holder.gmailStatus.setImageResource(R.drawable.ic_gmail_pending);
                    }
                    holder.gmailStatus.setVisibility(View.VISIBLE);
                }

                // Google Drive -tilakuvake
                boolean driveForwardingEnabled = prefs.getBoolean("google_drive_enabled", false);
                if (!driveForwardingEnabled) {
                    holder.driveStatus.setVisibility(View.GONE); // Piilota, jos Drive-lähetys ei ole käytössä
                } else {
                    if (latestMessage.driveSent()) {
                        holder.driveStatus.setImageResource(R.drawable.drive_success);
                        holder.driveStatus.setVisibility(View.VISIBLE);
                    } else if (latestMessage.driveFailed()) {
                        holder.driveStatus.setImageResource(R.drawable.drive_failed);
                        holder.driveStatus.setVisibility(View.VISIBLE);
                    } else {
                        holder.driveStatus.setVisibility(View.GONE);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ConversationsAdapter", "Error getting latest message status", e);
        }

        holder.itemView.setOnLongClickListener(null);

        holder.itemView.setOnClickListener(v -> {
            int clickPosition = holder.getAbsoluteAdapterPosition();
            if (clickPosition == RecyclerView.NO_POSITION) return;

            if (selectedPosition != -1) {
                int oldSelected = selectedPosition;
                selectedPosition = -1;
                notifyItemChanged(oldSelected);
                if (clickPosition != oldSelected) notifyItemChanged(clickPosition);
            } else if (clickListener != null) {
                // Navigoi heti keskusteluun; DB-kirjoitus ei saa estää siirtymistä
                clickListener.onConversationClick(clickPosition, conversation);
                // Merkitään keskustelu luetuksi parhaan yrityksen periaatteella
                try (DatabaseHelper dbHelper = new DatabaseHelper(v.getContext())) {
                    dbHelper.markConversationAsRead(conversation.phoneNumber());
                    holder.unreadBadge.setBackgroundResource(R.drawable.ic_circle_check);
                    holder.unreadBadge.setText("");
                    holder.unreadBadge.setTextColor(0xFF00008B);
                } catch (Exception e) {
                    Log.e("ConversationsAdapter", "Error marking conversation as read", e);
                }
            }
        });

        // Etusivulla viestin tekstin napautus toimii kuten koko rivin napautus
        holder.lastMessage.setOnClickListener(v -> holder.itemView.performClick());
        
        // Päivitetään tekstikenttien fonttikoko
        holder.contactName.setTextSize(16 * fontScale); // Peruskoko 16sp
        holder.lastMessage.setTextSize(14 * fontScale); // Peruskoko 14sp
        holder.timestamp.setTextSize(12 * fontScale);   // Peruskoko 12sp
        holder.unreadBadge.setTextSize(12 * fontScale); // Peruskoko 12sp
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final TextView contactName;
        final TextView lastMessage;
        final TextView timestamp;
        final TextView unreadBadge;
        final ImageView cloudStatus;
        final ImageView gmailStatus;
        final ImageView driveStatus;

        ConversationViewHolder(View itemView) {
            super(itemView);
            contactName = itemView.findViewById(R.id.phone_number);
            lastMessage = itemView.findViewById(R.id.last_message);
            timestamp = itemView.findViewById(R.id.timestamp);
            unreadBadge = itemView.findViewById(R.id.unread_badge);
            cloudStatus = itemView.findViewById(R.id.cloud_status);
            gmailStatus = itemView.findViewById(R.id.gmail_status);
            driveStatus = itemView.findViewById(R.id.drive_status);
        }
    }

    public void updateConversations(List<SMSConversation> newConversations) {
        Log.d("VPSIconDebug", "updateConversations: called with " + (newConversations == null ? 0 : newConversations.size()) + " conversations");
        if (newConversations == null) return;
        Log.d("ConversationsAdapter", "updateConversations: updating with " + newConversations.size() + " conversations");
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return conversations.size();
            }

            @Override
            public int getNewListSize() {
                return newConversations.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return conversations.get(oldItemPosition).phoneNumber().equals(
                    newConversations.get(newItemPosition).phoneNumber());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SMSConversation oldItem = conversations.get(oldItemPosition);
                SMSConversation newItem = newConversations.get(newItemPosition);
                return oldItem.lastMessage().equals(newItem.lastMessage()) &&
                       oldItem.timestamp().equals(newItem.timestamp()) &&
                       oldItem.unreadCount() == newItem.unreadCount() &&
                       oldItem.uploadedToVPS() == newItem.uploadedToVPS() &&
                       oldItem.uploadFailed() == newItem.uploadFailed() &&
                       oldItem.googleSent() == newItem.googleSent() &&
                       oldItem.googleFailed() == newItem.googleFailed() &&
                       oldItem.driveSent() == newItem.driveSent() &&
                       oldItem.driveFailed() == newItem.driveFailed();
            }
        });
        conversations.clear();
        conversations.addAll(newConversations);
        diffResult.dispatchUpdatesTo(this);
        Log.d("VPSIconDebug", "updateConversations: finished updating conversations");
    }

    public SMSConversation getConversationAt(int position) {
        return conversations.get(position);
    }

    public void removeConversationAt(int position) {
        conversations.remove(position);
        notifyItemRemoved(position);
    }

    public void restoreConversationAt(int position, SMSConversation conversation) {
        conversations.add(position, conversation);
        notifyItemInserted(position);
    }
}