package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.repositories.AttendeesToEventRepository;

import java.util.ArrayList;
import java.util.List;

public class FirebaseAttendeesToEventRepository implements AttendeesToEventRepository {
    private final FirebaseFirestore db;

    public FirebaseAttendeesToEventRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void createAttendeeToEvent(AttendeesToEvent attendeeToEvent, AttendeesToEventRepository.RepositoryCallback<AttendeesToEvent> callback) {
        // Primero verificar si ya existe una inscripción del mismo usuario al mismo evento
        db.collection("attendeesToEvent")
                .whereEqualTo("eventId", attendeeToEvent.getEventId())
                .whereEqualTo("userId", attendeeToEvent.getUserId())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Ya existe una inscripción
                        callback.onError("Ya estás inscrito en este evento");
                        return;
                    }
                    
                    // No existe, crear la nueva inscripción
                    db.collection("attendeesToEvent")
                            .add(attendeeToEvent)
                            .addOnSuccessListener(documentReference -> {
                                attendeeToEvent.setId(documentReference.getId());
                                callback.onSuccess(attendeeToEvent);
                            })
                            .addOnFailureListener(e -> callback.onError("Error al crear inscripción: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("Error al verificar inscripción: " + e.getMessage()));
    }

    @Override
    public void loadAttendeesToEvent(String eventId, AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>> callback) {
        db.collection("attendeesToEvent")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AttendeesToEvent> attendees = new ArrayList<>();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        AttendeesToEvent attendee = document.toObject(AttendeesToEvent.class);
                        attendee.setId(document.getId());
                        attendees.add(attendee);
                    }
                    callback.onSuccess(attendees);
                })
                .addOnFailureListener(e -> callback.onError("Error al cargar asistentes: " + e.getMessage()));
    }

    @Override
    public void deleteAttendeeToEvent(String attendeeToEventId, AttendeesToEventRepository.RepositoryCallback<Void> callback) {
        db.collection("attendeesToEvent")
                .document(attendeeToEventId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("Error al eliminar inscripción: " + e.getMessage()));
    }

    @Override
    public void updateAttendeeToEvent(AttendeesToEvent attendeeToEvent, AttendeesToEventRepository.RepositoryCallback<AttendeesToEvent> callback) {
        db.collection("attendeesToEvent")
                .document(attendeeToEvent.getId())
                .set(attendeeToEvent)
                .addOnSuccessListener(aVoid -> callback.onSuccess(attendeeToEvent))
                .addOnFailureListener(e -> callback.onError("Error al actualizar inscripción: " + e.getMessage()));
    }
}
