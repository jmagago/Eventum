package com.us.eventum;

/**
 * Clase que representa un usuario en la aplicación
 * Contiene toda la información necesaria del usuario
 */
public class User {
    // ID único del usuario en Firebase
    private String uid;
    
    // Email del usuario (usado para login)
    private String email;
    
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
     * @param nombre Nombre del usuario
     * @param primerApellido Primer apellido del usuario
     * @param segundoApellido Segundo apellido del usuario
     * @param fechaNacimiento Fecha de nacimiento
     * @param lugarNacimiento Lugar de nacimiento
     */
    public User(String uid, String email, String nombre, String primerApellido, 
                String segundoApellido, String fechaNacimiento, String lugarNacimiento) {
        this.uid = uid;
        this.email = email;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.lugarNacimiento = lugarNacimiento;
    }

    // Getters y Setters para cada campo
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
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

    public String getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(String fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getLugarNacimiento() {
        return lugarNacimiento;
    }

    public void setLugarNacimiento(String lugarNacimiento) {
        this.lugarNacimiento = lugarNacimiento;
    }
} 