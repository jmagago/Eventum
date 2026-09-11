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
import com.us.eventum.utils.EventImageManager;
import com.us.eventum.utils.LocaleUtils;
import de.hdodenhof.circleimageview.CircleImageView;
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

    public enum CardDisplayMode {
        DEFAULT,
        HISTORY
    }

    private List<Event> events = new ArrayList<>();
    private OnEventClickListener listener;
    private FirebaseFirestore db;
    private boolean useDynamicParticipantCount = true;
    private boolean showQrQuickAction = false;
    private CardDisplayMode cardDisplayMode = CardDisplayMode.DEFAULT;
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
        List<Event> oldEvents = this.events;
        this.events = newEvents;

        if (oldSize != newSize) {
            notifyListSizeChanged(oldEvents, newEvents, oldSize, newSize);
            return;
        }
        for (int i = 0; i < newSize; i++) {
            notifyItemDiff(oldEvents.get(i), newEvents.get(i), i);
        }
    }

    private void notifyItemDiff(Event oldEvent, Event newEvent, int position) {
        if (oldEvent == null || newEvent == null
                || oldEvent.getId() == null || newEvent.getId() == null
                || !oldEvent.getId().equals(newEvent.getId())) {
            notifyItemChanged(position);
            return;
        }
        int flags = EventChangePayload.computeFlags(oldEvent, newEvent);
        if (flags != 0) {
            notifyItemChanged(position, new EventChangePayload(flags));
        }
    }

    private void notifyListSizeChanged(List<Event> oldEvents, List<Event> newEvents,
                                       int oldSize, int newSize) {
        if (newSize > oldSize) {
            for (int i = 0; i < oldSize; i++) {
                notifyItemDiff(oldEvents.get(i), newEvents.get(i), i);
            }
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
            for (int i = 0; i < newSize; i++) {
                notifyItemDiff(oldEvents.get(i), newEvents.get(i), i);
            }
        }
    }

    private static List<Event> copyEvents(List<Event> events) {
        return events != null ? new ArrayList<>(events) : new ArrayList<>();
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

    public void setCardDisplayMode(CardDisplayMode cardDisplayMode) {
        this.cardDisplayMode = cardDisplayMode != null ? cardDisplayMode : CardDisplayMode.DEFAULT;
        notifyItemRangeChanged(0, getItemCount());
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
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty() || position < 0 || position >= events.size()) {
            onBindViewHolder(holder, position);
            return;
        }
        Event event = events.get(position);
        boolean metadata = false;
        boolean stats = false;
        for (Object payload : payloads) {
            if (payload instanceof EventChangePayload change) {
                metadata |= change.includesMetadata();
                stats |= change.includesStats();
            }
        }
        if (!metadata && !stats) {
            onBindViewHolder(holder, position);
            return;
        }
        if (metadata) {
            holder.bindMetadata(event);
        }
        if (stats) {
            holder.bindStats(event);
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView eventCard;
        private TextView titleText;
        private CircleImageView eventImageView;
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
            eventImageView = itemView.findViewById(R.id.eventImageView);
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
            bindMetadata(event);
            if (eventImageView != null) {
                String eventId = event.getId();
                Object imageTag = eventImageView.getTag(R.id.tag_image_load_key);
                if (imageTag != null && eventId != null
                        && !imageTag.toString().startsWith(eventId + "#")) {
                    eventImageView.setImageResource(R.mipmap.ic_launcher);
                    eventImageView.setTag(R.id.tag_image_load_key, null);
                }
                EventImageManager.loadEventImage(itemView.getContext(), eventImageView, eventId);
            }

            bindStats(event);
        }

        void bindMetadata(Event event) {
            titleText.setText(event.getTitle());

            boolean historyMode = cardDisplayMode == CardDisplayMode.HISTORY;
            itemView.setAlpha(historyMode ? 0.72f : 1f);

            boolean isPrivate = event.getPrivateEvent();
            if (isPrivate) {
                privateIcon.setImageResource(R.drawable.ic_lock_closed);
                privateIcon.setVisibility(View.VISIBLE);
            } else {
                privateIcon.setImageResource(R.drawable.ic_lock_open);
                privateIcon.setVisibility(View.VISIBLE);
            }

            if (event.getDate() != null) {
                String formattedDate = displayDateFormat.format(event.getDate());
                formattedDate = LocaleUtils.capitalizeFirst(formattedDate);
                dateText.setText(formattedDate);
                try {
                    SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    timeText.setText(hourFormat.format(event.getDate()));
                } catch (Exception e) {
                    timeText.setText("");
                }
            } else {
                dateText.setText("");
                timeText.setText("");
            }

            locationText.setText(event.getLocation());
        }

        void bindStats(Event event) {
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
            if (!showQrQuickAction || qrButton == null
                    || cardDisplayMode == CardDisplayMode.HISTORY) {
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

        private void applyEnrolledCardStyle(Event event) {
            Resources res = itemView.getContext().getResources();
            int strokePx = Math.round(3f * res.getDisplayMetrics().density);
            if (cardDisplayMode == CardDisplayMode.HISTORY) {
                eventCard.setStrokeWidth(strokePx);
                if (event.isCurrentUserScannedQR()) {
                    eventCard.setStrokeColor(ColorStateList.valueOf(
                            ContextCompat.getColor(itemView.getContext(), R.color.attendee_verified_stroke)));
                } else {
                    eventCard.setStrokeColor(ColorStateList.valueOf(
                            ContextCompat.getColor(itemView.getContext(), R.color.attendee_not_attended_stroke)));
                }
                eventCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), android.R.color.white));
                return;
            }
            if (event.isCurrentUserWaitlistOffered()) {
                eventCard.setStrokeWidth(strokePx);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.waitlist_accent)));
            } else if (event.isCurrentUserOnWaitlist()) {
                eventCard.setStrokeWidth(strokePx);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.attendee_waitlist_stroke)));
            } else if (event.isCurrentUserScannedQR()) {
                eventCard.setStrokeWidth(strokePx);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.attendee_verified_stroke)));
            } else {
                eventCard.setStrokeWidth(0);
                eventCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), android.R.color.transparent)));
            }
            eventCard.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.getContext(), android.R.color.white));
        }
    }
} 