package com.us.eventum.utils;

import java.util.Locale;

/** Utilidades de localización para formateo en español. */
public final class LocaleUtils {

    private LocaleUtils() {
    }

    public static Locale spanish() {
        return Locale.forLanguageTag("es-ES");
    }

    public static String format(String pattern, Object... args) {
        return String.format(spanish(), pattern, args);
    }

    public static String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(spanish()) + text.substring(1);
    }
}
