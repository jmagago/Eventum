package com.us.eventum.core.config;

public class AppConfig {
    // Validación de contraseña (alineada con el registro)
    public static final int MIN_PASSWORD_LENGTH = 6;

    // Validación de edad
    public static final int ATTENDEE_MIN_AGE = 16;
    public static final int ADULT_AGE = 18;
    public static final int ORGANIZER_MIN_AGE = 18;

    // Intentos máximos de inicio de sesión antes de avisar al usuario
    public static final int MAX_LOGIN_ATTEMPTS = 5;
}
