package com.us.eventum.core.utils;

import com.us.eventum.R;

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
                    AnimationUtils.showErrorWithAnimation(dniLayout, dniLayout.getContext().getString(R.string.dni_required));
                } else if (!isValidDni(dni)) {
                    AnimationUtils.showErrorWithAnimation(dniLayout, dniLayout.getContext().getString(R.string.dni_invalid_letter));
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
}
