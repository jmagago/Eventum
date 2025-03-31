package com.us.eventum;

import java.util.Date;

public class Event {
    private String id;
    private String userId;
    private String title;
    private String description;
    private Date date;
    private String location;
    private int maxParticipants;
    private int currentParticipants;

    // Constructor vacío requerido para Firestore
    public Event() {}

    public Event(String userId, String title, String description, Date date, 
                String location, int maxParticipants) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.maxParticipants = maxParticipants;
        this.currentParticipants = 0;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public int getCurrentParticipants() { return currentParticipants; }
    public void setCurrentParticipants(int currentParticipants) { this.currentParticipants = currentParticipants; }
} 