package com.us.eventum.core.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.us.eventum.data.models.EventActivityLog;
import com.us.eventum.data.repositories.EventActivityLogRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventActivityLogRepository;

/**
 * Registra entradas en el log de actividad del evento (fire-and-forget).
 */
public final class EventActivityLogHelper {

    private static final String TAG = "EventActivityLogHelper";

    private static EventActivityLogRepository repository = new FirebaseEventActivityLogRepository();

    private EventActivityLogHelper() {
    }

    public static void logJoined(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_JOINED, attendeeName, null);
    }

    public static void logLeft(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_LEFT, attendeeName, null);
    }

    public static void logVerifiedQr(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_VERIFIED_QR, attendeeName, null);
    }

    public static void logVerifiedManual(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_VERIFIED_MANUAL, attendeeName, null);
    }

    public static void logRemoved(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_REMOVED, attendeeName, null);
    }

    public static void logListCleared(String eventId, int count) {
        append(eventId, EventActivityLog.TYPE_LIST_CLEARED, null, String.valueOf(count));
    }

    public static void logEventUpdated(String eventId) {
        append(eventId, EventActivityLog.TYPE_EVENT_UPDATED, null, null);
    }

    public static void logEventCancelled(String eventId) {
        append(eventId, EventActivityLog.TYPE_EVENT_CANCELLED, null, null);
    }

    public static void logWaitlistJoined(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_WAITLIST_JOINED, attendeeName, null);
    }

    public static void logWaitlistLeft(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_WAITLIST_LEFT, attendeeName, null);
    }

    public static void logWaitlistOffered(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_WAITLIST_OFFERED, attendeeName, null);
    }

    public static void logWaitlistOfferExpired(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_WAITLIST_OFFER_EXPIRED, attendeeName, null);
    }

    public static void logWaitlistPromoted(String eventId, @Nullable String attendeeName) {
        append(eventId, EventActivityLog.TYPE_WAITLIST_PROMOTED, attendeeName, null);
    }

    private static void append(String eventId, String type,
                               @Nullable String subjectName, @Nullable String detail) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        String name = subjectName != null && !subjectName.trim().isEmpty()
                ? subjectName.trim()
                : null;
        EventActivityLog entry = new EventActivityLog(eventId, type, name, detail);
        repository.append(eventId, entry, new EventActivityLogRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "No se pudo registrar actividad: " + error);
            }
        });
    }
}
