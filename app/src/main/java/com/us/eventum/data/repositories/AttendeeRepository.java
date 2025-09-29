package com.us.eventum.data.repositories;

import com.us.eventum.data.models.Attendee;
import java.util.List;

/**
 * Interfaz que define los contratos para operaciones de asistentes
 * Implementa el patrón Repository para abstraer la fuente de datos
 */
public interface AttendeeRepository {
    
    /**
     * Cargar asistentes de un evento
     * @param eventId ID del evento
     * @param callback Callback para manejar el resultado
     */
    void loadEventAttendees(String eventId, RepositoryCallback<List<Attendee>> callback);
    
    /**
     * Crear un nuevo asistente
     * @param attendee Asistente a crear
     * @param callback Callback para manejar el resultado
     */
    void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback);
    
    /**
     * Actualizar un asistente existente
     * @param attendeeId ID del asistente
     * @param attendee Datos actualizados del asistente
     * @param callback Callback para manejar el resultado
     */
    void updateAttendee(String attendeeId, Attendee attendee, RepositoryCallback<Void> callback);
    
    /**
     * Eliminar un asistente
     * @param attendeeId ID del asistente
     * @param callback Callback para manejar el resultado
     */
    void deleteAttendee(String attendeeId, RepositoryCallback<Void> callback);
    
    /**
     * Verificar un asistente
     * @param attendeeId ID del asistente
     * @param eventId ID del evento
     * @param callback Callback para manejar el resultado
     */
    void verifyAttendee(String attendeeId, String eventId, RepositoryCallback<Void> callback);
    
    /**
     * Limpiar todos los asistentes de un evento
     * @param eventId ID del evento
     * @param callback Callback para manejar el resultado
     */
    void clearEventAttendees(String eventId, RepositoryCallback<Void> callback);
    
    /**
     * Desapuntar un asistente por eventId y email
     */
    void unsubscribeByEventAndEmail(String eventId, String email, RepositoryCallback<Void> callback);
    
    /**
     * Sincronizar asistentes pendientes
     * @param callback Callback para manejar el resultado
     */
    void syncPendingAttendees(RepositoryCallback<Void> callback);
    
    /**
     * Callback genérico para operaciones del repositorio
     */
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
