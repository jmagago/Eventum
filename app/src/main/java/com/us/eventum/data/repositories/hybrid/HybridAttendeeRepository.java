package com.us.eventum.data.repositories.hybrid;

import android.content.Context;
import com.us.eventum.data.local.entities.AttendeeEntity;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.network.NetworkStateManager;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeRepository;
import com.us.eventum.data.repositories.room.RoomAttendeeRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio híbrido para asistentes
 * Combina Firebase (online) y Room (offline) con sincronización automática
 */
public class HybridAttendeeRepository implements AttendeeRepository {
    
    private final FirebaseAttendeeRepository firebaseRepository;
    public final RoomAttendeeRepository roomRepository; // Público para acceso directo desde EventViewModel
    private final NetworkStateManager networkManager;
    private final ExecutorService executor;
    
    public HybridAttendeeRepository(Context context) {
        this.firebaseRepository = new FirebaseAttendeeRepository();
        this.roomRepository = new RoomAttendeeRepository(context);
        this.networkManager = NetworkStateManager.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        
        // Sincronización automática cuando se recupera la conexión
        networkManager.addNetworkStateListener(new NetworkStateManager.NetworkStateListener() {
            @Override
            public void onNetworkAvailable() {
                syncPendingAttendees(null);
            }
            
            @Override
            public void onNetworkLost() {
                // No hacer nada cuando se pierde la conexión
            }
        });
        
    }
    
    @Override
    public void loadEventAttendees(String eventId, RepositoryCallback<List<Attendee>> callback) {
        System.out.println("HybridAttendeeRepository: loadEventAttendees para evento: " + eventId);
        
        // PRIMERO: Verificar cuántos asistentes hay en la caché local ANTES de cargar
        try {
            int localCount = roomRepository.attendeeDao.getEventAttendeesCount(eventId);
            System.out.println("HybridAttendeeRepository: Caché local tiene " + localCount + " asistentes para evento " + eventId);
        } catch (Exception e) {
            System.err.println("HybridAttendeeRepository: Error contando asistentes locales: " + e.getMessage());
        }
        
        if (networkManager.isOnline()) {
            System.out.println("HybridAttendeeRepository: Online - cargando desde Firebase");
            // Online: cargar desde Firebase y actualizar caché local
            firebaseRepository.loadEventAttendees(eventId, new RepositoryCallback<List<Attendee>>() {
                @Override
                public void onSuccess(List<Attendee> result) {
                    System.out.println("HybridAttendeeRepository: Firebase devolvió " + (result != null ? result.size() : 0) + " asistentes");
                    updateLocalCache(result);
                    callback.onSuccess(result);
                }
                
                @Override
                public void onError(String error) {
                    System.err.println("HybridAttendeeRepository: Error desde Firebase: " + error);
                    // Si falla Firebase, cargar desde caché local
                    roomRepository.loadEventAttendees(eventId, callback);
                }
            });
        } else {
            System.out.println("HybridAttendeeRepository: Offline - cargando desde caché local");
            // Offline: cargar desde caché local
            roomRepository.loadEventAttendees(eventId, callback);
        }
    }
    
    @Override
    public void createAttendee(Attendee attendee, RepositoryCallback<Attendee> callback) {
        if (networkManager.isOnline()) {
            // Online: crear en Firebase primero, luego en caché local
            firebaseRepository.createAttendee(attendee, new RepositoryCallback<Attendee>() {
                @Override
                public void onSuccess(Attendee result) {
                    // Crear en caché local (fallback silencioso)
                    roomRepository.createAttendee(result, new RepositoryCallback<Attendee>() {
                        @Override
                        public void onSuccess(Attendee localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, el asistente ya está en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Offline: crear solo en caché local (se sincronizará después)
            roomRepository.createAttendee(attendee, callback);
        }
    }
    
    @Override
    public void updateAttendee(String attendeeId, Attendee attendee, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Online: actualizar en Firebase primero, luego en caché local
            firebaseRepository.updateAttendee(attendeeId, attendee, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Actualizar en caché local (fallback silencioso)
                    roomRepository.updateAttendee(attendeeId, attendee, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, ya está actualizado en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Offline: actualizar solo en caché local (se sincronizará después)
            roomRepository.updateAttendee(attendeeId, attendee, callback);
        }
    }
    
    @Override
    public void deleteAttendee(String attendeeId, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Online: eliminar en Firebase primero, luego en caché local
            firebaseRepository.deleteAttendee(attendeeId, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Eliminar en caché local (fallback silencioso)
                    roomRepository.deleteAttendee(attendeeId, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, ya está eliminado en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Offline: eliminar solo en caché local (se sincronizará después)
            roomRepository.deleteAttendee(attendeeId, callback);
        }
    }
    
    @Override
    public void verifyAttendee(String attendeeId, String eventId, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Online: verificar en Firebase primero, luego en caché local
            firebaseRepository.verifyAttendee(attendeeId, eventId, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Verificar en caché local (fallback silencioso)
                    roomRepository.verifyAttendee(attendeeId, eventId, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, ya está verificado en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Offline: verificar solo en caché local (se sincronizará después)
            roomRepository.verifyAttendee(attendeeId, eventId, callback);
        }
    }
    
    @Override
    public void clearEventAttendees(String eventId, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Online: limpiar en Firebase primero, luego en caché local
            firebaseRepository.clearEventAttendees(eventId, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Limpiar en caché local (fallback silencioso)
                    roomRepository.clearEventAttendees(eventId, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, ya están eliminados en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Offline: limpiar solo en caché local (se sincronizará después)
            roomRepository.clearEventAttendees(eventId, callback);
        }
    }
    
    @Override
    public void syncPendingAttendees(RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            if (networkManager.isOnline()) {
                roomRepository.syncPendingAttendees(callback);
            } else {
                if (callback != null) {
                    callback.onError("Sin conexión a internet");
                }
            }
        });
    }

    @Override
    public void unsubscribeByEventAndEmail(String eventId, String email, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            firebaseRepository.unsubscribeByEventAndEmail(eventId, email, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Reflejar baja en caché local si existe
                    roomRepository.unsubscribeByEventAndEmail(eventId, email, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void local) {
                            if (callback != null) callback.onSuccess(result);
                        }

                        @Override
                        public void onError(String error) {
                            // Aunque falle local, la baja ya se hizo en Firebase
                            if (callback != null) callback.onSuccess(null);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    if (callback != null) callback.onError(error);
                }
            });
        } else {
            roomRepository.unsubscribeByEventAndEmail(eventId, email, callback);
        }
    }
    
    
    
    
    
    /**
     * Actualizar caché local con datos de Firebase
     */
    private void updateLocalCache(List<Attendee> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return;
        }
        
        // Agrupar asistentes por eventId
        java.util.Map<String, List<Attendee>> attendeesByEvent = new java.util.HashMap<>();
        for (Attendee attendee : attendees) {
            String eventId = attendee.getEventId();
            if (eventId != null) {
                attendeesByEvent.computeIfAbsent(eventId, k -> new java.util.ArrayList<>()).add(attendee);
            }
        }
        
        // Actualizar cada evento en background
        for (java.util.Map.Entry<String, List<Attendee>> entry : attendeesByEvent.entrySet()) {
            String eventId = entry.getKey();
            List<Attendee> eventAttendees = entry.getValue();
            
            executor.execute(() -> {
                try {
                    System.out.println("HybridAttendeeRepository: updateLocalCache - Reemplazando asistentes para evento " + eventId);
                    // Reemplazar asistentes del evento (eliminar TODOS)
                    roomRepository.attendeeDao.deleteEventAttendees(eventId);
                    
                    // Insertar nuevos asistentes marcados como sincronizados
                    System.out.println("HybridAttendeeRepository: updateLocalCache - Insertando " + eventAttendees.size() + " asistentes para evento " + eventId);
                    for (Attendee attendee : eventAttendees) {
                        AttendeeEntity entity = roomRepository.convertModelToEntity(attendee);
                        entity.setSynced(true);
                        roomRepository.attendeeDao.insertAttendee(entity);
                    }
                    
                    // Verificar cuántos asistentes quedaron después de la actualización
                    int finalCount = roomRepository.attendeeDao.getEventAttendeesCount(eventId);
                    System.out.println("HybridAttendeeRepository: updateLocalCache - Evento " + eventId + " tiene " + finalCount + " asistentes después de actualización");
                } catch (Exception e) {
                    System.err.println("Error actualizando caché local para evento " + eventId + ": " + e.getMessage());
            }
        });
    }
}
}