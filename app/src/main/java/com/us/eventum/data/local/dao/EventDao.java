package com.us.eventum.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.us.eventum.data.local.entities.EventEntity;
import java.util.List;

/**
 * DAO para operaciones de eventos en la base de datos local
 * Define todas las consultas SQL para la tabla de eventos
 */
@Dao
public interface EventDao {
    
    /**
     * Insertar un nuevo evento
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEvent(EventEntity event);
    
    /**
     * Insertar múltiples eventos
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEvents(List<EventEntity> events);
    
    /**
     * Actualizar un evento existente
     */
    @Update
    void updateEvent(EventEntity event);
    
    /**
     * Eliminar un evento
     */
    @Delete
    void deleteEvent(EventEntity event);
    
    /**
     * Obtener todos los eventos de un usuario
     */
    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY date ASC")
    List<EventEntity> getUserEvents(String userId);
    
    /**
     * Obtener un evento por ID
     */
    @Query("SELECT * FROM events WHERE id = :eventId")
    EventEntity getEventById(String eventId);
    
    /**
     * Obtener eventos no sincronizados (que no estén siendo sincronizados)
     */
    @Query("SELECT * FROM events WHERE isSynced = 0 AND isSyncing = 0")
    List<EventEntity> getUnsyncedEvents();
    
    /**
     * Obtener eventos eliminados pendientes de sincronización
     */
    @Query("SELECT * FROM events WHERE isDeleted = 1 AND isSyncing = 0")
    List<EventEntity> getDeletedEvents();
    
    /**
     * Marcar evento como sincronizado
     */
    @Query("UPDATE events SET isSynced = 1, lastUpdated = :timestamp WHERE id = :eventId")
    void markEventAsSynced(String eventId, long timestamp);
    
    /**
     * Marcar evento como en proceso de sincronización
     */
    @Query("UPDATE events SET isSyncing = 1, lastUpdated = :timestamp WHERE id = :eventId")
    void markEventAsSyncing(String eventId, long timestamp);
    
    /**
     * Desmarcar evento como en proceso de sincronización
     */
    @Query("UPDATE events SET isSyncing = 0, lastUpdated = :timestamp WHERE id = :eventId")
    void unmarkEventAsSyncing(String eventId, long timestamp);
    
    /**
     * Marcar evento como eliminado
     */
    @Query("UPDATE events SET isDeleted = 1, lastUpdated = :timestamp WHERE id = :eventId")
    void markEventAsDeleted(String eventId, long timestamp);
    
    /**
     * Eliminar eventos marcados como eliminados y sincronizados
     */
    @Query("DELETE FROM events WHERE isDeleted = 1 AND isSynced = 1")
    void deleteSyncedDeletedEvents();
    
    /**
     * Eliminar evento por ID
     */
    @Query("DELETE FROM events WHERE id = :eventId")
    void deleteEventById(String eventId);
    
    /**
     * Obtener eventos futuros de un usuario
     */
    @Query("SELECT * FROM events WHERE userId = :userId AND date > :currentTime ORDER BY date ASC")
    List<EventEntity> getFutureEvents(String userId, long currentTime);
    
    /**
     * Obtener eventos pasados de un usuario
     */
    @Query("SELECT * FROM events WHERE userId = :userId AND date <= :currentTime ORDER BY date DESC")
    List<EventEntity> getPastEvents(String userId, long currentTime);
    
    /**
     * Contar eventos no sincronizados
     */
    @Query("SELECT COUNT(*) FROM events WHERE isSynced = 0")
    int getUnsyncedEventsCount();
    
    /**
     * Eliminar todos los eventos de un usuario
     */
    @Query("DELETE FROM events WHERE userId = :userId")
    void deleteAllUserEvents(String userId);
    
    /**
     * Eliminar todos los eventos
     */
    @Query("DELETE FROM events")
    void deleteAllEvents();
}
