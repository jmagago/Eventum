package com.us.eventum.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.us.eventum.R;
import com.us.eventum.models.Attendee;
import java.util.ArrayList;
import java.util.List;

public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.ViewHolder> {

    private List<Attendee> attendees = new ArrayList<>();
    private OnAttendeeClickListener onAttendeeClickListener;

    public interface OnAttendeeClickListener {
        void onAttendeeClick(Attendee attendee);
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
        // Ordenar por apellido
        attendees.sort((a1, a2) -> {
            String lastName1 = a1.getLastName() != null ? a1.getLastName() : "";
            String lastName2 = a2.getLastName() != null ? a2.getLastName() : "";
            return lastName1.compareToIgnoreCase(lastName2);
        });
        
        this.attendees = attendees;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendee attendee = attendees.get(position);
        
        // Formato "Apellidos, Nombre"
        String lastName = attendee.getLastName() != null ? attendee.getLastName() : "";
        String name = attendee.getName() != null ? attendee.getName() : "";
        
        if (!lastName.isEmpty()) {
            holder.nameTextView.setText(lastName + ", " + name);
        } else {
            holder.nameTextView.setText(name);
        }
        
        holder.emailTextView.setText(attendee.getEmail());
        
        String phone = attendee.getPhone();
        if (phone != null && !phone.isEmpty()) {
            holder.phoneTextView.setText(phone);
            holder.phoneTextView.setVisibility(View.VISIBLE);
        } else {
            holder.phoneTextView.setVisibility(View.GONE);
        }

        // Cambiar el borde según el estado de verificación
        if (attendee.isVerified()) {
            holder.itemView.setBackgroundResource(R.drawable.bg_card_verified);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_card_normal);
        }

        holder.itemView.setOnClickListener(v -> {
            if (onAttendeeClickListener != null) {
                onAttendeeClickListener.onAttendeeClick(attendee);
            }
        });
    }

    @Override
    public int getItemCount() {
        return attendees.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView emailTextView;
        TextView phoneTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.attendeeNameTextView);
            emailTextView = itemView.findViewById(R.id.attendeeEmailTextView);
            phoneTextView = itemView.findViewById(R.id.attendeePhoneTextView);
        }
    }

    public void setOnAttendeeClickListener(OnAttendeeClickListener onAttendeeClickListener) {
        this.onAttendeeClickListener = onAttendeeClickListener;
    }
} 