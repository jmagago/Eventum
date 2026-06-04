package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.data.repositories.OrganizerNotificationRepository;

public class FirebaseOrganizerNotificationRepository implements OrganizerNotificationRepository {

    private static final String COLLECTION = "organizerNotifications";

    private final FirebaseFirestore db;

    public FirebaseOrganizerNotificationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void create(OrganizerNotification notification, RepositoryCallback<Void> callback) {
        if (notification == null
                || notification.getOrganizerId() == null
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
