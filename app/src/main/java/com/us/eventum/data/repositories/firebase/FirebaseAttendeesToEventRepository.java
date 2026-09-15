package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

import java.util.ArrayList;
import java.util.List;

public class FirebaseAttendeesToEventRepository implements AttendeesToEventRepository {
    private final FirebaseFirestore db;
    private com.google.firebase.firestore.ListenerRegistration eventAttendeesListenerRegistration;
    private com.google.firebase.firestore.ListenerRegistration allRegistrationsListenerRegistration;

    public FirebaseAttendeesToEventRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void createAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback) {
        if (attendeeToEvent.getEventId() == null || attendeeToEvent.getUserId() == null) {
            callback.onError(FirebaseBackendErrorHandler.getIncompleteActivityMessage(null));
            return;
        }
        db.collection("attendeesToEvent")
                .whereEqualTo("eventId", attendeeToEvent.getEventId())
                .whereEqualTo("userId", attendeeToEvent.getUserId())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        callback.onError(FirebaseBackendErrorHandler.getAlreadyRegisteredMessage(null));
                        return;
                    }
                    db.collection("attendeesToEvent")
                            .whereEqualTo("eventId", attendeeToEvent.getEventId())
                            .get()
                            .addOnSuccessListener(allRegs ->
                                    createRegistrationInTransaction(
                                            attendeeToEvent,
                                            allRegs.size(),
                                            callback))
                            .addOnFailureListener(e -> callback.onError(
                                    FirebaseBackendErrorHandler.getErrorMessage(
                                            e, R.string.backend_op_verify_registration)));
                })
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_verify_registration)));
    }

    private void createRegistrationInTransaction(AttendeesToEvent attendeeToEvent,
                                                 int knownRegistered,
                                                 RepositoryCallback<AttendeesToEvent> callback) {
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, attendeeToEvent.getEventId());
        DocumentReference registrationRef = db.collection("attendeesToEvent").document();
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    if (!eventSnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    EventCapacityTransactions.applyCapacityUpdate(
                            transaction, eventRef, eventSnap, knownRegistered, 0, 1, 0);
                    transaction.set(registrationRef, attendeeToEvent);
                    attendeeToEvent.setId(registrationRef.getId());
                    return attendeeToEvent;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> {
                    if (EventCapacityTransactions.isEventCancelled(e)) {
                        callback.onError(FirebaseBackendErrorHandler.getEventCancelledMessage(null));
                    } else if (EventCapacityTransactions.isEventFull(e)) {
                        callback.onError(FirebaseBackendErrorHandler.getEventFullMessage(null));
                    } else {
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_create_registration));
                    }
                });
    }

    @Override
    public void loadAttendeesToEvent(String eventId, RepositoryCallback<List<AttendeesToEvent>> callback) {
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
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_load_registrations)));
    }

    @Override
    public void startEventAttendeesListener(String eventId, EventAttendeesListener listener) {
        stopEventAttendeesListener();
        if (eventId == null || eventId.isEmpty()) {
            listener.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        eventAttendeesListenerRegistration = db.collection("attendeesToEvent")
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_event_attendees));
                        return;
                    }
                    List<AttendeesToEvent> attendees = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            AttendeesToEvent attendee = document.toObject(AttendeesToEvent.class);
                            if (attendee != null) {
                                attendee.setId(document.getId());
                                attendees.add(attendee);
                            }
                        }
                    }
                    listener.onAttendeesUpdated(attendees);
                });
    }

    @Override
    public void stopEventAttendeesListener() {
        if (eventAttendeesListenerRegistration != null) {
            eventAttendeesListenerRegistration.remove();
            eventAttendeesListenerRegistration = null;
        }
    }

    @Override
    public void startAllRegistrationsListener(AllRegistrationsListener listener) {
        stopAllRegistrationsListener();
        allRegistrationsListenerRegistration = db.collection("attendeesToEvent")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        listener.onError(FirebaseBackendErrorHandler.getListenerErrorMessage(
                                null, error, R.string.backend_op_listen_registrations));
                        return;
                    }
                    List<AttendeesToEvent> registrations = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            AttendeesToEvent row = document.toObject(AttendeesToEvent.class);
                            if (row != null) {
                                row.setId(document.getId());
                                registrations.add(row);
                            }
                        }
                    }
                    listener.onRegistrationsUpdated(registrations);
                });
    }

    @Override
    public void stopAllRegistrationsListener() {
        if (allRegistrationsListenerRegistration != null) {
            allRegistrationsListenerRegistration.remove();
            allRegistrationsListenerRegistration = null;
        }
    }

    @Override
    public void deleteAttendeeToEvent(String attendeeToEventId, RepositoryCallback<Void> callback) {
        if (attendeeToEventId == null || attendeeToEventId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getIncompleteActivityMessage(null));
            return;
        }
        DocumentReference registrationRef = db.collection("attendeesToEvent").document(attendeeToEventId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot registrationSnap = transaction.get(registrationRef);
                    if (!registrationSnap.exists()) {
                        return null;
                    }
                    String eventId = registrationSnap.getString("eventId");
                    DocumentSnapshot eventSnap = null;
                    DocumentReference eventRef = null;
                    if (eventId != null && !eventId.isEmpty()) {
                        eventRef = EventCapacityTransactions.eventRef(db, eventId);
                        eventSnap = transaction.get(eventRef);
                    }
                    transaction.delete(registrationRef);
                    if (eventRef != null && eventSnap != null && eventSnap.exists()) {
                        EventCapacityTransactions.applyCapacityUpdate(
                                transaction, eventRef, eventSnap, 1, 0, -1, 0);
                    }
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_delete_registration)));
    }

    @Override
    public void updateAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback) {
        db.collection("attendeesToEvent")
                .document(attendeeToEvent.getId())
                .set(attendeeToEvent)
                .addOnSuccessListener(aVoid -> callback.onSuccess(attendeeToEvent))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_registration)));
    }
}
