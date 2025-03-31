package com.us.eventum.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.Date;

public class Attendee implements Serializable {
    private String id;
    private String eventId;
    private String name;
    private String lastName;
    private String email;
    private String phone;
    private Timestamp birthDate;
    private boolean requiresParentalAuthorization;
    private Date registrationDate;

    public Attendee() {
        // Constructor vacío requerido para Firestore
    }

    public Attendee(String name, String lastName, String email, String phone, String birthDate) {
        this.name = name;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.birthDate = Timestamp.now(); // Por defecto, usamos la fecha actual
        this.requiresParentalAuthorization = false;
        this.registrationDate = new Date();
    }

    // Getters y Setters
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

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }
} 