package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.AttendeeRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de AttendeeRepository usando Firebase Firestore
 * Maneja todas las operaciones de asistentes en la nube
 */
public class FirebaseAttendeeRepository implements AttendeeRepository {
    
    private final FirebaseFirestore db;
    
    public FirebaseAttendeeRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    @Override
    public void loadEventAttendees(String eventId, RepositoryCallback<List<Attendee>> callback) {
        android.util.Log.d("FirebaseAttendeeRepository", "Consultando Firebase para eventId: " + eventId);
        
        db.collection("attendees")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Attendee> attendees = new ArrayList<>();
                    android.util.Log.d("FirebaseAttendeeRepository", "Documentos encontrados: " + queryDocumentSnapshots.size());
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Attendee attendee = document.toObject(Attendee.class);
                            attendee.setId(document.getId());
                            attendees.add(attendee);
                            android.util.Log.d("FirebaseAttendeeRepository", "Asistente cargado: " + attendee.getName());
                        } catch (Exception e) {
                            android.util.Log.e("FirebaseAttendeeRepository", "Error al convertir documento: " + e.getMessage());
                        }
                    }
                    callback.onSuccess(attendees);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FirebaseAttendeeRepository", "Error en consulta Firebase: " + e.getMessage());
                    callback.onError("Error al cargar asistentes: " + e.getMessage());
                });
    }
    
    @Override
    public void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback) {
        db.collection("attendees")
                .add(attendee)
                .addOnSuccessListener(documentReference -> {
                    attendee.setId(documentReference.getId());
                    callback.onSuccess(attendee);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al crear asistente: " + e.getMessage());
                });
    }
    
    @Override
    public void updateAttendee(String attendeeId, Attendee attendee, RepositoryCallback<Void> callback) {
        db.collection("attendees").document(attendeeId)
                .set(attendee)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al actualizar asistente: " + e.getMessage());
                });
    }
    
    @Override
    public void deleteAttendee(String attendeeId, RepositoryCallback<Void> callback) {
        db.collection("attendees").document(attendeeId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al eliminar asistente: " + e.getMessage());
                });
    }
    
    @Override
    public void verifyAttendee(String attendeeId, String eventId, RepositoryCallback<Void> callback) {
        db.collection("attendees")
                .document(attendeeId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean isVerified = documentSnapshot.getBoolean("verified");
                        if (isVerified != null && isVerified) {
                            callback.onError("Este asistente ya fue verificado");
                            return;
                        }
                        
                        String attendeeEventId = documentSnapshot.getString("eventId");
                        if (!eventId.equals(attendeeEventId)) {
                            callback.onError("Este asistente no pertenece a este evento");
                            return;
                        }
                        
                        documentSnapshot.getReference()
                                .update("verified", true, "verificationTimestamp", System.currentTimeMillis())
                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                .addOnFailureListener(e -> {
                                    callback.onError("Error al verificar asistente: " + e.getMessage());
                                });
                    } else {
                        callback.onError("Asistente no encontrado");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al verificar asistente: " + e.getMessage());
                });
    }
    
    @Override
    public void clearEventAttendees(String eventId, RepositoryCallback<Void> callback) {
        db.collection("attendees")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalToDelete = queryDocumentSnapshots.size();
                    
                    if (totalToDelete == 0) {
                        callback.onError("No hay asistentes para eliminar");
                        return;
                    }
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().delete();
                    }
                    
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al vaciar la lista: " + e.getMessage());
                });
    }
    
    @Override
    public void unsubscribeByEventAndEmail(String eventId, String email, RepositoryCallback<Void> callback) {
        db.collection("attendees")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        callback.onError("No existe inscripción para ese email en este evento");
                        return;
                    }
                    java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(queryDocumentSnapshots.size());
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    if (pending.decrementAndGet() == 0) callback.onSuccess(null);
                                })
                                .addOnFailureListener(e -> callback.onError("Error al desapuntarse: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error al buscar inscripción: " + e.getMessage()));
    }
    
    @Override
    public void syncPendingAttendees(RepositoryCallback<Void> callback) {
        // En Firebase, no hay asistentes pendientes por sincronizar
        // Esta implementación es para compatibilidad con el patrón offline
        callback.onSuccess(null);
    }
}
