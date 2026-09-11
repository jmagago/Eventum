package com.us.eventum.data.repositories;

import com.google.firebase.firestore.ListenerRegistration;
import com.us.eventum.data.models.EventActivityLog;

import java.util.List;

public interface EventActivityLogRepository {

    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    void append(String eventId, EventActivityLog entry, RepositoryCallback<Void> callback);

    ListenerRegistration subscribe(String eventId, RepositoryCallback<List<EventActivityLog>> callback);
}
