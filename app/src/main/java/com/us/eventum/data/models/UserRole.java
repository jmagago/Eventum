package com.us.eventum.data.models;

/**
 * Constantes para los roles de usuario en la aplicación
 * Centraliza los valores de roles para evitar typos y facilitar mantenimiento
 */
public class UserRole {
    /**
     * Rol de organizador - Puede crear, editar y eliminar eventos
     */
    public static final String ORGANIZER = "ORGANIZADOR";
    
    /**
     * Rol de asistente - Puede ver eventos y apuntarse/desapuntarse
     */
    public static final String ATTENDEE = "ASISTENTE";
    
    /**
     * Constructor privado para evitar instanciación
     */
    private UserRole() {
        throw new AssertionError("No se debe instanciar esta clase");
    }
    
    /**
     * Verifica si un rol es válido
     * @param role el rol a verificar
     * @return true si el rol es ORGANIZER o ATTENDEE
     */
    public static boolean isValidRole(String role) {
        return ORGANIZER.equals(role) || ATTENDEE.equals(role);
    }
}
