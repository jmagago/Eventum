package com.us.eventum.data.repositories.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.us.eventum.R;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.repositories.OrganizerRepository;
import com.us.eventum.core.utils.FirebaseBackendErrorHandler;

import java.util.HashMap;

public class FirebaseOrganizerRepository implements OrganizerRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;

    public FirebaseOrganizerRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public void createOrganizer(Organizer organizer, RepositoryCallback<Organizer> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        organizer.setUid(uid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            String usernameLower = organizer.getUsernameLower();
            DocumentReference usernameRef = db.collection("usernames").document(usernameLower);
            DocumentSnapshot usernameSnapshot = transaction.get(usernameRef);

            if (usernameSnapshot.exists()) {
                throw new FirebaseFirestoreException("Username already in use", FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.set(usernameRef, new HashMap<String, Object>() {{ put("uid", uid); }});
            transaction.set(db.collection("organizers").document(uid), organizer);
            return null;
        })
        .addOnSuccessListener(aVoid -> callback.onSuccess(organizer))
        .addOnFailureListener(e -> {
            if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                callback.onError(FirebaseBackendErrorHandler.getUsernameInUseMessage(null));
            } else {
                callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_create_organizer));
            }
        });
    }

    @Override
    public void getOrganizer(String uid, RepositoryCallback<Organizer> callback) {
        db.collection("organizers").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Organizer organizer = documentSnapshot.toObject(Organizer.class);
                        if (organizer == null) {
                            callback.onError(FirebaseBackendErrorHandler.getOrganizerNotFoundMessage(null));
                            return;
                        }
                        callback.onSuccess(organizer);
                    } else {
                        callback.onError(FirebaseBackendErrorHandler.getOrganizerNotFoundMessage(null));
                    }
                })
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_organizer)));
    }

    @Override
    public void updateOrganizer(Organizer organizer, RepositoryCallback<Organizer> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        organizer.setUid(uid);

        db.collection("organizers").document(uid)
                .set(organizer)
                .addOnSuccessListener(aVoid -> callback.onSuccess(organizer))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_organizer)));
    }

    @Override
    public void deleteOrganizer(String uid, RepositoryCallback<Void> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        getOrganizer(uid, new RepositoryCallback<Organizer>() {
            @Override
            public void onSuccess(Organizer organizer) {
                if (organizer.getUsernameLower() != null) {
                    db.collection("usernames").document(organizer.getUsernameLower()).delete();
                }

                db.collection("organizers").document(uid).delete()
                        .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                        .addOnFailureListener(e -> callback.onError(
                                FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_delete_organizer)));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback) {
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
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_check_username)));
    }
}
