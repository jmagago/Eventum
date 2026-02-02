package com.us.eventum.data.repositories;

import com.us.eventum.data.models.AttendeesToEvent;
import java.util.List;

public interface AttendeesToEventRepository {
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    void createAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback);
    void loadAttendeesToEvent(String eventId, RepositoryCallback<List<AttendeesToEvent>> callback);
    void deleteAttendeeToEvent(String attendeeToEventId, RepositoryCallback<Void> callback);
    void updateAttendeeToEvent(AttendeesToEvent attendeeToEvent, RepositoryCallback<AttendeesToEvent> callback);
}
