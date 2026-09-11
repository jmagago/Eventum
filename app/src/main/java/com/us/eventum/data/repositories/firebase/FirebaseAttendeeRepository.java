package com.us.eventum.data.repositories.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.us.eventum.R;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

import java.util.HashMap;

public class FirebaseAttendeeRepository implements AttendeeRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;

    public FirebaseAttendeeRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        attendee.setUid(uid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            String usernameLower = attendee.getUsernameLower();
            DocumentReference usernameRef = db.collection("usernames").document(usernameLower);
            DocumentSnapshot usernameSnapshot = transaction.get(usernameRef);

            if (usernameSnapshot.exists()) {
                throw new FirebaseFirestoreException("Username already in use", FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.set(usernameRef, new HashMap<String, Object>() {{ put("uid", uid); }});
            transaction.set(db.collection("attendees").document(uid), attendee);
            return null;
        })
        .addOnSuccessListener(aVoid -> callback.onSuccess(attendee))
        .addOnFailureListener(e -> {
            if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                callback.onError(FirebaseBackendErrorHandler.getUsernameInUseMessage(null));
            } else {
                callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_create_attendee));
            }
        });
    }

    @Override
    public void getAttendee(String uid, RepositoryCallback<Attendee> callback) {
        db.collection("attendees").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Attendee attendee = documentSnapshot.toObject(Attendee.class);
                        if (attendee == null) {
                            callback.onError(FirebaseBackendErrorHandler.getAttendeeNotFoundMessage(null));
                            return;
                        }
                        attendee.setUid(uid);
                        callback.onSuccess(attendee);
                    } else {
                        callback.onError(FirebaseBackendErrorHandler.getAttendeeNotFoundMessage(null));
                    }
                })
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_attendee)));
    }

    @Override
    public void updateAttendee(Attendee attendee, RepositoryCallback<Attendee> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        attendee.setUid(uid);

        db.collection("attendees").document(uid)
                .set(attendee)
                .addOnSuccessListener(aVoid -> callback.onSuccess(attendee))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_attendee)));
    }

    @Override
    public void deleteAttendee(String uid, RepositoryCallback<Void> callback) {
        if (mAuth.getCurrentUser() == null) {
            callback.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }

        getAttendee(uid, new RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee attendee) {
                if (attendee.getUsernameLower() != null) {
                    db.collection("usernames").document(attendee.getUsernameLower()).delete();
                }

                db.collection("attendees").document(uid).delete()
                        .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                        .addOnFailureListener(e -> callback.onError(
                                FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_delete_attendee)));
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
