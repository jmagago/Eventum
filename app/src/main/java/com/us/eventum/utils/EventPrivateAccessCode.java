package com.us.eventum.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.us.eventum.data.models.Event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class EventPrivateAccessCode {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    private static final String ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private EventPrivateAccessCode() {
    }

    @Nullable
    public static String normalize(@Nullable String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    public static boolean isValidFormat(@Nullable String code) {
        return code != null && CODE_PATTERN.matcher(code.trim()).matches();
    }

    @NonNull
    public static String generateRandom() {
        int length = MIN_LENGTH + RANDOM.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }

    public static boolean matches(@Nullable String entered, @Nullable String expected) {
        String trimmedExpected = normalize(expected);
        if (trimmedExpected == null) {
            return false;
        }
        String trimmedEntered = normalize(entered);
        return trimmedExpected.equals(trimmedEntered);
    }

    @NonNull
    public static String hash(@Nullable String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public static boolean hasHash(@Nullable String hash) {
        return hash != null && !hash.trim().isEmpty();
    }

    public static boolean isConfigured(@Nullable Event event) {
        if (event == null) {
            return false;
        }
        return normalize(event.getPrivateAccessCode()) != null
                || hasHash(event.getPrivateAccessCodeHash());
    }

    public static boolean matchesEvent(@Nullable String entered, @Nullable Event event) {
        if (event == null || !isValidFormat(entered)) {
            return false;
        }
        if (matches(entered, event.getPrivateAccessCode())) {
            return true;
        }
        String expectedHash = event.getPrivateAccessCodeHash();
        return hasHash(expectedHash) && expectedHash.equalsIgnoreCase(hash(entered));
    }
}
