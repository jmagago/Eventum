package com.us.eventum.utils;

import com.us.eventum.data.models.Event;
import com.us.eventum.utils.LocaleUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventSearchFilter {
    private String keywords;
    private String eventType;
    private String location;
    private Date dateFrom;
    private Date dateTo;
    
    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", LocaleUtils.spanish());
    
    public EventSearchFilter() {
        // Constructor vacío
    }
    
    public EventSearchFilter(String keywords, String eventType, String location, Date dateFrom, Date dateTo) {
        this.keywords = keywords;
        this.eventType = eventType;
        this.location = location;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }
    
    // Getters y Setters
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public Date getDateFrom() { return dateFrom; }
    public void setDateFrom(Date dateFrom) { this.dateFrom = dateFrom; }
    
    public Date getDateTo() { return dateTo; }
    public void setDateTo(Date dateTo) { this.dateTo = dateTo; }
    
    /**
     * Filtra una lista de eventos según los criterios de búsqueda
     */
    public List<Event> filterEvents(List<Event> events) {
        if (events == null) return new ArrayList<>();
        
        List<Event> filteredEvents = new ArrayList<>();
        
        for (Event event : events) {
            if (matchesCriteria(event)) {
                filteredEvents.add(event);
            }
        }
        
        return filteredEvents;
    }
    
    /**
     * Verifica si un evento cumple con los criterios de búsqueda
     */
    private boolean matchesCriteria(Event event) {
        // Verificar palabras clave (título)
        if (keywords != null && !keywords.trim().isEmpty()) {
            String searchKeywords = keywords.toLowerCase(Locale.ROOT).trim();
            String eventTitle = event.getTitle() != null ? event.getTitle().toLowerCase(Locale.ROOT) : "";
            
            if (!eventTitle.contains(searchKeywords)) {
                return false;
            }
        }
        
        // Verificar tipo de evento
        if (eventType != null && !eventType.trim().isEmpty()) {
            String searchEventType = eventType.toLowerCase(Locale.ROOT).trim();
            String eventEventType = event.getEventType() != null ? event.getEventType().toLowerCase(Locale.ROOT) : "";
            
            if (!eventEventType.contains(searchEventType)) {
                return false;
            }
        }
        
        // Verificar ubicación
        if (location != null && !location.trim().isEmpty()) {
            String searchLocation = location.toLowerCase(Locale.ROOT).trim();
            String eventLocation = event.getLocation() != null ? event.getLocation().toLowerCase(Locale.ROOT) : "";
            
            if (!eventLocation.contains(searchLocation)) {
                return false;
            }
        }
        
        // Verificar rango de fechas
        if (event.getDate() != null) {
            Date eventDate = event.getDate();
            
            // Verificar fecha desde
            if (dateFrom != null && eventDate.before(dateFrom)) {
                return false;
            }
            
            // Verificar fecha hasta
            if (dateTo != null && eventDate.after(dateTo)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Verifica si hay algún criterio de búsqueda activo
     */
    public boolean hasActiveFilters() {
        return (keywords != null && !keywords.trim().isEmpty()) ||
               (eventType != null && !eventType.trim().isEmpty()) ||
               (location != null && !location.trim().isEmpty()) ||
               dateFrom != null || dateTo != null;
    }
    
    /**
     * Limpia todos los filtros
     */
    public void clearFilters() {
        keywords = null;
        eventType = null;
        location = null;
        dateFrom = null;
        dateTo = null;
    }
    
    /**
     * Obtiene un resumen de los filtros activos
     */
    public String getActiveFiltersSummary() {
        List<String> activeFilters = new ArrayList<>();
        
        if (keywords != null && !keywords.trim().isEmpty()) {
            activeFilters.add("Título: " + keywords);
        }
        
        if (eventType != null && !eventType.trim().isEmpty()) {
            activeFilters.add("Tipo: " + eventType);
        }
        
        if (location != null && !location.trim().isEmpty()) {
            activeFilters.add("Lugar: " + location);
        }
        
        if (dateFrom != null) {
            activeFilters.add("Desde: " + dateFormat.format(dateFrom));
        }
        
        if (dateTo != null) {
            activeFilters.add("Hasta: " + dateFormat.format(dateTo));
        }
        
        return activeFilters.isEmpty() ? "Sin filtros" : String.join(", ", activeFilters);
    }
}
