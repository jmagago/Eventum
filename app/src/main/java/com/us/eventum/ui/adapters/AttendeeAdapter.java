package com.us.eventum.ui.adapters;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.us.eventum.R;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.core.utils.ProfileImageManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.ViewHolder> {

    private List<Attendee> attendees = new ArrayList<>();
    private OnAttendeeClickListener onAttendeeClickListener;
    private OnParentalAuthClickListener onParentalAuthClickListener;
    private Map<String, Boolean> scannedAttendeesMap = new HashMap<>();
    private Map<String, String> parentalAuthUrls = new HashMap<>();
    private boolean eventRequiresParentalAuth;
    @Nullable
    private Date eventDate;

    public interface OnAttendeeClickListener {
        void onAttendeeClick(Attendee attendee);
    }

    public interface OnParentalAuthClickListener {
        void onParentalAuthClick(@NonNull Attendee attendee, @Nullable String parentalAuthUrl);
    }

    public AttendeeAdapter(OnAttendeeClickListener onAttendeeClickListener) {
        this.onAttendeeClickListener = onAttendeeClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendee, parent, false);
        return new ViewHolder(view);
    }

    public void setAttendees(List<Attendee> attendees) {
        int oldSize = this.attendees.size();
        if (attendees == null) {
            this.attendees = new ArrayList<>();
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize);
            }
            return;
        }

        attendees.sort((a1, a2) -> {
            String lastName1 = a1.getLastName() != null ? a1.getLastName() : "";
            String lastName2 = a2.getLastName() != null ? a2.getLastName() : "";
            return lastName1.compareToIgnoreCase(lastName2);
        });

        this.attendees = attendees;
        notifyListSizeChanged(oldSize, this.attendees.size());
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendee attendee = attendees.get(position);
        holder.nameTextView.setText(attendee.getSortedNameLabel());

        String dni = attendee.getDni();
        if (dni != null && !dni.isEmpty()) {
            holder.dniTextView.setText(dni);
            holder.dniTextView.setVisibility(View.VISIBLE);
        } else {
            holder.dniTextView.setVisibility(View.GONE);
        }

        holder.emailTextView.setText(attendee.getEmail());

        String phone = attendee.getPhone();
        if (phone != null && !phone.isEmpty()) {
            holder.phoneTextView.setText(phone);
            holder.phoneTextView.setVisibility(View.VISIBLE);
        } else {
            holder.phoneTextView.setVisibility(View.GONE);
        }

        ProfileImageManager.loadProfileImage(
                holder.itemView.getContext(),
                holder.profileImageView,
                attendee);

        boolean isScanned = scannedAttendeesMap.getOrDefault(attendee.getUid(), false);
        applyVerifiedCardStyle(holder, isScanned);

        String parentalAuthUrl = parentalAuthUrls.get(attendee.getUid());
        boolean showParentalAuthIcon = shouldShowParentalAuthIcon(attendee);
        if (showParentalAuthIcon) {
            holder.parentalAuthIcon.setVisibility(View.VISIBLE);
            holder.parentalAuthIcon.setOnClickListener(v -> {
                if (onParentalAuthClickListener != null) {
                    onParentalAuthClickListener.onParentalAuthClick(attendee, parentalAuthUrl);
                }
            });
        } else {
            holder.parentalAuthIcon.setVisibility(View.GONE);
            holder.parentalAuthIcon.setOnClickListener(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (onAttendeeClickListener != null) {
                onAttendeeClickListener.onAttendeeClick(attendee);
            }
        });
    }

    private void applyVerifiedCardStyle(ViewHolder holder, boolean isScanned) {
        Resources res = holder.itemView.getContext().getResources();
        int strokePx = Math.round(3f * res.getDisplayMetrics().density);
        if (isScanned) {
            holder.attendeeCard.setStrokeWidth(strokePx);
            holder.attendeeCard.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.attendee_verified_stroke)));
        } else {
            holder.attendeeCard.setStrokeWidth(0);
            holder.attendeeCard.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent)));
        }
        holder.attendeeCard.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
    }

    @Override
    public int getItemCount() {
        return attendees.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView attendeeCard;
        ImageView profileImageView;
        TextView nameTextView;
        TextView dniTextView;
        TextView emailTextView;
        TextView phoneTextView;
        ImageView parentalAuthIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            attendeeCard = (MaterialCardView) itemView;
            profileImageView = itemView.findViewById(R.id.attendeeProfileImage);
            nameTextView = itemView.findViewById(R.id.attendeeNameTextView);
            dniTextView = itemView.findViewById(R.id.attendeeDniTextView);
            emailTextView = itemView.findViewById(R.id.attendeeEmailTextView);
            phoneTextView = itemView.findViewById(R.id.attendeePhoneTextView);
            parentalAuthIcon = itemView.findViewById(R.id.parentalAuthIcon);
        }
    }

    public void setOnAttendeeClickListener(OnAttendeeClickListener onAttendeeClickListener) {
        this.onAttendeeClickListener = onAttendeeClickListener;
    }

    public void setOnParentalAuthClickListener(@Nullable OnParentalAuthClickListener listener) {
        this.onParentalAuthClickListener = listener;
    }

    public void setScannedAttendeesMap(Map<String, Boolean> scannedAttendeesMap) {
        this.scannedAttendeesMap = scannedAttendeesMap != null ? scannedAttendeesMap : new HashMap<>();
        if (!this.attendees.isEmpty()) {
            notifyItemRangeChanged(0, this.attendees.size());
        }
    }

    public void setParentalAuthData(boolean eventRequiresParentalAuth,
                                    @Nullable Date eventDate,
                                    @Nullable Map<String, String> urls) {
        this.eventRequiresParentalAuth = eventRequiresParentalAuth;
        this.eventDate = eventDate;
        this.parentalAuthUrls = urls != null ? new HashMap<>(urls) : new HashMap<>();
        if (!this.attendees.isEmpty()) {
            notifyItemRangeChanged(0, this.attendees.size());
        }
    }

    private boolean shouldShowParentalAuthIcon(@NonNull Attendee attendee) {
        if (!eventRequiresParentalAuth || eventDate == null) {
            return false;
        }
        return attendee.isRequiresParentalAuthorization(eventDate);
    }

    private void notifyListSizeChanged(int oldSize, int newSize) {
        if (newSize == oldSize) {
            if (newSize > 0) {
                notifyItemRangeChanged(0, newSize);
            }
        } else if (newSize > oldSize) {
            if (oldSize > 0) {
                notifyItemRangeChanged(0, oldSize);
            }
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
            if (newSize > 0) {
                notifyItemRangeChanged(0, newSize);
            }
        }
    }
}
