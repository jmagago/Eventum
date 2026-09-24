package com.us.eventum.core.utils;

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

    @NonNull
    public static List<WaitlistToEvent> findNewestValidOffers(@Nullable List<WaitlistToEvent> entries,
                                                              int limit) {
        List<WaitlistToEvent> offered = new ArrayList<>();
        if (limit <= 0) {
            return offered;
        }
        long now = System.currentTimeMillis();
        if (entries != null) {
            for (WaitlistToEvent entry : entries) {
                if (isValidOffer(entry, now)) {
                    offered.add(entry);
                }
            }
        }
        Collections.sort(offered, (a, b) -> {
            long aTime = a.getOfferedAt() != null ? a.getOfferedAt().toDate().getTime() : 0L;
            long bTime = b.getOfferedAt() != null ? b.getOfferedAt().toDate().getTime() : 0L;
            return Long.compare(bTime, aTime);
        });
        if (offered.size() > limit) {
            return new ArrayList<>(offered.subList(0, limit));
        }
        return offered;
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
