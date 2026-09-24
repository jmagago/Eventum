package com.us.eventum.core.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.us.eventum.data.models.OrganizerNotification;

import java.util.HashSet;
import java.util.Set;

/**
 * Escucha en tiempo real las notificaciones del organizador y las muestra en el sistema.
 */
public class OrganizerNotificationWatcher {

    private static final String TAG = "OrgNotificationWatcher";
    private static final String COLLECTION = "organizerNotifications";
    private static final String PREFS = "organizer_notification_prefs";
    private static final String KEY_SHOWN_IDS = "shown_ids";
    /** En la primera carga de la sesión, solo avisos de los últimos 10 minutos */
    private static final long INITIAL_WINDOW_MS = 10L * 60L * 1000L;

    private final Context appContext;
    private ListenerRegistration registration;
    private boolean firstSnapshot = true;
    private final Set<String> shownIds = new HashSet<>();

    public OrganizerNotificationWatcher(Context context) {
        this.appContext = context.getApplicationContext();
        loadShownIds();
    }

    public void start(String organizerId) {
        stop();
        if (organizerId == null || organizerId.isEmpty()) {
            return;
        }

        OrganizerNotificationHelper.ensureChannel(appContext);
        firstSnapshot = true;

        Query query = FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .whereEqualTo("organizerId", organizerId);

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

                OrganizerNotification notification =
                        change.getDocument().toObject(OrganizerNotification.class);
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
                deliver(notification);
            }
        });
    }

    private void deliver(OrganizerNotification notification) {
        OrganizerNotificationHelper.show(appContext, notification);
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

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        firstSnapshot = true;
    }
}
