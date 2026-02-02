package com.us.eventum.data.repositories;

import com.us.eventum.data.models.Organizer;

public interface OrganizerRepository {
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    void createOrganizer(Organizer organizer, RepositoryCallback<Organizer> callback);
    void getOrganizer(String uid, RepositoryCallback<Organizer> callback);
    void updateOrganizer(Organizer organizer, RepositoryCallback<Organizer> callback);
    void deleteOrganizer(String uid, RepositoryCallback<Void> callback);
    void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback);
}
