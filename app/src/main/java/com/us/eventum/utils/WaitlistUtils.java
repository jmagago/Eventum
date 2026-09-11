package com.us.eventum.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.us.eventum.data.models.WaitlistToEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class WaitlistUtils {

    public static final long OFFER_DURATION_MS = 60L * 60L * 1000L;

    private WaitlistUtils() {
    }

    public static int countValidOffers(@Nullable List<WaitlistToEvent> entries) {
        if (entries == null) {
            return 0;
        }
        int count = 0;
        long now = System.currentTimeMillis();
        for (WaitlistToEvent entry : entries) {
            if (entry != null && isValidOffer(entry, now)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isValidOffer(@Nullable WaitlistToEvent entry, long nowMs) {
        if (entry == null || !WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
            return false;
        }
        Timestamp expiresAt = entry.getOfferExpiresAt();
        return expiresAt != null && expiresAt.toDate().getTime() > nowMs;
    }

    public static boolean isOfferExpired(@Nullable WaitlistToEvent entry, long nowMs) {
        if (entry == null || !WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
            return false;
        }
        Timestamp expiresAt = entry.getOfferExpiresAt();
        return expiresAt == null || expiresAt.toDate().getTime() <= nowMs;
    }

    @NonNull
    public static List<WaitlistToEvent> sortByJoinedAt(@Nullable List<WaitlistToEvent> entries) {
        List<WaitlistToEvent> sorted = new ArrayList<>();
        if (entries != null) {
            sorted.addAll(entries);
        }
        Collections.sort(sorted, Comparator.comparing(
                entry -> entry.getJoinedAt() != null ? entry.getJoinedAt() : new Timestamp(new Date(0)),
                Comparator.naturalOrder()));
        return sorted;
    }

    public static int computeWaitingPosition(@Nullable List<WaitlistToEvent> entries,
                                             @Nullable String userId) {
        if (userId == null || entries == null) {
            return 0;
        }
        int position = 0;
        for (WaitlistToEvent entry : sortByJoinedAt(entries)) {
            if (entry == null || !WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())) {
                continue;
            }
            position++;
            if (userId.equals(entry.getUserId())) {
                return position;
            }
        }
        return 0;
    }

    @Nullable
    public static WaitlistToEvent findActiveEntryForUser(@Nullable List<WaitlistToEvent> entries,
                                                         @Nullable String userId) {
        if (userId == null || entries == null) {
            return null;
        }
        WaitlistToEvent offered = null;
        WaitlistToEvent waiting = null;
        for (WaitlistToEvent entry : entries) {
            if (entry == null || !userId.equals(entry.getUserId()) || !entry.isActiveForUser()) {
                continue;
            }
            if (WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
                offered = entry;
            } else if (WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())) {
                waiting = entry;
            }
        }
        return offered != null ? offered : waiting;
    }

    @Nullable
    public static WaitlistToEvent findFirstWaiting(@Nullable List<WaitlistToEvent> entries) {
        for (WaitlistToEvent entry : sortByJoinedAt(entries)) {
            if (entry != null && WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())) {
                return entry;
            }
        }
        return null;
    }

    public static int countActiveWaitlist(@Nullable List<WaitlistToEvent> entries) {
        if (entries == null) {
            return 0;
        }
        int count = 0;
        for (WaitlistToEvent entry : entries) {
            if (entry != null && entry.isActiveForUser()) {
                count++;
            }
        }
        return count;
    }
}
