package com.us.eventum.core.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.us.eventum.data.models.AttendeeNotification;

/**
 * Reparte notificaciones de asistente a la UI en primer plano.
 */
public final class AttendeeNotificationDispatcher {

    private static final MutableLiveData<AttendeeNotification> latestNotification = new MutableLiveData<>();

    private AttendeeNotificationDispatcher() {
    }

    public static LiveData<AttendeeNotification> getLatestNotification() {
        return latestNotification;
    }

    public static void dispatch(AttendeeNotification notification) {
        if (notification != null) {
            latestNotification.postValue(notification);
        }
    }
}
