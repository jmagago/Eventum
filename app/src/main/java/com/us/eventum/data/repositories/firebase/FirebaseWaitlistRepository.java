package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.WaitlistRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;
import com.us.eventum.utils.WaitlistUtils;

import java.util.ArrayList;
import java.util.List;

public class FirebaseWaitlistRepository implements WaitlistRepository {

    private static final String COLLECTION = "waitlistToEvent";

    private final FirebaseFirestore db;
    private ListenerRegistration eventWaitlistListenerRegistration;
    private ListenerRegistration allWaitlistListenerRegistration;

    public FirebaseWaitlistRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void createWaitlistEntry(WaitlistToEvent entry, RepositoryCallback<WaitlistToEvent> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("eventId", entry.getEventId())
                .whereEqualTo("userId", entry.getUserId())
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        WaitlistToEvent existing = document.toObject(WaitlistToEvent.class);
                        if (existing != null && existing.isActiveForUser()) {
                            callback.onError(FirebaseBackendErrorHandler.getAlreadyRegisteredMessage(null));
                            return;
                        }
                    }
                    db.collection(COLLECTION)
                            .add(entry)
                            .addOnSuccessListener(documentReference -> {
                                entry.setId(documentReference.getId());
                                callback.onSuccess(entry);
                            })
                            .addOnFailureListener(e -> callback.onError(
                                    FirebaseBackendErrorHandler.getErrorMessage(
                                            e, R.string.backend_op_create_waitlist)));
                })
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_verify_waitlist)));
    }

    @Override
    public void loadWaitlistForEvent(String eventId, RepositoryCallback<List<WaitlistToEvent>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot ->
                        callback.onSuccess(sortEntries(parseSnapshot(querySnapshot))))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_load_waitlist)));
    }

    @Override
    public void updateWaitlistEntry(WaitlistToEvent entry, RepositoryCallback<WaitlistToEvent> callback) {
        if (entry.getId() == null || entry.getId().isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getIncompleteActivityMessage(null));
            return;
        }
        db.collection(COLLECTION)
                .document(entry.getId())
                .set(entry)
                .addOnSuccessListener(aVoid -> callback.onSuccess(entry))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_update_waitlist)));
    }

    @Override
    public void deleteWaitlistEntry(String entryId, RepositoryCallback<Void> callback) {
        db.collection(COLLECTION)
                .document(entryId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_delete_waitlist)));
    }

    @Override
    public void startEventWaitlistListener(String eventId, EventWaitlistListener listener) {
        stopEventWaitlistListener();
        if (eventId == null || eventId.isEmpty()) {
            listener.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        eventWaitlistListenerRegistration = db.collection(COLLECTION)
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_waitlist));
                        return;
                    }
                    listener.onWaitlistUpdated(sortEntries(
                            querySnapshot != null ? parseSnapshot(querySnapshot) : new ArrayList<>()));
                });
    }

    @Override
    public void stopEventWaitlistListener() {
        if (eventWaitlistListenerRegistration != null) {
            eventWaitlistListenerRegistration.remove();
            eventWaitlistListenerRegistration = null;
        }
    }

    @Override
    public void startAllWaitlistListener(AllWaitlistListener listener) {
        stopAllWaitlistListener();
        allWaitlistListenerRegistration = db.collection(COLLECTION)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_waitlist));
                        return;
                    }
                    listener.onWaitlistUpdated(
                            querySnapshot != null ? parseSnapshot(querySnapshot) : new ArrayList<>());
                });
    }

    @Override
    public void stopAllWaitlistListener() {
        if (allWaitlistListenerRegistration != null) {
            allWaitlistListenerRegistration.remove();
            allWaitlistListenerRegistration = null;
        }
    }

    private static List<WaitlistToEvent> parseSnapshot(
            com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        List<WaitlistToEvent> entries = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot) {
            WaitlistToEvent entry = document.toObject(WaitlistToEvent.class);
            if (entry != null) {
                entry.setId(document.getId());
                entries.add(entry);
            }
        }
        return entries;
    }

    private static List<WaitlistToEvent> sortEntries(List<WaitlistToEvent> entries) {
        return WaitlistUtils.sortByJoinedAt(entries);
    }
}
