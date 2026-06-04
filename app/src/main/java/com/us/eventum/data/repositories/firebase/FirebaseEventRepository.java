package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.repositories.EventRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación de EventRepository usando Firebase Firestore
 * Maneja todas las operaciones de eventos en la nube
 */
public class FirebaseEventRepository implements EventRepository {
    
    private final FirebaseFirestore db;
    
    public FirebaseEventRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    @Override
    public void loadUserEvents(String userId, RepositoryCallback<List<Event>> callback) {
        db.collection("events")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Event event = document.toObject(Event.class);
                        event.setId(document.getId());
                        events.add(event);
                    }
                    callback.onSuccess(events);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al cargar eventos: " + e.getMessage());
                });
    }
    
    @Override
    public void loadAvailableEvents(RepositoryCallback<List<Event>> callback) {
        // Para la home de asistente se necesita el catálogo completo:
        // - Descubrir (no inscritos y futuros)
        // - Mis eventos (inscritos)
        // - Historial (pasados + QR validado)
        db.collection("events")
                .get()
                .addOnSuccessListener(snap -> {
                    List<Event> events = new java.util.ArrayList<>();
                    for (QueryDocumentSnapshot document : snap) {
                        Event event = document.toObject(Event.class);
                        event.setId(document.getId());
                        events.add(event);
                    }
                    callback.onSuccess(events);
                })
                .addOnFailureListener(e -> callback.onError("Error al cargar eventos: " + e.getMessage()));
    }
    
    @Override
    public void createEvent(Event event, RepositoryCallback<Event> callback) {
        db.collection("events")
                .add(event)
                .addOnSuccessListener(documentReference -> {
                    event.setId(documentReference.getId());
                    callback.onSuccess(event);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al crear evento: " + e.getMessage());
                });
    }
    
    @Override
    public void updateEvent(String eventId, Event event, RepositoryCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", event.getTitle());
        updates.put("description", event.getDescription());
        updates.put("date", event.getDate());
        updates.put("location", event.getLocation());
        updates.put("maxParticipants", event.getMaxParticipants());
        updates.put("eventType", event.getEventType());
        updates.put("privateEvent", event.getPrivateEvent());
        
        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al actualizar evento: " + e.getMessage());
                });
    }
    
    @Override
    public void updateEventPrivacy(String eventId, boolean isPrivate, RepositoryCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("privateEvent", isPrivate);
        
        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al actualizar privacidad del evento: " + e.getMessage());
                });
    }
    
    @Override
    public void deleteEvent(String eventId, RepositoryCallback<Void> callback) {
        db.collection("events").document(eventId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al eliminar evento: " + e.getMessage());
                });
    }

    @Override
    public void getEventById(String eventId, RepositoryCallback<Event> callback) {
        if (eventId == null || eventId.isEmpty()) {
            callback.onError("ID de evento no válido");
            return;
        }
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError("Evento no encontrado");
                        return;
                    }
                    Event event = document.toObject(Event.class);
                    if (event == null) {
                        callback.onError("Error al leer el evento");
                        return;
                    }
                    event.setId(document.getId());
                    callback.onSuccess(event);
                })
                .addOnFailureListener(e ->
                        callback.onError("Error al cargar evento: " + e.getMessage()));
    }
    
}
