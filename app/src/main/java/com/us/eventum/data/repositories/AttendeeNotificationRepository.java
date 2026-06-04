package com.us.eventum.data.repositories;

import com.us.eventum.data.models.AttendeeNotification;

public interface AttendeeNotificationRepository {

    void create(AttendeeNotification notification, RepositoryCallback<Void> callback);

    interface RepositoryCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }
}
