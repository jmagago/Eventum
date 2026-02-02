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
        java.util.Date now = new java.util.Date();
        // Cargamos todos los eventos futuros (públicos y privados) para mostrar en la lista
        db.collection("events")
                .whereGreaterThanOrEqualTo("date", now)
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
                .addOnFailureListener(e -> {
                    // Fallback: traer todo y filtrar por fecha >= hoy
                    db.collection("events")
                            .get()
                            .addOnSuccessListener(all -> {
                                List<Event> future = new java.util.ArrayList<>();
                                for (QueryDocumentSnapshot d2 : all) {
                                    try {
                                        Event ev2 = d2.toObject(Event.class);
                                        ev2.setId(d2.getId());
                                        if (ev2.getDate() != null && !ev2.getDate().before(now)) {
                                            future.add(ev2);
                                        }
                                    } catch (Exception ignore) {}
                                }
                                callback.onSuccess(future);
                            })
                            .addOnFailureListener(e3 -> callback.onError("Error al cargar eventos: " + e3.getMessage()));
                });
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
    
}
