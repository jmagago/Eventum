package com.us.eventum.data.models;

import java.io.Serializable;

public class AttendeesToEvent implements Serializable {
    private String id;
    private String eventId;
    private String userId; // referencia al usuario inscrito
    private boolean scannedQR;
    private String parentalAuthUrl;

    public AttendeesToEvent() {
        // Constructor vacío requerido para Firestore
    }

    public AttendeesToEvent(String eventId, String userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.scannedQR = false;
    }

    public AttendeesToEvent(String id, String eventId, String userId, boolean scannedQR) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.scannedQR = scannedQR;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() { 
        return userId; 
    }
    
    public void setUserId(String userId) { 
        this.userId = userId; 
    }

    public boolean isScannedQR() {
        return scannedQR;
    }

    public void setScannedQR(boolean scannedQR) {
        this.scannedQR = scannedQR;
    }

    public String getParentalAuthUrl() {
        return parentalAuthUrl;
    }

    public void setParentalAuthUrl(String parentalAuthUrl) {
        this.parentalAuthUrl = parentalAuthUrl;
    }
}
