package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.data.models.EventActivityLog;
import com.us.eventum.data.repositories.EventActivityLogRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

import java.util.ArrayList;
import java.util.List;

public class FirebaseEventActivityLogRepository implements EventActivityLogRepository {

    private static final String EVENTS = "events";
    private static final String ACTIVITY_LOG = "activityLog";

    private final FirebaseFirestore db;

    public FirebaseEventActivityLogRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void append(String eventId, EventActivityLog entry, RepositoryCallback<Void> callback) {
        if (eventId == null || eventId.isEmpty() || entry == null) {
            callback.onError(FirebaseBackendErrorHandler.getIncompleteActivityMessage(null));
            return;
        }
        entry.setEventId(eventId);
        db.collection(EVENTS)
                .document(eventId)
                .collection(ACTIVITY_LOG)
                .add(entry)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_append_activity)));
    }

    @Override
    public ListenerRegistration subscribe(String eventId, RepositoryCallback<List<EventActivityLog>> callback) {
        if (eventId == null || eventId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventForActivityMessage(null));
            return null;
        }
        return db.collection(EVENTS)
                .document(eventId)
                .collection(ACTIVITY_LOG)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_activity));
                        return;
                    }
                    List<EventActivityLog> logs = new ArrayList<>();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            EventActivityLog log = doc.toObject(EventActivityLog.class);
                            if (log != null) {
                                log.setId(doc.getId());
                                logs.add(log);
                            }
                        }
                    }
                    callback.onSuccess(logs);
                });
    }
}
