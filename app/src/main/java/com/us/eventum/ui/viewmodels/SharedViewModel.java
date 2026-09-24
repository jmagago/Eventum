package com.us.eventum.ui.viewmodels;

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
        eventsUpdated.postValue(true);
    }

    public void resetEventsUpdated() {
        eventsUpdated.postValue(false);
    }

    public void notifyProfileImageUpdated() {
        profileImageUpdated.postValue(true);
    }

    public void resetProfileImageUpdated() {
        profileImageUpdated.postValue(false);
    }
} 