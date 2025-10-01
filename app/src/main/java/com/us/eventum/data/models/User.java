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

    // DNI (inmutable tras primer guardado)
    private String dni;

    // Teléfono de contacto
    private String phone;

    // Verificación de cuenta (email verificado)
    private boolean verified;

    // Momento en el que el email quedó verificado (epoch millis)
    private long confirmationTime;

    // Fecha/hora de registro de la cuenta (epoch millis)
    private long registrationDate;

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
     */
    public User(String uid, String email, String username, String nombre, String primerApellido,
                String segundoApellido, String fechaNacimiento) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.dni = "";
        this.phone = "";
        this.role = UserRole.ORGANIZER; // Valor por defecto para compatibilidad hacia atrás
        this.verified = false;
        this.confirmationTime = 0L;
        this.registrationDate = System.currentTimeMillis();
    }

    /**
     * Constructor con rol explícito
     */
    public User(String uid, String email, String username, String nombre, String primerApellido,
                String segundoApellido, String fechaNacimiento, String role) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.fechaNacimiento = fechaNacimiento;
        this.dni = "";
        this.phone = "";
        this.role = role;
        this.verified = false;
        this.confirmationTime = 0L;
        this.registrationDate = System.currentTimeMillis();
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

    // Eliminado lugarNacimiento por no ser relevante

    @PropertyName("role")
    public String getRole() {
        return role;
    }

    @PropertyName("role")
    public void setRole(String role) {
        this.role = role;
    }

    @PropertyName("verified")
    public boolean isVerified() {
        return verified;
    }

    @PropertyName("verified")
    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    @PropertyName("confirmationTime")
    public long getConfirmationTime() {
        return confirmationTime;
    }

    @PropertyName("confirmationTime")
    public void setConfirmationTime(long confirmationTime) {
        this.confirmationTime = confirmationTime;
    }

    @PropertyName("registrationDate")
    public long getRegistrationDate() {
        return registrationDate;
    }

    @PropertyName("registrationDate")
    public void setRegistrationDate(long registrationDate) {
        this.registrationDate = registrationDate;
    }

    @PropertyName("dni")
    public String getDni() { return dni; }

    @PropertyName("dni")
    public void setDni(String dni) { this.dni = dni; }

    @PropertyName("phone")
    public String getPhone() { return phone; }

    @PropertyName("phone")
    public void setPhone(String phone) { this.phone = phone; }

    /**
     * Verifica si el perfil del usuario está completo (solo para ATTENDEE).
     * Para asistentes, se requieren: nombre, primer apellido, DNI, teléfono y fecha de nacimiento.
     */
    public boolean isProfileComplete() {
        return nombre != null && !nombre.trim().isEmpty() &&
               primerApellido != null && !primerApellido.trim().isEmpty() &&
               dni != null && !dni.trim().isEmpty() &&
               phone != null && !phone.trim().isEmpty() &&
               fechaNacimiento != null && !fechaNacimiento.trim().isEmpty();
    }
} 