package com.us.eventum.data.repositories.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.AttendeeRepository;

import java.util.HashMap;

public class FirebaseAttendeeRepository implements AttendeeRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;

    public FirebaseAttendeeRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public void createAttendee(Attendee attendee, AttendeeRepository.RepositoryCallback<Attendee> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        attendee.setUid(uid);

        // Usar transacción para garantizar unicidad del username
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            String usernameLower = attendee.getUsernameLower();
            DocumentReference usernameRef = db.collection("usernames").document(usernameLower);
            DocumentSnapshot usernameSnapshot = transaction.get(usernameRef);

            if (usernameSnapshot.exists()) {
                throw new FirebaseFirestoreException("Username already in use", FirebaseFirestoreException.Code.ABORTED);
            }

            // Crear índice de username y documento del asistente
            transaction.set(usernameRef, new HashMap<String, Object>() {{ put("uid", uid); }});
            transaction.set(db.collection("attendees").document(uid), attendee);
            return null;
        })
        .addOnSuccessListener(aVoid -> callback.onSuccess(attendee))
        .addOnFailureListener(e -> {
            if (e instanceof FirebaseFirestoreException && 
                ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                callback.onError("Este username ya está en uso");
            } else {
                callback.onError("Error al crear asistente: " + e.getMessage());
            }
        });
    }

    @Override
    public void getAttendee(String uid, AttendeeRepository.RepositoryCallback<Attendee> callback) {
        db.collection("attendees").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Attendee attendee = documentSnapshot.toObject(Attendee.class);
                        if (attendee == null) {
                            callback.onError("Asistente no encontrado");
                            return;
                        }
                        // El id del documento es el UID de Auth; conviene fijarlo aunque el mapa no traiga el campo
                        attendee.setUid(uid);
                        callback.onSuccess(attendee);
                    } else {
                        callback.onError("Asistente no encontrado");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error al obtener asistente: " + e.getMessage()));
    }

    @Override
    public void updateAttendee(Attendee attendee, AttendeeRepository.RepositoryCallback<Attendee> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        attendee.setUid(uid);

        db.collection("attendees").document(uid)
                .set(attendee)
                .addOnSuccessListener(aVoid -> callback.onSuccess(attendee))
                .addOnFailureListener(e -> callback.onError("Error al actualizar asistente: " + e.getMessage()));
    }

    @Override
    public void deleteAttendee(String uid, AttendeeRepository.RepositoryCallback<Void> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        // Obtener el asistente para eliminar el índice de username
        getAttendee(uid, new AttendeeRepository.RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee attendee) {
                // Eliminar índice de username
                if (attendee.getUsernameLower() != null) {
                    db.collection("usernames").document(attendee.getUsernameLower()).delete();
                }
                
                // Eliminar documento del asistente
                db.collection("attendees").document(uid).delete()
                        .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                        .addOnFailureListener(e -> callback.onError("Error al eliminar asistente: " + e.getMessage()));
            }

            @Override
            public void onError(String error) {
                callback.onError("Error al obtener asistente para eliminación: " + error);
            }
        });
    }

    @Override
    public void checkUsernameAvailability(String username, AttendeeRepository.RepositoryCallback<Boolean> callback) {
        String normalized = username == null ? null : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized == null || normalized.isEmpty()) {
            callback.onSuccess(false);
            return;
        }

        db.collection("usernames")
                .document(normalized)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    boolean isAvailable = !documentSnapshot.exists();
                    callback.onSuccess(isAvailable);
                })
                .addOnFailureListener(e -> callback.onError("Error al verificar username: " + e.getMessage()));
    }
}