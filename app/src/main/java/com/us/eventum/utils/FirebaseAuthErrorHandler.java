package com.us.eventum.utils;

import com.google.firebase.auth.FirebaseAuthException;

/**
 * Clase para manejar los errores de Firebase Authentication de forma centralizada
 * Convierte los códigos de error técnicos de Firebase en mensajes amigables para el usuario
 * 
 * CÓDIGOS VERIFICADOS:
 * - ERROR_INVALID_CREDENTIAL: Credenciales inválidas
 * - ERROR_USER_NOT_FOUND: Usuario no encontrado
 * - ERROR_EMAIL_ALREADY_IN_USE: Email ya en uso
 * - ERROR_WEAK_PASSWORD: Contraseña débil
 * - ERROR_INVALID_EMAIL: Email inválido
 * - ERROR_TOO_MANY_REQUESTS: Demasiados intentos
 * - ERROR_NETWORK_REQUEST_FAILED: Error de red
 * - ERROR_USER_DISABLED: Usuario deshabilitado
 * - ERROR_OPERATION_NOT_ALLOWED: Operación no permitida
 * - ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL: Cuenta existe con otro método
 */
public class FirebaseAuthErrorHandler {

    /**
     * Obtener mensaje de error amigable para el usuario basado en la excepción de Firebase Auth
      * @param exception La excepción que ocurrió durante la autenticación
     * @return Mensaje de error en español que el usuario puede entender
     */
    public static String getErrorMessage(Exception exception) {
        if (exception == null) {
            return "Error desconocido de autenticación";
        }

        // Si es un error específico de Firebase Auth, lo procesamos
        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authException = (FirebaseAuthException) exception;
            String errorCode = authException.getErrorCode();
            return getErrorMessageByCode(errorCode);
        }

        // Si no es un error de Firebase Auth, mostramos un mensaje genérico
        return "Error de autenticación. Intenta nuevamente";
    }

    /**
     * Traduce el código de error técnico de Firebase en un mensaje amigable para el usuario
     * @param errorCode El código de error que devuelve Firebase
     * @return Mensaje de error en español que explica qué pasó y qué puede hacer el usuario
     */
    private static String getErrorMessageByCode(String errorCode) {
        switch (errorCode) {
            // Errores relacionados con email y contraseña
            case "ERROR_INVALID_EMAIL":
                return "El formato del email no es válido";
            case "ERROR_EMAIL_ALREADY_IN_USE":
                return "Este email ya está registrado";
            case "ERROR_USER_NOT_FOUND":
                return "No existe una cuenta con este email";
            case "ERROR_WRONG_PASSWORD":
                return "La contraseña es incorrecta";
            case "ERROR_WEAK_PASSWORD":
                return "La contraseña es demasiado débil (mínimo 6 caracteres)";
            case "ERROR_USER_DISABLED":
                return "Tu cuenta ha sido deshabilitada";
            case "ERROR_OPERATION_NOT_ALLOWED":
                return "El método de autenticación está deshabilitado";

            // Errores de credenciales y tokens
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                return "Ya existe una cuenta con ese email pero con otro método de acceso";
            case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                return "Estas credenciales ya están vinculadas a otra cuenta";
            case "ERROR_INVALID_CREDENTIAL":
                return "La credencial no es válida o ha caducado";
            case "ERROR_USER_TOKEN_EXPIRED":
                return "La sesión ha expirado. Inicia sesión de nuevo";
            case "ERROR_INVALID_USER_TOKEN":
                return "La sesión no es válida. Intenta iniciar sesión otra vez";

            // Errores de seguridad y sesión
            case "ERROR_REQUIRES_RECENT_LOGIN":
                return "Debes iniciar sesión nuevamente para realizar esta acción";
            case "ERROR_USER_MISMATCH":
                return "Las credenciales no corresponden al usuario actual";

            // Errores de red y servidor
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "Error de red. Revisa tu conexión a internet";
            case "ERROR_TOO_MANY_REQUESTS":
                return "Demasiados intentos. Inténtalo más tarde";
            case "ERROR_INTERNAL_ERROR":
                return "Error interno. Inténtalo más tarde";

            // Errores de tokens personalizados
            case "ERROR_INVALID_CUSTOM_TOKEN":
                return "El token personalizado no es válido";
            case "ERROR_CUSTOM_TOKEN_MISMATCH":
                return "El token personalizado corresponde a otro proyecto";

            // Errores de verificación de email
            case "ERROR_INVALID_VERIFICATION_CODE":
                return "Código de verificación inválido";
            case "ERROR_INVALID_VERIFICATION_ID":
                return "ID de verificación inválido";
            case "ERROR_MISSING_VERIFICATION_CODE":
                return "Código de verificación requerido";
            case "ERROR_MISSING_VERIFICATION_ID":
                return "ID de verificación requerido";

            // Errores de restablecimiento de contraseña
            case "ERROR_INVALID_ACTION_CODE":
                return "Código de acción inválido o expirado";
            case "ERROR_EXPIRED_ACTION_CODE":
                return "El código ha expirado. Solicita uno nuevo";
            case "ERROR_INVALID_CONTINUE_URI":
                return "URL de continuación inválida";
            case "ERROR_MISSING_CONTINUE_URI":
                return "URL de continuación requerida";

            // Errores de cuota y límites
            case "ERROR_QUOTA_EXCEEDED":
                return "Se ha excedido la cuota. Intenta más tarde";
            case "ERROR_APP_NOT_AUTHORIZED":
                return "La aplicación no está autorizada para usar Firebase Auth";
            case "ERROR_KEYCHAIN_ERROR":
                return "Error en el llavero del dispositivo";

            // Errores de configuración
            case "ERROR_INVALID_API_KEY":
                return "Clave API inválida";

            // Errores de captcha y teléfono
            case "ERROR_CAPTCHA_CHECK_FAILED":
                return "Verificación de captcha fallida";
            case "ERROR_INVALID_PHONE_NUMBER":
                return "Número de teléfono inválido";
            case "ERROR_MISSING_PHONE_NUMBER":
                return "Número de teléfono requerido";

            // Errores de proveedores de autenticación
            case "ERROR_PROVIDER_ALREADY_LINKED":
                return "Este proveedor ya está vinculado a la cuenta";
            case "ERROR_NO_SUCH_PROVIDER":
                return "Proveedor no encontrado";

            // Si no reconocemos el código de error, mostramos un mensaje genérico
            default:
                return "Error de autenticación. Intenta nuevamente";
        }
    }
}