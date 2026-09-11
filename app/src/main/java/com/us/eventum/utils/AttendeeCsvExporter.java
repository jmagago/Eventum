package com.us.eventum.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.us.eventum.data.models.Attendee;

import java.text.SimpleDateFormat;
import java.util.List;

public final class AttendeeCsvExporter {

    private static final String LINE_SEPARATOR = "\r\n";

    private AttendeeCsvExporter() {
    }

    @NonNull
    public static String buildCsvContent(@NonNull List<Attendee> attendees) {
        StringBuilder sb = new StringBuilder();
        // Excel (Windows): sep=; en la primera línea + UTF-8 con BOM al escribir el archivo
        sb.append("sep=;").append(LINE_SEPARATOR);
        sb.append("DNI;Primer apellido;Segundo apellido;Nombre;Fecha de nacimiento;Teléfono;Email")
                .append(LINE_SEPARATOR);
        for (Attendee attendee : attendees) {
            sb.append(escapeCsvField(attendee.getDni())).append(';')
                    .append(escapeCsvField(attendee.getPrimerApellido())).append(';')
                    .append(escapeCsvField(attendee.getSegundoApellido())).append(';')
                    .append(escapeCsvField(attendee.getNombre())).append(';')
                    .append(escapeCsvField(formatBirthDate(attendee))).append(';')
                    .append(escapeCsvField(attendee.getPhone())).append(';')
                    .append(escapeCsvField(attendee.getEmail()))
                    .append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    @NonNull
    public static String suggestedFilename(@Nullable String eventTitle) {
        if (eventTitle != null && !eventTitle.trim().isEmpty()) {
            return sanitizeFilename(eventTitle.trim()) + ".csv";
        }
        return "asistentes.csv";
    }

    @NonNull
    private static String formatBirthDate(@NonNull Attendee attendee) {
        Timestamp birthDate = attendee.getFechaNacimiento();
        if (birthDate == null) {
            return "";
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", LocaleUtils.spanish());
        return dateFormat.format(birthDate.toDate());
    }

    @NonNull
    private static String sanitizeFilename(@NonNull String title) {
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) {
            return "asistentes";
        }
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        return sanitized;
    }

    @NonNull
    private static String escapeCsvField(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.contains(";") || trimmed.contains("\"") || trimmed.contains("\n") || trimmed.contains("\r")) {
            return "\"" + trimmed.replace("\"", "\"\"") + "\"";
        }
        return trimmed;
    }
}
