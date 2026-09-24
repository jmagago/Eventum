package com.us.eventum.data.models;

import com.google.firebase.Timestamp;

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

public class Organizer implements Serializable {
    private String uid;
    private String username;
    private String email;
    private String cif;
    private String phone;
    private Timestamp fechaNacimiento;
    private String usernameLower; // Para búsquedas case-insensitive

    public Organizer() {
        // Constructor vacío requerido para Firestore
    }

    public Organizer(String uid, String username, String email, String cif, String phone) {
        this(uid, username, email, cif, phone, null);
    }

    public Organizer(String uid, String username, String email, String cif, String phone,
                     String fechaNacimiento) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.cif = cif;
        this.phone = phone;
        this.usernameLower = username != null ? username.trim().toLowerCase(Locale.ROOT) : null;
        setFechaNacimientoFromString(fechaNacimiento);
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

    public Timestamp getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(Timestamp fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public void setFechaNacimientoFromString(String fechaNacimiento) {
        if (fechaNacimiento == null || fechaNacimiento.trim().isEmpty()) {
            this.fechaNacimiento = null;
            return;
        }
        Date parsed = com.us.eventum.core.utils.AgeUtils.parseBirthDate(fechaNacimiento);
        if (parsed == null) {
            this.fechaNacimiento = null;
            return;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(parsed);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        this.fechaNacimiento = new Timestamp(cal.getTime());
    }
}
