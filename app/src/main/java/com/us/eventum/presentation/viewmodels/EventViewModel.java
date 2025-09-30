package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.hybrid.HybridEventRepository;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.hybrid.HybridAttendeeRepository;
import com.us.eventum.presentation.viewmodels.SharedViewModel;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventViewModel extends ViewModel {
    private MutableLiveData<List<Event>> events = new MutableLiveData<>();
    private MutableLiveData<List<Event>> allEvents = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventCreated = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventUpdated = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventDeleted = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private EventRepository eventRepository;
    private AttendeeRepository attendeeRepository;
    private SharedViewModel sharedViewModel;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // Getters para LiveData
    public LiveData<List<Event>> getEvents() { return events; }
    public LiveData<Boolean> getEventCreated() { return eventCreated; }
    public LiveData<Boolean> getEventUpdated() { return eventUpdated; }
    public LiveData<Boolean> getEventDeleted() { return eventDeleted; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<List<Event>> getAllEvents() { return allEvents; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        if (eventRepository == null) {
            eventRepository = new HybridEventRepository(context);
        }
        if (attendeeRepository == null) {
            attendeeRepository = new HybridAttendeeRepository(context);
        }
        if (sharedViewModel == null) {
            sharedViewModel = SharedViewModel.getInstance();
        }
    }

    /**
     * Cargar todos los eventos del usuario actual
     */
    public void loadUserEvents() {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }
        
        eventRepository.loadUserEvents(userId, new EventRepository.RepositoryCallback<List<Event>>() {
            @Override
            public void onSuccess(List<Event> result) {
                // Cargar número de asistentes para cada evento
                loadAttendeeCounts(result);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Cargar eventos disponibles (públicos y futuros) para asistentes
     */
    public void loadAllEvents() {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }

        isLoading.postValue(true);

        eventRepository.loadAvailableEvents(new EventRepository.RepositoryCallback<List<Event>>() {
            @Override
            public void onSuccess(List<Event> result) {
                // Cargar número de asistentes para cada evento
                loadAttendeeCountsForAllEvents(result);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Cargar el número de asistentes para cada evento desde caché local
     */
    private void loadAttendeeCounts(List<Event> eventsList) {
        if (attendeeRepository instanceof HybridAttendeeRepository) {
            // Cargar conteos de asistentes desde caché local de forma eficiente
            loadAttendeeCountsFromCache(eventsList);
        } else {
            // Si no hay repositorio híbrido, establecer en 0
            for (Event event : eventsList) {
                event.setCurrentParticipants(0);
            }
            this.events.postValue(eventsList);
            isLoading.postValue(false);
        }
    }

    /**
     * Cargar el número de asistentes para todos los eventos (usado en AttendeeHomeActivity)
     */
    private void loadAttendeeCountsForAllEvents(List<Event> eventsList) {
        if (attendeeRepository instanceof HybridAttendeeRepository) {
            // Cargar conteos de asistentes desde caché local de forma eficiente
            loadAttendeeCountsFromCacheForAllEvents(eventsList);
        } else {
            // Si no hay repositorio híbrido, establecer en 0
            for (Event event : eventsList) {
                event.setCurrentParticipants(0);
            }
            this.allEvents.postValue(eventsList);
            isLoading.postValue(false);
        }
    }
    
    private void loadAttendeeCountsFromCache(List<Event> eventsList) {
        if (attendeeRepository instanceof HybridAttendeeRepository) {
        HybridAttendeeRepository hybridRepo = (HybridAttendeeRepository) attendeeRepository;
        int[] completedCount = {0};
        int totalEvents = eventsList.size();
            
            // Si no hay eventos, devolver inmediatamente
            if (totalEvents == 0) {
                events.postValue(eventsList);
                isLoading.postValue(false);
                return;
            }
        
        for (Event event : eventsList) {
            executor.execute(() -> {
                    try {
                        // Cargar asistentes usando el método público del repositorio híbrido
                        hybridRepo.loadEventAttendees(event.getId(), new AttendeeRepository.RepositoryCallback<List<Attendee>>() {
                            @Override
                            public void onSuccess(List<Attendee> result) {
                                // Contar asistentes desde caché local de forma segura
                try {
                    int count = hybridRepo.roomRepository.attendeeDao.getEventAttendeesCount(event.getId());
                    event.setCurrentParticipants(count);
                } catch (Exception e) {
                    event.setCurrentParticipants(0);
                }
                                
                                synchronized (completedCount) {
                                    completedCount[0]++;
                                    if (completedCount[0] == totalEvents) {
                                        events.postValue(eventsList);
                                        isLoading.postValue(false);
                                    }
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                // Si falla, establecer en 0
                                event.setCurrentParticipants(0);
                                synchronized (completedCount) {
                                    completedCount[0]++;
                                    if (completedCount[0] == totalEvents) {
                                        events.postValue(eventsList);
                                        isLoading.postValue(false);
                                    }
                                }
                            }
                        });
                    } catch (Exception e) {
                        event.setCurrentParticipants(0);
                        synchronized (completedCount) {
                    completedCount[0]++;
                    if (completedCount[0] == totalEvents) {
                        events.postValue(eventsList);
                        isLoading.postValue(false);
                            }
                        }
                    }
                });
            }
        } else {
            // Si no hay repositorio híbrido, establecer en 0
            for (Event event : eventsList) {
                event.setCurrentParticipants(0);
            }
            events.postValue(eventsList);
            isLoading.postValue(false);
        }
    }

    private void loadAttendeeCountsFromCacheForAllEvents(List<Event> eventsList) {
        if (attendeeRepository instanceof HybridAttendeeRepository) {
            HybridAttendeeRepository hybridRepo = (HybridAttendeeRepository) attendeeRepository;
            int[] completedCount = {0};
            int totalEvents = eventsList.size();
            
            // Si no hay eventos, devolver inmediatamente
            if (totalEvents == 0) {
                allEvents.postValue(eventsList);
                isLoading.postValue(false);
                return;
            }
            
            for (Event event : eventsList) {
                executor.execute(() -> {
                    try {
                        // Cargar asistentes usando el método público del repositorio híbrido
                        hybridRepo.loadEventAttendees(event.getId(), new AttendeeRepository.RepositoryCallback<List<Attendee>>() {
                            @Override
                            public void onSuccess(List<Attendee> result) {
                                // Contar asistentes desde caché local de forma segura
                                try {
                                    int count = hybridRepo.roomRepository.attendeeDao.getEventAttendeesCount(event.getId());
                                    event.setCurrentParticipants(count);
                                } catch (Exception e) {
                                    event.setCurrentParticipants(0);
                                }
                                
                                synchronized (completedCount) {
                                    completedCount[0]++;
                                    if (completedCount[0] == totalEvents) {
                                        allEvents.postValue(eventsList);
                                        isLoading.postValue(false);
                                    }
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                // Si falla, establecer en 0
                                event.setCurrentParticipants(0);
                                synchronized (completedCount) {
                                    completedCount[0]++;
                                    if (completedCount[0] == totalEvents) {
                                        allEvents.postValue(eventsList);
                                        isLoading.postValue(false);
                                    }
                                }
                            }
                        });
                    } catch (Exception e) {
                        event.setCurrentParticipants(0);
                        synchronized (completedCount) {
                            completedCount[0]++;
                            if (completedCount[0] == totalEvents) {
                                allEvents.postValue(eventsList);
                                isLoading.postValue(false);
                            }
                    }
                }
            });
            }
        } else {
            // Si no hay repositorio híbrido, establecer en 0
            for (Event event : eventsList) {
                event.setCurrentParticipants(0);
            }
            allEvents.postValue(eventsList);
            isLoading.postValue(false);
        }
    }

    /**
     * Crear un nuevo evento
     */
    public void createEvent(String userId, String title, String description, Date date, String location, 
                           int maxParticipants, String eventType, boolean isPrivate) {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        // Validaciones
        if (title == null || title.trim().isEmpty()) {
            errorMessage.postValue("El título es obligatorio");
            isLoading.postValue(false);
            return;
        }
        
        if (description == null || description.trim().isEmpty()) {
            errorMessage.postValue("La descripción es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (date == null) {
            errorMessage.postValue("La fecha es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (location == null || location.trim().isEmpty()) {
            errorMessage.postValue("La ubicación es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (maxParticipants <= 0) {
            errorMessage.postValue("El número máximo de participantes debe ser mayor a 0");
            isLoading.postValue(false);
            return;
        }

        Event event = new Event(title, description, date, location, userId, maxParticipants, eventType);
        event.setPrivateEvent(isPrivate);

        eventRepository.createEvent(event, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event result) {
                eventCreated.postValue(true);
                isLoading.postValue(false);
                // Notificar al SharedViewModel que los eventos han sido actualizados
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
                // Recargar eventos para incluir el nuevo
                loadUserEvents();
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
                           String location, int maxParticipants, String eventType, boolean isPrivate) {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        // Validaciones (mismas que en createEvent)
        if (title == null || title.trim().isEmpty()) {
            errorMessage.postValue("El título es obligatorio");
            isLoading.postValue(false);
            return;
        }
        
        if (description == null || description.trim().isEmpty()) {
            errorMessage.postValue("La descripción es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (date == null) {
            errorMessage.postValue("La fecha es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (location == null || location.trim().isEmpty()) {
            errorMessage.postValue("La ubicación es obligatoria");
            isLoading.postValue(false);
            return;
        }
        
        if (maxParticipants <= 0) {
            errorMessage.postValue("El número máximo de participantes debe ser mayor a 0");
            isLoading.postValue(false);
            return;
        }

        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }
        
        Event event = new Event(title, description, date, location, userId, maxParticipants, eventType);
        event.setId(eventId);
        event.setPrivateEvent(isPrivate);

        eventRepository.updateEvent(eventId, event, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                // Notificar al SharedViewModel que los eventos han sido actualizados
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
                // Recargar eventos para reflejar los cambios
                loadUserEvents();
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Eliminar un evento
     */
    public void deleteEvent(String eventId) {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
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
                // Recargar eventos para reflejar los cambios
                loadUserEvents();
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
        eventUpdated.postValue(false);
        eventDeleted.postValue(false);
        errorMessage.postValue(null);
    }
}