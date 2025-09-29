package com.us.eventum.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.annotation.NonNull;
import com.us.eventum.data.models.UserRole;

/**
 * Entidad Room para usuarios
 * Representa la estructura de datos local para usuarios
 */
@Entity(
    tableName = "users",
    indices = {@Index(value = {"username"}, unique = true), @Index(value = {"isSynced"})}
)
public class UserEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "uid")
    public String uid;
    
    @ColumnInfo(name = "email")
    public String email;
    
    @ColumnInfo(name = "username")
    public String username;
    
    @ColumnInfo(name = "nombre")
    public String nombre;
    
    @ColumnInfo(name = "primerApellido")
    public String primerApellido;
    
    @ColumnInfo(name = "segundoApellido")
    public String segundoApellido;
    
    @ColumnInfo(name = "fechaNacimiento")
    public String fechaNacimiento;
    
    @ColumnInfo(name = "lugarNacimiento")
    public String lugarNacimiento;
    
    @ColumnInfo(name = "role")
    public String role;
    
    @ColumnInfo(name = "isSynced")
    public boolean isSynced;
    
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    // Constructor vacío requerido por Room
    public UserEntity() {
        this.uid = ""; // Inicializar con valor por defecto
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.isSynced = false;
    }
    
    // Constructor completo - ignorado por Room
    @androidx.room.Ignore
    public UserEntity(String uid, String email, String username, String nombre, String primerApellido,
                     String segundoApellido, String fechaNacimiento, String lugarNacimiento) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.lugarNacimiento = lugarNacimiento;
        this.role = UserRole.ORGANIZER; // Valor por defecto para migraciones
        this.isSynced = false;
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters y Setters
    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    
    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }
    
    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String segundoApellido) { this.segundoApellido = segundoApellido; }
    
    public String getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(String fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    
    public String getLugarNacimiento() { return lugarNacimiento; }
    public void setLugarNacimiento(String lugarNacimiento) { this.lugarNacimiento = lugarNacimiento; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
    
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
