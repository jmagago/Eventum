package com.us.eventum.adapters;

import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;
import com.us.eventum.utils.LocaleUtils;
import android.content.res.ColorStateList;
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
    private boolean showQrQuickAction = false;
    private SimpleDateFormat dateFormat;

    public interface OnEventClickListener {
        void onEventClick(Event event);
        void onEventLongClick(View view, Event event);
        void onLockIconLongClick(Event event);
        default void onEventQrClick(Event event) {
        }
    }

    /**
     * Constructor con listener para eventos de clic
     */
    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = copyEvents(events);
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
     * Actualiza la lista de eventos y notifica cambios al adaptador.
     * Copia defensiva: la lista externa puede mutarse (clear/add) sin romper el RecyclerView.
     */
    public void setEvents(List<Event> events) {
        List<Event> newEvents = copyEvents(events);
        int oldSize = this.events.size();
        int newSize = newEvents.size();
        this.events = newEvents;
        notifyListSizeChanged(oldSize, newSize);
    }

    private static List<Event> copyEvents(List<Event> events) {
        return events != null ? new ArrayList<>(events) : new ArrayList<>();
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

    /**
     * Configura si se debe usar el recuento dinámico de participantes de Firestore
     */
    public void setUseDynamicParticipantCount(boolean useDynamicParticipantCount) {
        this.useDynamicParticipantCount = useDynamicParticipantCount;
    }

    public void setShowQrQuickAction(boolean showQrQuickAction) {
        this.showQrQuickAction = showQrQuickAction;
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
        if (position >= 0 && position < events.size()) {
            Event event = events.get(position);
            holder.bind(event);
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView eventCard;
        private TextView titleText;
        private TextView dateText;
        private TextView locationText;
        private TextView participantsText;
        private TextView timeText;
        private ImageView privateIcon;
        private ImageButton qrButton;
        private SimpleDateFormat displayDateFormat;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventCard = (MaterialCardView) itemView;
            titleText = itemView.findViewById(R.id.eventTitleTextView);
            dateText = itemView.findViewById(R.id.eventDateTextView);
            locationText = itemView.findViewById(R.id.eventLocationTextView);
            participantsText = itemView.findViewById(R.id.eventParticipantsTextView);
            timeText = itemView.findViewById(R.id.eventTimeTextView);
            privateIcon = itemView.findViewById(R.id.eventPrivateIcon);
            qrButton = itemView.findViewById(R.id.eventQrButton);
            displayDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", com.us.eventum.utils.LocaleUtils.spanish());

            // Configurar clic largo en el icono del candado para cambiar privacidad
            privateIcon.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null && 
                    position < events.size() && !events.isEmpty()) {
                    Log.d("EventAdapter", "Clic largo en candado para evento: " + events.get(position).getTitle());
                    EventAdapter.this.listener.onLockIconLongClick(events.get(position));
                    return true;
                }
                return false;
            });

            // Configurar los clics usando el listener del adaptador
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null && 
                    position < events.size() && !events.isEmpty()) {
                    Log.d("EventAdapter", "Click normal en posición: " + position);
                    EventAdapter.this.listener.onEventClick(events.get(position));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null && 
                    position < events.size() && !events.isEmpty()) {
                    Log.d("EventAdapter", "Click largo en posición: " + position);
                    EventAdapter.this.listener.onEventLongClick(v, events.get(position));
                    return true;
                }
                return false;
            });
        }

        public void bind(Event event) {
            titleText.setText(event.getTitle());
            
            // Mostrar icono de candado según el tipo de evento
            boolean isPrivate = event.getPrivateEvent();
            Log.d("EventAdapter", "Evento: " + event.getTitle() + " - isPrivate: " + isPrivate);
            if (isPrivate) {
                privateIcon.setImageResource(R.drawable.ic_lock_closed);
                privateIcon.setVisibility(View.VISIBLE);
                Log.d("EventAdapter", "Mostrando candado CERRADO para: " + event.getTitle());
            } else {
                privateIcon.setImageResource(R.drawable.ic_lock_open);
                privateIcon.setVisibility(View.VISIBLE);
                Log.d("EventAdapter", "Mostrando candado ABIERTO para: " + event.getTitle());
            }
            
            // Formatear la fecha y la hora
            String formattedDate = displayDateFormat.format(event.getDate());
            formattedDate = LocaleUtils.capitalizeFirst(formattedDate);
            dateText.setText(formattedDate);

            // Hora en formato HH:mm
            try {
                SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeText.setText(hourFormat.format(event.getDate()));
            } catch (Exception e) {
                timeText.setText("");
            }
            
            locationText.setText(event.getLocation());
            
            // Mostrar el número de participantes o mensaje de error
            if (event.getCurrentParticipants() == -1) {
                participantsText.setText(itemView.getContext().getString(R.string.event_participants_load_error));
                participantsText.setTextColor(itemView.getContext().getResources().getColor(R.color.colorError, itemView.getContext().getTheme()));
            } else {
                participantsText.setText(itemView.getContext().getResources().getQuantityString(
                        R.plurals.event_participants_count,
                        event.getMaxParticipants(),
                        event.getCurrentParticipants(),
                        event.getMaxParticipants()));
                participantsText.setTextColor(itemView.getContext().getResources().getColor(R.color.colorSecondaryText, itemView.getContext().getTheme()));
            }

            applyEnrolledCardStyle(event);
            bindQrQuickAction(event);
        }

        private void bindQrQuickAction(Event event) {
            if (!showQrQuickAction || qrButton == null) {
                if (qrButton != null) {
                    qrButton.setVisibility(View.GONE);
                }
                return;
            }
            qrButton.setVisibility(View.VISIBLE);
            qrButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && EventAdapter.this.listener != null
                        && position < events.size()) {
                    EventAdapter.this.listener.onEventQrClick(events.get(position));
                }
            });
        }

        /**
         * Resalta en verde solo cuando el check-in QR del asistente ya fue validado.
         */
        private void applyEnrolledCardStyle(Event event) {
            Resources res = itemView.getContext().getResources();
            int strokePx = Math.round(3f * res.getDisplayMetrics().density);
            if (event.isCurrentUserScannedQR()) {
                eventCard.setStrokeWidth(strokePx);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.attendee_verified_stroke)));
                eventCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.attendee_verified_background));
            } else {
                eventCard.setStrokeWidth(0);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), android.R.color.transparent)));
                eventCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), android.R.color.white));
            }
        }
    }
} 