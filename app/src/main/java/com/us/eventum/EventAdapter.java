package com.us.eventum;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private List<Event> events;
    private SimpleDateFormat dateFormat;
    private OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public EventAdapter(OnEventClickListener listener) {
        this.events = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        this.listener = listener;
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

    public void setEvents(List<Event> events) {
        this.events = events;
        notifyDataSetChanged();
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
            dateText.setText("Fecha: " + dateFormat.format(event.getDate()));
            locationText.setText("Ubicación: " + event.getLocation());
            participantsText.setText(String.format("Participantes: %d/%d", 
                event.getCurrentParticipants(), event.getMaxParticipants()));
        }
    }
} 