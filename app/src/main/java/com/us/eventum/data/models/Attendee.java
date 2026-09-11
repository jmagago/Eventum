package com.us.eventum.data.models;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

public class Attendee implements Serializable {
    private String uid;
    private String username;
    private String email;
    private String nombre; // Nombre real del asistente
    private String dni;
    private String phone;
    private String primerApellido;
    private String segundoApellido;
    private Timestamp fechaNacimiento;
    private String usernameLower; // Para búsquedas case-insensitive

    public Attendee() {
        // Constructor vacío requerido para Firestore
    }

    public Attendee(String uid, String username, String email, String nombre, String dni, String phone, 
                   String primerApellido, String segundoApellido, String fechaNacimiento) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.nombre = nombre;
        this.dni = dni;
        this.phone = phone;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.usernameLower = username != null ? username.trim().toLowerCase(Locale.ROOT) : null;
        
        // Convertir String a Timestamp si se proporciona
        if (fechaNacimiento != null && !fechaNacimiento.isEmpty()) {
            try {
                // Asumir formato dd/MM/yyyy
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "dd/MM/yyyy", com.us.eventum.utils.LocaleUtils.spanish());
                Date date = sdf.parse(fechaNacimiento);
                
                // Crear fecha a medianoche (00:00:00) para evitar incluir hora
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                
                this.fechaNacimiento = new Timestamp(cal.getTime());
            } catch (Exception e) {
                // Si falla la conversión, dejar como null
                this.fechaNacimiento = null;
            }
        }
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public void setPrimerApellido(String primerApellido) {
        this.primerApellido = primerApellido;
    }

    public String getSegundoApellido() {
        return segundoApellido;
    }

    public void setSegundoApellido(String segundoApellido) {
        this.segundoApellido = segundoApellido;
    }

    public Timestamp getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(Timestamp fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }
    
    /**
     * Establece la fecha de nacimiento desde un String en formato dd/MM/yyyy
     * La fecha se guarda a medianoche (00:00:00) para evitar incluir hora
     */
    public void setFechaNacimientoFromString(String fechaNacimiento) {
        if (fechaNacimiento != null && !fechaNacimiento.isEmpty()) {
            try {
                // Asumir formato dd/MM/yyyy
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "dd/MM/yyyy", com.us.eventum.utils.LocaleUtils.spanish());
                Date date = sdf.parse(fechaNacimiento);
                
                // Crear fecha a medianoche (00:00:00) para evitar incluir hora
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                
                this.fechaNacimiento = new Timestamp(cal.getTime());
            } catch (Exception e) {
                // Si falla la conversión, dejar como null
                this.fechaNacimiento = null;
            }
        } else {
            this.fechaNacimiento = null;
        }
    }

    public String getUsernameLower() {
        return usernameLower;
    }

    public void setUsernameLower(String usernameLower) {
        this.usernameLower = usernameLower;
    }

    /**
     * Verifica si el perfil del asistente está completo
     */
    public boolean isProfileComplete() {
        return username != null && !username.trim().isEmpty() &&
               nombre != null && !nombre.trim().isEmpty() &&
               primerApellido != null && !primerApellido.trim().isEmpty() &&
               segundoApellido != null && !segundoApellido.trim().isEmpty() &&
               dni != null && !dni.trim().isEmpty() &&
               phone != null && !phone.trim().isEmpty() &&
               fechaNacimiento != null;
    }

    // Métodos de compatibilidad para SettingsActivity
    public String getLastName() {
        return primerApellido; // Primer apellido como apellido principal
    }

    /** Nombre de pila para mostrar (nombre real, no el username de la cuenta). */
    public String getFirstNameForDisplay() {
        if (nombre != null && !nombre.trim().isEmpty()) {
            return nombre.trim();
        }
        if (username != null && !username.trim().isEmpty()) {
            return username.trim();
        }
        return "";
    }

    /** Apellidos completos para mostrar. */
    public String getApellidosForDisplay() {
        String p1 = primerApellido != null ? primerApellido.trim() : "";
        String p2 = segundoApellido != null ? segundoApellido.trim() : "";
        if (!p1.isEmpty() && !p2.isEmpty()) {
            return p1 + " " + p2;
        }
        return !p1.isEmpty() ? p1 : p2;
    }

    /** Formato lista: "Apellidos, Nombre". */
    public String getSortedNameLabel() {
        String apellidos = getApellidosForDisplay();
        String nombrePila = getFirstNameForDisplay();
        if (!apellidos.isEmpty() && !nombrePila.isEmpty()) {
            return apellidos + ", " + nombrePila;
        }
        if (!nombrePila.isEmpty()) {
            return nombrePila;
        }
        if (!apellidos.isEmpty()) {
            return apellidos;
        }
        return username != null ? username.trim() : "";
    }

    /** Formato ficha: "Nombre Apellido1 Apellido2". */
    public String getFullNameLabel() {
        StringBuilder sb = new StringBuilder();
        String nombrePila = getFirstNameForDisplay();
        if (!nombrePila.isEmpty()) {
            sb.append(nombrePila);
        }
        String apellidos = getApellidosForDisplay();
        if (!apellidos.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(apellidos);
        }
        if (sb.length() == 0 && username != null) {
            sb.append(username.trim());
        }
        return sb.toString().trim();
    }

    // Métodos de compatibilidad para mantener la interfaz existente
    // Nota: getId() eliminado para evitar duplicación en Firestore

    public Timestamp getBirthDate() {
        return fechaNacimiento;
    }

    public boolean isRequiresParentalAuthorization(@NonNull java.util.Date eventDate) {
        if (fechaNacimiento == null) {
            return false;
        }
        return com.us.eventum.utils.AgeUtils.requiresParentalAuthOnEventDay(
                fechaNacimiento.toDate(), eventDate);
    }
}