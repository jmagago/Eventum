package com.us.eventum.core.utils;

import com.us.eventum.data.models.Attendee;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filtro para búsqueda de asistentes
 * Permite filtrar asistentes por diferentes criterios
 */
public class AttendeeSearchFilter {
    private String dni;
    private String name;
    private String firstLastName;
    private String secondLastName;
    private String email;
    private String phone;
    
    // Constructor vacío
    public AttendeeSearchFilter() {
        clearFilters();
    }
    
    // Getters y Setters
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getFirstLastName() { return firstLastName; }
    public void setFirstLastName(String firstLastName) { this.firstLastName = firstLastName; }
    
    public String getSecondLastName() { return secondLastName; }
    public void setSecondLastName(String secondLastName) { this.secondLastName = secondLastName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    /**
     * Verificar si hay filtros activos
     */
    public boolean hasActiveFilters() {
        return (dni != null && !dni.trim().isEmpty()) ||
               (name != null && !name.trim().isEmpty()) ||
               (firstLastName != null && !firstLastName.trim().isEmpty()) ||
               (secondLastName != null && !secondLastName.trim().isEmpty()) ||
               (email != null && !email.trim().isEmpty()) ||
               (phone != null && !phone.trim().isEmpty());
    }
    
    /**
     * Limpiar todos los filtros
     */
    public void clearFilters() {
        this.dni = null;
        this.name = null;
        this.firstLastName = null;
        this.secondLastName = null;
        this.email = null;
        this.phone = null;
    }
    
    /**
     * Obtener resumen de filtros activos
     */
    public String getActiveFiltersSummary() {
        List<String> activeFilters = new ArrayList<>();
        
        if (dni != null && !dni.trim().isEmpty()) {
            activeFilters.add("DNI: " + dni);
        }
        if (name != null && !name.trim().isEmpty()) {
            activeFilters.add("Nombre: " + name);
        }
        if (firstLastName != null && !firstLastName.trim().isEmpty()) {
            activeFilters.add("Apellido: " + firstLastName);
        }
        if (secondLastName != null && !secondLastName.trim().isEmpty()) {
            activeFilters.add("2º Apellido: " + secondLastName);
        }
        if (email != null && !email.trim().isEmpty()) {
            activeFilters.add("Email: " + email);
        }
        if (phone != null && !phone.trim().isEmpty()) {
            activeFilters.add("Teléfono: " + phone);
        }
        
        return String.join(", ", activeFilters);
    }
    
    /**
     * Filtrar lista de asistentes según los criterios activos
     */
    public List<Attendee> filterAttendees(List<Attendee> attendees) {
        if (!hasActiveFilters()) {
            return new ArrayList<>(attendees);
        }
        
        List<Attendee> filtered = new ArrayList<>();
        
        for (Attendee attendee : attendees) {
            boolean matches = true;
            
            // Filtrar por DNI
            if (dni != null && !dni.trim().isEmpty()) {
                matches &= attendee.getDni() != null &&
                          attendee.getDni().toUpperCase(Locale.ROOT).contains(dni.toUpperCase(Locale.ROOT));
            }
            
            // Filtrar por nombre
            if (name != null && !name.trim().isEmpty()) {
                String nameUpper = name.toUpperCase(Locale.ROOT);
                boolean nameMatch = attendee.getSortedNameLabel().toUpperCase(Locale.ROOT).contains(nameUpper)
                        || (attendee.getNombre() != null
                        && attendee.getNombre().toUpperCase(Locale.ROOT).contains(nameUpper))
                        || (attendee.getUsername() != null
                        && attendee.getUsername().toUpperCase(Locale.ROOT).contains(nameUpper));
                matches &= nameMatch;
            }
            
            // Filtrar por apellidos (combinar primer y segundo apellido)
            if ((firstLastName != null && !firstLastName.trim().isEmpty()) || 
                (secondLastName != null && !secondLastName.trim().isEmpty())) {
                
                String fullLastName = "";
                if (attendee.getPrimerApellido() != null) {
                    fullLastName = attendee.getPrimerApellido().toUpperCase(Locale.ROOT);
                }
                if (attendee.getSegundoApellido() != null && !attendee.getSegundoApellido().trim().isEmpty()) {
                    fullLastName += " " + attendee.getSegundoApellido().toUpperCase(Locale.ROOT);
                }
                
                // Verificar si coincide con primer apellido
                if (firstLastName != null && !firstLastName.trim().isEmpty()) {
                    matches &= fullLastName.contains(firstLastName.toUpperCase(Locale.ROOT));
                }
                
                // Verificar si coincide con segundo apellido
                if (secondLastName != null && !secondLastName.trim().isEmpty()) {
                    matches &= fullLastName.contains(secondLastName.toUpperCase(Locale.ROOT));
                }
            }
            
            // Filtrar por email
            if (email != null && !email.trim().isEmpty()) {
                matches &= attendee.getEmail() != null &&
                          attendee.getEmail().toUpperCase(Locale.ROOT).contains(email.toUpperCase(Locale.ROOT));
            }
            
            // Filtrar por teléfono
            if (phone != null && !phone.trim().isEmpty()) {
                matches &= attendee.getPhone() != null && 
                          attendee.getPhone().contains(phone);
            }
            
            if (matches) {
                filtered.add(attendee);
            }
        }
        
        return filtered;
    }
}
