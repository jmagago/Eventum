package com.us.eventum.utils;

import androidx.annotation.NonNull;

import com.us.eventum.data.models.Event;

import java.util.Date;
import java.util.Objects;

/**
 * Fusiona datos de {@link Event} para UI en tiempo real sin duplicar lógica entre lista y panel.
 */
public final class EventUiMerger {

    private EventUiMerger() {
    }

    /** Campos persistidos en Firestore (documento {@code events}). */
    public static void copyDocumentFields(@NonNull Event target, @NonNull Event source) {
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setDate(source.getDate());
        target.setLocation(source.getLocation());
        target.setUserId(source.getUserId());
        target.setMaxParticipants(source.getMaxParticipants());
        target.setEventType(source.getEventType());
        target.setPrivateEvent(source.getPrivateEvent());
        target.setPrivateAccessCode(source.getPrivateAccessCode());
        target.setRequiresParentalAuth(source.getRequiresParentalAuth());
    }

    /** Estado derivado de inscripciones y lista de espera (no persistido en {@code events}). */
    public static void copyRegistrationUiState(@NonNull Event target, @NonNull Event source) {
        target.setCurrentUserJoined(source.isCurrentUserJoined());
        target.setCurrentUserScannedQR(source.isCurrentUserScannedQR());
        target.setCurrentUserWaitlisted(source.isCurrentUserWaitlisted());
        target.setCurrentUserWaitlistOffered(source.isCurrentUserWaitlistOffered());
        target.setCurrentUserWaitlistPosition(source.getCurrentUserWaitlistPosition());
        target.setWaitlistOfferExpiresAt(source.getWaitlistOfferExpiresAt());
        target.setWaitlistCount(source.getWaitlistCount());
        if (source.getCurrentParticipants() >= 0) {
            target.setCurrentParticipants(source.getCurrentParticipants());
        }
    }

    /** Sincroniza panel abierto o modelo local con el evento de la lista en tiempo real. */
    public static void syncAllExceptImage(@NonNull Event target, @NonNull Event source) {
        copyDocumentFields(target, source);
        copyRegistrationUiState(target, source);
    }

    /** Para abrir panel: datos remotos + estado local de la lista (inscripción, aforo en UI). */
    @NonNull
    public static Event mergeRemoteWithListState(@NonNull Event remote, @NonNull Event fromList) {
        copyRegistrationUiState(remote, fromList);
        return remote;
    }

    public static boolean hasDocumentFieldsChanged(@NonNull Event before, @NonNull Event after) {
        return !Objects.equals(before.getTitle(), after.getTitle())
                || !Objects.equals(before.getDescription(), after.getDescription())
                || !datesEqual(before.getDate(), after.getDate())
                || !Objects.equals(before.getLocation(), after.getLocation())
                || before.getMaxParticipants() != after.getMaxParticipants()
                || !Objects.equals(before.getEventType(), after.getEventType())
                || before.getPrivateEvent() != after.getPrivateEvent()
                || before.getRequiresParentalAuth() != after.getRequiresParentalAuth();
    }

    public static boolean hasRegistrationUiChanged(@NonNull Event before, @NonNull Event after) {
        return before.getCurrentParticipants() != after.getCurrentParticipants()
                || before.isCurrentUserJoined() != after.isCurrentUserJoined()
                || before.isCurrentUserScannedQR() != after.isCurrentUserScannedQR()
                || before.isCurrentUserWaitlisted() != after.isCurrentUserWaitlisted()
                || before.isCurrentUserWaitlistOffered() != after.isCurrentUserWaitlistOffered()
                || before.getCurrentUserWaitlistPosition() != after.getCurrentUserWaitlistPosition()
                || before.getWaitlistCount() != after.getWaitlistCount()
                || !datesEqual(before.getWaitlistOfferExpiresAt(), after.getWaitlistOfferExpiresAt());
    }

    private static boolean datesEqual(Date a, Date b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getTime() == b.getTime();
    }
}
