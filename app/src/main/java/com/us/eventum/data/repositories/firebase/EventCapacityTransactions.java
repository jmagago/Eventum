package com.us.eventum.data.repositories.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.us.eventum.core.utils.EventPrivateAccessCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Contadores de aforo en el documento {@code events} para ofertas e inscripciones atómicas.
 */
final class EventCapacityTransactions {

    static final String REGISTERED_COUNT = "registeredCount";
    static final String OFFERED_COUNT = "waitlistOfferedCount";
    static final String EVENT_FULL = "EVENT_FULL";
    static final String EVENT_CANCELLED = "EVENT_CANCELLED";

    private EventCapacityTransactions() {
    }

    @NonNull
    static DocumentReference eventRef(@NonNull FirebaseFirestore db, @NonNull String eventId) {
        return db.collection("events").document(eventId);
    }

    static int readInt(@Nullable DocumentSnapshot snap, @NonNull String field, int fallback) {
        if (snap == null || !snap.contains(field) || snap.get(field) == null) {
            return fallback;
        }
        Long value = snap.getLong(field);
        if (value != null) {
            return value.intValue();
        }
        Double decimal = snap.getDouble(field);
        return decimal != null ? decimal.intValue() : fallback;
    }

    static int readRegistered(@NonNull DocumentSnapshot eventSnap, int knownRegistered) {
        int known = Math.max(0, knownRegistered);
        if (!eventSnap.contains(REGISTERED_COUNT)) {
            return known;
        }
        return Math.max(known, readInt(eventSnap, REGISTERED_COUNT, 0));
    }

    static int readOffered(@NonNull DocumentSnapshot eventSnap, int knownOffered) {
        return eventSnap.contains(OFFERED_COUNT)
                ? readInt(eventSnap, OFFERED_COUNT, 0)
                : Math.max(0, knownOffered);
    }

    static int readMaxParticipants(@NonNull DocumentSnapshot eventSnap) {
        return Math.max(0, readInt(eventSnap, "maxParticipants", 0));
    }

    static void applyCapacityUpdate(
            @NonNull Transaction transaction,
            @NonNull DocumentReference eventRef,
            @NonNull DocumentSnapshot eventSnap,
            int knownRegistered,
            int knownOffered,
            int registeredDelta,
            int offeredDelta) throws FirebaseFirestoreException {
        if ((registeredDelta > 0 || offeredDelta > 0) && isCancelled(eventSnap)) {
            throw new FirebaseFirestoreException(
                    EVENT_CANCELLED, FirebaseFirestoreException.Code.ABORTED);
        }
        int registered = readRegistered(eventSnap, knownRegistered);
        int offered = readOffered(eventSnap, knownOffered);
        int max = readMaxParticipants(eventSnap);
        int newRegistered = Math.max(0, registered + registeredDelta);
        int newOffered = Math.max(0, offered + offeredDelta);
        if ((registeredDelta > 0 || offeredDelta > 0) && max > 0
                && newRegistered + newOffered > max) {
            throw new FirebaseFirestoreException(
                    EVENT_FULL, FirebaseFirestoreException.Code.ABORTED);
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put(REGISTERED_COUNT, newRegistered);
        updates.put(OFFERED_COUNT, newOffered);
        String leaked = eventSnap.getString("privateAccessCode");
        if (leaked != null) {
            updates.put("privateAccessCode", FieldValue.delete());
            if (!eventSnap.contains("privateAccessCodeHash")
                    || eventSnap.get("privateAccessCodeHash") == null
                    || String.valueOf(eventSnap.get("privateAccessCodeHash")).trim().isEmpty()) {
                updates.put("privateAccessCodeHash", EventPrivateAccessCode.hash(leaked));
            }
        }
        transaction.update(eventRef, updates);
    }

    static boolean isCancelled(@Nullable DocumentSnapshot eventSnap) {
        return eventSnap != null && Boolean.TRUE.equals(eventSnap.getBoolean("cancelled"));
    }

    static boolean isEventCancelled(@Nullable Exception exception) {
        return hasAbortedCode(exception, EVENT_CANCELLED);
    }

    static boolean isEventFull(@Nullable Exception exception) {
        return hasAbortedCode(exception, EVENT_FULL);
    }

    private static boolean hasAbortedCode(@Nullable Exception exception, @NonNull String code) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) current;
                String message = firestoreException.getMessage();
                if (firestoreException.getCode() == FirebaseFirestoreException.Code.ABORTED
                        && message != null && message.contains(code)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
