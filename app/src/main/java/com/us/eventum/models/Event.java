package com.us.eventum.models;

import java.io.Serializable;
import java.util.Date;

public class Event implements Serializable {
    private String id;
    private String title;
    private String description;
    private Date date;
    private String location;
    private String userId;
    private int maxParticipants;

    public Event() {
        // Constructor vacío requerido para Firestore
    }

    public Event(String title, String description, Date date, String location, String userId, int maxParticipants) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.userId = userId;
        this.maxParticipants = maxParticipants;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }
} 