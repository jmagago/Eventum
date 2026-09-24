package com.us.eventum.core.utils;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class DniValidator {
    
    // Letras del DNI en orden
    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    
    public static android.view.View.OnFocusChangeListener createDniFocusValidator(TextInputLayout dniLayout) {
        return (v, hasFocus) -> {
            if (!hasFocus) {
                String dni = ((android.widget.EditText) v).getText().toString().trim().toUpperCase(Locale.ROOT);
                if (dni.isEmpty()) {
                    AnimationUtils.showErrorWithAnimation(dniLayout, "El DNI es requerido");
                } else if (!isValidDni(dni)) {
                    AnimationUtils.showErrorWithAnimation(dniLayout, "DNI inválido. La letra no coincide con el número");
                } else {
                    AnimationUtils.clearErrorWithAnimation(dniLayout);
                }
            }
        };
    }
    
    public static boolean isValidDni(String dni) {
        if (dni == null || dni.length() != 9) {
            return false;
        }
        
        // Verificar formato: 8 dígitos + 1 letra
        String numbers = dni.substring(0, 8);
        char letter = dni.charAt(8);
        
        // Verificar que los primeros 8 caracteres son dígitos
        try {
            int number = Integer.parseInt(numbers);
            if (number < 10000000 || number > 99999999) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        
        // Verificar que el último carácter es una letra
        if (!Character.isLetter(letter)) {
            return false;
        }
        
        // Calcular la letra correcta
        int number = Integer.parseInt(numbers);
        char correctLetter = DNI_LETTERS.charAt(number % 23);
        
        return letter == correctLetter;
    }
    
    public static String formatDni(String dni) {
        if (dni == null) return "";
        
        // Eliminar espacios y convertir a mayúsculas
        String cleanDni = dni.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        
        // Si tiene 8 dígitos, añadir la letra calculada
        if (cleanDni.matches("\\d{8}")) {
            try {
                int number = Integer.parseInt(cleanDni);
                char letter = DNI_LETTERS.charAt(number % 23);
                return cleanDni + letter;
            } catch (NumberFormatException e) {
                return cleanDni;
            }
        }
        
        return cleanDni;
    }
}
