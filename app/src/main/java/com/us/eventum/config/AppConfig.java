package com.us.eventum.config;

public class AppConfig {
    // URL de la base de datos de Firebase
    public static final String FIREBASE_DATABASE_URL = "https://eventum-8c0c1-default-rtdb.europe-west1.firebasedatabase.app";
    
    // Configuración de la aplicación
    public static final String APP_PREFS_NAME = "EventumPrefs";
    
    // Validación de contraseña
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final boolean REQUIRE_SPECIAL_CHARS = true;
    public static final boolean REQUIRE_NUMBERS = true;
    public static final boolean REQUIRE_UPPERCASE = true;
    
    // Validación de edad
    public static final int MIN_AGE = 18;

    // Número máximo de intentos de inicio de sesión
    public static final int MAX_LOGIN_ATTEMPTS = 3;

    // Tiempo de bloqueo en milisegundos (30 minutos)
    public static final long LOCKOUT_DURATION = 30 * 60 * 1000;
} 