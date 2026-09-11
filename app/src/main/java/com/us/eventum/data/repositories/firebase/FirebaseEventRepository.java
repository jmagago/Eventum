package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

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
    private ListenerRegistration availableEventsListenerRegistration;
    private ListenerRegistration userEventsListenerRegistration;
    private ListenerRegistration singleEventListenerRegistration;

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
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_events)));
    }

    @Override
    public void loadAvailableEvents(RepositoryCallback<List<Event>> callback) {
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
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_events)));
    }

    @Override
    public void startAvailableEventsListener(AvailableEventsListener listener) {
        stopAvailableEventsListener();
        availableEventsListenerRegistration = db.collection("events")
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_events));
                        return;
                    }
                    if (snap == null) {
                        listener.onEventsUpdated(new ArrayList<>());
                        return;
                    }
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snap) {
                        Event event = document.toObject(Event.class);
                        if (event != null) {
                            event.setId(document.getId());
                            events.add(event);
                        }
                    }
                    listener.onEventsUpdated(events);
                });
    }

    @Override
    public void stopAvailableEventsListener() {
        if (availableEventsListenerRegistration != null) {
            availableEventsListenerRegistration.remove();
            availableEventsListenerRegistration = null;
        }
    }

    @Override
    public void startUserEventsListener(String userId, AvailableEventsListener listener) {
        stopUserEventsListener();
        if (userId == null || userId.isEmpty()) {
            listener.onError(FirebaseBackendErrorHandler.getNotAuthenticatedMessage(null));
            return;
        }
        userEventsListenerRegistration = db.collection("events")
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_events));
                        return;
                    }
                    if (snap == null) {
                        listener.onEventsUpdated(new ArrayList<>());
                        return;
                    }
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snap) {
                        Event event = document.toObject(Event.class);
                        if (event != null) {
                            event.setId(document.getId());
                            events.add(event);
                        }
                    }
                    listener.onEventsUpdated(events);
                });
    }

    @Override
    public void stopUserEventsListener() {
        if (userEventsListenerRegistration != null) {
            userEventsListenerRegistration.remove();
            userEventsListenerRegistration = null;
        }
    }

    @Override
    public void startEventListener(String eventId, SingleEventListener listener) {
        stopEventListener();
        if (eventId == null || eventId.isEmpty()) {
            listener.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        singleEventListenerRegistration = db.collection("events").document(eventId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_event));
                        return;
                    }
                    if (document == null || !document.exists()) {
                        listener.onEventRemoved();
                        return;
                    }
                    Event event = document.toObject(Event.class);
                    if (event == null) {
                        listener.onError(FirebaseBackendErrorHandler.getReadEventFailedMessage(null));
                        return;
                    }
                    event.setId(document.getId());
                    listener.onEventUpdated(event);
                });
    }

    @Override
    public void stopEventListener() {
        if (singleEventListenerRegistration != null) {
            singleEventListenerRegistration.remove();
            singleEventListenerRegistration = null;
        }
    }

    @Override
    public void createEvent(Event event, RepositoryCallback<Event> callback) {
        db.collection("events")
                .add(event)
                .addOnSuccessListener(documentReference -> {
                    event.setId(documentReference.getId());
                    callback.onSuccess(event);
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_create_event)));
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
        updates.put("requiresParentalAuth", event.getRequiresParentalAuth());
        updates.put("privateAccessCode", event.getPrivateAccessCode());
        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_event)));
    }

    @Override
    public void updateEventPrivacy(String eventId, boolean isPrivate, String privateAccessCode,
                                   RepositoryCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("privateEvent", isPrivate);
        if (isPrivate) {
            updates.put("privateAccessCode", privateAccessCode);
        } else {
            updates.put("privateAccessCode", null);
        }

        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_update_event_privacy)));
    }

    @Override
    public void deleteEvent(String eventId, RepositoryCallback<Void> callback) {
        db.collection("events").document(eventId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_delete_event)));
    }

    @Override
    public void getEventById(String eventId, RepositoryCallback<Event> callback) {
        if (eventId == null || eventId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError(FirebaseBackendErrorHandler.getEventNotFoundMessage(null));
                        return;
                    }
                    Event event = document.toObject(Event.class);
                    if (event == null) {
                        callback.onError(FirebaseBackendErrorHandler.getReadEventFailedMessage(null));
                        return;
                    }
                    event.setId(document.getId());
                    callback.onSuccess(event);
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_event)));
    }
}
