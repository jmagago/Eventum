package com.us.eventum.data.repositories.room;

import android.content.Context;
import com.us.eventum.data.local.EventumDatabase;
import com.us.eventum.data.local.dao.EventDao;
import com.us.eventum.data.local.entities.EventEntity;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementación de EventRepository usando Room Database
 * Maneja todas las operaciones de eventos en la base de datos local
 */
public class RoomEventRepository implements EventRepository {
    
    private final EventDao eventDao;
    private final ExecutorService executor;
    
    public RoomEventRepository(Context context) {
        EventumDatabase database = EventumDatabase.getDatabase(context);
        this.eventDao = database.eventDao();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public void loadUserEvents(String userId, RepositoryCallback<List<Event>> callback) {
        executor.execute(() -> {
            try {
                List<EventEntity> entities = eventDao.getUserEvents(userId);
                List<Event> events = new ArrayList<>();
                
                for (EventEntity entity : entities) {
                    // Excluir eventos marcados como eliminados
                    if (!entity.isDeleted()) {
                        Event event = convertEntityToModel(entity);
                        events.add(event);
                    }
                }
                
                System.out.println("RoomEventRepository: Cargados " + events.size() + " eventos para userId: " + userId);
                callback.onSuccess(events);
            } catch (Exception e) {
                System.err.println("RoomEventRepository: Error al cargar eventos: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error al cargar eventos: " + e.getMessage());
            }
        });
    }

    @Override
    public void loadAvailableEvents(RepositoryCallback<List<Event>> callback) {
        executor.execute(() -> {
            try {
                // Para offline, listamos todos los eventos públicos del usuario actual cacheados no es trivial.
                // Como caché local no replica todos los eventos públicos de todos los usuarios, devolvemos lista vacía
                // para que la capa híbrida recurra a Firebase cuando haya red.
                callback.onSuccess(new ArrayList<>());
            } catch (Exception e) {
                callback.onError("Error al cargar eventos disponibles offline: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void createEvent(Event event, RepositoryCallback<Event> callback) {
        executor.execute(() -> {
            try {
                // Generar ID único para eventos offline si no existe
                if (event.getId() == null || event.getId().isEmpty()) {
                    String offlineId = generateUniqueOfflineId(event.getUserId());
                    event.setId(offlineId);
                    System.out.println("RoomEventRepository: Generado ID offline único: " + offlineId);
                }
                
                EventEntity entity = convertModelToEntity(event);
                entity.setSynced(false); // Marcar explícitamente como no sincronizado
                entity.setCreatedAt(System.currentTimeMillis());
                entity.setLastUpdated(System.currentTimeMillis());
                
                eventDao.insertEvent(entity);
                System.out.println("RoomEventRepository: Evento offline creado exitosamente con ID: " + entity.getId());
                
                // Verificar que el evento se guardó correctamente
                EventEntity savedEvent = eventDao.getEventById(entity.getId());
                if (savedEvent != null) {
                    System.out.println("RoomEventRepository: Verificación exitosa - evento guardado en BD local");
                    callback.onSuccess(event);
                } else {
                    System.err.println("RoomEventRepository: Error - evento no encontrado después de guardar");
                    callback.onError("Error al verificar evento guardado");
                }
            } catch (Exception e) {
                System.err.println("RoomEventRepository: Error al crear evento offline: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error al crear evento: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generar ID único para eventos offline
     */
    private String generateUniqueOfflineId(String userId) {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 10000);
        return "offline_" + timestamp + "_" + random + "_" + userId.hashCode();
    }
    
    @Override
    public void updateEvent(String eventId, Event event, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                EventEntity entity = convertModelToEntity(event);
                entity.setId(eventId);
                entity.setLastUpdated(System.currentTimeMillis());
                eventDao.updateEvent(entity);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al actualizar evento: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void deleteEvent(String eventId, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Verificar si el evento existe
                EventEntity existingEvent = eventDao.getEventById(eventId);
                if (existingEvent == null) {
                    callback.onError("Evento no encontrado");
                    return;
                }
                
                // Si el evento ya está sincronizado con Firebase, marcarlo como eliminado
                // para sincronizar la eliminación después
                if (existingEvent.isSynced()) {
                    eventDao.markEventAsDeleted(eventId, System.currentTimeMillis());
                    System.out.println("RoomEventRepository: Evento marcado como eliminado para sincronización: " + eventId);
                } else {
                    // Si no está sincronizado, eliminarlo directamente de la BD local
                    eventDao.deleteEventById(eventId);
                    System.out.println("RoomEventRepository: Evento offline eliminado directamente: " + eventId);
                }
                
                callback.onSuccess(null);
            } catch (Exception e) {
                System.err.println("RoomEventRepository: Error al eliminar evento: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error al eliminar evento: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void syncPendingEvents(RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Obtener eventos creados pendientes de sincronización
                List<EventEntity> unsyncedEvents = eventDao.getUnsyncedEvents();
                System.out.println("RoomEventRepository: Encontrados " + unsyncedEvents.size() + " eventos creados pendientes de sincronización");
                
                // Obtener eventos eliminados pendientes de sincronización
                List<EventEntity> deletedEvents = eventDao.getDeletedEvents();
                System.out.println("RoomEventRepository: Encontrados " + deletedEvents.size() + " eventos eliminados pendientes de sincronización");
                
                if (unsyncedEvents.isEmpty() && deletedEvents.isEmpty()) {
                    System.out.println("RoomEventRepository: No hay eventos pendientes de sincronización");
                    callback.onSuccess(null);
                    return;
                }
                
                // Filtrar solo eventos offline creados (que empiezan con "offline_")
                List<EventEntity> offlineEvents = new ArrayList<>();
                for (EventEntity event : unsyncedEvents) {
                    if (event.getId().startsWith("offline_")) {
                        offlineEvents.add(event);
                    }
                }
                
                System.out.println("RoomEventRepository: Eventos offline creados pendientes de sincronización: " + offlineEvents.size());
                System.out.println("RoomEventRepository: Eventos eliminados pendientes de sincronización: " + deletedEvents.size());
                
                // Sincronizar primero eventos eliminados, luego eventos creados
                syncDeletedEventsSequentially(deletedEvents, 0, new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // Después de sincronizar eliminaciones, sincronizar creaciones
                        if (offlineEvents.isEmpty()) {
                            System.out.println("RoomEventRepository: Sincronización completada - no hay eventos offline para crear");
                            callback.onSuccess(null);
                        } else {
                            syncEventsSequentially(offlineEvents, 0, callback);
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        System.err.println("RoomEventRepository: Error al sincronizar eliminaciones: " + error);
                        // Continuar con la sincronización de creaciones aunque falle la eliminación
                        if (offlineEvents.isEmpty()) {
                            callback.onSuccess(null);
                        } else {
                            syncEventsSequentially(offlineEvents, 0, callback);
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("RoomEventRepository: Error al sincronizar eventos: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error al sincronizar eventos: " + e.getMessage());
            }
        });
    }
    
    /**
     * Sincronizar eventos eliminados secuencialmente con Firebase
     */
    private void syncDeletedEventsSequentially(List<EventEntity> deletedEvents, int index, RepositoryCallback<Void> callback) {
        if (index >= deletedEvents.size()) {
            System.out.println("RoomEventRepository: Sincronización de eliminaciones completada");
            callback.onSuccess(null);
            return;
        }
        
        EventEntity deletedEvent = deletedEvents.get(index);
        System.out.println("RoomEventRepository: Sincronizando eliminación de evento: " + deletedEvent.getId());
        
        // Verificar que el evento aún existe en la base de datos local
        EventEntity existingEntity = eventDao.getEventById(deletedEvent.getId());
        if (existingEntity == null) {
            System.out.println("RoomEventRepository: Evento eliminado ya no existe localmente, saltando: " + deletedEvent.getId());
            syncDeletedEventsSequentially(deletedEvents, index + 1, callback);
            return;
        }
        
        // Marcar evento como en proceso de sincronización
        eventDao.markEventAsSyncing(deletedEvent.getId(), System.currentTimeMillis());
        System.out.println("RoomEventRepository: Evento eliminado marcado como sincronizando: " + deletedEvent.getId());
        
        // Eliminar evento de Firebase
        FirebaseEventRepository firebaseRepo = new FirebaseEventRepository();
        firebaseRepo.deleteEvent(deletedEvent.getId(), new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                System.out.println("RoomEventRepository: Evento eliminado de Firebase exitosamente: " + deletedEvent.getId());
                
                // Marcar como sincronizado y eliminar de la BD local en hilo de fondo
                executor.execute(() -> {
                    eventDao.markEventAsSynced(deletedEvent.getId(), System.currentTimeMillis());
                    eventDao.deleteEventById(deletedEvent.getId());
                    System.out.println("RoomEventRepository: Evento eliminado completamente de la BD local");
                    
                    // Continuar con el siguiente evento eliminado
                    syncDeletedEventsSequentially(deletedEvents, index + 1, callback);
                });
            }
            
            @Override
            public void onError(String error) {
                System.err.println("RoomEventRepository: Error al eliminar evento de Firebase: " + error);
                // Desmarcar como sincronizando en caso de error (en hilo de fondo)
                executor.execute(() -> {
                    eventDao.unmarkEventAsSyncing(deletedEvent.getId(), System.currentTimeMillis());
                });
                // Continuar con el siguiente evento aunque falle la eliminación
                syncDeletedEventsSequentially(deletedEvents, index + 1, callback);
            }
        });
    }
    
    /**
     * Sincronizar eventos secuencialmente con Firebase
     */
    private void syncEventsSequentially(List<EventEntity> events, int index, RepositoryCallback<Void> callback) {
        if (index >= events.size()) {
            System.out.println("RoomEventRepository: Sincronización completada para todos los eventos offline");
            callback.onSuccess(null);
            return;
        }
        
        EventEntity entity = events.get(index);
        System.out.println("RoomEventRepository: Sincronizando evento offline: " + entity.getId());
        
        // Verificar que el evento aún existe en la base de datos local
        EventEntity existingEntity = eventDao.getEventById(entity.getId());
        if (existingEntity == null) {
            System.out.println("RoomEventRepository: Evento ya no existe localmente, saltando: " + entity.getId());
            syncEventsSequentially(events, index + 1, callback);
            return;
        }
        
        // Marcar evento como en proceso de sincronización para evitar duplicaciones
        eventDao.markEventAsSyncing(entity.getId(), System.currentTimeMillis());
        System.out.println("RoomEventRepository: Evento marcado como sincronizando: " + entity.getId());
        
        // Convertir entidad a modelo
        Event event = convertEntityToModel(entity);
        
        // Crear evento en Firebase
        FirebaseEventRepository firebaseRepo = new FirebaseEventRepository();
        firebaseRepo.createEvent(event, new RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event firebaseEvent) {
                System.out.println("RoomEventRepository: Evento sincronizado con Firebase, nuevo ID: " + firebaseEvent.getId());
                
                // Actualizar el evento local con el nuevo ID de Firebase
                updateEventWithFirebaseId(entity.getId(), firebaseEvent, new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        System.out.println("RoomEventRepository: Evento local actualizado exitosamente");
                        // Continuar con el siguiente evento
                        syncEventsSequentially(events, index + 1, callback);
                    }
                    
                    @Override
                    public void onError(String error) {
                        System.err.println("RoomEventRepository: Error al actualizar evento local: " + error);
                        // Desmarcar como sincronizando en caso de error (en hilo de fondo)
                        executor.execute(() -> {
                            eventDao.unmarkEventAsSyncing(entity.getId(), System.currentTimeMillis());
                        });
                        // Continuar con el siguiente evento aunque falle la actualización
                        syncEventsSequentially(events, index + 1, callback);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                System.err.println("RoomEventRepository: Error al sincronizar evento con Firebase: " + error);
                // Desmarcar como sincronizando en caso de error (en hilo de fondo)
                executor.execute(() -> {
                    eventDao.unmarkEventAsSyncing(entity.getId(), System.currentTimeMillis());
                });
                // Continuar con el siguiente evento aunque falle la sincronización
                syncEventsSequentially(events, index + 1, callback);
            }
        });
    }
    
    /**
     * Actualizar evento local con el ID de Firebase
     */
    private void updateEventWithFirebaseId(String oldId, Event firebaseEvent, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Obtener la entidad existente
                EventEntity existingEntity = eventDao.getEventById(oldId);
                if (existingEntity == null) {
                    callback.onError("Evento no encontrado localmente");
                    return;
                }
                
                // Crear nueva entidad con el ID de Firebase
                EventEntity newEntity = new EventEntity(
                    firebaseEvent.getId(), // Nuevo ID de Firebase
                    firebaseEvent.getTitle(),
                    firebaseEvent.getDescription(),
                    firebaseEvent.getDate(),
                    firebaseEvent.getLocation(),
                    firebaseEvent.getUserId(),
                    firebaseEvent.getMaxParticipants(),
                    firebaseEvent.getEventType(),
                    firebaseEvent.getPrivateEvent()
                );
                newEntity.setSynced(true);
                newEntity.setSyncing(false); // Ya no está sincronizando
                newEntity.setLastUpdated(System.currentTimeMillis());
                newEntity.setCreatedAt(existingEntity.getCreatedAt()); // Mantener fecha de creación original
                
                // Eliminar la entidad antigua e insertar la nueva
                eventDao.deleteEventById(oldId);
                eventDao.insertEvent(newEntity);
                
                System.out.println("RoomEventRepository: Evento actualizado de ID " + oldId + " a " + firebaseEvent.getId());
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al actualizar evento local: " + e.getMessage());
            }
        });
    }
    
    /**
     * Convertir EventEntity a Event
     */
    private Event convertEntityToModel(EventEntity entity) {
        Event event = new Event(
            entity.getTitle(),
            entity.getDescription(),
            entity.getDate(),
            entity.getLocation(),
            entity.getUserId(),
            entity.getMaxParticipants(),
            entity.getEventType()
        );
        event.setId(entity.getId());
        event.setPrivateEvent(entity.isPrivate());
        return event;
    }
    
    /**
     * Reemplazar completamente la caché local con los eventos de Firebase
     */
    public void replaceAllUserEvents(String userId, List<Event> events, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                System.out.println("RoomEventRepository: Iniciando reemplazo de caché local para userId: " + userId);
                
                // Obtener eventos existentes antes de eliminar
                List<EventEntity> existingEvents = eventDao.getUserEvents(userId);
                System.out.println("RoomEventRepository: Eventos existentes en caché local: " + existingEvents.size());
                
                // Crear un mapa de eventos de Firebase por ID para verificación rápida
                java.util.Map<String, Event> firebaseEventsMap = new java.util.HashMap<>();
                for (Event event : events) {
                    firebaseEventsMap.put(event.getId(), event);
                }
                
                // Eliminar solo eventos que NO están en la lista de Firebase (eventos obsoletos)
                // pero NO eliminar eventos que ya están marcados como eliminados localmente
                for (EventEntity existingEvent : existingEvents) {
                    if (!firebaseEventsMap.containsKey(existingEvent.getId()) && !existingEvent.isDeleted()) {
                        System.out.println("RoomEventRepository: Eliminando evento obsoleto: " + existingEvent.getId());
                        eventDao.deleteEventById(existingEvent.getId());
                    }
                }
                
                // Insertar o actualizar eventos de Firebase
                for (Event event : events) {
                    EventEntity entity = convertModelToEntity(event);
                    entity.setSynced(true); // Marcar como sincronizado
                    entity.setLastUpdated(System.currentTimeMillis());
                    
                    // Verificar si el evento ya existe
                    EventEntity existingEntity = eventDao.getEventById(event.getId());
                    if (existingEntity != null) {
                        // Actualizar evento existente
                        entity.setCreatedAt(existingEntity.getCreatedAt()); // Mantener fecha de creación original
                        eventDao.updateEvent(entity);
                        System.out.println("RoomEventRepository: Evento actualizado: " + event.getId());
                    } else {
                        // Insertar nuevo evento
                        eventDao.insertEvent(entity);
                        System.out.println("RoomEventRepository: Evento insertado: " + event.getId());
                    }
                }
                
                System.out.println("RoomEventRepository: Caché local actualizada exitosamente con " + events.size() + " eventos de Firebase");
                
                // Verificar que los eventos se insertaron correctamente
                List<EventEntity> insertedEvents = eventDao.getUserEvents(userId);
                System.out.println("RoomEventRepository: Verificación - eventos en caché local después del reemplazo: " + insertedEvents.size());
                
                callback.onSuccess(null);
            } catch (Exception e) {
                System.err.println("RoomEventRepository: Error al reemplazar caché local: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error al reemplazar caché local: " + e.getMessage());
            }
        });
    }

    /**
     * Convertir Event a EventEntity
     */
    private EventEntity convertModelToEntity(Event event) {
        EventEntity entity = new EventEntity(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getDate(),
            event.getLocation(),
            event.getUserId(),
            event.getMaxParticipants(),
            event.getEventType(),
            event.getPrivateEvent()
        );
        entity.setDeleted(false); // Inicializar como no eliminado
        return entity;
    }
}
