package com.us.eventum.data.models;

import java.io.Serializable;
import java.util.Locale;

public class Organizer implements Serializable {
    private String uid;
    private String username;
    private String email;
    private String cif;
    private String phone;
    private String usernameLower; // Para búsquedas case-insensitive

    public Organizer() {
        // Constructor vacío requerido para Firestore
    }

    public Organizer(String uid, String username, String email, String cif, String phone) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.cif = cif;
        this.phone = phone;
        this.usernameLower = username != null ? username.trim().toLowerCase(Locale.ROOT) : null;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        this.usernameLower = username != null ? username.trim().toLowerCase(Locale.ROOT) : null;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCif() {
        return cif;
    }

    public void setCif(String cif) {
        this.cif = cif;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsernameLower() {
        return usernameLower;
    }

    public void setUsernameLower(String usernameLower) {
        this.usernameLower = usernameLower;
    }
}
