package com.us.eventum.data.repositories;

import com.us.eventum.data.models.WaitlistToEvent;

import java.util.List;

public interface WaitlistRepository {

    interface RepositoryCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    interface EventWaitlistListener {
        void onWaitlistUpdated(List<WaitlistToEvent> entries);

        void onError(String error);
    }

    interface AllWaitlistListener {
        void onWaitlistUpdated(List<WaitlistToEvent> entries);

        void onError(String error);
    }

    void createWaitlistEntry(WaitlistToEvent entry, RepositoryCallback<WaitlistToEvent> callback);

    void loadWaitlistForEvent(String eventId, RepositoryCallback<List<WaitlistToEvent>> callback);

    void updateWaitlistEntry(WaitlistToEvent entry, RepositoryCallback<WaitlistToEvent> callback);

    void deleteWaitlistEntry(String entryId, RepositoryCallback<Void> callback);

    void startEventWaitlistListener(String eventId, EventWaitlistListener listener);

    void stopEventWaitlistListener();

    void startAllWaitlistListener(AllWaitlistListener listener);

    void stopAllWaitlistListener();
}
