package com.us.eventum.data.repositories.room;

import android.content.Context;
import com.us.eventum.data.local.EventumDatabase;
import com.us.eventum.data.local.dao.AttendeeDao;
import com.us.eventum.data.local.entities.AttendeeEntity;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.AttendeeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementación de AttendeeRepository usando Room Database
 * Maneja todas las operaciones de asistentes en la base de datos local
 */
public class RoomAttendeeRepository implements AttendeeRepository {
    
    public final AttendeeDao attendeeDao;
    private final ExecutorService executor;
    
    public RoomAttendeeRepository(Context context) {
        EventumDatabase database = EventumDatabase.getDatabase(context);
        this.attendeeDao = database.attendeeDao();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public void loadEventAttendees(String eventId, RepositoryCallback<List<Attendee>> callback) {
        executor.execute(() -> {
            try {
                List<AttendeeEntity> entities = attendeeDao.getEventAttendees(eventId);
                List<Attendee> attendees = new ArrayList<>();
                
                for (AttendeeEntity entity : entities) {
                    Attendee attendee = convertEntityToModel(entity);
                    attendees.add(attendee);
                }
                
                callback.onSuccess(attendees);
            } catch (Exception e) {
                callback.onError("Error al cargar asistentes: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback) {
        executor.execute(() -> {
            try {
                AttendeeEntity entity = convertModelToEntity(attendee);
                attendeeDao.insertAttendee(entity);
                callback.onSuccess(attendee);
            } catch (Exception e) {
                callback.onError("Error al crear asistente: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void updateAttendee(String attendeeId, Attendee attendee, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                AttendeeEntity entity = convertModelToEntity(attendee);
                entity.setId(attendeeId);
                entity.setLastUpdated(System.currentTimeMillis());
                attendeeDao.updateAttendee(entity);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al actualizar asistente: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void deleteAttendee(String attendeeId, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Verificar si el asistente ya está sincronizado con Firebase
                AttendeeEntity entity = attendeeDao.getAttendeeById(attendeeId);
                if (entity != null && entity.isSynced()) {
                    // Si está sincronizado, marcar como eliminado para sincronización posterior
                    attendeeDao.markAttendeeAsDeleted(attendeeId, System.currentTimeMillis());
                } else {
                    // Si no está sincronizado, eliminar directamente
                    attendeeDao.deleteAttendeeById(attendeeId);
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al eliminar asistente: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void verifyAttendee(String attendeeId, String eventId, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                AttendeeEntity entity = attendeeDao.getAttendeeById(attendeeId);
                if (entity != null) {
                    if (entity.isVerified()) {
                        callback.onError("Este asistente ya fue verificado");
                        return;
                    }
                    
                    if (!eventId.equals(entity.getEventId())) {
                        callback.onError("Este asistente no pertenece a este evento");
                        return;
                    }
                    
                    attendeeDao.markAttendeeAsVerified(attendeeId, System.currentTimeMillis());
                    callback.onSuccess(null);
                } else {
                    callback.onError("Asistente no encontrado");
                }
            } catch (Exception e) {
                callback.onError("Error al verificar asistente: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void clearEventAttendees(String eventId, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                attendeeDao.deleteEventAttendees(eventId);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al vaciar la lista: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void syncPendingAttendees(RepositoryCallback<Void> callback) {
        // Sincronización simplificada - solo limpiar asistentes eliminados ya sincronizados
        executor.execute(() -> {
            try {
                attendeeDao.deleteSyncedDeletedAttendees();
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al sincronizar asistentes: " + e.getMessage());
            }
        });
    }

    @Override
    public void unsubscribeByEventAndEmail(String eventId, String email, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                AttendeeEntity entity = attendeeDao.getAttendeeByEventAndEmail(eventId, email);
                if (entity == null) {
                    callback.onError("No existe inscripción local para ese email");
                    return;
                }
                if (entity.isSynced()) {
                    attendeeDao.markAttendeeAsDeleted(entity.getId(), System.currentTimeMillis());
                } else {
                    attendeeDao.deleteAttendeeById(entity.getId());
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al desapuntarse offline: " + e.getMessage());
            }
        });
    }
    
    
    
    /**
     * Convertir AttendeeEntity a Attendee
     */
    public Attendee convertEntityToModel(AttendeeEntity entity) {
        Attendee attendee = new Attendee(
            entity.getName(),
            entity.getLastName(),
            entity.getDni(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getBirthDate() // birthDate es String en la entidad
        );
        attendee.setId(entity.getId());
        attendee.setEventId(entity.getEventId());
        attendee.setRequiresParentalAuthorization(entity.isRequiresParentalAuthorization());
        attendee.setVerified(entity.isVerified());
        attendee.setConfirmationTime(entity.getVerificationTimestamp());
        return attendee;
    }
    
    /**
     * Convertir Attendee a AttendeeEntity
     */
    public AttendeeEntity convertModelToEntity(Attendee attendee) {
        AttendeeEntity entity = new AttendeeEntity(
            attendee.getId(),
            attendee.getName(),
            attendee.getLastName(),
            attendee.getDni(),
            attendee.getEmail(),
            attendee.getPhone(),
            attendee.getBirthDate() != null ? attendee.getBirthDate().toDate().toString() : "",
            attendee.getEventId(),
            attendee.isRequiresParentalAuthorization()
        );
        entity.setVerified(attendee.isVerified());
        entity.setVerificationTimestamp(attendee.getConfirmationTime());
        entity.setSynced(false);
        return entity;
    }
}
