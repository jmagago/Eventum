package com.us.eventum.data.repositories.firebase;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.WaitlistRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;
import com.us.eventum.utils.WaitlistUtils;

import java.util.ArrayList;
import java.util.Date;
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
        if (entry == null || entry.getEventId() == null || entry.getEventId().isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        db.collection("events").document(entry.getEventId()).get()
                .addOnSuccessListener(eventSnap -> {
                    if (!eventSnap.exists()) {
                        callback.onError(FirebaseBackendErrorHandler.getEventNotFoundMessage(null));
                        return;
                    }
                    if (EventCapacityTransactions.isCancelled(eventSnap)) {
                        callback.onError(FirebaseBackendErrorHandler.getEventCancelledMessage(null));
                        return;
                    }
                    insertWaitlistEntry(entry, callback);
                })
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_verify_waitlist)));
    }

    private void insertWaitlistEntry(WaitlistToEvent entry, RepositoryCallback<WaitlistToEvent> callback) {
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
    public void offerNextSpot(String eventId, String entryId, int knownRegistered, int knownOffered,
                              long offerDurationMs, RepositoryCallback<WaitlistToEvent> callback) {
        if (eventId == null || eventId.isEmpty() || entryId == null || entryId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, eventId);
        DocumentReference entryRef = db.collection(COLLECTION).document(entryId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot entrySnap = transaction.get(entryRef);
                    if (!eventSnap.exists() || !entrySnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    WaitlistToEvent entry = entrySnap.toObject(WaitlistToEvent.class);
                    if (entry == null || !WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())) {
                        throw new FirebaseFirestoreException(
                                "NOT_WAITING", FirebaseFirestoreException.Code.ABORTED);
                    }
                    long now = System.currentTimeMillis();
                    Timestamp offeredAt = Timestamp.now();
                    Timestamp expiresAt = new Timestamp(new Date(now + offerDurationMs));
                    entry.setId(entrySnap.getId());
                    entry.setStatus(WaitlistToEvent.STATUS_OFFERED);
                    entry.setOfferedAt(offeredAt);
                    entry.setOfferExpiresAt(expiresAt);
                    transaction.set(entryRef, entry);
                    EventCapacityTransactions.applyCapacityUpdate(
                            transaction, eventRef, eventSnap,
                            knownRegistered, knownOffered, 0, 1);
                    return entry;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(mapCapacityError(e, R.string.backend_op_update_waitlist)));
    }

    @Override
    public void expireOffer(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback) {
        if (eventId == null || eventId.isEmpty() || entryId == null || entryId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, eventId);
        DocumentReference entryRef = db.collection(COLLECTION).document(entryId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot entrySnap = transaction.get(entryRef);
                    if (!entrySnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    WaitlistToEvent entry = entrySnap.toObject(WaitlistToEvent.class);
                    if (entry == null) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    entry.setId(entrySnap.getId());
                    if (!WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
                        return entry;
                    }
                    entry.setStatus(WaitlistToEvent.STATUS_EXPIRED);
                    entry.setOfferedAt(null);
                    entry.setOfferExpiresAt(null);
                    transaction.set(entryRef, entry);
                    if (eventSnap.exists()) {
                        EventCapacityTransactions.applyCapacityUpdate(
                                transaction, eventRef, eventSnap, 0, 1, 0, -1);
                    }
                    return entry;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_waitlist)));
    }

    @Override
    public void revertOfferToWaiting(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback) {
        if (eventId == null || eventId.isEmpty() || entryId == null || entryId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, eventId);
        DocumentReference entryRef = db.collection(COLLECTION).document(entryId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot entrySnap = transaction.get(entryRef);
                    if (!entrySnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    WaitlistToEvent entry = entrySnap.toObject(WaitlistToEvent.class);
                    if (entry == null) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    entry.setId(entrySnap.getId());
                    if (!WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
                        return entry;
                    }
                    boolean wasValidOffer = WaitlistUtils.isValidOffer(entry, System.currentTimeMillis());
                    entry.setStatus(WaitlistToEvent.STATUS_WAITING);
                    entry.setOfferedAt(null);
                    entry.setOfferExpiresAt(null);
                    transaction.set(entryRef, entry);
                    if (wasValidOffer && eventSnap.exists()) {
                        EventCapacityTransactions.applyCapacityUpdate(
                                transaction, eventRef, eventSnap, 0, 1, 0, -1);
                    }
                    return entry;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_waitlist)));
    }

    @Override
    public void cancelEntry(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback) {
        if (eventId == null || eventId.isEmpty() || entryId == null || entryId.isEmpty()) {
            callback.onError(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, eventId);
        DocumentReference entryRef = db.collection(COLLECTION).document(entryId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot entrySnap = transaction.get(entryRef);
                    if (!entrySnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    WaitlistToEvent entry = entrySnap.toObject(WaitlistToEvent.class);
                    if (entry == null) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    entry.setId(entrySnap.getId());
                    boolean wasValidOffer = WaitlistUtils.isValidOffer(entry, System.currentTimeMillis());
                    entry.setStatus(WaitlistToEvent.STATUS_CANCELLED);
                    entry.setOfferedAt(null);
                    entry.setOfferExpiresAt(null);
                    transaction.set(entryRef, entry);
                    if (wasValidOffer && eventSnap.exists()) {
                        EventCapacityTransactions.applyCapacityUpdate(
                                transaction, eventRef, eventSnap, 0, 1, 0, -1);
                    }
                    return entry;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(e, R.string.backend_op_update_waitlist)));
    }

    @Override
    public void confirmOfferAndRegister(WaitlistToEvent offer, AttendeesToEvent registration,
                                        int knownRegistered, int knownOffered,
                                        RepositoryCallback<AttendeesToEvent> callback) {
        if (offer == null || offer.getId() == null || offer.getEventId() == null
                || registration == null || registration.getUserId() == null) {
            callback.onError(FirebaseBackendErrorHandler.getIncompleteActivityMessage(null));
            return;
        }
        String eventId = offer.getEventId();
        db.collection("attendeesToEvent")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(regs -> confirmOfferInTransaction(
                        offer, registration, regs.size(), knownOffered, callback))
                .addOnFailureListener(e -> callback.onError(
                        FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_verify_registration)));
    }

    private void confirmOfferInTransaction(WaitlistToEvent offer, AttendeesToEvent registration,
                                           int knownRegistered, int knownOffered,
                                           RepositoryCallback<AttendeesToEvent> callback) {
        String eventId = offer.getEventId();
        DocumentReference eventRef = EventCapacityTransactions.eventRef(db, eventId);
        DocumentReference offerRef = db.collection(COLLECTION).document(offer.getId());
        DocumentReference registrationRef = db.collection("attendeesToEvent").document();
        db.runTransaction(transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot offerSnap = transaction.get(offerRef);
                    if (!eventSnap.exists() || !offerSnap.exists()) {
                        throw new FirebaseFirestoreException(
                                "NOT_FOUND", FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    WaitlistToEvent current = offerSnap.toObject(WaitlistToEvent.class);
                    if (current == null
                            || !WaitlistUtils.isValidOffer(current, System.currentTimeMillis())) {
                        throw new FirebaseFirestoreException(
                                "OFFER_INVALID", FirebaseFirestoreException.Code.ABORTED);
                    }
                    int max = EventCapacityTransactions.readMaxParticipants(eventSnap);
                    int registered = EventCapacityTransactions.readRegistered(eventSnap, knownRegistered);
                    if (max > 0 && registered >= max) {
                        throw new FirebaseFirestoreException(
                                EventCapacityTransactions.EVENT_FULL,
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    current.setId(offerSnap.getId());
                    current.setStatus(WaitlistToEvent.STATUS_PROMOTED);
                    current.setOfferedAt(null);
                    current.setOfferExpiresAt(null);
                    transaction.set(registrationRef, registration);
                    transaction.set(offerRef, current);
                    EventCapacityTransactions.applyCapacityUpdate(
                            transaction, eventRef, eventSnap,
                            knownRegistered, knownOffered, 1, -1);
                    registration.setId(registrationRef.getId());
                    return registration;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(mapCapacityError(
                        e, R.string.backend_op_create_registration)));
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

    private static String mapCapacityError(Exception exception, int fallbackRes) {
        if (EventCapacityTransactions.isEventCancelled(exception)) {
            return FirebaseBackendErrorHandler.getEventCancelledMessage(null);
        }
        if (EventCapacityTransactions.isEventFull(exception)) {
            return FirebaseBackendErrorHandler.getEventFullMessage(null);
        }
        if (exception instanceof FirebaseFirestoreException) {
            String message = exception.getMessage();
            if ("OFFER_INVALID".equals(message) || "NOT_WAITING".equals(message)) {
                return FirebaseBackendErrorHandler.getWaitlistOfferInvalidMessage(null);
            }
        }
        return FirebaseBackendErrorHandler.getErrorMessage(exception, fallbackRes);
    }
}
