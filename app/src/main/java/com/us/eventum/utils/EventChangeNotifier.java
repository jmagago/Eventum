package com.us.eventum.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.repositories.WaitlistRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aviso in-app a inscritos y lista de espera cuando cambian datos visibles del evento.
 */
public final class EventChangeNotifier {

    private static final String TAG = "EventChangeNotifier";

    private EventChangeNotifier() {
    }

    public static void notifyAttendees(@Nullable String eventId,
                                       @Nullable String eventTitle,
                                       @Nullable AttendeesToEventRepository attendeesRepo,
                                       @Nullable WaitlistRepository waitlistRepo,
                                       @Nullable AttendeeNotificationRepository notificationRepo) {
        notifyAttendees(eventId, eventTitle, attendeesRepo, waitlistRepo, notificationRepo,
                AttendeeNotification.TYPE_EVENT_CHANGED);
    }

    public static void notifyAttendees(@Nullable String eventId,
                                       @Nullable String eventTitle,
                                       @Nullable AttendeesToEventRepository attendeesRepo,
                                       @Nullable WaitlistRepository waitlistRepo,
                                       @Nullable AttendeeNotificationRepository notificationRepo,
                                       @Nullable String type) {
        if (eventId == null || eventId.isEmpty() || attendeesRepo == null || notificationRepo == null) {
            return;
        }
        String notificationType = type != null && !type.trim().isEmpty()
                ? type
                : AttendeeNotification.TYPE_EVENT_CHANGED;
        String title = eventTitle != null ? eventTitle.trim() : "";
        attendeesRepo.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> registrations) {
                Set<String> attendeeIds = new HashSet<>();
                addRegistrationIds(registrations, attendeeIds);
                if (waitlistRepo == null) {
                    sendTo(attendeeIds, eventId, title, notificationType, notificationRepo);
                    return;
                }
                waitlistRepo.loadWaitlistForEvent(eventId, new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
                    @Override
                    public void onSuccess(List<WaitlistToEvent> entries) {
                        addWaitlistIds(entries, attendeeIds);
                        sendTo(attendeeIds, eventId, title, notificationType, notificationRepo);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Waitlist para aviso de cambios: " + error);
                        sendTo(attendeeIds, eventId, title, notificationType, notificationRepo);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Inscritos para aviso de cambios: " + error);
                if (waitlistRepo == null) {
                    return;
                }
                waitlistRepo.loadWaitlistForEvent(eventId, new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
                    @Override
                    public void onSuccess(List<WaitlistToEvent> entries) {
                        Set<String> attendeeIds = new HashSet<>();
                        addWaitlistIds(entries, attendeeIds);
                        sendTo(attendeeIds, eventId, title, notificationType, notificationRepo);
                    }

                    @Override
                    public void onError(String waitlistError) {
                        Log.w(TAG, "Waitlist para aviso de cambios: " + waitlistError);
                    }
                });
            }
        });
    }

    public static void notifyFromLists(@Nullable String eventId,
                                       @Nullable String eventTitle,
                                       @Nullable List<AttendeesToEvent> registrations,
                                       @Nullable List<WaitlistToEvent> waitlist,
                                       @Nullable AttendeeNotificationRepository notificationRepo,
                                       @Nullable String type) {
        if (eventId == null || eventId.isEmpty() || notificationRepo == null) {
            return;
        }
        String notificationType = type != null && !type.trim().isEmpty()
                ? type
                : AttendeeNotification.TYPE_EVENT_CHANGED;
        Set<String> attendeeIds = new HashSet<>();
        addRegistrationIds(registrations, attendeeIds);
        addWaitlistIds(waitlist, attendeeIds);
        sendTo(attendeeIds, eventId, eventTitle != null ? eventTitle.trim() : "",
                notificationType, notificationRepo);
    }

    private static void addRegistrationIds(@Nullable List<AttendeesToEvent> registrations,
                                           @NonNull Set<String> attendeeIds) {
        if (registrations == null) {
            return;
        }
        for (AttendeesToEvent registration : registrations) {
            if (registration != null && registration.getUserId() != null && !registration.getUserId().isEmpty()) {
                attendeeIds.add(registration.getUserId());
            }
        }
    }

    private static void addWaitlistIds(@Nullable List<WaitlistToEvent> entries,
                                       @NonNull Set<String> attendeeIds) {
        if (entries == null) {
            return;
        }
        for (WaitlistToEvent entry : entries) {
            if (entry != null && entry.isActiveForUser()
                    && entry.getUserId() != null && !entry.getUserId().isEmpty()) {
                attendeeIds.add(entry.getUserId());
            }
        }
    }

    private static void sendTo(@NonNull Set<String> attendeeIds,
                               @NonNull String eventId,
                               @NonNull String eventTitle,
                               @NonNull String type,
                               @NonNull AttendeeNotificationRepository notificationRepo) {
        for (String attendeeId : attendeeIds) {
            AttendeeNotification notification = new AttendeeNotification(
                    attendeeId,
                    eventId,
                    eventTitle,
                    type);
            notificationRepo.create(notification, new AttendeeNotificationRepository.RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Aviso enviado a " + attendeeId);
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "No se pudo avisar a " + attendeeId + ": " + error);
                }
            });
        }
    }
}
