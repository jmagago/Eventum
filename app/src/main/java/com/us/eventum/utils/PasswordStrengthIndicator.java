package com.us.eventum.utils;

import android.graphics.Color;
import android.widget.ProgressBar;
import android.widget.TextView;

public class PasswordStrengthIndicator {
    private static final String[] STRENGTH_LABELS = {
        "Muy débil",
        "Débil",
        "Media",
        "Fuerte",
        "Muy fuerte"
    };

    private static final int[] STRENGTH_COLORS = {
        Color.RED,                    // Muy débil
        Color.parseColor("#FF6600"),  // Débil (naranja)
        Color.YELLOW,                 // Media
        Color.GREEN,                  // Fuerte
        Color.BLUE                    // Muy fuerte
    };

    public static void updateStrengthIndicator(String password, ProgressBar progressBar, TextView strengthTextView) {
        int strength = PasswordValidator.calculateStrength(password);
        
        // Actualizar la barra de progreso (cada nivel es 20%)
        progressBar.setMax(100);
        progressBar.setProgress((strength + 1) * 20);
        
        // Actualizar el color y texto
        int color = getStrengthColor(strength);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        strengthTextView.setText(getStrengthText(strength));
        strengthTextView.setTextColor(color);
    }

    public static String getStrengthText(int strength) {
        if (strength < 0 || strength >= STRENGTH_LABELS.length) {
            return "Desconocida";
        }
        return STRENGTH_LABELS[strength];
    }

    public static int getStrengthColor(int strength) {
        if (strength < 0 || strength >= STRENGTH_COLORS.length) {
            return STRENGTH_COLORS[0];
        }
        return STRENGTH_COLORS[strength];
    }
} 