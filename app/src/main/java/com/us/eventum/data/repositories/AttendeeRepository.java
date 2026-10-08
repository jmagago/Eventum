package com.us.eventum.data.repositories;

import com.us.eventum.data.models.Attendee;

public interface AttendeeRepository {
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback);
    void getAttendee(String uid, RepositoryCallback<Attendee> callback);
    void updateAttendee(Attendee attendee, RepositoryCallback<Attendee> callback);
    /** Actualiza solo {@code imageUpdatedAt} tras subir/reemplazar la foto en Storage. */
    void updateAttendeeImageUpdatedAt(String uid, long imageUpdatedAt, RepositoryCallback<Void> callback);
    void deleteAttendee(String uid, RepositoryCallback<Void> callback);
    void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback);
    void checkDniAvailability(String dni, String excludeUid, RepositoryCallback<Boolean> callback);
}