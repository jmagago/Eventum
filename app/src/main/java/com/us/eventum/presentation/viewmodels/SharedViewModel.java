package com.us.eventum.presentation.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    private static SharedViewModel instance;
    private final MutableLiveData<Boolean> eventsUpdated = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> profileImageUpdated = new MutableLiveData<>(false);

    private SharedViewModel() {
        // Constructor privado para singleton
    }

    public static synchronized SharedViewModel getInstance() {
        if (instance == null) {
            instance = new SharedViewModel();
        }
        return instance;
    }

    public LiveData<Boolean> getEventsUpdated() {
        return eventsUpdated;
    }

    public LiveData<Boolean> getProfileImageUpdated() {
        return profileImageUpdated;
    }

    public void notifyEventsUpdated() {
        eventsUpdated.setValue(true);
    }

    public void resetEventsUpdated() {
        eventsUpdated.setValue(false);
    }

    public void notifyProfileImageUpdated() {
        profileImageUpdated.setValue(true);
    }

    public void resetProfileImageUpdated() {
        profileImageUpdated.setValue(false);
    }
} 