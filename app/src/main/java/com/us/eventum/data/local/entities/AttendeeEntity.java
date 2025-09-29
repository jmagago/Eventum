package com.us.eventum.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Entidad Room para asistentes
 * Representa la estructura de datos local para asistentes
 */
@Entity(
    tableName = "attendees",
    indices = {@Index(value = {"eventId"}), @Index(value = {"dni"}), @Index(value = {"isSynced"})}
)
public class AttendeeEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "lastName")
    public String lastName;
    
    @ColumnInfo(name = "dni")
    public String dni;
    
    @ColumnInfo(name = "email")
    public String email;
    
    @ColumnInfo(name = "phone")
    public String phone;
    
    @ColumnInfo(name = "birthDate")
    public String birthDate;
    
    @ColumnInfo(name = "eventId")
    public String eventId;
    
    @ColumnInfo(name = "requiresParentalAuthorization")
    public boolean requiresParentalAuthorization;
    
    @ColumnInfo(name = "verified")
    public boolean verified;
    
    @ColumnInfo(name = "verificationTimestamp")
    public long verificationTimestamp;
    
    @ColumnInfo(name = "isSynced")
    public boolean isSynced;
    
    @ColumnInfo(name = "isDeleted")
    public boolean isDeleted;
    
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    // Constructor vacío requerido por Room
    public AttendeeEntity() {
        this.id = ""; // Inicializar con valor por defecto
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.isSynced = false;
        this.isDeleted = false;
        this.verified = false;
        this.verificationTimestamp = 0;
    }
    
    // Constructor completo
    public AttendeeEntity(String id, String name, String lastName, String dni, String email,
                         String phone, String birthDate, String eventId, boolean requiresParentalAuthorization) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.dni = dni;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.eventId = eventId;
        this.requiresParentalAuthorization = requiresParentalAuthorization;
        this.verified = false;
        this.verificationTimestamp = 0;
        this.isSynced = false;
        this.isDeleted = false;
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters y Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public boolean isRequiresParentalAuthorization() { return requiresParentalAuthorization; }
    public void setRequiresParentalAuthorization(boolean requiresParentalAuthorization) { 
        this.requiresParentalAuthorization = requiresParentalAuthorization; 
    }
    
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    
    public long getVerificationTimestamp() { return verificationTimestamp; }
    public void setVerificationTimestamp(long verificationTimestamp) { this.verificationTimestamp = verificationTimestamp; }
    
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
