package com.us.eventum.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.annotation.NonNull;

import java.util.Date;

/**
 * Entidad Room para eventos
 * Representa la estructura de datos local para eventos
 */
@Entity(
    tableName = "events",
    indices = {@Index(value = {"userId"}), @Index(value = {"isSynced"})}
)
public class EventEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;
    
    @ColumnInfo(name = "title")
    public String title;
    
    @ColumnInfo(name = "description")
    public String description;
    
    @ColumnInfo(name = "date")
    public Date date;
    
    @ColumnInfo(name = "location")
    public String location;
    
    @ColumnInfo(name = "userId")
    public String userId;
    
    @ColumnInfo(name = "maxParticipants")
    public int maxParticipants;
    
    @ColumnInfo(name = "eventType")
    public String eventType;
    
    @ColumnInfo(name = "isPrivate")
    public boolean isPrivate;
    
    @ColumnInfo(name = "isSynced")
    public boolean isSynced;
    
    @ColumnInfo(name = "isSyncing")
    public boolean isSyncing;
    
    @ColumnInfo(name = "isDeleted")
    public boolean isDeleted;
    
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    // Constructor vacío requerido por Room
    public EventEntity() {
        this.id = ""; // Inicializar con valor por defecto
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.isSynced = false;
        this.isDeleted = false;
    }
    
    // Constructor completo
    public EventEntity(String id, String title, String description, Date date, String location,
                      String userId, int maxParticipants, String eventType, boolean isPrivate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.userId = userId;
        this.maxParticipants = maxParticipants;
        this.eventType = eventType;
        this.isPrivate = isPrivate;
        this.isSynced = false;
        this.isSyncing = false;
        this.isDeleted = false;
        this.lastUpdated = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters y Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean aPrivate) { isPrivate = aPrivate; }
    
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
    
    public boolean isSyncing() { return isSyncing; }
    public void setSyncing(boolean syncing) { isSyncing = syncing; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
