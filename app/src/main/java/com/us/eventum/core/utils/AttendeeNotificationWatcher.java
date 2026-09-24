package com.us.eventum.core.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.us.eventum.data.models.AttendeeNotification;

import java.util.HashSet;
import java.util.Set;

/**
 * Escucha en tiempo real las notificaciones del asistente y las muestra en el sistema.
 */
public class AttendeeNotificationWatcher {

    private static final String TAG = "AttNotificationWatcher";
    private static final String COLLECTION = "attendeeNotifications";
    private static final String PREFS = "attendee_notification_prefs";
    private static final String KEY_SHOWN_IDS = "shown_ids";
    private static final long INITIAL_WINDOW_MS = 10L * 60L * 1000L;

    private final Context appContext;
    private ListenerRegistration registration;
    private boolean firstSnapshot = true;
    private final Set<String> shownIds = new HashSet<>();

    public AttendeeNotificationWatcher(Context context) {
        this.appContext = context.getApplicationContext();
        loadShownIds();
    }

    public void start(String attendeeId) {
        stop();
        if (attendeeId == null || attendeeId.isEmpty()) {
            return;
        }

        AttendeeNotificationHelper.ensureChannel(appContext);
        firstSnapshot = true;

        Query query = FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .whereEqualTo("attendeeId", attendeeId);

        registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error escuchando notificaciones: " + error.getMessage(), error);
                return;
            }
            if (snapshot == null) {
                return;
            }

            boolean initialPass = firstSnapshot;
            if (firstSnapshot) {
                firstSnapshot = false;
            }

            long minCreatedAtMs = initialPass
                    ? System.currentTimeMillis() - INITIAL_WINDOW_MS
                    : 0L;

            for (DocumentChange change : snapshot.getDocumentChanges()) {
                if (change.getType() != DocumentChange.Type.ADDED) {
                    continue;
                }

                String docId = change.getDocument().getId();
                if (shownIds.contains(docId)) {
                    continue;
                }

                AttendeeNotification notification =
                        change.getDocument().toObject(AttendeeNotification.class);
                if (notification == null) {
                    continue;
                }

                if (initialPass && notification.getCreatedAt() != null) {
                    long createdMs = notification.getCreatedAt().toDate().getTime();
                    if (createdMs < minCreatedAtMs) {
                        markShown(docId);
                        continue;
                    }
                }

                notification.setId(docId);
                markShown(docId);
                AttendeeNotificationHelper.show(appContext, notification);
            }
        });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        firstSnapshot = true;
    }

    private void markShown(String docId) {
        shownIds.add(docId);
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> stored = new HashSet<>(prefs.getStringSet(KEY_SHOWN_IDS, new HashSet<>()));
        stored.add(docId);
        if (stored.size() > 200) {
            stored.clear();
            stored.add(docId);
        }
        prefs.edit().putStringSet(KEY_SHOWN_IDS, stored).apply();
    }

    private void loadShownIds() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_SHOWN_IDS, null);
        if (stored != null) {
            shownIds.addAll(stored);
        }
    }
}
