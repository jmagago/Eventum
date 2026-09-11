package com.us.eventum.data.repositories.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.R;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

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
