package com.us.eventum.utils;

/**
 * Resultado del check-in por QR en un evento.
 */
public enum QrCheckInResult {
    VALID,
    ALREADY_USED,
    NOT_REGISTERED,
    WRONG_EVENT,
    INVALID_TOKEN,
    EXPIRED_TOKEN
}
