package com.us.eventum.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.R;
import com.us.eventum.models.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private List<Event> events = new ArrayList<>();
    private OnEventClickListener listener;
    private FirebaseFirestore db;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public EventAdapter() {
        db = FirebaseFirestore.getInstance();
    }

    public void setOnItemClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private TextView titleText;
        private TextView dateText;
        private TextView locationText;
        private TextView participantsText;
        private SimpleDateFormat dateFormat;
        private OnEventClickListener listener;

        public EventViewHolder(@NonNull View itemView, OnEventClickListener listener) {
            super(itemView);
            this.listener = listener;
            titleText = itemView.findViewById(R.id.eventTitleTextView);
            dateText = itemView.findViewById(R.id.eventDateTextView);
            locationText = itemView.findViewById(R.id.eventLocationTextView);
            participantsText = itemView.findViewById(R.id.eventParticipantsTextView);
            dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && this.listener != null) {
                    this.listener.onEventClick(events.get(position));
                }
            });
        }

        public void bind(Event event) {
            titleText.setText(event.getTitle());
            dateText.setText(dateFormat.format(event.getDate()));
            locationText.setText(event.getLocation());
            
            // Obtener el número de asistentes desde Firestore
            FirebaseFirestore.getInstance()
                .collection("attendees")
                .whereEqualTo("eventId", event.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int numAttendees = queryDocumentSnapshots.size();
                    participantsText.setText(String.format(Locale.getDefault(), "Asistentes: %d/%d", numAttendees, event.getMaxParticipants()));
                })
                .addOnFailureListener(e -> {
                    participantsText.setText(String.format(Locale.getDefault(), "Asistentes: 0/%d", event.getMaxParticipants()));
                });
        }
    }
} 