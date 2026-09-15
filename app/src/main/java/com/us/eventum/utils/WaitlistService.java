package com.us.eventum.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.WaitlistRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de promoción FIFO de la lista de espera cuando se liberan plazas.
 */
public final class WaitlistService {

    private static final String TAG = "WaitlistService";

    private WaitlistService() {
    }

    public static void promoteIfNeeded(
            String eventId,
            AttendeesToEventRepository attendeesRepo,
            WaitlistRepository waitlistRepo,
            EventRepository eventRepo,
            AttendeeNotificationRepository notificationRepo) {
        if (eventId == null || eventId.isEmpty()
                || attendeesRepo == null || waitlistRepo == null || eventRepo == null) {
            return;
        }
        eventRepo.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                if (event == null || event.isCancelled()) {
                    return;
                }
                loadAndPromote(event, attendeesRepo, waitlistRepo, notificationRepo, 0);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "No se pudo cargar evento: " + error);
            }
        });
    }

    private static void loadAndPromote(
            Event event,
            AttendeesToEventRepository attendeesRepo,
            WaitlistRepository waitlistRepo,
            AttendeeNotificationRepository notificationRepo,
            int depth) {
        attendeesRepo.loadAttendeesToEvent(event.getId(),
                new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
                    @Override
                    public void onSuccess(List<AttendeesToEvent> registrations) {
                        waitlistRepo.loadWaitlistForEvent(event.getId(),
                                new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
                                    @Override
                                    public void onSuccess(List<WaitlistToEvent> waitlist) {
                                        promoteInternal(
                                                event,
                                                registrations != null ? registrations : new ArrayList<>(),
                                                waitlist != null ? waitlist : new ArrayList<>(),
                                                attendeesRepo,
                                                waitlistRepo,
                                                notificationRepo,
                                                depth);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Log.w(TAG, "No se pudo cargar waitlist: " + error);
                                    }
                                });
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "No se pudieron cargar inscripciones: " + error);
                    }
                });
    }

    private static void promoteInternal(
            Event event,
            List<AttendeesToEvent> registrations,
            List<WaitlistToEvent> waitlist,
            AttendeesToEventRepository attendeesRepo,
            WaitlistRepository waitlistRepo,
            AttendeeNotificationRepository notificationRepo,
            int depth) {
        if (depth > 25) {
            return;
        }
        long now = System.currentTimeMillis();
        List<WaitlistToEvent> expired = new ArrayList<>();
        for (WaitlistToEvent entry : waitlist) {
            if (WaitlistUtils.isOfferExpired(entry, now)) {
                expired.add(entry);
            }
        }
        if (!expired.isEmpty()) {
            expireOffers(event.getId(), expired, waitlistRepo, 0, () ->
                    loadAndPromote(event, attendeesRepo, waitlistRepo, notificationRepo, depth + 1));
            return;
        }
        int registered = registrations.size();
        int validOffers = WaitlistUtils.countValidOffers(waitlist);
        int overflow = registered + validOffers - event.getMaxParticipants();
        if (overflow > 0) {
            List<WaitlistToEvent> excess = WaitlistUtils.findNewestValidOffers(waitlist, overflow);
            if (!excess.isEmpty()) {
                revertOffersToWaiting(event.getId(), excess, waitlistRepo, 0, () ->
                        loadAndPromote(event, attendeesRepo, waitlistRepo, notificationRepo, depth + 1));
                return;
            }
        }
        int available = event.getMaxParticipants() - registered - validOffers;
        if (available <= 0) {
            return;
        }
        WaitlistToEvent next = WaitlistUtils.findFirstWaiting(waitlist);
        if (next == null || next.getId() == null) {
            return;
        }
        waitlistRepo.offerNextSpot(
                event.getId(),
                next.getId(),
                registered,
                validOffers,
                WaitlistUtils.OFFER_DURATION_MS,
                new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
                    @Override
                    public void onSuccess(WaitlistToEvent result) {
                        notifySpotAvailable(notificationRepo, result != null ? result.getUserId() : next.getUserId(), event);
                        EventActivityLogHelper.logWaitlistOffered(event.getId(), null);
                        loadAndPromote(event, attendeesRepo, waitlistRepo, notificationRepo, depth + 1);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "No se pudo ofrecer plaza: " + error);
                        if (error != null && error.contains(
                                FirebaseBackendErrorHandler.getWaitlistOfferInvalidMessage(null))) {
                            loadAndPromote(event, attendeesRepo, waitlistRepo, notificationRepo, depth + 1);
                        }
                    }
                });
    }

    private static void revertOffersToWaiting(
            String eventId,
            List<WaitlistToEvent> excess,
            WaitlistRepository waitlistRepo,
            int index,
            Runnable onComplete) {
        if (index >= excess.size()) {
            onComplete.run();
            return;
        }
        WaitlistToEvent entry = excess.get(index);
        if (entry.getId() == null) {
            revertOffersToWaiting(eventId, excess, waitlistRepo, index + 1, onComplete);
            return;
        }
        waitlistRepo.revertOfferToWaiting(eventId, entry.getId(),
                new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
                    @Override
                    public void onSuccess(WaitlistToEvent result) {
                        revertOffersToWaiting(eventId, excess, waitlistRepo, index + 1, onComplete);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "No se pudo devolver la oferta a espera: " + error);
                        revertOffersToWaiting(eventId, excess, waitlistRepo, index + 1, onComplete);
                    }
                });
    }

    private static void expireOffers(
            String eventId,
            List<WaitlistToEvent> expired,
            WaitlistRepository waitlistRepo,
            int index,
            Runnable onComplete) {
        if (index >= expired.size()) {
            onComplete.run();
            return;
        }
        WaitlistToEvent entry = expired.get(index);
        if (entry.getId() == null) {
            expireOffers(eventId, expired, waitlistRepo, index + 1, onComplete);
            return;
        }
        waitlistRepo.expireOffer(eventId, entry.getId(),
                new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
                    @Override
                    public void onSuccess(WaitlistToEvent result) {
                        EventActivityLogHelper.logWaitlistOfferExpired(eventId, null);
                        expireOffers(eventId, expired, waitlistRepo, index + 1, onComplete);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "No se pudo expirar oferta: " + error);
                        expireOffers(eventId, expired, waitlistRepo, index + 1, onComplete);
                    }
                });
    }

    private static void notifySpotAvailable(
            @Nullable AttendeeNotificationRepository notificationRepo,
            @Nullable String attendeeId,
            Event event) {
        if (notificationRepo == null || attendeeId == null || event == null) {
            return;
        }
        String title = event.getTitle() != null ? event.getTitle().trim() : "";
        AttendeeNotification notification = new AttendeeNotification(
                attendeeId,
                event.getId(),
                title,
                AttendeeNotification.TYPE_WAITLIST_SPOT_AVAILABLE);
        notificationRepo.create(notification,
                new AttendeeNotificationRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "Notificación de plaza disponible enviada");
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "No se pudo notificar plaza disponible: " + error);
                    }
                });
    }
}
