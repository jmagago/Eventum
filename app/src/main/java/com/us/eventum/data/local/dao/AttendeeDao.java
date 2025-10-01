package com.us.eventum.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.us.eventum.data.local.entities.AttendeeEntity;
import java.util.List;

/**
 * DAO para operaciones de asistentes en la base de datos local
 * Define todas las consultas SQL para la tabla de asistentes
 */
@Dao
public interface AttendeeDao {
    
    /**
     * Insertar un nuevo asistente
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAttendee(AttendeeEntity attendee);
    
    /**
     * Insertar múltiples asistentes
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAttendees(List<AttendeeEntity> attendees);
    
    /**
     * Actualizar un asistente existente
     */
    @Update
    void updateAttendee(AttendeeEntity attendee);
    
    /**
     * Eliminar un asistente
     */
    @Delete
    void deleteAttendee(AttendeeEntity attendee);
    
    /**
     * Obtener todos los asistentes de un evento (excluyendo eliminados)
     */
    @Query("SELECT * FROM attendees WHERE eventId = :eventId AND isDeleted = 0 ORDER BY name ASC")
    List<AttendeeEntity> getEventAttendees(String eventId);
    
    /**
     * Obtener un asistente por ID
     */
    @Query("SELECT * FROM attendees WHERE id = :attendeeId")
    AttendeeEntity getAttendeeById(String attendeeId);
    
    
    /**
     * Eliminar asistente por ID (soft delete)
     */
    @Query("UPDATE attendees SET isDeleted = 1, lastUpdated = :timestamp WHERE id = :attendeeId")
    void markAttendeeAsDeleted(String attendeeId, long timestamp);
    
    /**
     * Eliminar asistente por ID (hard delete)
     */
    @Query("DELETE FROM attendees WHERE id = :attendeeId")
    void deleteAttendeeById(String attendeeId);
    
    /**
     * Eliminar todos los asistentes de un evento
     */
    @Query("DELETE FROM attendees WHERE eventId = :eventId")
    void deleteEventAttendees(String eventId);

    /**
     * Obtener asistente por evento y email
     */
    @Query("SELECT * FROM attendees WHERE eventId = :eventId AND email = :email LIMIT 1")
    AttendeeEntity getAttendeeByEventAndEmail(String eventId, String email);

    /**
     * Obtener asistentes por usuario
     */
    @Query("SELECT * FROM attendees WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    List<AttendeeEntity> getUserAttendees(String userId);
    
    
    /**
     * Verificar si existe un DNI para un evento
     */
    @Query("SELECT COUNT(*) FROM attendees WHERE dni = :dni AND eventId = :eventId")
    int getDniCountForEvent(String dni, String eventId);
    
    /**
     * Marcar asistente como escaneado (asistió)
     */
    @Query("UPDATE attendees SET verified = 1, verificationTimestamp = :timestamp WHERE id = :attendeeId")
    void markAttendeeAsScanned(String attendeeId, long timestamp);
    
    /**
     * Obtener asistentes escaneados de un evento
     */
    @Query("SELECT * FROM attendees WHERE eventId = :eventId AND verified = 1 ORDER BY verificationTimestamp DESC")
    List<AttendeeEntity> getScannedAttendees(String eventId);
    
    /**
     * Obtener asistentes no escaneados de un evento
     */
    @Query("SELECT * FROM attendees WHERE eventId = :eventId AND verified = 0 ORDER BY name ASC")
    List<AttendeeEntity> getUnscannedAttendees(String eventId);
    
    /**
     * Contar asistentes de un evento
     */
    @Query("SELECT COUNT(*) FROM attendees WHERE eventId = :eventId")
    int getEventAttendeesCount(String eventId);
    
    /**
     * Contar asistentes escaneados de un evento
     */
    @Query("SELECT COUNT(*) FROM attendees WHERE eventId = :eventId AND verified = 1")
    int getScannedAttendeesCount(String eventId);
    
    
    /**
     * Eliminar asistentes sincronizados que están marcados como eliminados
     */
    @Query("DELETE FROM attendees WHERE isDeleted = 1 AND isSynced = 1")
    void deleteSyncedDeletedAttendees();
    
    
    /**
     * Eliminar todos los asistentes
     */
    @Query("DELETE FROM attendees")
    void deleteAllAttendees();
}
