package com.us.eventum.data.repositories.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.core.utils.EventImageManager;
import com.us.eventum.core.utils.EventPrivateAccessCode;
import com.us.eventum.core.utils.FirebaseBackendErrorHandler;
import com.us.eventum.core.utils.ParentalAuthManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación de EventRepository usando Firebase Firestore
 * Maneja todas las operaciones de eventos en la nube
 */
public class FirebaseEventRepository implements EventRepository {

    private static final String SECRETS_COLLECTION = "secrets";
    private static final String ACCESS_SECRET_DOC = "access";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private ListenerRegistration availableEventsListenerRegistration;
    private ListenerRegistration userEventsListenerRegistration;
    private ListenerRegistration singleEventListenerRegistration;

    public FirebaseEventRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    @Override
    public void loadUserEvents(String userId, RepositoryCallback<List<Event>> callback) {
        db.collection("events")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Event event = mapPublicEvent(document, true);
                        if (event != null) {
                            events.add(event);
                        }
                    }
                    attachOrganizerSecrets(events, () -> callback.onSuccess(events));
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
                        Event event = mapPublicEvent(document, false);
                        if (event != null) {
                            events.add(event);
                        }
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
                        Event event = mapPublicEvent(document, false);
                        if (event != null) {
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
                        Event event = mapPublicEvent(document, true);
                        if (event != null) {
                            events.add(event);
                        }
                    }
                    attachOrganizerSecrets(events, () -> listener.onEventsUpdated(events));
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
                    Event event = mapPublicEvent(document, true);
                    if (event == null) {
                        listener.onError(FirebaseBackendErrorHandler.getReadEventFailedMessage(null));
                        return;
                    }
                    if (isCurrentUserOrganizer(event)) {
                        attachOrganizerSecrets(List.of(event), () -> listener.onEventUpdated(event));
                    } else {
                        event.setPrivateAccessCode(null);
                        listener.onEventUpdated(event);
                    }
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
        String plaintext = EventPrivateAccessCode.normalize(event.getPrivateAccessCode());
        event.setPrivateAccessCodeHash(event.getPrivateEvent()
                ? EventPrivateAccessCode.hash(plaintext)
                : null);
        event.setPrivateAccessCode(null);
        db.collection("events")
                .add(event)
                .addOnSuccessListener(documentReference -> {
                    event.setId(documentReference.getId());
                    persistAccessSecret(documentReference.getId(), event.getPrivateEvent(), plaintext,
                            () -> {
                                event.setPrivateAccessCode(plaintext);
                                callback.onSuccess(event);
                            },
                            e -> callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                    e, R.string.backend_op_create_event)));
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_create_event)));
    }

    @Override
    public void updateEvent(String eventId, Event event, RepositoryCallback<Void> callback) {
        String plaintext = EventPrivateAccessCode.normalize(event.getPrivateAccessCode());
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", event.getTitle());
        updates.put("description", event.getDescription());
        updates.put("date", event.getDate());
        updates.put("location", event.getLocation());
        updates.put("maxParticipants", event.getMaxParticipants());
        updates.put("eventType", event.getEventType());
        updates.put("privateEvent", event.getPrivateEvent());
        updates.put("requiresParentalAuth", event.getRequiresParentalAuth());
        updates.put("privateAccessCode", FieldValue.delete());
        updates.put("privateAccessCodeHash", event.getPrivateEvent()
                ? EventPrivateAccessCode.hash(plaintext)
                : null);
        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> persistAccessSecret(
                        eventId,
                        event.getPrivateEvent(),
                        plaintext,
                        () -> callback.onSuccess(null),
                        e -> callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_update_event))))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_event)));
    }

    @Override
    public void updateEventPrivacy(String eventId, boolean isPrivate, String privateAccessCode,
                                   RepositoryCallback<Void> callback) {
        String plaintext = isPrivate ? EventPrivateAccessCode.normalize(privateAccessCode) : null;
        Map<String, Object> updates = new HashMap<>();
        updates.put("privateEvent", isPrivate);
        updates.put("privateAccessCode", FieldValue.delete());
        updates.put("privateAccessCodeHash", isPrivate ? EventPrivateAccessCode.hash(plaintext) : null);

        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> persistAccessSecret(
                        eventId,
                        isPrivate,
                        plaintext,
                        () -> callback.onSuccess(null),
                        e -> callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_update_event_privacy))))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_update_event_privacy)));
    }

    @Override
    public void deleteEvent(String eventId, RepositoryCallback<Void> callback) {
        if (eventId == null || eventId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        EventDeleteCascade.deleteEventAndRelated(
                db,
                eventId,
                () -> {
                    EventImageManager.deleteEventImage(eventId);
                    ParentalAuthManager.deleteForEvent(eventId);
                    callback.onSuccess(null);
                },
                e -> callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                        e, R.string.backend_op_delete_event)));
    }

    @Override
    public void cancelEvent(String eventId, RepositoryCallback<Void> callback) {
        if (eventId == null || eventId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError(FirebaseBackendErrorHandler.getEventNotFoundMessage(null));
                        return;
                    }
                    if (Boolean.TRUE.equals(document.getBoolean("cancelled"))) {
                        callback.onError(FirebaseBackendErrorHandler.getEventAlreadyCancelledMessage(null));
                        return;
                    }
                    closeWaitlistAndMarkCancelled(eventRef, eventId, callback);
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_cancel_event)));
    }

    private void closeWaitlistAndMarkCancelled(DocumentReference eventRef, String eventId,
                                               RepositoryCallback<Void> callback) {
        db.collection("waitlistToEvent")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(waitlistSnap -> {
                    WriteBatch batch = db.batch();
                    Map<String, Object> eventUpdates = new HashMap<>();
                    eventUpdates.put("cancelled", true);
                    eventUpdates.put("cancelledAt", FieldValue.serverTimestamp());
                    eventUpdates.put(EventCapacityTransactions.OFFERED_COUNT, 0);
                    batch.update(eventRef, eventUpdates);
                    if (waitlistSnap != null) {
                        for (DocumentSnapshot waitlistDoc : waitlistSnap.getDocuments()) {
                            String status = waitlistDoc.getString("status");
                            if (!WaitlistToEvent.STATUS_WAITING.equals(status)
                                    && !WaitlistToEvent.STATUS_OFFERED.equals(status)) {
                                continue;
                            }
                            Map<String, Object> waitlistUpdates = new HashMap<>();
                            waitlistUpdates.put("status", WaitlistToEvent.STATUS_CANCELLED);
                            waitlistUpdates.put("offeredAt", FieldValue.delete());
                            waitlistUpdates.put("offerExpiresAt", FieldValue.delete());
                            batch.update(waitlistDoc.getReference(), waitlistUpdates);
                        }
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(e ->
                                    callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                            e, R.string.backend_op_cancel_event)));
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_cancel_event)));
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
                    Event event = mapPublicEvent(document, true);
                    if (event == null) {
                        callback.onError(FirebaseBackendErrorHandler.getReadEventFailedMessage(null));
                        return;
                    }
                    if (isCurrentUserOrganizer(event)) {
                        attachOrganizerSecrets(List.of(event), () -> callback.onSuccess(event));
                    } else {
                        event.setPrivateAccessCode(null);
                        callback.onSuccess(event);
                    }
                })
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_event)));
    }

    @Nullable
    private Event mapPublicEvent(@NonNull DocumentSnapshot document, boolean includePlaintext) {
        Event event = document.toObject(Event.class);
        if (event == null) {
            return null;
        }
        event.setId(document.getId());
        String leaked = EventPrivateAccessCode.normalize(document.getString("privateAccessCode"));
        if (!EventPrivateAccessCode.hasHash(event.getPrivateAccessCodeHash()) && leaked != null) {
            event.setPrivateAccessCodeHash(EventPrivateAccessCode.hash(leaked));
        }
        event.setPrivateAccessCode(includePlaintext ? leaked : null);
        return event;
    }

    private boolean isCurrentUserOrganizer(@Nullable Event event) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        return uid != null && event != null && uid.equals(event.getUserId());
    }

    private void attachOrganizerSecrets(@NonNull List<Event> events, @NonNull Runnable onComplete) {
        if (events.isEmpty()) {
            onComplete.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(events.size());
        for (Event event : events) {
            if (event.getId() == null || event.getId().isEmpty()) {
                if (remaining.decrementAndGet() == 0) {
                    onComplete.run();
                }
                continue;
            }
            String leaked = event.getPrivateAccessCode();
            db.collection("events").document(event.getId())
                    .collection(SECRETS_COLLECTION)
                    .document(ACCESS_SECRET_DOC)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            event.setPrivateAccessCode(EventPrivateAccessCode.normalize(
                                    task.getResult().getString("privateAccessCode")));
                        } else if (leaked != null && event.getPrivateEvent()) {
                            persistAccessSecret(event.getId(), true, leaked, () -> {
                            }, e -> {
                            });
                        }
                        if (remaining.decrementAndGet() == 0) {
                            onComplete.run();
                        }
                    });
        }
    }

    private void persistAccessSecret(
            @NonNull String eventId,
            boolean isPrivate,
            @Nullable String plaintext,
            @NonNull Runnable onSuccess,
            @NonNull OnFailureListener onFailure) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference secretRef = eventRef.collection(SECRETS_COLLECTION).document(ACCESS_SECRET_DOC);
        WriteBatch batch = db.batch();
        Map<String, Object> eventCleanup = new HashMap<>();
        eventCleanup.put("privateAccessCode", FieldValue.delete());
        eventCleanup.put("privateAccessCodeHash",
                isPrivate && plaintext != null ? EventPrivateAccessCode.hash(plaintext) : null);
        batch.update(eventRef, eventCleanup);
        if (isPrivate && plaintext != null) {
            Map<String, Object> secret = new HashMap<>();
            secret.put("privateAccessCode", plaintext);
            batch.set(secretRef, secret);
        } else {
            batch.delete(secretRef);
        }
        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(onFailure);
    }
}
