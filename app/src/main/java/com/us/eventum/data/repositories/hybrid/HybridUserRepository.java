package com.us.eventum.data.repositories.hybrid;

import android.content.Context;
import com.us.eventum.data.models.User;
import com.us.eventum.data.network.NetworkStateManager;
import com.us.eventum.data.repositories.UserRepository;
import com.us.eventum.data.repositories.firebase.FirebaseUserRepository;
import com.us.eventum.data.repositories.room.RoomUserRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio híbrido para usuarios
 * Combina Firebase (online) y Room (offline) con sincronización automática
 */
public class HybridUserRepository implements UserRepository {
    
    private final FirebaseUserRepository firebaseRepository;
    private final RoomUserRepository roomRepository;
    private final NetworkStateManager networkManager;
    private final ExecutorService executor;
    
    public HybridUserRepository(Context context) {
        this.firebaseRepository = new FirebaseUserRepository();
        this.roomRepository = new RoomUserRepository(context);
        this.networkManager = NetworkStateManager.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        
        // Escuchar cambios de red para sincronización automática
        networkManager.addNetworkStateListener(new NetworkStateManager.NetworkStateListener() {
            @Override
            public void onNetworkAvailable() {
                syncPendingUserData(null);
            }
            
            @Override
            public void onNetworkLost() {
                // No hacer nada cuando se pierde la conexión
            }
        });
    }
    
    @Override
    public void loadUser(String userId, RepositoryCallback<User> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: cargar desde Firebase y actualizar caché local
            firebaseRepository.loadUser(userId, new RepositoryCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    // Actualizar caché local con los datos de Firebase
                    updateLocalCache(result);
                    callback.onSuccess(result);
                }
                
                @Override
                public void onError(String error) {
                    System.out.println("HybridUserRepository: Error al cargar usuario de Firebase: " + error);
                    // Si falla Firebase, intentar cargar desde caché local
                    loadFromLocalCache(userId, callback);
                }
            });
        } else {
            // Si no hay internet: cargar desde caché local
            System.out.println("HybridUserRepository: Sin conexión, cargando usuario desde caché local");
            loadFromLocalCache(userId, callback);
        }
    }
    
    @Override
    public void createUser(User user, RepositoryCallback<User> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: crear en Firebase y luego en caché local
            firebaseRepository.createUser(user, new RepositoryCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    // Crear también en caché local
                    roomRepository.createUser(result, new RepositoryCallback<User>() {
                        @Override
                        public void onSuccess(User localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, el usuario ya está en Firebase
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
            // Si no hay internet: crear solo en caché local (se sincronizará después)
            roomRepository.createUser(user, callback);
        }
    }
    
    @Override
    public void updateUser(String userId, User user, RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: actualizar en Firebase y luego en caché local
            firebaseRepository.updateUser(userId, user, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Actualizar también en caché local
                    roomRepository.updateUser(userId, user, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, el usuario ya está actualizado en Firebase
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
            roomRepository.updateUser(userId, user, callback);
        }
    }
    
    @Override
    public void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: verificar en Firebase
            firebaseRepository.checkUsernameAvailability(username, callback);
        } else {
            // Si no hay internet: verificar en caché local
            roomRepository.checkUsernameAvailability(username, callback);
        }
    }
    
    @Override
    public void syncPendingUserData(RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            if (networkManager.isOnline()) {
                // Sincronizar datos de usuario pendientes desde caché local a Firebase
                roomRepository.syncPendingUserData(new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (callback != null) {
                            callback.onSuccess(result);
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        if (callback != null) {
                            callback.onError(error);
                        }
                    }
                });
            } else {
                if (callback != null) {
                    callback.onError("Sin conexión a internet");
                }
            }
        });
    }
    
    /**
     * Cargar usuario desde caché local
     */
    private void loadFromLocalCache(String userId, RepositoryCallback<User> callback) {
        roomRepository.loadUser(userId, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                callback.onSuccess(result);
            }
            
            @Override
            public void onError(String error) {
                // Si no se encuentra en caché local, crear usuario básico con datos de Firebase Auth
                System.out.println("HybridUserRepository: Usuario no encontrado en caché local, creando usuario básico");
                createBasicUserFromFirebaseAuth(userId, callback);
            }
        });
    }
    
    /**
     * Crear usuario básico con datos de Firebase Auth cuando no está en caché local
     */
    private void createBasicUserFromFirebaseAuth(String userId, RepositoryCallback<User> callback) {
        // Obtener datos básicos de Firebase Auth
        com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            // Crear usuario básico con datos de Firebase Auth
            User basicUser = new User(
                userId,
                firebaseUser.getEmail(),
                firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "",
                firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "",
                "", // primerApellido
                "", // segundoApellido
                ""  // fechaNacimiento
            );
            
            // Guardar en caché local
            roomRepository.createUser(basicUser, new RepositoryCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    System.out.println("HybridUserRepository: Usuario básico creado en caché local");
                    callback.onSuccess(result);
                }
                
                @Override
                public void onError(String error) {
                    System.err.println("HybridUserRepository: Error al crear usuario básico: " + error);
                    callback.onError("Error al crear usuario básico: " + error);
                }
            });
        } else {
            callback.onError("Usuario de Firebase Auth no disponible");
        }
    }
    
    /**
     * Actualizar caché local con datos de Firebase
     */
    private void updateLocalCache(User user) {
        executor.execute(() -> {
            roomRepository.createUser(user, new RepositoryCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    // Usuario actualizado en caché local
                }
                
                    @Override
                    public void onError(String error) {
                        // Log error but don't crash
                        System.err.println("Error updating local cache: " + error);
                    }
            });
        });
    }
    
    @Override
    public void logout(RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: cerrar sesión en Firebase y limpiar caché local
            firebaseRepository.logout(new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Limpiar caché local
                    roomRepository.logout(new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, la sesión ya está cerrada en Firebase
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
            // Si no hay internet: solo limpiar caché local
            roomRepository.logout(callback);
        }
    }
    
    @Override
    public void deleteAccount(RepositoryCallback<Void> callback) {
        if (networkManager.isOnline()) {
            // Si hay internet: eliminar cuenta en Firebase y limpiar caché local
            firebaseRepository.deleteAccount(new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // Limpiar caché local
                    roomRepository.deleteAccount(new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void localResult) {
                            callback.onSuccess(result);
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Aunque falle el caché local, la cuenta ya está eliminada en Firebase
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
            // Si no hay internet: solo limpiar caché local
            roomRepository.deleteAccount(callback);
        }
    }
}
