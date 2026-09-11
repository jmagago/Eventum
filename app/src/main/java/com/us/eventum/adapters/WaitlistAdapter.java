package com.us.eventum.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.us.eventum.R;
import com.us.eventum.data.models.WaitlistToEvent;

import java.util.ArrayList;
import java.util.List;

public class WaitlistAdapter extends RecyclerView.Adapter<WaitlistAdapter.ViewHolder> {

    public interface Listener {
        void onRemove(@NonNull WaitlistToEvent entry, @NonNull String displayName);
    }

    public static final class Row {
        public final WaitlistToEvent entry;
        public final String displayName;
        public final int position;
        public final String statusLabel;

        public Row(WaitlistToEvent entry, String displayName, int position, String statusLabel) {
            this.entry = entry;
            this.displayName = displayName;
            this.position = position;
            this.statusLabel = statusLabel;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Row> newRows) {
        int oldSize = rows.size();
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        int newSize = rows.size();
        if (oldSize == 0 && newSize == 0) {
            return;
        }
        if (oldSize == 0) {
            notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0) {
            notifyItemRangeRemoved(0, oldSize);
        } else if (oldSize == newSize) {
            notifyItemRangeChanged(0, newSize);
        } else if (newSize > oldSize) {
            notifyItemRangeChanged(0, oldSize);
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
            notifyItemRangeChanged(0, newSize);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waitlist_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.nameText.setText(row.displayName);
        holder.statusText.setText(row.statusLabel);
        holder.removeButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemove(row.entry, row.displayName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView statusText;
        final MaterialButton removeButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.waitlistItemName);
            statusText = itemView.findViewById(R.id.waitlistItemStatus);
            removeButton = itemView.findViewById(R.id.waitlistRemoveButton);
        }
    }
}
