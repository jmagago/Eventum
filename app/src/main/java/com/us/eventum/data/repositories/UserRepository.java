package com.us.eventum.data.repositories;

import com.us.eventum.data.models.User;

/**
 * Interfaz que define los contratos para operaciones de usuarios
 * Implementa el patrón Repository para abstraer la fuente de datos
 */
public interface UserRepository {
    
    /**
     * Cargar datos del usuario actual
     * @param userId ID del usuario
     * @param callback Callback para manejar el resultado
     */
    void loadUser(String userId, RepositoryCallback<User> callback);
    
    /**
     * Crear un nuevo usuario
     * @param user Usuario a crear
     * @param callback Callback para manejar el resultado
     */
    void createUser(User user, RepositoryCallback<User> callback);
    
    /**
     * Actualizar datos del usuario
     * @param userId ID del usuario
     * @param user Datos actualizados del usuario
     * @param callback Callback para manejar el resultado
     */
    void updateUser(String userId, User user, RepositoryCallback<Void> callback);
    
    /**
     * Verificar disponibilidad de username
     * @param username Username a verificar
     * @param callback Callback para manejar el resultado
     */
    void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback);
    
    /**
     * Sincronizar datos de usuario pendientes
     * @param callback Callback para manejar el resultado
     */
    void syncPendingUserData(RepositoryCallback<Void> callback);
    
    /**
     * Cerrar sesión del usuario
     * @param callback Callback para manejar el resultado
     */
    void logout(RepositoryCallback<Void> callback);
    
    /**
     * Eliminar cuenta del usuario
     * @param callback Callback para manejar el resultado
     */
    void deleteAccount(RepositoryCallback<Void> callback);
    
    /**
     * Callback genérico para operaciones del repositorio
     */
    interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
