package com.us.eventum.data.repositories.hybrid;

import android.content.Context;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.network.NetworkStateManager;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventRepository;
import com.us.eventum.data.repositories.room.RoomEventRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio híbrido para eventos
 * Combina Firebase (online) y Room (offline) con sincronización automática
 */
public class HybridEventRepository implements EventRepository {
    
    private final FirebaseEventRepository firebaseRepository;
    private final RoomEventRepository roomRepository;
    private final NetworkStateManager networkManager;
    private final ExecutorService executor;
    private volatile boolean isSyncing = false; // Flag para evitar sincronizaciones concurrentes
    
    public HybridEventRepository(Context context) {
        this.firebaseRepository = new FirebaseEventRepository();
        this.roomRepository = new RoomEventRepository(context);
        this.networkManager = NetworkStateManager.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        
        // Escuchar cambios de red para sincronización automática
        networkManager.addNetworkStateListener(new NetworkStateManager.NetworkStateListener() {
            @Override
            public void onNetworkAvailable() {
                System.out.println("HybridEventRepository: Red disponible, iniciando sincronización automática...");
                syncPendingEvents(null);
            }
            
            @Override
            public void onNetworkLost() {
                // No hacer nada cuando se pierde la conexión
            }
        });
    }
    
    @Override
    public void loadUserEvents(String userId, RepositoryCallback<List<Event>> callback) {
        boolean isOnline = networkManager.isOnline();
        System.out.println("HybridEventRepository: loadUserEvents - isOnline: " + isOnline);
        
        if (isOnline) {
            System.out.println("HybridEventRepository: Cargando eventos desde Firebase...");
            // Si hay internet: cargar desde Firebase y actualizar caché local
            firebaseRepository.loadUserEvents(userId, new RepositoryCallback<List<Event>>() {
                @Override
                public void onSuccess(List<Event> result) {
                    System.out.println("HybridEventRepository: Eventos cargados desde Firebase: " + result.size());
                    // Actualizar caché local con los datos de Firebase
                    updateLocalCache(result);
                    callback.onSuccess(result);
                }
                
                @Override
                public void onError(String error) {
                    System.err.println("HybridEventRepository: Error al cargar desde Firebase: " + error);
                    // Si falla Firebase, intentar cargar desde caché local
                    loadFromLocalCache(userId, callback);
                }
            });
        } else {
            System.out.println("HybridEventRepository: Sin conexión, cargando desde caché local...");
            // Si no hay internet: cargar desde caché local
            loadFromLocalCache(userId, callback);
        }
    }

    @Override
    public void loadAvailableEvents(RepositoryCallback<List<Event>> callback) {
        boolean isOnline = networkManager.isOnline();
        if (isOnline) {
            firebaseRepository.loadAvailableEvents(new RepositoryCallback<List<Event>>() {
                @Override
                public void onSuccess(List<Event> result) {
                    callback.onSuccess(result);
                }

                @Override
                public void onError(String error) {
                    // En caso de error, intentar devolver lista vacía desde caché local
                    roomRepository.loadAvailableEvents(callback);
                }
            });
        } else {
            roomRepository.loadAvailableEvents(callback);
        }
    }
    
    @Override
    public void createEvent(Event event, RepositoryCallback<Event> callback) {
        boolean isOnline = networkManager.isOnline();
        System.out.println("HybridEventRepository: createEvent - isOnline: " + isOnline);
        System.out.println("HybridEventRepository: Evento a crear - ID: " + event.getId() + ", Título: " + event.getTitle());
        
        if (isOnline) {
            System.out.println("HybridEventRepository: Creando evento en Firebase...");
            // Si hay internet: crear en Firebase y luego en caché local
            firebaseRepository.createEvent(event, new RepositoryCallback<Event>() {
                @Override
                public void onSuccess(Event result) {
                    System.out.println("HybridEventRepository: Evento creado en Firebase con ID: " + result.getId() + ", guardando en caché local...");
                    // Crear también en caché local
                    roomRepository.createEvent(result, new RepositoryCallback<Event>() {
                        @Override
                        public void onSuccess(Event localResult) {
                            System.out.println("HybridEventRepository: Evento guardado en caché local exitosamente con ID: " + localResult.getId());
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            System.err.println("HybridEventRepository: Error al guardar en caché local: " + error);
                            // Aunque falle el caché local, el evento ya está en Firebase
                            callback.onSuccess(result);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    System.err.println("HybridEventRepository: Error al crear evento en Firebase: " + error);
                    callback.onError(error);
                }
            });
        } else {
            System.out.println("HybridEventRepository: Sin conexión, creando evento solo en caché local...");
            // Si no hay internet: crear solo en caché local (se sincronizará después)
            roomRepository.createEvent(event, new RepositoryCallback<Event>() {
                @Override
                public void onSuccess(Event result) {
                    System.out.println("HybridEventRepository: Evento creado en caché local exitosamente (offline) con ID: " + result.getId());
                    callback.onSuccess(result);
                }
                
                @Override
                public void onError(String error) {
                    System.err.println("HybridEventRepository: Error al crear evento en caché local: " + error);
                    callback.onError(error);
                }
            });
        }
    }
    
    @Override
    public void updateEvent(String eventId, Event event, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: actualizar en Firebase y luego en caché local
            firebaseRepository.updateEvent(eventId, event, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Actualizar también en caché local
                    roomRepository.updateEvent(eventId, event, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, el evento ya está actualizado en Firebase
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
            // Si no hay internet: actualizar solo en caché local (se sincronizará después)
            roomRepository.updateEvent(eventId, event, callback);
        }
    }
    
    @Override
    public void deleteEvent(String eventId, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: eliminar en Firebase y luego en caché local
            firebaseRepository.deleteEvent(eventId, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Eliminar también en caché local
                    roomRepository.deleteEvent(eventId, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, el evento ya está eliminado en Firebase
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
            // Si no hay internet: eliminar solo en caché local (se sincronizará después)
            roomRepository.deleteEvent(eventId, callback);
        }
    }
    
    @Override
    public void syncPendingEvents(RepositoryCallback<Void> callback) {
        // Evitar sincronizaciones concurrentes
        if (isSyncing) {
            System.out.println("HybridEventRepository: Sincronización ya en progreso, saltando...");
            if (callback != null) {
                callback.onSuccess(null);
            }
            return;
        }
        
        executor.execute(() -> {
            if (networkManager.isOnline()) {
                isSyncing = true;
                System.out.println("HybridEventRepository: Iniciando sincronización de eventos pendientes...");
                // Sincronizar eventos pendientes desde caché local a Firebase
                roomRepository.syncPendingEvents(new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        System.out.println("HybridEventRepository: Sincronización de eventos completada exitosamente");
                        isSyncing = false;
                        if (callback != null) {
                            callback.onSuccess(result);
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        System.err.println("HybridEventRepository: Error en sincronización: " + error);
                        isSyncing = false;
                        if (callback != null) {
                            callback.onError(error);
                        }
                    }
                });
            } else {
                System.out.println("HybridEventRepository: Sin conexión a internet, no se puede sincronizar");
                if (callback != null) {
                    callback.onError("Sin conexión a internet");
                }
            }
        });
    }
    
    /**
     * Cargar eventos desde caché local
     */
    private void loadFromLocalCache(String userId, RepositoryCallback<List<Event>> callback) {
        System.out.println("HybridEventRepository: Cargando eventos desde caché local para userId: " + userId);
        roomRepository.loadUserEvents(userId, new RepositoryCallback<List<Event>>() {
            @Override
            public void onSuccess(List<Event> result) {
                System.out.println("HybridEventRepository: Eventos cargados desde caché local: " + result.size());
                callback.onSuccess(result);
            }
            
            @Override
            public void onError(String error) {
                System.err.println("HybridEventRepository: Error al cargar desde caché local: " + error);
                callback.onError(error);
            }
        });
    }
    
    /**
     * Actualizar caché local con datos de Firebase
     */
    private void updateLocalCache(List<Event> events) {
        executor.execute(() -> {
            if (events.isEmpty()) {
                System.out.println("HybridEventRepository: No hay eventos para actualizar en caché local");
                return;
            }
            
            // Obtener el userId del primer evento (todos deberían tener el mismo userId)
            String userId = events.get(0).getUserId();
            if (userId == null) {
                System.err.println("HybridEventRepository: No se puede actualizar caché local sin userId");
                return;
            }
            
            System.out.println("HybridEventRepository: Iniciando actualización de caché local para userId: " + userId);
            
            // Si ya hay una sincronización en progreso, esperar a que termine
            if (isSyncing) {
                System.out.println("HybridEventRepository: Sincronización en progreso, esperando...");
                // Esperar un poco y reintentar
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Reintentar después de un breve delay
                updateLocalCache(events);
                return;
            }
            
            // Primero sincronizar eventos pendientes antes de reemplazar la caché
            roomRepository.syncPendingEvents(new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void syncResult) {
                    System.out.println("HybridEventRepository: Sincronización de eventos pendientes completada");
                    // Ahora reemplazar la caché local con los eventos de Firebase
                    replaceLocalCacheWithFirebaseEvents(userId, events);
                }
                
                @Override
                public void onError(String syncError) {
                    System.err.println("HybridEventRepository: Error en sincronización de eventos pendientes: " + syncError);
                    // Continuar con la actualización de caché aunque falle la sincronización
                    replaceLocalCacheWithFirebaseEvents(userId, events);
                }
            });
        });
    }
    
    /**
     * Reemplazar caché local con eventos de Firebase
     */
    private void replaceLocalCacheWithFirebaseEvents(String userId, List<Event> events) {
        roomRepository.replaceAllUserEvents(userId, events, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                System.out.println("HybridEventRepository: Caché local reemplazada exitosamente con " + events.size() + " eventos de Firebase");
            }
            
            @Override
            public void onError(String error) {
                System.err.println("HybridEventRepository: Error al reemplazar caché local: " + error);
            }
        });
    }
}
