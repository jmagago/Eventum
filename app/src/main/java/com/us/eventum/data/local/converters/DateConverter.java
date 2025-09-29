package com.us.eventum.data.local.converters;

import androidx.room.TypeConverter;
import java.util.Date;

/**
 * Convertidor de tipos para Room Database
 * Convierte entre Date y Long para almacenamiento en SQLite
 */
public class DateConverter {
    
    /**
     * Convertir Date a Long para almacenamiento
     */
    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }
    
    /**
     * Convertir Long a Date para uso en la aplicación
     */
    @TypeConverter
    public static Date toDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }
}