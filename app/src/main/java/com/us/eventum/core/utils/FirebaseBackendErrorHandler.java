package com.us.eventum.core.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.StorageException;
import com.us.eventum.R;

/**
 * Mensajes claros en español para Firestore, Storage y otros servicios Firebase (no Auth).
 */
public final class FirebaseBackendErrorHandler {

    private FirebaseBackendErrorHandler() {
    }

    @NonNull
    public static String getErrorMessage(@Nullable Exception exception) {
        return getErrorMessage(null, exception, R.string.backend_error_generic);
    }

    @NonNull
    public static String getErrorMessage(@Nullable Exception exception, @StringRes int operationFallbackRes) {
        return getErrorMessage(null, exception, operationFallbackRes);
    }

    @NonNull
    public static String getErrorMessage(@Nullable Context context, @Nullable Exception exception,
                                         @StringRes int operationFallbackRes) {
        if (exception == null) {
            return resolveString(context, operationFallbackRes, null);
        }

        if (findAuthException(exception) != null) {
            return FirebaseAuthErrorHandler.getErrorMessage(context, exception);
        }

        FirebaseFirestoreException firestoreException = findFirestoreException(exception);
        if (firestoreException != null) {
            String mapped = getFirestoreMessage(context, firestoreException.getCode());
            if (!isGenericBackendMessage(context, mapped)) {
                return mapped;
            }
        }

        StorageException storageException = findStorageException(exception);
        if (storageException != null) {
            String mapped = getStorageMessage(context, storageException.getErrorCode());
            if (!isGenericBackendMessage(context, mapped)) {
                return mapped;
            }
        }

        if (exception instanceof FirebaseException) {
            return resolveString(context, R.string.backend_error_firebase, null);
        }

        return resolveString(context, operationFallbackRes, null);
    }

    @NonNull
    public static String getListenerErrorMessage(@Nullable Context context, @Nullable Exception exception,
                                                 @StringRes int operationFallbackRes) {
        return getErrorMessage(context, exception, operationFallbackRes);
    }

    @NonNull
    public static String getNotAuthenticatedMessage(@Nullable Context context) {
        return FirebaseAuthErrorHandler.getNotAuthenticatedMessage(context);
    }

    @NonNull
    public static String getInvalidEventIdMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_invalid_event_id, null);
    }

    @NonNull
    public static String getEventNotFoundMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_event_not_found, null);
    }

    @NonNull
    public static String getAttendeeNotFoundMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_attendee_not_found, null);
    }

    @NonNull
    public static String getOrganizerNotFoundMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_organizer_not_found, null);
    }

    @NonNull
    public static String getReadEventFailedMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_read_event, null);
    }

    @NonNull
    public static String getAlreadyRegisteredMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_already_registered, null);
    }

    @NonNull
    public static String getEventFullMessage(@Nullable Context context) {
        return resolveString(context, R.string.waitlist_event_full, "El evento no tiene plazas libres");
    }

    @NonNull
    public static String getEventCancelledMessage(@Nullable Context context) {
        return resolveString(context, R.string.event_cancelled_cannot_join, "Este evento ha sido cancelado");
    }

    @NonNull
    public static String getEventAlreadyCancelledMessage(@Nullable Context context) {
        return resolveString(context, R.string.event_already_cancelled, "Este evento ya está cancelado");
    }

    @NonNull
    public static String getWaitlistOfferInvalidMessage(@Nullable Context context) {
        return resolveString(context, R.string.waitlist_offer_no_longer_valid, "La plaza ya no está disponible");
    }

    @NonNull
    public static String getUsernameInUseMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_username_in_use, null);
    }

    @NonNull
    public static String getDniInUseMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_dni_in_use, null);
    }

    @NonNull
    public static String getIncompleteNotificationMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_incomplete_notification, null);
    }

    @NonNull
    public static String getIncompleteActivityMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_incomplete_activity, null);
    }

    @NonNull
    public static String getInvalidEventForActivityMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_invalid_event_for_activity, null);
    }

    @NonNull
    public static String getIncompleteImageDataMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_incomplete_image_data, null);
    }

    @NonNull
    public static String getProcessImageFailedMessage(@Nullable Context context) {
        return resolveString(context, R.string.backend_error_process_image, null);
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

    @Nullable
    private static FirebaseFirestoreException findFirestoreException(@Nullable Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof FirebaseFirestoreException) {
                return (FirebaseFirestoreException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    @Nullable
    private static StorageException findStorageException(@Nullable Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StorageException) {
                return (StorageException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    @NonNull
    private static String getFirestoreMessage(@Nullable Context context,
                                              @NonNull FirebaseFirestoreException.Code code) {
        @StringRes int stringRes;
        switch (code) {
            case PERMISSION_DENIED:
                stringRes = R.string.backend_error_permission_denied;
                break;
            case UNAUTHENTICATED:
                stringRes = R.string.backend_error_unauthenticated;
                break;
            case NOT_FOUND:
                stringRes = R.string.backend_error_not_found;
                break;
            case ALREADY_EXISTS:
                stringRes = R.string.backend_error_already_exists;
                break;
            case UNAVAILABLE:
            case DEADLINE_EXCEEDED:
                stringRes = R.string.backend_error_unavailable;
                break;
            case CANCELLED:
                stringRes = R.string.backend_error_cancelled;
                break;
            case RESOURCE_EXHAUSTED:
                stringRes = R.string.backend_error_quota;
                break;
            case FAILED_PRECONDITION:
            case INVALID_ARGUMENT:
            case OUT_OF_RANGE:
                stringRes = R.string.backend_error_invalid_request;
                break;
            case ABORTED:
                stringRes = R.string.backend_error_aborted;
                break;
            case DATA_LOSS:
            case INTERNAL:
            case UNKNOWN:
            default:
                stringRes = R.string.backend_error_internal;
                break;
        }
        return resolveString(context, stringRes, null);
    }

    @NonNull
    private static String getStorageMessage(@Nullable Context context, int errorCode) {
        @StringRes int stringRes;
        switch (errorCode) {
            case StorageException.ERROR_OBJECT_NOT_FOUND:
                stringRes = R.string.backend_error_storage_not_found;
                break;
            case StorageException.ERROR_BUCKET_NOT_FOUND:
            case StorageException.ERROR_PROJECT_NOT_FOUND:
                stringRes = R.string.backend_error_storage_config;
                break;
            case StorageException.ERROR_NOT_AUTHENTICATED:
                stringRes = R.string.backend_error_unauthenticated;
                break;
            case StorageException.ERROR_NOT_AUTHORIZED:
                stringRes = R.string.backend_error_permission_denied;
                break;
            case StorageException.ERROR_QUOTA_EXCEEDED:
                stringRes = R.string.backend_error_quota;
                break;
            case StorageException.ERROR_RETRY_LIMIT_EXCEEDED:
            case StorageException.ERROR_CANCELED:
                stringRes = R.string.backend_error_cancelled;
                break;
            case StorageException.ERROR_INVALID_CHECKSUM:
                stringRes = R.string.backend_error_storage_corrupt;
                break;
            case StorageException.ERROR_UNKNOWN:
            default:
                stringRes = R.string.backend_error_storage;
                break;
        }
        return resolveString(context, stringRes, null);
    }

    private static boolean isGenericBackendMessage(@Nullable Context context, @NonNull String message) {
        String generic = resolveString(context, R.string.backend_error_generic, null);
        String internal = resolveString(context, R.string.backend_error_internal, null);
        return message.equals(generic) || message.equals(internal);
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
        return fallbackForResId(resId);
    }

    @NonNull
    private static String fallbackForResId(@StringRes int resId) {
        if (resId == R.string.backend_error_permission_denied) {
            return "No tienes permiso para realizar esta operación";
        }
        if (resId == R.string.backend_error_unauthenticated) {
            return "Debes iniciar sesión para continuar";
        }
        if (resId == R.string.backend_error_unavailable) {
            return "Error de red. Comprueba tu conexión e inténtalo de nuevo";
        }
        if (resId == R.string.backend_error_not_found) {
            return "No se encontraron los datos solicitados";
        }
        if (resId == R.string.waitlist_event_full) {
            return "El evento no tiene plazas libres";
        }
        if (resId == R.string.waitlist_offer_no_longer_valid) {
            return "La plaza ya no está disponible";
        }
        return "No se pudo completar la operación. Inténtalo de nuevo";
    }
}
