package com.us.eventum.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.us.eventum.R;
import com.us.eventum.models.Attendee;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.ViewHolder> {
    private List<Attendee> attendees = new ArrayList<>();
    private OnAttendeeClickListener onAttendeeClickListener;
    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public interface OnAttendeeClickListener {
        void onAttendeeClick(Attendee attendee);
    }

    public AttendeeAdapter(OnAttendeeClickListener onAttendeeClickListener) {
        this.onAttendeeClickListener = onAttendeeClickListener;
    }

    public void setAttendees(List<Attendee> attendees) {
        this.attendees = attendees;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendee, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendee attendee = attendees.get(position);
        
        // Mostrar nombre completo
        String fullName = attendee.getName() + " " + (attendee.getLastName() != null ? attendee.getLastName() : "");
        holder.nameTextView.setText(fullName.trim());
        
        // Mostrar email
        holder.emailTextView.setText(attendee.getEmail());
        
        // Mostrar teléfono con formato
        String phone = attendee.getPhone();
        if (phone != null && !phone.isEmpty()) {
            holder.phoneTextView.setText(phone);
            holder.phoneTextView.setVisibility(View.VISIBLE);
        } else {
            holder.phoneTextView.setVisibility(View.GONE);
        }

        // Configurar el clic en el item
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
            nameTextView = itemView.findViewById(R.id.attendeeName);
            emailTextView = itemView.findViewById(R.id.attendeeEmail);
            phoneTextView = itemView.findViewById(R.id.attendeePhone);
        }
    }

    private void showAttendeeDetails(Attendee attendee) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_attendee_details, null);
        
        TextView nameTextView = dialogView.findViewById(R.id.detailNameTextView);
        TextView emailTextView = dialogView.findViewById(R.id.detailEmailTextView);
        TextView phoneTextView = dialogView.findViewById(R.id.detailPhoneTextView);
        TextView birthDateTextView = dialogView.findViewById(R.id.detailBirthDateTextView);
        TextView parentalAuthTextView = dialogView.findViewById(R.id.detailParentalAuthTextView);

        nameTextView.setText(attendee.getName());
        emailTextView.setText(attendee.getEmail());
        phoneTextView.setText(attendee.getPhone());
        
        // Convertir Timestamp a String formateado
        if (attendee.getBirthDate() != null) {
            Date birthDate = attendee.getBirthDate().toDate();
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            birthDateTextView.setText(dateFormat.format(birthDate));
        } else {
            birthDateTextView.setText("No especificada");
        }
        
        parentalAuthTextView.setText(attendee.isRequiresParentalAuthorization() ? 
            "Requiere autorización parental" : "No requiere autorización parental");

        new AlertDialog.Builder(context)
            .setTitle("Detalles del Asistente")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show();
    }
} 