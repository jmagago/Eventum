package com.us.eventum.data.models;

import com.google.firebase.firestore.PropertyName;

/**
 * Clase que representa un usuario en la aplicación
 * Contiene toda la información necesaria del usuario
 */
public class User {
    // ID único del usuario en Firebase
    private String uid;
    
    // Email del usuario (usado para login)
    private String email;
    
    // Username del usuario (único)
    private String username;
    
    // Nombre del usuario
    private String nombre;
    
    // Primer apellido del usuario
    private String primerApellido;
    
    // Segundo apellido del usuario (opcional)
    private String segundoApellido;
    
    // Fecha de nacimiento en formato dd/MM/yyyy
    private String fechaNacimiento;
    
    // Lugar de nacimiento
    private String lugarNacimiento;

    // Rol del usuario: UserRole.ORGANIZER o UserRole.ATTENDEE
    private String role;

    /**
     * Constructor vacío requerido por Firebase
     * Firebase necesita este constructor para poder deserializar los objetos
     */
    public User() {
        // Constructor vacío
    }

    /**
     * Constructor completo para crear un nuevo usuario
     * @param uid ID único del usuario
     * @param email Email del usuario
     * @param username Username del usuario
     * @param nombre Nombre del usuario
     * @param primerApellido Primer apellido del usuario
     * @param segundoApellido Segundo apellido del usuario
     * @param fechaNacimiento Fecha de nacimiento
     * @param lugarNacimiento Lugar de nacimiento
     */
    public User(String uid, String email, String username, String nombre, String primerApellido, 
                String segundoApellido, String fechaNacimiento, String lugarNacimiento) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.lugarNacimiento = lugarNacimiento;
        this.role = UserRole.ORGANIZER; // Valor por defecto para compatibilidad hacia atrás
    }

    /**
     * Constructor con rol explícito
     */
    public User(String uid, String email, String username, String nombre, String primerApellido,
                String segundoApellido, String fechaNacimiento, String lugarNacimiento, String role) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.lugarNacimiento = lugarNacimiento;
        this.role = role;
    }

    // Getters y Setters para cada campo
    @PropertyName("uid")
    public String getUid() {
        return uid;
    }

    @PropertyName("uid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @PropertyName("email")
    public String getEmail() {
        return email;
    }

    @PropertyName("email")
    public void setEmail(String email) {
        this.email = email;
    }

    @PropertyName("username")
    public String getUsername() {
        return username;
    }

    @PropertyName("username")
    public void setUsername(String username) {
        this.username = username;
    }

    @PropertyName("nombre")
    public String getNombre() {
        return nombre;
    }

    @PropertyName("nombre")
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @PropertyName("primerApellido")
    public String getPrimerApellido() {
        return primerApellido;
    }

    @PropertyName("primerApellido")
    public void setPrimerApellido(String primerApellido) {
        this.primerApellido = primerApellido;
    }

    @PropertyName("segundoApellido")
    public String getSegundoApellido() {
        return segundoApellido;
    }

    @PropertyName("segundoApellido")
    public void setSegundoApellido(String segundoApellido) {
        this.segundoApellido = segundoApellido;
    }

    @PropertyName("fechaNacimiento")
    public String getFechaNacimiento() {
        return fechaNacimiento;
    }

    @PropertyName("fechaNacimiento")
    public void setFechaNacimiento(String fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    @PropertyName("lugarNacimiento")
    public String getLugarNacimiento() {
        return lugarNacimiento;
    }

    @PropertyName("lugarNacimiento")
    public void setLugarNacimiento(String lugarNacimiento) {
        this.lugarNacimiento = lugarNacimiento;
    }

    @PropertyName("role")
    public String getRole() {
        return role;
    }

    @PropertyName("role")
    public void setRole(String role) {
        this.role = role;
    }
} 