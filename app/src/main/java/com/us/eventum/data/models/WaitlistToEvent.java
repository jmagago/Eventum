package com.us.eventum.data.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class WaitlistToEvent {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_OFFERED = "OFFERED";
    public static final String STATUS_PROMOTED = "PROMOTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String id;
    private String eventId;
    private String userId;
    private String status;
    private Timestamp joinedAt;
    private Timestamp offeredAt;
    private Timestamp offerExpiresAt;
    private String parentalAuthUrl;

    public WaitlistToEvent() {
    }

    public WaitlistToEvent(String eventId, String userId, String status) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
        this.joinedAt = Timestamp.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("eventId")
    public String getEventId() {
        return eventId;
    }

    @PropertyName("eventId")
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @PropertyName("userId")
    public String getUserId() {
        return userId;
    }

    @PropertyName("userId")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @PropertyName("status")
    public String getStatus() {
        return status;
    }

    @PropertyName("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @PropertyName("joinedAt")
    public Timestamp getJoinedAt() {
        return joinedAt;
    }

    @PropertyName("joinedAt")
    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }

    @PropertyName("offeredAt")
    public Timestamp getOfferedAt() {
        return offeredAt;
    }

    @PropertyName("offeredAt")
    public void setOfferedAt(Timestamp offeredAt) {
        this.offeredAt = offeredAt;
    }

    @PropertyName("offerExpiresAt")
    public Timestamp getOfferExpiresAt() {
        return offerExpiresAt;
    }

    @PropertyName("offerExpiresAt")
    public void setOfferExpiresAt(Timestamp offerExpiresAt) {
        this.offerExpiresAt = offerExpiresAt;
    }

    @PropertyName("parentalAuthUrl")
    public String getParentalAuthUrl() {
        return parentalAuthUrl;
    }

    @PropertyName("parentalAuthUrl")
    public void setParentalAuthUrl(String parentalAuthUrl) {
        this.parentalAuthUrl = parentalAuthUrl;
    }

    public boolean isActiveForUser() {
        if (STATUS_WAITING.equals(status)) {
            return true;
        }
        if (!STATUS_OFFERED.equals(status)) {
            return false;
        }
        if (offerExpiresAt == null || offerExpiresAt.toDate() == null) {
            return false;
        }
        return offerExpiresAt.toDate().getTime() > System.currentTimeMillis();
    }
}
