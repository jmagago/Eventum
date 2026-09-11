package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.R;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.data.repositories.OrganizerNotificationRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

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
            callback.onError(FirebaseBackendErrorHandler.getIncompleteNotificationMessage(null));
            return;
        }

        db.collection(COLLECTION)
                .add(notification)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(e ->
                        callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                                e, R.string.backend_op_create_notification)));
    }
}
