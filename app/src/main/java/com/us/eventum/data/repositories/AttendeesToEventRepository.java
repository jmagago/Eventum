package com.us.eventum.data.repositories;

import com.us.eventum.data.models.AttendeesToEvent;
import java.util.List;

public interface AttendeesToEventRepository {
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    interface EventAttendeesListener {
        void onAttendeesUpdated(List<AttendeesToEvent> attendees);

        void onError(String error);
    }

    interface AllRegistrationsListener {
        void onRegistrationsUpdated(List<AttendeesToEvent> registrations);

        void onError(String error);
    }

    void createAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback);
    void loadAttendeesToEvent(String eventId, RepositoryCallback<List<AttendeesToEvent>> callback);

    void startEventAttendeesListener(String eventId, EventAttendeesListener listener);

    void stopEventAttendeesListener();

    void startAllRegistrationsListener(AllRegistrationsListener listener);

    void stopAllRegistrationsListener();
    void deleteAttendeeToEvent(String attendeeToEventId, RepositoryCallback<Void> callback);
    void updateAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback);
}
