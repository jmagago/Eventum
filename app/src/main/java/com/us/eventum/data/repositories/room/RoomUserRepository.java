package com.us.eventum.data.repositories.room;

import android.content.Context;
import com.us.eventum.data.local.EventumDatabase;
import com.us.eventum.data.local.dao.UserDao;
import com.us.eventum.data.local.entities.UserEntity;
import com.us.eventum.data.models.User;
import com.us.eventum.data.repositories.UserRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementación de UserRepository usando Room Database
 * Maneja todas las operaciones de usuarios en la base de datos local
 */
public class RoomUserRepository implements UserRepository {
    
    private final UserDao userDao;
    private final ExecutorService executor;
    
    public RoomUserRepository(Context context) {
        EventumDatabase database = EventumDatabase.getDatabase(context);
        this.userDao = database.userDao();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public void loadUser(String userId, RepositoryCallback<User> callback) {
        executor.execute(() -> {
            try {
                UserEntity entity = userDao.getUserById(userId);
                if (entity != null) {
                    User user = convertEntityToModel(entity);
                    callback.onSuccess(user);
                } else {
                    callback.onError("Usuario no encontrado");
                }
            } catch (Exception e) {
                callback.onError("Error al cargar usuario: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void createUser(User user, RepositoryCallback<User> callback) {
        executor.execute(() -> {
            try {
                UserEntity entity = convertModelToEntity(user);
                userDao.insertUser(entity);
                callback.onSuccess(user);
            } catch (Exception e) {
                callback.onError("Error al crear usuario: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void updateUser(String userId, User user, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                UserEntity entity = convertModelToEntity(user);
                entity.setUid(userId);
                entity.setLastUpdated(System.currentTimeMillis());
                userDao.updateUser(entity);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al actualizar usuario: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                int count = userDao.getUsernameCount(username);
                boolean isAvailable = count == 0;
                callback.onSuccess(isAvailable);
            } catch (Exception e) {
                callback.onError("Error al verificar username: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void syncPendingUserData(RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Aquí se implementaría la lógica de sincronización
                // Por ahora, simplemente marcamos como sincronizados
                List<UserEntity> unsyncedUsers = userDao.getUnsyncedUsers();
                for (UserEntity entity : unsyncedUsers) {
                    userDao.markUserAsSynced(entity.getUid(), System.currentTimeMillis());
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al sincronizar datos de usuario: " + e.getMessage());
            }
        });
    }
    
    /**
     * Convertir UserEntity a User
     */
    private User convertEntityToModel(UserEntity entity) {
        User user = new User(
            entity.getUid(),
            entity.getEmail(),
            entity.getUsername(),
            entity.getNombre(),
            entity.getPrimerApellido(),
            entity.getSegundoApellido(),
            entity.getFechaNacimiento(),
            entity.getRole()
        );
        user.setVerified(entity.isVerified());
        user.setConfirmationTime(entity.getConfirmationTime());
        user.setRegistrationDate(entity.getRegistrationDate());
        return user;
    }
    
    /**
     * Convertir User a UserEntity
     */
    private UserEntity convertModelToEntity(User user) {
        // Validar que el uid no sea nulo o vacío
        if (user.getUid() == null || user.getUid().trim().isEmpty()) {
            throw new IllegalArgumentException("El UID del usuario no puede ser nulo o vacío");
        }
        
        UserEntity entity = new UserEntity(
            user.getUid(),
            user.getEmail(),
            user.getUsername(),
            user.getNombre(),
            user.getPrimerApellido(),
            user.getSegundoApellido(),
            user.getFechaNacimiento()
        );
        entity.setRole(user.getRole());
        entity.setVerified(user.isVerified());
        entity.setConfirmationTime(user.getConfirmationTime());
        entity.setRegistrationDate(user.getRegistrationDate());
        return entity;
    }
    
    @Override
    public void logout(RepositoryCallback<Void> callback) {
        // En Room, no hay operación específica para logout
        // Solo se limpia la caché local si es necesario
        callback.onSuccess(null);
    }
    
    @Override
    public void deleteAccount(RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // Eliminar todos los datos del usuario de la caché local
                userDao.deleteAllUsers();
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError("Error al eliminar datos locales: " + e.getMessage());
            }
        });
    }
}
