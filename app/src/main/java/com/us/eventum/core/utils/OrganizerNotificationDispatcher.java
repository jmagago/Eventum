package com.us.eventum.core.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.us.eventum.data.models.OrganizerNotification;

/**
 * Reparte avisos al organizador en la UI (toast) cuando la notificación del sistema no está disponible.
 */
public final class OrganizerNotificationDispatcher {

    private static final MutableLiveData<OrganizerNotification> latestNotification = new MutableLiveData<>();

    private OrganizerNotificationDispatcher() {
    }

    public static LiveData<OrganizerNotification> getLatestNotification() {
        return latestNotification;
    }

    public static void dispatch(OrganizerNotification notification) {
        if (notification != null) {
            latestNotification.postValue(notification);
        }
    }
}
