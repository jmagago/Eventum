package com.us.eventum.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.us.eventum.data.local.entities.UserEntity;
import java.util.List;

/**
 * DAO para operaciones de usuarios en la base de datos local
 * Define todas las consultas SQL para la tabla de usuarios
 */
@Dao
public interface UserDao {
    
    /**
     * Insertar un nuevo usuario
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);
    
    /**
     * Insertar múltiples usuarios
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUsers(List<UserEntity> users);
    
    /**
     * Actualizar un usuario existente
     */
    @Update
    void updateUser(UserEntity user);
    
    /**
     * Eliminar un usuario
     */
    @Delete
    void deleteUser(UserEntity user);
    
    /**
     * Obtener usuario por ID
     */
    @Query("SELECT * FROM users WHERE uid = :userId")
    UserEntity getUserById(String userId);
    
    /**
     * Obtener usuario por username
     */
    @Query("SELECT * FROM users WHERE username = :username")
    UserEntity getUserByUsername(String username);
    
    /**
     * Obtener usuario por email
     */
    @Query("SELECT * FROM users WHERE email = :email")
    UserEntity getUserByEmail(String email);
    
    /**
     * Obtener usuarios no sincronizados
     */
    @Query("SELECT * FROM users WHERE isSynced = 0")
    List<UserEntity> getUnsyncedUsers();
    
    /**
     * Marcar usuario como sincronizado
     */
    @Query("UPDATE users SET isSynced = 1, lastUpdated = :timestamp WHERE uid = :userId")
    void markUserAsSynced(String userId, long timestamp);
    
    /**
     * Eliminar usuario por ID
     */
    @Query("DELETE FROM users WHERE uid = :userId")
    void deleteUserById(String userId);
    
    /**
     * Verificar si existe un username
     */
    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    int getUsernameCount(String username);
    
    /**
     * Verificar si existe un email
     */
    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    int getEmailCount(String email);
    
    /**
     * Contar usuarios no sincronizados
     */
    @Query("SELECT COUNT(*) FROM users WHERE isSynced = 0")
    int getUnsyncedUsersCount();
    
    /**
     * Eliminar todos los usuarios
     */
    @Query("DELETE FROM users")
    void deleteAllUsers();
}
