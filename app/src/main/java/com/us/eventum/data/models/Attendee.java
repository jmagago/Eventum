package com.us.eventum.data.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.Date;

public class Attendee implements Serializable {
    private String id;
    private String eventId;
    private String userId; // referencia al usuario inscrito
    private String name;
    private String lastName;
    private String dni;
    private String email;
    private String phone;
    private Timestamp birthDate;
    private boolean requiresParentalAuthorization;
    private boolean scanned;

    public Attendee() {
        // Constructor vacío requerido para Firestore
    }

    public Attendee(String name, String lastName, String dni, String email, String phone, String birthDate) {
        this.name = name;
        this.lastName = lastName;
        this.dni = dni;
        this.email = email;
        this.phone = phone;
        this.requiresParentalAuthorization = false;
        this.scanned = false;
        
        // Convertir String a Timestamp si se proporciona
        if (birthDate != null && !birthDate.isEmpty()) {
            try {
                // Asumir formato dd/MM/yyyy
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                Date date = sdf.parse(birthDate);
                this.birthDate = new Timestamp(date);
            } catch (Exception e) {
                // Si falla la conversión, dejar como null
                this.birthDate = null;
            }
        }
    }

    public Attendee(String id, String eventId, String name, String lastName, String dni, String email, String phone, long confirmationTime, boolean scanned) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.lastName = lastName;
        this.dni = dni;
        this.email = email;
        this.phone = phone;
        this.scanned = scanned;
        this.requiresParentalAuthorization = false;
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

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Timestamp getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Timestamp birthDate) {
        this.birthDate = birthDate;
    }

    public boolean isRequiresParentalAuthorization() {
        return requiresParentalAuthorization;
    }

    public void setRequiresParentalAuthorization(boolean requiresParentalAuthorization) {
        this.requiresParentalAuthorization = requiresParentalAuthorization;
    }

    public boolean isScanned() {
        return scanned;
    }

    public void setScanned(boolean scanned) {
        this.scanned = scanned;
    }

    // Eliminados verified/registrationDate/confirmationTime (mueven a User)
}
