package com.us.eventum.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.firebase.auth.FirebaseAuthException;
import com.us.eventum.R;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte códigos de error de Firebase Authentication en mensajes claros en español.
 * Soporta códigos modernos (p. ej. INVALID_LOGIN_CREDENTIALS con protección anti-enumeración).
 */
public final class FirebaseAuthErrorHandler {

    private static final Pattern AUTH_SLASH_CODE = Pattern.compile(
            "auth/([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_CODE = Pattern.compile(
            "\\[\\s*([A-Z0-9_\\-]+)\\s*\\]");

    public enum AuthErrorField {
        EMAIL,
        PASSWORD,
        GENERAL
    }

    private FirebaseAuthErrorHandler() {
    }

    @NonNull
    public static String getErrorMessage(@Nullable Exception exception) {
        return getErrorMessage(null, exception);
    }

    @NonNull
    public static String getErrorMessage(@Nullable Context context, @Nullable Exception exception) {
        if (exception == null) {
            return resolveString(context, R.string.auth_error_unknown, null);
        }

        FirebaseAuthException authException = findAuthException(exception);
        if (authException != null) {
            return getErrorMessageByCode(context, authException.getErrorCode());
        }

        String message = exception.getMessage();
        if (message != null && !message.isEmpty()) {
            String fromMessage = getErrorMessageByCode(context, extractCodeFromMessage(message));
            if (!isGenericMessage(context, fromMessage)) {
                return fromMessage;
            }
        }

        return resolveString(context, R.string.auth_error_generic, null);
    }

    @NonNull
    public static AuthErrorField getDisplayField(@Nullable Exception exception) {
        FirebaseAuthException authException = findAuthException(exception);
        if (authException != null) {
            return getDisplayFieldByCode(authException.getErrorCode());
        }
        String message = exception != null ? exception.getMessage() : null;
        if (message != null) {
            return getDisplayFieldByCode(extractCodeFromMessage(message));
        }
        return AuthErrorField.GENERAL;
    }

    /**
     * Indica en qué campo del formulario de login mostrar el error (email, contraseña o toast general).
     */
    @NonNull
    public static AuthErrorField getDisplayFieldForMessage(@Nullable String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return AuthErrorField.GENERAL;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("contraseña")) {
            return AuthErrorField.PASSWORD;
        }
        if (lower.contains("correo")
                || lower.contains("email")
                || lower.contains("cuenta")
                || lower.contains("registrado")
                || lower.contains("verificar")) {
            return AuthErrorField.EMAIL;
        }
        return AuthErrorField.GENERAL;
    }

    @NonNull
    public static String getVerificationEmailError(@Nullable Context context, @Nullable Exception exception) {
        String detail = getErrorMessage(context, exception);
        if (context != null) {
            return context.getString(R.string.auth_error_verification_email_failed, detail);
        }
        return "No se pudo enviar el correo de verificación. " + detail;
    }

    @NonNull
    public static String getNotAuthenticatedMessage(@Nullable Context context) {
        return resolveString(context, R.string.auth_error_not_authenticated, null);
    }

    @NonNull
    public static String getUserNotInDatabaseMessage(@Nullable Context context) {
        return resolveString(context, R.string.auth_error_user_not_in_database, null);
    }

    @NonNull
    public static String getNoUserForVerificationMessage(@Nullable Context context) {
        return resolveString(context, R.string.auth_error_no_user_for_verification, null);
    }

    @NonNull
    public static String getNullFirebaseUserMessage(@Nullable Context context) {
        return resolveString(context, R.string.auth_error_null_firebase_user, null);
    }

    @NonNull
    public static String getRepoNotInitializedMessage(@Nullable Context context) {
        return resolveString(context, R.string.auth_error_repo_not_initialized, null);
    }

    @Nullable
    private static FirebaseAuthException findAuthException(@Nullable Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof FirebaseAuthException) {
                return (FirebaseAuthException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String extractCodeFromMessage(String message) {
        Matcher slashMatcher = AUTH_SLASH_CODE.matcher(message);
        if (slashMatcher.find()) {
            return slashMatcher.group(1);
        }

        Matcher bracketMatcher = BRACKET_CODE.matcher(message);
        if (bracketMatcher.find()) {
            return bracketMatcher.group(1);
        }

        String upper = message.toUpperCase(Locale.ROOT);
        String[] knownCodes = {
                "INVALID_LOGIN_CREDENTIALS", "INVALID-LOGIN-CREDENTIALS",
                "INVALID_CREDENTIAL", "INVALID-CREDENTIAL",
                "INVALID_PASSWORD", "INVALID-PASSWORD",
                "EMAIL_NOT_VERIFIED", "EMAIL-NOT-VERIFIED",
                "USER_NOT_FOUND", "WRONG_PASSWORD",
                "TOO_MANY_REQUESTS", "NETWORK_REQUEST_FAILED",
                "CAPTCHA_CHECK_FAILED", "MISSING_RECAPTCHA_TOKEN",
                "INVALID_RECAPTCHA_TOKEN", "RECAPTCHA_NOT_ENABLED",
                "REQUIRES_RECENT_LOGIN", "SESSION_EXPIRED", "CODE_EXPIRED"
        };
        for (String code : knownCodes) {
            if (upper.contains(code.replace('-', '_'))) {
                return code;
            }
        }
        return message;
    }

    private static boolean isGenericMessage(@Nullable Context context, String message) {
        String generic = resolveString(context, R.string.auth_error_generic, null);
        return generic.equals(message);
    }

    private static String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return "";
        }
        String normalized = errorCode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("ERROR_")) {
            normalized = normalized.substring("ERROR_".length());
        }
        return normalized;
    }

    @NonNull
    private static AuthErrorField getDisplayFieldByCode(String errorCode) {
        String normalized = normalizeErrorCode(errorCode);
        switch (normalized) {
            case "INVALID_EMAIL":
            case "MISSING_EMAIL":
            case "USER_NOT_FOUND":
            case "EMAIL_ALREADY_IN_USE":
            case "EMAIL_NOT_VERIFIED":
            case "ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
            case "EMAIL_EXISTS":
                return AuthErrorField.EMAIL;
            case "WRONG_PASSWORD":
            case "MISSING_PASSWORD":
            case "INVALID_LOGIN_CREDENTIALS":
            case "INVALID_CREDENTIAL":
            case "INVALID_PASSWORD":
            case "WEAK_PASSWORD":
            case "REJECTED_CREDENTIAL":
                return AuthErrorField.PASSWORD;
            default:
                if (normalized.contains("INVALID") && normalized.contains("CREDENTIAL")) {
                    return AuthErrorField.PASSWORD;
                }
                return AuthErrorField.GENERAL;
        }
    }

    @NonNull
    private static String getErrorMessageByCode(@Nullable Context context, String errorCode) {
        String normalized = normalizeErrorCode(errorCode);
        if (normalized.isEmpty()) {
            return resolveString(context, R.string.auth_error_generic, null);
        }

        @StringRes int stringRes;
        switch (normalized) {
            case "INVALID_EMAIL":
                stringRes = R.string.auth_error_invalid_email;
                break;
            case "MISSING_EMAIL":
                stringRes = R.string.auth_error_missing_email;
                break;
            case "EMAIL_ALREADY_IN_USE":
            case "EMAIL_EXISTS":
                stringRes = R.string.auth_error_email_in_use;
                break;
            case "USER_NOT_FOUND":
                stringRes = R.string.auth_error_user_not_found;
                break;
            case "WRONG_PASSWORD":
                stringRes = R.string.auth_error_wrong_password;
                break;
            case "MISSING_PASSWORD":
                stringRes = R.string.auth_error_missing_password;
                break;
            case "INVALID_LOGIN_CREDENTIALS":
            case "INVALID_CREDENTIAL":
            case "INVALID_PASSWORD":
            case "REJECTED_CREDENTIAL":
                stringRes = R.string.auth_error_invalid_credentials;
                break;
            case "WEAK_PASSWORD":
                stringRes = R.string.auth_error_weak_password;
                break;
            case "USER_DISABLED":
                stringRes = R.string.auth_error_user_disabled;
                break;
            case "EMAIL_NOT_VERIFIED":
                stringRes = R.string.auth_error_email_not_verified;
                break;
            case "OPERATION_NOT_ALLOWED":
                stringRes = R.string.auth_error_operation_not_allowed;
                break;
            case "ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                stringRes = R.string.auth_error_account_exists_different_credential;
                break;
            case "CREDENTIAL_ALREADY_IN_USE":
                stringRes = R.string.auth_error_credential_in_use;
                break;
            case "PROVIDER_ALREADY_LINKED":
                stringRes = R.string.auth_error_provider_already_linked;
                break;
            case "USER_TOKEN_EXPIRED":
                stringRes = R.string.auth_error_session_expired;
                break;
            case "INVALID_USER_TOKEN":
            case "INVALID_ID_TOKEN":
                stringRes = R.string.auth_error_invalid_session;
                break;
            case "REQUIRES_RECENT_LOGIN":
            case "CREDENTIAL_TOO_OLD_LOGIN_AGAIN":
                stringRes = R.string.auth_error_requires_recent_login;
                break;
            case "USER_MISMATCH":
                stringRes = R.string.auth_error_user_mismatch;
                break;
            case "SECOND_FACTOR_REQUIRED":
            case "MULTI_FACTOR_AUTH_REQUIRED":
                stringRes = R.string.auth_error_second_factor_required;
                break;
            case "MISSING_MULTI_FACTOR_SESSION":
                stringRes = R.string.auth_error_missing_mfa_session;
                break;
            case "MULTI_FACTOR_INFO_NOT_FOUND":
                stringRes = R.string.auth_error_mfa_not_found;
                break;
            case "NETWORK_REQUEST_FAILED":
                stringRes = R.string.auth_error_network;
                break;
            case "TOO_MANY_REQUESTS":
                stringRes = R.string.auth_error_too_many_requests;
                break;
            case "INTERNAL_ERROR":
                stringRes = R.string.auth_error_internal;
                break;
            case "INVALID_CUSTOM_TOKEN":
                stringRes = R.string.auth_error_invalid_token;
                break;
            case "CUSTOM_TOKEN_MISMATCH":
                stringRes = R.string.auth_error_token_mismatch;
                break;
            case "INVALID_VERIFICATION_CODE":
                stringRes = R.string.auth_error_invalid_verification_code;
                break;
            case "INVALID_VERIFICATION_ID":
                stringRes = R.string.auth_error_invalid_verification_id;
                break;
            case "MISSING_VERIFICATION_CODE":
                stringRes = R.string.auth_error_missing_verification_code;
                break;
            case "MISSING_VERIFICATION_ID":
                stringRes = R.string.auth_error_missing_verification_id;
                break;
            case "INVALID_ACTION_CODE":
                stringRes = R.string.auth_error_invalid_action_code;
                break;
            case "EXPIRED_ACTION_CODE":
                stringRes = R.string.auth_error_expired_action_code;
                break;
            case "SESSION_EXPIRED":
            case "CODE_EXPIRED":
                stringRes = R.string.auth_error_code_expired;
                break;
            case "QUOTA_EXCEEDED":
                stringRes = R.string.auth_error_quota_exceeded;
                break;
            case "APP_NOT_AUTHORIZED":
                stringRes = R.string.auth_error_app_not_authorized;
                break;
            case "UNAUTHORIZED_DOMAIN":
                stringRes = R.string.auth_error_unauthorized_domain;
                break;
            case "CAPTCHA_CHECK_FAILED":
                stringRes = R.string.auth_error_captcha_failed;
                break;
            case "MISSING_RECAPTCHA_TOKEN":
                stringRes = R.string.auth_error_missing_recaptcha_token;
                break;
            case "INVALID_RECAPTCHA_TOKEN":
                stringRes = R.string.auth_error_invalid_recaptcha_token;
                break;
            case "RECAPTCHA_NOT_ENABLED":
                stringRes = R.string.auth_error_recaptcha_not_enabled;
                break;
            case "MISSING_CLIENT_IDENTIFIER":
                stringRes = R.string.auth_error_missing_client_identifier;
                break;
            case "MISSING_PHONE_NUMBER":
                stringRes = R.string.auth_error_missing_phone_number;
                break;
            case "INVALID_PHONE_NUMBER":
                stringRes = R.string.auth_error_invalid_phone_number;
                break;
            case "MISSING_ACTIVITY_FOR_RECAPTCHA":
                stringRes = R.string.auth_error_missing_activity_recaptcha;
                break;
            default:
                if (normalized.contains("INVALID") && normalized.contains("CREDENTIAL")) {
                    stringRes = R.string.auth_error_invalid_credentials;
                } else if (normalized.contains("RECAPTCHA") || normalized.contains("CAPTCHA")) {
                    stringRes = R.string.auth_error_captcha_failed;
                } else if (normalized.contains("NETWORK")) {
                    stringRes = R.string.auth_error_network;
                } else {
                    stringRes = R.string.auth_error_generic;
                }
                break;
        }
        return resolveString(context, stringRes, null);
    }

    @NonNull
    private static String resolveString(@Nullable Context context, @StringRes int resId,
                                        @Nullable String fallback) {
        if (context != null) {
            return context.getString(resId);
        }
        if (fallback != null) {
            return fallback;
        }
        return "Error de autenticación. Intenta nuevamente";
    }
}
