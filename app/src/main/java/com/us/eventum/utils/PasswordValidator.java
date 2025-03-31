package com.us.eventum.utils;

import com.us.eventum.config.AppConfig;

public class PasswordValidator {
    public static class PasswordValidationResult {
        public final boolean isValid;
        public final String errorMessage;

        public PasswordValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    public static PasswordValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return new PasswordValidationResult(false, "La contraseña no puede estar vacía");
        }

        if (password.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            return new PasswordValidationResult(false, 
                "La contraseña debe tener al menos " + AppConfig.MIN_PASSWORD_LENGTH + " caracteres");
        }

        if (AppConfig.REQUIRE_SPECIAL_CHARS && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return new PasswordValidationResult(false, "La contraseña debe contener al menos un carácter especial");
        }

        if (AppConfig.REQUIRE_NUMBERS && !password.matches(".*\\d.*")) {
            return new PasswordValidationResult(false, "La contraseña debe contener al menos un número");
        }

        if (AppConfig.REQUIRE_UPPERCASE && !password.matches(".*[A-Z].*")) {
            return new PasswordValidationResult(false, "La contraseña debe contener al menos una letra mayúscula");
        }

        return new PasswordValidationResult(true, null);
    }

    public static int getPasswordStrength(String password) {
        int strength = 0;
        
        // Longitud mínima
        if (password.length() >= AppConfig.MIN_PASSWORD_LENGTH) {
            strength += 1;
        }
        
        // Caracteres especiales
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            strength += 1;
        }
        
        // Números
        if (password.matches(".*\\d.*")) {
            strength += 1;
        }
        
        // Mayúsculas
        if (password.matches(".*[A-Z].*")) {
            strength += 1;
        }
        
        // Minúsculas
        if (password.matches(".*[a-z].*")) {
            strength += 1;
        }
        
        return strength;
    }

    public static int calculateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        // Longitud (máximo 40 puntos)
        if (password.length() >= 6) score += 10;
        if (password.length() >= 8) score += 10;
        if (password.length() >= 12) score += 20;

        // Tipos de caracteres (máximo 60 puntos)
        if (password.matches(".*[A-Z].*")) score += 20;
        if (password.matches(".*[0-9].*")) score += 20;
        if (password.matches(".*[!@#$%^&*()_+\\-=].*")) score += 20;

        // Convertir a escala de 0-4
        if (score < 20) return 0;      // Muy débil (0-19 puntos)
        if (score < 40) return 1;      // Débil (20-39 puntos)
        if (score < 60) return 2;      // Media (40-59 puntos)
        if (score < 80) return 3;      // Fuerte (60-79 puntos)
        return 4;                      // Muy fuerte (80-100 puntos)
    }
} 