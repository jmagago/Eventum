package com.us.eventum.adapters;

import android.util.Log;
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

/**
 * Adaptador para mostrar eventos en RecyclerViews
 * Soporta tanto contador de participantes estático como dinámico desde Firestore
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private List<Event> events = new ArrayList<>();
    private OnEventClickListener listener;
    private FirebaseFirestore db;
    private boolean useDynamicParticipantCount = true;
    private SimpleDateFormat dateFormat;

    public interface OnEventClickListener {
        void onEventClick(Event event);
        void onEventLongClick(View view, Event event);
    }

    /**
     * Constructor con listener para eventos de clic
     */
    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }

    /**
     * Constructor simple para usar con setOnItemClickListener
     */
    public EventAdapter() {
        this.events = new ArrayList<>();
        this.db = FirebaseFirestore.getInstance();
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }

    /**
     * Establece el listener para eventos de clic
     */
    public void setOnItemClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    /**
     * Actualiza la lista de eventos y notifica cambios al adaptador
     */
    public void setEvents(List<Event> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    /**
     * Configura si se debe usar el recuento dinámico de participantes de Firestore
     */
    public void setUseDynamicParticipantCount(boolean useDynamicParticipantCount) {
        this.useDynamicParticipantCount = useDynamicParticipantCount;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
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
        private SimpleDateFormat displayDateFormat;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.eventTitleTextView);
            dateText = itemView.findViewById(R.id.eventDateTextView);
            locationText = itemView.findViewById(R.id.eventLocationTextView);
            participantsText = itemView.findViewById(R.id.eventParticipantsTextView);
            displayDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

            // Configurar los clics usando el listener del adaptador
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null) {
                    Log.d("EventAdapter", "Click normal en posición: " + position);
                    EventAdapter.this.listener.onEventClick(events.get(position));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null) {
                    Log.d("EventAdapter", "Click largo en posición: " + position);
                    EventAdapter.this.listener.onEventLongClick(v, events.get(position));
                    return true;
                }
                return false;
            });
        }

        public void bind(Event event) {
            titleText.setText(event.getTitle());
            
            // Formatear la fecha
            String formattedDate = displayDateFormat.format(event.getDate());
            formattedDate = formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1);
            dateText.setText(formattedDate);
            
            locationText.setText(event.getLocation());
            
            // Mostrar el número de participantes o mensaje de error
            if (event.getCurrentParticipants() == -1) {
                participantsText.setText("Error al cargar asistentes");
                participantsText.setTextColor(itemView.getContext().getResources().getColor(R.color.colorError));
            } else {
                participantsText.setText(String.format("%d/%d asistentes", 
                    event.getCurrentParticipants(), 
                    event.getMaxParticipants()));
                participantsText.setTextColor(itemView.getContext().getResources().getColor(R.color.colorSecondaryText));
            }
        }
    }
} 