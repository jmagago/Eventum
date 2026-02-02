package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeesToEventRepository;
import com.us.eventum.presentation.viewmodels.SharedViewModel;

import java.util.Date;
import java.util.List;

public class EventViewModel extends ViewModel {
    private MutableLiveData<List<Event>> events = new MutableLiveData<>();
    private MutableLiveData<List<Event>> allEvents = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventCreated = new MutableLiveData<>();
    private MutableLiveData<Boolean> eventUpdated = new MutableLiveData<>();
    private MutableLiveData<String> eventUpdateMessage = new MutableLiveData<>(); // Mensaje que se mostrará en el toast al actualizar un evento
    private MutableLiveData<Boolean> eventDeleted = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private EventRepository eventRepository;
    private AttendeesToEventRepository attendeesToEventRepository;
    private SharedViewModel sharedViewModel;

    // Getters para LiveData
    public LiveData<List<Event>> getEvents() { return events; }
    public LiveData<Boolean> getEventCreated() { return eventCreated; }
    public LiveData<Boolean> getEventUpdated() { return eventUpdated; }
    public LiveData<String> getEventUpdateMessage() { return eventUpdateMessage; }
    public LiveData<Boolean> getEventDeleted() { return eventDeleted; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<List<Event>> getAllEvents() { return allEvents; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        if (eventRepository == null) {
            eventRepository = new FirebaseEventRepository();
        }
        if (attendeesToEventRepository == null) {
            attendeesToEventRepository = new FirebaseAttendeesToEventRepository();
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
     * Cargar el número de asistentes para cada evento desde Firebase
     */
    private void loadAttendeeCounts(List<Event> eventsList) {
        loadAttendeeCountsFromFirebase(eventsList, events);
    }

    /**
     * Cargar el número de asistentes para todos los eventos (usado en AttendeeHomeActivity)
     */
    private void loadAttendeeCountsForAllEvents(List<Event> eventsList) {
        loadAttendeeCountsFromFirebase(eventsList, allEvents);
    }
    
    /**
     * Cargar el número de asistentes para cada evento desde Firebase
     * @param eventsList Lista de eventos a procesar
     * @param targetLiveData LiveData a actualizar (events o allEvents)
     */
    private void loadAttendeeCountsFromFirebase(List<Event> eventsList, MutableLiveData<List<Event>> targetLiveData) {
        if (attendeesToEventRepository == null) {
            // Si no hay repositorio, establecer en 0 y continuar
            for (Event event : eventsList) {
                event.setCurrentParticipants(0);
            }
            targetLiveData.postValue(eventsList);
            isLoading.postValue(false);
            return;
        }

        // Cargar asistentes para cada evento
        loadAttendeeCountsForEvents(eventsList, 0, targetLiveData);
    }

    /**
     * Cargar asistentes para eventos de forma recursiva
     */
    private void loadAttendeeCountsForEvents(List<Event> eventsList, int currentIndex, MutableLiveData<List<Event>> targetLiveData) {
        if (currentIndex >= eventsList.size()) {
            // Todos los eventos procesados
            targetLiveData.postValue(eventsList);
            isLoading.postValue(false);
            return;
        }

        Event event = eventsList.get(currentIndex);
        attendeesToEventRepository.loadAttendeesToEvent(event.getId(), new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> attendees) {
                event.setCurrentParticipants(attendees != null ? attendees.size() : 0);
                // Procesar siguiente evento
                loadAttendeeCountsForEvents(eventsList, currentIndex + 1, targetLiveData);
            }

            @Override
            public void onError(String error) {
                // En caso de error, establecer en 0 y continuar
                event.setCurrentParticipants(0);
                loadAttendeeCountsForEvents(eventsList, currentIndex + 1, targetLiveData);
            }
        });
    }

    /**
     * Crear un nuevo evento
     */
    public void createEvent(String userId, String title, String description, Date date, String location, 
                           int maxParticipants, String eventType, boolean isPrivate, boolean requiresParentalAuth) {
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
        event.setRequiresParentalAuth(requiresParentalAuth);

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
                           String location, int maxParticipants, String eventType, boolean isPrivate, boolean requiresParentalAuth) {
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
        event.setRequiresParentalAuth(requiresParentalAuth);

        eventRepository.updateEvent(eventId, event, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Genero el mensaje del toast antes de notificar la actualización
                String estado = event.getPrivateEvent() ? "privado" : "público";
                eventUpdateMessage.postValue("Evento actualizado a '" + estado + "'");
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                
                if (sharedViewModel != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
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
     * Cambiar el estado de privacidad de un evento
     */
    public void toggleEventPrivacy(String eventId, boolean isPrivate) {
        if (eventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        eventRepository.updateEventPrivacy(eventId, isPrivate, new EventRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Genero el mensaje del toast antes de notificar la actualización
                String estado = isPrivate ? "privado" : "público";
                eventUpdateMessage.postValue("Evento actualizado a '" + estado + "'");
                eventUpdated.postValue(true);
                isLoading.postValue(false);
                // Actualizo el evento en la lista local sin recargar todo desde Firestore
                updateEventPrivacyInList(eventId, isPrivate);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Actualiza el estado de privacidad de un evento en la lista local sin recargar desde Firestore
     */
    private void updateEventPrivacyInList(String eventId, boolean isPrivate) {
        List<Event> currentEvents = events.getValue();
        if (currentEvents != null) {
            for (Event event : currentEvents) {
                if (event.getId() != null && event.getId().equals(eventId)) {
                    event.setPrivateEvent(isPrivate);
                    break;
                }
            }
            events.postValue(currentEvents);
        }
        
        // También actualizo en allEvents si existe
        List<Event> currentAllEvents = allEvents.getValue();
        if (currentAllEvents != null) {
            for (Event event : currentAllEvents) {
                if (event.getId() != null && event.getId().equals(eventId)) {
                    event.setPrivateEvent(isPrivate);
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
     * Cargar asistentes para un evento específico (método de prueba)
     */
    public void loadAttendeeCountForEvent(String eventId) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        attendeesToEventRepository.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> attendees) {
                int count = attendees != null ? attendees.size() : 0;
                android.util.Log.d("EventViewModel", "Asistentes cargados para evento " + eventId + ": " + count);
                // Aquí podrías actualizar un LiveData específico si fuera necesario
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("EventViewModel", "Error al cargar asistentes: " + error);
                errorMessage.postValue(error);
            }
        });
    }

    /**
     * Limpiar estados de operaciones
     */
    public void clearOperationStates() {
        eventCreated.postValue(false);
        eventUpdated.postValue(false);
        eventUpdateMessage.postValue(null);
        eventDeleted.postValue(false);
        errorMessage.postValue(null);
    }
}