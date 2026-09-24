package com.us.eventum.ui.viewmodels;

import com.us.eventum.R;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.function.Consumer;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.WaitlistRepository;
import com.us.eventum.data.repositories.firebase.FirebaseWaitlistRepository;
import com.us.eventum.core.utils.WaitlistUtils;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeesToEventRepository;
import com.us.eventum.ui.viewmodels.SharedViewModel;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeNotificationRepository;
import com.us.eventum.core.utils.EventActivityLogHelper;
import com.us.eventum.core.utils.EventChangeNotifier;
import com.us.eventum.core.utils.EventPrivateAccessCode;
import com.us.eventum.core.utils.FirebaseBackendErrorHandler;
import com.us.eventum.core.utils.EventUiMerger;
import com.us.eventum.core.utils.WaitlistService;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EventViewModel extends ViewModel {
    private MutableLiveData<List<Event>> events = new MutableLiveData<>();
    private MutableLiveData<List<Event>> allEvents = new MutableLiveData<>();
    private MutableLiveData<Event> observedEvent = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventCreated = new MutableLiveData<>();
    private MutableLiveData<String> createdEventId = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventUpdated = new MutableLiveData<>();
    private MutableLiveData<String> eventUpdateMessage = new MutableLiveData<>(); // Mensaje que se mostrará en el toast al actualizar un evento
    private MutableLiveData<Boolean> eventDeleted = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private EventRepository eventRepository;
    private AttendeesToEventRepository attendeesToEventRepository;
    private WaitlistRepository waitlistRepository;
    private AttendeeNotificationRepository attendeeNotificationRepository;
    private SharedViewModel sharedViewModel;
    private boolean listeningAllEvents;
    private boolean listeningUserEvents;
    private boolean listeningSingleEvent;
    private boolean listeningRegistrations;
    private boolean listeningWaitlist;
    private boolean registrationsDataReady;
    private boolean waitlistDataReady;
    private List<Event> cachedAllEvents;
    private List<Event> cachedUserEvents;
    private List<AttendeesToEvent> latestRegistrations = new ArrayList<>();
    private List<WaitlistToEvent> latestWaitlist = new ArrayList<>();
    private Context appContext;

    // Getters para LiveData
    public LiveData<List<Event>> getEvents() { return events; }
    public LiveData<Event> getObservedEvent() { return observedEvent; }
    public LiveData<Boolean> getEventCreated() { return eventCreated; }
    public LiveData<String> getCreatedEventId() { return createdEventId; }
    public LiveData<Boolean> getEventUpdated() { return eventUpdated; }
    public LiveData<String> getEventUpdateMessage() { return eventUpdateMessage; }
    public LiveData<Boolean> getEventDeleted() { return eventDeleted; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<List<Event>> getAllEvents() { return allEvents; }

    public EventRepository getEventRepository() {
        return eventRepository;
    }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        if (eventRepository == null) {
            eventRepository = new FirebaseEventRepository();
        }
        if (attendeesToEventRepository == null) {
            attendeesToEventRepository = new FirebaseAttendeesToEventRepository();
        }
        if (waitlistRepository == null) {
            waitlistRepository = new FirebaseWaitlistRepository();
        }
        if (attendeeNotificationRepository == null) {
            attendeeNotificationRepository = new FirebaseAttendeeNotificationRepository();
        }
        if (sharedViewModel == null) {
            sharedViewModel = SharedViewModel.getInstance();
        }
    }

    /**
     * Cargar todos los eventos del usuario actual (inicia escucha en tiempo real).
     */
    public void loadUserEvents() {
        if (listeningUserEvents) {
            republishUserEvents();
            return;
        }
        startListeningUserEvents();
    }

    /**
     * Escucha en tiempo real los eventos del organizador.
     */
    public void startListeningUserEvents() {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }
        if (listeningUserEvents) {
            return;
        }
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }
        listeningUserEvents = true;
        isLoading.postValue(true);
        eventRepository.startUserEventsListener(userId, new EventRepository.AvailableEventsListener() {
            @Override
            public void onEventsUpdated(List<Event> result) {
                cachedUserEvents = result;
                syncRegistrationsListener();
                publishUserEventsWithRegistrations(false);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    public void stopListeningUserEvents() {
        if (eventRepository == null || !listeningUserEvents) {
            return;
        }
        eventRepository.stopUserEventsListener();
        listeningUserEvents = false;
        cachedUserEvents = null;
        syncRegistrationsListener();
    }

    public void restartListeningUserEvents() {
        stopListeningUserEvents();
        startListeningUserEvents();
    }

    /**
     * Escucha en tiempo real un evento concreto (detalle del organizador).
     */
    public void startListeningEvent(@NonNull String eventId) {
        if (eventRepository == null) {
            return;
        }
        if (listeningSingleEvent) {
            eventRepository.stopEventListener();
            listeningSingleEvent = false;
        }
        listeningSingleEvent = true;
        eventRepository.startEventListener(eventId, new EventRepository.SingleEventListener() {
            @Override
            public void onEventUpdated(Event event) {
                observedEvent.postValue(event);
            }

            @Override
            public void onEventRemoved() {
                observedEvent.postValue(null);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
            }
        });
    }

    public void stopListeningEvent() {
        if (eventRepository == null || !listeningSingleEvent) {
            return;
        }
        eventRepository.stopEventListener();
        listeningSingleEvent = false;
        observedEvent.postValue(null);
    }

    public void restartListeningEvent(@NonNull String eventId) {
        stopListeningEvent();
        startListeningEvent(eventId);
    }

    public void restartListeningAllEvents() {
        stopListeningAllEvents();
        startListeningAllEvents();
    }

    /**
     * Cargar catálogo completo para la home del asistente y clasificar por pestañas en UI.
     */
    public void loadAllEvents() {
        if (listeningAllEvents) {
            republishAllEvents();
            return;
        }
        startListeningAllEvents();
    }

    /**
     * Escucha Firestore en tiempo real para reflejar cambios (p. ej. público → privado).
     */
    public void startListeningAllEvents() {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }
        if (listeningAllEvents) {
            return;
        }
        listeningAllEvents = true;
        isLoading.postValue(true);
        eventRepository.startAvailableEventsListener(new EventRepository.AvailableEventsListener() {
            @Override
            public void onEventsUpdated(List<Event> events) {
                cachedAllEvents = events;
                syncRegistrationsListener();
                publishAllEventsWithRegistrations(false);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    public void stopListeningAllEvents() {
        if (eventRepository == null || !listeningAllEvents) {
            return;
        }
        eventRepository.stopAvailableEventsListener();
        listeningAllEvents = false;
        cachedAllEvents = null;
        syncRegistrationsListener();
    }

    public void fetchEventById(@NonNull String eventId,
                               @NonNull Consumer<Event> onSuccess,
                               @Nullable Runnable onError) {
        if (eventRepository == null) {
            if (onError != null) {
                onError.run();
            }
            return;
        }
        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event result) {
                onSuccess.accept(result);
            }

            @Override
            public void onError(String error) {
                if (onError != null) {
                    onError.run();
                }
            }
        });
    }

    @Override
    protected void onCleared() {
        stopListeningAllEvents();
        stopListeningUserEvents();
        stopListeningEvent();
        stopListeningRegistrations();
        stopListeningWaitlist();
    }

    private void syncRegistrationsListener() {
        if (listeningAllEvents || listeningUserEvents) {
            startListeningRegistrations();
            startListeningWaitlist();
        } else {
            stopListeningRegistrations();
            stopListeningWaitlist();
        }
    }

    private void startListeningRegistrations() {
        if (attendeesToEventRepository == null || listeningRegistrations) {
            return;
        }
        listeningRegistrations = true;
        attendeesToEventRepository.startAllRegistrationsListener(
                new AttendeesToEventRepository.AllRegistrationsListener() {
                    @Override
                    public void onRegistrationsUpdated(List<AttendeesToEvent> registrations) {
                        latestRegistrations = registrations != null ? registrations : new ArrayList<>();
                        registrationsDataReady = true;
                        publishAllEventsWithRegistrations(true);
                        publishUserEventsWithRegistrations(true);
                    }

                    @Override
                    public void onError(String error) {
                        errorMessage.postValue(error);
                        isLoading.postValue(false);
                    }
                });
    }

    private void stopListeningRegistrations() {
        if (attendeesToEventRepository == null || !listeningRegistrations) {
            return;
        }
        attendeesToEventRepository.stopAllRegistrationsListener();
        listeningRegistrations = false;
        registrationsDataReady = false;
        latestRegistrations = new ArrayList<>();
    }

    private void startListeningWaitlist() {
        if (waitlistRepository == null || listeningWaitlist) {
            return;
        }
        listeningWaitlist = true;
        waitlistRepository.startAllWaitlistListener(new WaitlistRepository.AllWaitlistListener() {
            @Override
            public void onWaitlistUpdated(List<WaitlistToEvent> entries) {
                latestWaitlist = entries != null ? entries : new ArrayList<>();
                waitlistDataReady = true;
                publishAllEventsWithRegistrations(true);
                publishUserEventsWithRegistrations(true);
            }

            @Override
            public void onError(String error) {
                Log.w("EventViewModel", "Waitlist sync: " + error);
                latestWaitlist = new ArrayList<>();
            }
        });
    }

    private void stopListeningWaitlist() {
        if (waitlistRepository == null || !listeningWaitlist) {
            return;
        }
        waitlistRepository.stopAllWaitlistListener();
        listeningWaitlist = false;
        waitlistDataReady = false;
        latestWaitlist = new ArrayList<>();
    }

    private void publishAllEventsWithRegistrations(boolean fromRegistrations) {
        if (!listeningAllEvents || cachedAllEvents == null) {
            return;
        }
        if (!registrationsDataReady && !fromRegistrations) {
            return;
        }
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        applyRegistrationDataToEvents(cachedAllEvents, latestRegistrations, uid);
        if (waitlistDataReady) {
            applyWaitlistDataToEvents(cachedAllEvents, latestWaitlist, uid);
        }
        allEvents.postValue(new ArrayList<>(cachedAllEvents));
        isLoading.postValue(false);
    }

    private void publishUserEventsWithRegistrations(boolean fromRegistrations) {
        if (!listeningUserEvents || cachedUserEvents == null) {
            return;
        }
        if (!registrationsDataReady && !fromRegistrations) {
            return;
        }
        applyRegistrationDataToEvents(cachedUserEvents, latestRegistrations, null);
        if (waitlistDataReady) {
            String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
            applyWaitlistDataToEvents(cachedUserEvents, latestWaitlist, uid);
        }
        events.postValue(new ArrayList<>(cachedUserEvents));
        isLoading.postValue(false);
    }

    /** Vuelve a emitir la caché (p. ej. al volver a la home sin parar listeners). */
    private void republishUserEvents() {
        if (!listeningUserEvents || cachedUserEvents == null) {
            return;
        }
        if (registrationsDataReady) {
            applyRegistrationDataToEvents(cachedUserEvents, latestRegistrations, null);
        }
        if (waitlistDataReady) {
            applyWaitlistDataToEvents(cachedUserEvents, latestWaitlist, null);
        }
        events.postValue(new ArrayList<>(cachedUserEvents));
        isLoading.postValue(false);
    }

    private void republishAllEvents() {
        if (!listeningAllEvents || cachedAllEvents == null) {
            return;
        }
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (registrationsDataReady) {
            applyRegistrationDataToEvents(cachedAllEvents, latestRegistrations, uid);
        }
        if (waitlistDataReady) {
            applyWaitlistDataToEvents(cachedAllEvents, latestWaitlist, uid);
        }
        allEvents.postValue(new ArrayList<>(cachedAllEvents));
        isLoading.postValue(false);
    }

    private static void applyRegistrationDataToEvents(List<Event> eventsList,
                                                      List<AttendeesToEvent> allRegistrations,
                                                      @Nullable String currentUserIdForJoinHighlight) {
        Map<String, List<AttendeesToEvent>> byEventId = new HashMap<>();
        if (allRegistrations != null) {
            for (AttendeesToEvent row : allRegistrations) {
                if (row == null || row.getEventId() == null) {
                    continue;
                }
                byEventId.computeIfAbsent(row.getEventId(), id -> new ArrayList<>()).add(row);
            }
        }
        for (Event event : eventsList) {
            if (event == null || event.getId() == null) {
                continue;
            }
            List<AttendeesToEvent> attendees = byEventId.get(event.getId());
            event.setCurrentParticipants(attendees != null ? attendees.size() : 0);
            applyJoinHighlight(event, attendees, currentUserIdForJoinHighlight);
        }
    }

    private static void applyJoinHighlight(Event event, List<AttendeesToEvent> attendees,
                                           @Nullable String currentUserIdForJoinHighlight) {
        if (currentUserIdForJoinHighlight == null || currentUserIdForJoinHighlight.isEmpty()) {
            event.setCurrentUserJoined(false);
            event.setCurrentUserScannedQR(false);
            return;
        }
        boolean joined = false;
        boolean scanned = false;
        if (attendees != null) {
            for (AttendeesToEvent row : attendees) {
                if (row != null && currentUserIdForJoinHighlight.equals(row.getUserId())) {
                    joined = true;
                    scanned = row.isScannedQR();
                    break;
                }
            }
        }
        event.setCurrentUserJoined(joined);
        event.setCurrentUserScannedQR(scanned);
    }

    private static void applyWaitlistDataToEvents(List<Event> eventsList,
                                                  List<WaitlistToEvent> allWaitlist,
                                                  @Nullable String currentUserId) {
        Map<String, List<WaitlistToEvent>> byEventId = new HashMap<>();
        if (allWaitlist != null) {
            for (WaitlistToEvent row : allWaitlist) {
                if (row == null || row.getEventId() == null) {
                    continue;
                }
                byEventId.computeIfAbsent(row.getEventId(), id -> new ArrayList<>()).add(row);
            }
        }
        long now = System.currentTimeMillis();
        for (Event event : eventsList) {
            if (event == null || event.getId() == null) {
                continue;
            }
            List<WaitlistToEvent> entries = byEventId.get(event.getId());
            if (event.isCancelled()) {
                event.setWaitlistCount(0);
                event.setCurrentUserWaitlisted(false);
                event.setCurrentUserWaitlistOffered(false);
                event.setCurrentUserWaitlistPosition(0);
                event.setWaitlistOfferExpiresAt(null);
                continue;
            }
            event.setWaitlistCount(WaitlistUtils.countActiveWaitlist(entries));
            event.setCurrentUserWaitlisted(false);
            event.setCurrentUserWaitlistOffered(false);
            event.setCurrentUserWaitlistPosition(0);
            event.setWaitlistOfferExpiresAt(null);
            if (currentUserId == null || currentUserId.isEmpty() || event.isCurrentUserJoined()) {
                continue;
            }
            WaitlistToEvent userEntry = WaitlistUtils.findActiveEntryForUser(entries, currentUserId);
            if (userEntry == null) {
                continue;
            }
            if (WaitlistToEvent.STATUS_OFFERED.equals(userEntry.getStatus())
                    && WaitlistUtils.isValidOffer(userEntry, now)) {
                event.setCurrentUserWaitlistOffered(true);
                if (userEntry.getOfferExpiresAt() != null) {
                    event.setWaitlistOfferExpiresAt(userEntry.getOfferExpiresAt().toDate());
                }
            } else if (WaitlistToEvent.STATUS_WAITING.equals(userEntry.getStatus())) {
                event.setCurrentUserWaitlisted(true);
                event.setCurrentUserWaitlistPosition(
                        WaitlistUtils.computeWaitingPosition(entries, currentUserId));
            }
        }
    }

    /**
     * Crear un nuevo evento
     */
    public void createEvent(String userId, String title, String description, Date date, String location, 
                           int maxParticipants, String eventType, boolean isPrivate, boolean requiresParentalAuth,
                           @Nullable String privateAccessCode) {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }
        
        isLoading.postValue(true);
        
        // Validaciones
        if (title == null || title.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_title_required) : "El título es obligatorio"));
            isLoading.postValue(false);
            return;
        }
        
        if (description == null || description.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_description_required) : "La descripción es obligatoria"));
            isLoading.postValue(false);
            return;
        }
        
        if (date == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_edit_event_date_required) : "La fecha es obligatoria"));
            isLoading.postValue(false);
            return;
        }
        
        if (location == null || location.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_location_required_vm) : "La ubicación es obligatoria"));
            isLoading.postValue(false);
            return;
        }
        
        if (maxParticipants <= 0) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_max_participants_gt_zero) : "El número máximo de participantes debe ser mayor a 0"));
            isLoading.postValue(false);
            return;
        }

        String normalizedAccessCode = EventPrivateAccessCode.normalize(privateAccessCode);
        if (isPrivate) {
            if (!EventPrivateAccessCode.isValidFormat(privateAccessCode)) {
                errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_private_code_format_hyphen) : "El código del evento privado debe ser alfanumérico (4-20 caracteres)"));
                isLoading.postValue(false);
                return;
            }
        } else {
            normalizedAccessCode = null;
        }

        Event event = new Event(title, description, date, location, userId, maxParticipants, eventType);
        event.setPrivateEvent(isPrivate);
        event.setRequiresParentalAuth(requiresParentalAuth);
        event.setPrivateAccessCode(normalizedAccessCode);

        eventRepository.createEvent(event, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event result) {
                createdEventId.postValue(result != null ? result.getId() : null);
                eventCreated.postValue(true);
                isLoading.postValue(false);
                // Notificar al SharedViewModel que los eventos han sido actualizados
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Actualizar un evento existente
     */
    public void updateEvent(String eventId, String title, String description, Date date,
                           String location, int maxParticipants, String eventType, boolean isPrivate,
                           boolean requiresParentalAuth, @Nullable String privateAccessCode) {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }

        isLoading.postValue(true);

        // Validaciones (mismas que en createEvent)
        if (title == null || title.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_title_required) : "El título es obligatorio"));
            isLoading.postValue(false);
            return;
        }

        if (date == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_edit_event_date_required) : "La fecha es obligatoria"));
            isLoading.postValue(false);
            return;
        }

        if (location == null || location.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_location_required_vm) : "La ubicación es obligatoria"));
            isLoading.postValue(false);
            return;
        }

        if (maxParticipants <= 0) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_max_participants_gt_zero) : "El número máximo de participantes debe ser mayor a 0"));
            isLoading.postValue(false);
            return;
        }

        String normalizedAccessCode = EventPrivateAccessCode.normalize(privateAccessCode);
        if (isPrivate) {
            if (!EventPrivateAccessCode.isValidFormat(privateAccessCode)) {
                errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_private_code_format_hyphen) : "El código del evento privado debe ser alfanumérico (4-20 caracteres)"));
                isLoading.postValue(false);
                return;
            }
        } else {
            normalizedAccessCode = null;
        }

        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        String normalizedDescription = description != null ? description.trim() : "";
        Event event = new Event(title, normalizedDescription, date, location, userId, maxParticipants, eventType);
        event.setId(eventId);
        event.setPrivateEvent(isPrivate);
        event.setRequiresParentalAuth(requiresParentalAuth);
        event.setPrivateAccessCode(normalizedAccessCode);

        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event previous) {
                persistUpdatedEvent(eventId, event, previous);
            }

            @Override
            public void onError(String error) {
                Log.w("EventViewModel", "No se pudo leer el evento previo: " + error);
                persistUpdatedEvent(eventId, event, null);
            }
        });
    }

    private void persistUpdatedEvent(String eventId, Event event, @Nullable Event previous) {
        boolean notifyAttendees = previous == null
                || EventUiMerger.hasAttendeeVisibleDetailsChanged(previous, event);

        eventRepository.updateEvent(eventId, event, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                eventUpdateMessage.postValue((appContext != null ? appContext.getString(R.string.toast_event_updated) : "Evento actualizado correctamente"));
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                EventActivityLogHelper.logEventUpdated(eventId);
                WaitlistService.promoteIfNeeded(
                        eventId,
                        attendeesToEventRepository,
                        waitlistRepository,
                        eventRepository,
                        null);
                if (notifyAttendees) {
                    EventChangeNotifier.notifyAttendees(
                            eventId,
                            event.getTitle(),
                            attendeesToEventRepository,
                            waitlistRepository,
                            attendeeNotificationRepository);
                }

                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Cambiar el estado de privacidad de un evento
     */
    public void updateEventPrivacy(String eventId, boolean isPrivate, @Nullable String privateAccessCode) {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }

        String normalizedCode = EventPrivateAccessCode.normalize(privateAccessCode);
        if (isPrivate && !EventPrivateAccessCode.isValidFormat(normalizedCode)) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_private_access_code_format) : "El código debe ser alfanumérico (4–20 caracteres)"));
            return;
        }

        isLoading.postValue(true);

        eventRepository.updateEventPrivacy(eventId, isPrivate, isPrivate ? normalizedCode : null,
                new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                String estado = isPrivate
                        ? (appContext != null ? appContext.getString(R.string.label_privacy_state_private) : "privado")
                        : (appContext != null ? appContext.getString(R.string.label_privacy_state_public) : "público");
                if (appContext != null) {
                    eventUpdateMessage.postValue(
                            appContext.getString(R.string.toast_event_privacy_updated, estado));
                } else {
                    eventUpdateMessage.postValue("Evento actualizado a '" + estado + "'");
                }
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                updateEventPrivacyInList(eventId, isPrivate, isPrivate ? normalizedCode : null);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    private void updateEventPrivacyInList(String eventId, boolean isPrivate, @Nullable String privateAccessCode) {
        List<Event> currentEvents = events.getValue();
        if (currentEvents != null) {
            for (Event event : currentEvents) {
                if (event.getId() != null && event.getId().equals(eventId)) {
                    event.setPrivateEvent(isPrivate);
                    event.setPrivateAccessCode(isPrivate ? privateAccessCode : null);
                    event.setPrivateAccessCodeHash(isPrivate
                            ? EventPrivateAccessCode.hash(privateAccessCode) : null);
                    break;
                }
            }
            events.postValue(currentEvents);
        }

        List<Event> currentAllEvents = allEvents.getValue();
        if (currentAllEvents != null) {
            for (Event event : currentAllEvents) {
                if (event.getId() != null && event.getId().equals(eventId)) {
                    event.setPrivateEvent(isPrivate);
                    event.setPrivateAccessCode(isPrivate ? privateAccessCode : null);
                    event.setPrivateAccessCodeHash(isPrivate
                            ? EventPrivateAccessCode.hash(privateAccessCode) : null);
                    break;
                }
            }
            allEvents.postValue(currentAllEvents);
        }
    }

    /**
     * Eliminar un evento
     */
    public void deleteEvent(String eventId) {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }
        
        isLoading.postValue(true);
        
        eventRepository.deleteEvent(eventId, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                eventDeleted.postValue(true);
                isLoading.postValue(false);
                // Notificar al SharedViewModel que los eventos han sido actualizados
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Cancela un evento (sigue visible, con aviso a inscritos y waitlist).
     */
    public void cancelEvent(String eventId) {
        if (eventRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            return;
        }
        if (eventId == null || eventId.isEmpty()) {
            errorMessage.postValue(FirebaseBackendErrorHandler.getInvalidEventIdMessage(null));
            return;
        }

        isLoading.postValue(true);
        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                if (event != null && event.isCancelled()) {
                    errorMessage.postValue(FirebaseBackendErrorHandler.getEventAlreadyCancelledMessage(null));
                    isLoading.postValue(false);
                    return;
                }
                persistCancelledEvent(eventId, event != null ? event.getTitle() : null);
            }

            @Override
            public void onError(String error) {
                persistCancelledEvent(eventId, null);
            }
        });
    }

    private void persistCancelledEvent(String eventId, @Nullable String eventTitle) {
        notifyThenCancelEvent(eventId, eventTitle);
    }

    private void notifyThenCancelEvent(String eventId, @Nullable String eventTitle) {
        if (attendeesToEventRepository == null) {
            persistCancelledFlag(eventId, eventTitle);
            return;
        }
        attendeesToEventRepository.loadAttendeesToEvent(eventId,
                new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
                    @Override
                    public void onSuccess(List<AttendeesToEvent> registrations) {
                        if (waitlistRepository == null) {
                            EventChangeNotifier.notifyFromLists(
                                    eventId, eventTitle, registrations, null,
                                    attendeeNotificationRepository,
                                    AttendeeNotification.TYPE_EVENT_CANCELLED);
                            persistCancelledFlag(eventId, eventTitle);
                            return;
                        }
                        waitlistRepository.loadWaitlistForEvent(eventId,
                                new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
                                    @Override
                                    public void onSuccess(List<WaitlistToEvent> entries) {
                                        EventChangeNotifier.notifyFromLists(
                                                eventId, eventTitle, registrations, entries,
                                                attendeeNotificationRepository,
                                                AttendeeNotification.TYPE_EVENT_CANCELLED);
                                        persistCancelledFlag(eventId, eventTitle);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        EventChangeNotifier.notifyFromLists(
                                                eventId, eventTitle, registrations, null,
                                                attendeeNotificationRepository,
                                                AttendeeNotification.TYPE_EVENT_CANCELLED);
                                        persistCancelledFlag(eventId, eventTitle);
                                    }
                                });
                    }

                    @Override
                    public void onError(String error) {
                        persistCancelledFlag(eventId, eventTitle);
                    }
                });
    }

    private void persistCancelledFlag(String eventId, @Nullable String eventTitle) {
        eventRepository.cancelEvent(eventId, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                eventUpdateMessage.postValue((appContext != null ? appContext.getString(R.string.event_log_action_event_cancelled) : "Evento cancelado"));
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                EventActivityLogHelper.logEventCancelled(eventId);
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Limpiar estados de operaciones
     */
    public void clearOperationStates() {
        eventCreated.postValue(false);
        createdEventId.postValue(null);
        eventUpdated.postValue(false);
        eventUpdateMessage.postValue(null);
        eventDeleted.postValue(false);
        errorMessage.postValue(null);
    }
}