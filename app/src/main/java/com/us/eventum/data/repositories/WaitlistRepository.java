package com.us.eventum.data.repositories;

import com.us.eventum.data.models.AttendeesToEvent;
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

    void offerNextSpot(String eventId, String entryId, int knownRegistered, int knownOffered,
                       long offerDurationMs, RepositoryCallback<WaitlistToEvent> callback);

    void expireOffer(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback);

    void revertOfferToWaiting(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback);

    void cancelEntry(String eventId, String entryId, RepositoryCallback<WaitlistToEvent> callback);

    void confirmOfferAndRegister(WaitlistToEvent offer, AttendeesToEvent registration,
                                 int knownRegistered, int knownOffered,
                                 RepositoryCallback<AttendeesToEvent> callback);

    void startEventWaitlistListener(String eventId, EventWaitlistListener listener);

    void stopEventWaitlistListener();

    void startAllWaitlistListener(AllWaitlistListener listener);

    void stopAllWaitlistListener();
}
