package com.us.eventum.data.repositories.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.repositories.OrganizerRepository;

import java.util.HashMap;

public class FirebaseOrganizerRepository implements OrganizerRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;

    public FirebaseOrganizerRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public void createOrganizer(Organizer organizer, OrganizerRepository.RepositoryCallback<Organizer> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        organizer.setUid(uid);

        // Usar transacción para garantizar unicidad del username
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            String usernameLower = organizer.getUsernameLower();
            DocumentReference usernameRef = db.collection("usernames").document(usernameLower);
            DocumentSnapshot usernameSnapshot = transaction.get(usernameRef);

            if (usernameSnapshot.exists()) {
                throw new FirebaseFirestoreException("Username already in use", FirebaseFirestoreException.Code.ABORTED);
            }

            // Crear índice de username y documento del organizador
            transaction.set(usernameRef, new HashMap<String, Object>() {{ put("uid", uid); }});
            transaction.set(db.collection("organizers").document(uid), organizer);
            return null;
        })
        .addOnSuccessListener(aVoid -> callback.onSuccess(organizer))
        .addOnFailureListener(e -> {
            if (e instanceof FirebaseFirestoreException && 
                ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                callback.onError("Este username ya está en uso");
            } else {
                callback.onError("Error al crear organizador: " + e.getMessage());
            }
        });
    }

    @Override
    public void getOrganizer(String uid, OrganizerRepository.RepositoryCallback<Organizer> callback) {
        db.collection("organizers").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Organizer organizer = documentSnapshot.toObject(Organizer.class);
                        callback.onSuccess(organizer);
                    } else {
                        callback.onError("Organizador no encontrado");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error al obtener organizador: " + e.getMessage()));
    }

    @Override
    public void updateOrganizer(Organizer organizer, OrganizerRepository.RepositoryCallback<Organizer> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        organizer.setUid(uid);

        db.collection("organizers").document(uid)
                .set(organizer)
                .addOnSuccessListener(aVoid -> callback.onSuccess(organizer))
                .addOnFailureListener(e -> callback.onError("Error al actualizar organizador: " + e.getMessage()));
    }

    @Override
    public void deleteOrganizer(String uid, OrganizerRepository.RepositoryCallback<Void> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        // Obtener el organizador para eliminar el índice de username
        getOrganizer(uid, new OrganizerRepository.RepositoryCallback<Organizer>() {
            @Override
            public void onSuccess(Organizer organizer) {
                // Eliminar índice de username
                if (organizer.getUsernameLower() != null) {
                    db.collection("usernames").document(organizer.getUsernameLower()).delete();
                }
                
                // Eliminar documento del organizador
                db.collection("organizers").document(uid).delete()
                        .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                        .addOnFailureListener(e -> callback.onError("Error al eliminar organizador: " + e.getMessage()));
            }

            @Override
            public void onError(String error) {
                callback.onError("Error al obtener organizador para eliminación: " + error);
            }
        });
    }

    @Override
    public void checkUsernameAvailability(String username, OrganizerRepository.RepositoryCallback<Boolean> callback) {
        String normalized = username == null ? null : username.trim().toLowerCase();
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
