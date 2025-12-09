package com.sms2vps.app;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class VPSStorageSwipeCallback extends ItemTouchHelper.SimpleCallback {
    private final VPSMessageAdapter adapter;
    private final VPSStorageActivity activity;

    public VPSStorageSwipeCallback(VPSMessageAdapter adapter, VPSStorageActivity activity) {
        super(0, ItemTouchHelper.LEFT);
        this.adapter = adapter;
        this.activity = activity;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAbsoluteAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            SMSMessage message = adapter.getMessages().get(position);
            activity.removeMessageFromView(message);
            adapter.removeMessage(position);
            Toast.makeText(activity, "Message removed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }
} 