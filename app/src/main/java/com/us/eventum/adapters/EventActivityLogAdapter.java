package com.us.eventum.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.us.eventum.R;
import com.us.eventum.data.models.EventActivityLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class EventActivityLogAdapter extends RecyclerView.Adapter<EventActivityLogAdapter.ViewHolder> {

    private final List<EventActivityLog> entries = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final Context context;

    public EventActivityLogAdapter(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setEntries(@Nullable List<EventActivityLog> logs) {
        List<EventActivityLog> newEntries = logs != null ? new ArrayList<>(logs) : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return entries.size();
            }

            @Override
            public int getNewListSize() {
                return newEntries.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                EventActivityLog oldEntry = entries.get(oldItemPosition);
                EventActivityLog newEntry = newEntries.get(newItemPosition);
                String oldId = oldEntry.getId();
                String newId = newEntry.getId();
                if (oldId != null && newId != null) {
                    return oldId.equals(newId);
                }
                return oldItemPosition == newItemPosition
                        && Objects.equals(oldEntry.getCreatedAt(), newEntry.getCreatedAt())
                        && Objects.equals(oldEntry.getType(), newEntry.getType());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                EventActivityLog oldEntry = entries.get(oldItemPosition);
                EventActivityLog newEntry = newEntries.get(newItemPosition);
                return Objects.equals(oldEntry.getType(), newEntry.getType())
                        && Objects.equals(oldEntry.getSubjectName(), newEntry.getSubjectName())
                        && Objects.equals(oldEntry.getDetail(), newEntry.getDetail())
                        && Objects.equals(oldEntry.getCreatedAt(), newEntry.getCreatedAt());
            }
        });
        entries.clear();
        entries.addAll(newEntries);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_activity_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventActivityLog entry = entries.get(position);
        holder.bind(entry, position == 0, position == entries.size() - 1);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View timelineLineTop;
        private final View timelineLineBottom;
        private final View timelineDot;
        private final ImageView logTypeIcon;
        private final TextView logActionTextView;
        private final TextView logSubjectTextView;
        private final TextView logDetailTextView;
        private final TextView logTimeTextView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            timelineLineTop = itemView.findViewById(R.id.timelineLineTop);
            timelineLineBottom = itemView.findViewById(R.id.timelineLineBottom);
            timelineDot = itemView.findViewById(R.id.timelineDot);
            logTypeIcon = itemView.findViewById(R.id.logTypeIcon);
            logActionTextView = itemView.findViewById(R.id.logActionTextView);
            logSubjectTextView = itemView.findViewById(R.id.logSubjectTextView);
            logDetailTextView = itemView.findViewById(R.id.logDetailTextView);
            logTimeTextView = itemView.findViewById(R.id.logTimeTextView);
        }

        void bind(EventActivityLog entry, boolean isFirst, boolean isLast) {
            timelineLineTop.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            timelineLineBottom.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            int color = resolveColor(entry.getType());
            timelineDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
            logTypeIcon.setColorFilter(color);
            logTypeIcon.setImageResource(resolveIcon(entry.getType()));

            logActionTextView.setText(resolveActionText(entry.getType()));
            logSubjectTextView.setText(entry.getSubjectName() != null ? entry.getSubjectName() : "");
            logSubjectTextView.setVisibility(
                    entry.getSubjectName() != null && !entry.getSubjectName().isEmpty()
                            ? View.VISIBLE : View.GONE);

            String detail = resolveDetailText(entry);
            if (detail != null && !detail.isEmpty()) {
                logDetailTextView.setText(detail);
                logDetailTextView.setVisibility(View.VISIBLE);
            } else {
                logDetailTextView.setVisibility(View.GONE);
            }

            logTimeTextView.setText(formatTime(entry.getCreatedAt()));
        }

        private int resolveColor(String type) {
            if (type == null) {
                return ContextCompat.getColor(context, R.color.colorPrimary);
            }
            switch (type) {
                case EventActivityLog.TYPE_JOINED:
                    return ContextCompat.getColor(context, R.color.colorSuccess);
                case EventActivityLog.TYPE_VERIFIED_QR:
                case EventActivityLog.TYPE_VERIFIED_MANUAL:
                    return ContextCompat.getColor(context, R.color.colorWarning);
                case EventActivityLog.TYPE_LEFT:
                case EventActivityLog.TYPE_REMOVED:
                case EventActivityLog.TYPE_LIST_CLEARED:
                    return ContextCompat.getColor(context, R.color.colorError);
                case EventActivityLog.TYPE_EVENT_UPDATED:
                    return ContextCompat.getColor(context, R.color.colorPrimary);
                case EventActivityLog.TYPE_EVENT_CANCELLED:
                    return ContextCompat.getColor(context, R.color.event_cancelled_stroke);
                case EventActivityLog.TYPE_WAITLIST_JOINED:
                case EventActivityLog.TYPE_WAITLIST_OFFERED:
                case EventActivityLog.TYPE_WAITLIST_PROMOTED:
                    return ContextCompat.getColor(context, R.color.waitlist_accent);
                case EventActivityLog.TYPE_WAITLIST_LEFT:
                case EventActivityLog.TYPE_WAITLIST_OFFER_EXPIRED:
                    return ContextCompat.getColor(context, R.color.colorSecondaryText);
                default:
                    return ContextCompat.getColor(context, R.color.colorSecondaryText);
            }
        }

        private int resolveIcon(String type) {
            if (type == null) {
                return R.drawable.ic_history;
            }
            switch (type) {
                case EventActivityLog.TYPE_JOINED:
                    return R.drawable.ic_person_add;
                case EventActivityLog.TYPE_LEFT:
                    return R.drawable.ic_person;
                case EventActivityLog.TYPE_REMOVED:
                    return R.drawable.ic_delete;
                case EventActivityLog.TYPE_VERIFIED_QR:
                    return R.drawable.ic_qr_code;
                case EventActivityLog.TYPE_VERIFIED_MANUAL:
                    return R.drawable.ic_check_circle;
                case EventActivityLog.TYPE_LIST_CLEARED:
                    return R.drawable.ic_clear_all;
                case EventActivityLog.TYPE_EVENT_UPDATED:
                    return R.drawable.ic_edit;
                case EventActivityLog.TYPE_EVENT_CANCELLED:
                    return R.drawable.ic_warning;
                case EventActivityLog.TYPE_WAITLIST_JOINED:
                case EventActivityLog.TYPE_WAITLIST_PROMOTED:
                    return R.drawable.ic_person_add;
                case EventActivityLog.TYPE_WAITLIST_LEFT:
                case EventActivityLog.TYPE_WAITLIST_OFFER_EXPIRED:
                    return R.drawable.ic_person;
                case EventActivityLog.TYPE_WAITLIST_OFFERED:
                    return R.drawable.ic_time;
                default:
                    return R.drawable.ic_history;
            }
        }

        private String resolveActionText(String type) {
            if (type == null) {
                return context.getString(R.string.event_log_action_unknown);
            }
            switch (type) {
                case EventActivityLog.TYPE_JOINED:
                    return context.getString(R.string.event_log_action_joined);
                case EventActivityLog.TYPE_LEFT:
                    return context.getString(R.string.event_log_action_left);
                case EventActivityLog.TYPE_REMOVED:
                    return context.getString(R.string.event_log_action_removed);
                case EventActivityLog.TYPE_VERIFIED_QR:
                    return context.getString(R.string.event_log_action_verified_qr);
                case EventActivityLog.TYPE_VERIFIED_MANUAL:
                    return context.getString(R.string.event_log_action_verified_manual);
                case EventActivityLog.TYPE_LIST_CLEARED:
                    return context.getString(R.string.event_log_action_list_cleared);
                case EventActivityLog.TYPE_EVENT_UPDATED:
                    return context.getString(R.string.event_log_action_event_updated);
                case EventActivityLog.TYPE_EVENT_CANCELLED:
                    return context.getString(R.string.event_log_action_event_cancelled);
                case EventActivityLog.TYPE_WAITLIST_JOINED:
                    return context.getString(R.string.event_log_action_waitlist_joined);
                case EventActivityLog.TYPE_WAITLIST_LEFT:
                    return context.getString(R.string.event_log_action_waitlist_left);
                case EventActivityLog.TYPE_WAITLIST_OFFERED:
                    return context.getString(R.string.event_log_action_waitlist_offered);
                case EventActivityLog.TYPE_WAITLIST_OFFER_EXPIRED:
                    return context.getString(R.string.event_log_action_waitlist_offer_expired);
                case EventActivityLog.TYPE_WAITLIST_PROMOTED:
                    return context.getString(R.string.event_log_action_waitlist_promoted);
                default:
                    return context.getString(R.string.event_log_action_unknown);
            }
        }

        private String resolveDetailText(EventActivityLog entry) {
            if (entry.getDetail() == null || entry.getDetail().isEmpty()) {
                return null;
            }
            if (EventActivityLog.TYPE_REMOVED.equals(entry.getType())) {
                return context.getString(R.string.event_log_detail_by_organizer);
            }
            if (EventActivityLog.TYPE_LIST_CLEARED.equals(entry.getType())) {
                try {
                    int count = Integer.parseInt(entry.getDetail());
                    return context.getResources().getQuantityString(
                            R.plurals.event_log_detail_cleared_count, count, count);
                } catch (NumberFormatException ignored) {
                    return entry.getDetail();
                }
            }
            return entry.getDetail();
        }

        private String formatTime(Timestamp createdAt) {
            if (createdAt == null) {
                return "";
            }
            Date date = createdAt.toDate();
            Date now = new Date();
            long diffMs = now.getTime() - date.getTime();
            if (diffMs < 24 * 60 * 60 * 1000L) {
                return timeFormat.format(date);
            }
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            if (dayFormat.format(now).equals(dayFormat.format(date))) {
                return timeFormat.format(date);
            }
            return dateTimeFormat.format(date);
        }
    }
}
