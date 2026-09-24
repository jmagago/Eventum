package com.us.eventum.core.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.us.eventum.core.config.AppConfig;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class AgeUtils {

    public static final String BIRTH_DATE_PATTERN = "dd/MM/yyyy";

    private AgeUtils() {
    }

    public static int calculateAge(@NonNull Date birthDate, @NonNull Date referenceDate) {
        Calendar birth = Calendar.getInstance();
        birth.setTime(birthDate);
        Calendar reference = Calendar.getInstance();
        reference.setTime(referenceDate);
        int age = reference.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        if (reference.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
    }

    public static int calculateAgeToday(@NonNull Date birthDate) {
        return calculateAge(birthDate, new Date());
    }

    @Nullable
    public static Date parseBirthDate(@Nullable String birthDateText) {
        if (birthDateText == null || birthDateText.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(BIRTH_DATE_PATTERN, LocaleUtils.spanish());
            sdf.setLenient(false);
            return sdf.parse(birthDateText.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    public static boolean isAtLeastAge(@NonNull Date birthDate, int minAge, @NonNull Date onDate) {
        return calculateAge(birthDate, onDate) >= minAge;
    }

    public static boolean isAttendeeAgeValid(@NonNull Date birthDate, @NonNull Date onDate) {
        return isAtLeastAge(birthDate, AppConfig.ATTENDEE_MIN_AGE, onDate);
    }

    public static boolean isOrganizerAgeValid(@NonNull Date birthDate) {
        return isAtLeastAge(birthDate, AppConfig.ORGANIZER_MIN_AGE, new Date());
    }

    public static boolean requiresParentalAuthOnEventDay(@NonNull Date birthDate, @NonNull Date eventDate) {
        return calculateAge(birthDate, eventDate) < AppConfig.ADULT_AGE;
    }

    public static long getMaxSelectableBirthDateMillis(int minAge) {
        Calendar max = Calendar.getInstance();
        max.add(Calendar.YEAR, -minAge);
        return max.getTimeInMillis();
    }
}
