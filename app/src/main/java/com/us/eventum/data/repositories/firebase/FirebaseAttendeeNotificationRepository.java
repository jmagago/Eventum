package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;

public class FirebaseAttendeeNotificationRepository implements AttendeeNotificationRepository {

    private static final String COLLECTION = "attendeeNotifications";

    private final FirebaseFirestore db;

    public FirebaseAttendeeNotificationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void create(AttendeeNotification notification,
                       AttendeeNotificationRepository.RepositoryCallback<Void> callback) {
        if (notification == null
                || notification.getAttendeeId() == null
                || notification.getEventId() == null) {
            callback.onError("Datos de notificación incompletos");
            return;
        }

        db.collection(COLLECTION)
                .add(notification)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError("Error al crear notificación: " + e.getMessage()));
    }
}
