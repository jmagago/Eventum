package com.us.eventum.data.repositories;

import com.us.eventum.data.models.Event;
import java.util.List;

/**
 * Interfaz que define los contratos para operaciones de eventos
 * Implementa el patrón Repository para abstraer la fuente de datos
 */
public interface EventRepository {

    /**
     * Callback para actualizaciones en tiempo real del catálogo de eventos.
     */
    interface AvailableEventsListener {
        void onEventsUpdated(List<Event> events);

        void onError(String error);
    }

    /**
     * Callback para un documento de evento concreto.
     */
    interface SingleEventListener {
        void onEventUpdated(Event event);

        void onEventRemoved();

        void onError(String error);
    }

    /**
     * Cargar eventos del usuario actual
     * @param userId ID del usuario
     * @param callback Callback para manejar el resultado
     */
    void loadUserEvents(String userId, RepositoryCallback<List<Event>> callback);
    
    /**
     * Cargar eventos disponibles para asistentes (públicos y con fecha futura)
     */
    void loadAvailableEvents(RepositoryCallback<List<Event>> callback);

    /**
     * Escucha cambios en el catálogo de eventos (p. ej. privacidad) sin recargar manualmente.
     */
    void startAvailableEventsListener(AvailableEventsListener listener);

    void stopAvailableEventsListener();

    /**
     * Escucha los eventos del organizador en tiempo real.
     */
    void startUserEventsListener(String userId, AvailableEventsListener listener);

    void stopUserEventsListener();

    /**
     * Escucha cambios en un evento concreto.
     */
    void startEventListener(String eventId, SingleEventListener listener);

    void stopEventListener();
    
    /**
     * Crear un nuevo evento
     * @param event Evento a crear
     * @param callback Callback para manejar el resultado
     */
    void createEvent(Event event, RepositoryCallback<Event> callback);
    
    /**
     * Actualizar un evento existente
     * @param eventId ID del evento
     * @param event Datos actualizados del evento
     * @param callback Callback para manejar el resultado
     */
    void updateEvent(String eventId, Event event, RepositoryCallback<Void> callback);

    /**
     * Actualiza solo {@code imageUpdatedAt} tras subir/reemplazar la foto en Storage.
     */
    void updateEventImageUpdatedAt(String eventId, long imageUpdatedAt, RepositoryCallback<Void> callback);
    
    /**
     * Actualizar solo el estado de privacidad de un evento
     * @param eventId ID del evento
     * @param isPrivate Nuevo estado de privacidad
     * @param callback Callback para manejar el resultado
     */
    void updateEventPrivacy(String eventId, boolean isPrivate, @androidx.annotation.Nullable String privateAccessCode,
                           RepositoryCallback<Void> callback);
    
    /**
     * Eliminar un evento
     * @param eventId ID del evento
     * @param callback Callback para manejar el resultado
     */
    void deleteEvent(String eventId, RepositoryCallback<Void> callback);

    /**
     * Marca el evento como cancelado sin borrar el documento ni las inscripciones.
     */
    void cancelEvent(String eventId, RepositoryCallback<Void> callback);

    /**
     * Obtiene un evento por su identificador.
     */
    void getEventById(String eventId, RepositoryCallback<Event> callback);
    
    /**
     * Callback genérico para operaciones del repositorio
     */
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
