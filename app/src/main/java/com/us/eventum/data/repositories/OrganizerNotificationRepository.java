package com.us.eventum.data.repositories;

import com.us.eventum.data.models.OrganizerNotification;

public interface OrganizerNotificationRepository {

    void create(OrganizerNotification notification, RepositoryCallback<Void> callback);

    interface RepositoryCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }
}
