package com.us.eventum.data.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

/**
 * Entrada del registro de actividad de un evento (inscripciones, verificaciones, etc.).
 */
public class EventActivityLog {

    public static final String TYPE_JOINED = "JOINED";
    public static final String TYPE_LEFT = "LEFT";
    public static final String TYPE_VERIFIED_QR = "VERIFIED_QR";
    public static final String TYPE_VERIFIED_MANUAL = "VERIFIED_MANUAL";
    public static final String TYPE_REMOVED = "REMOVED";
    public static final String TYPE_LIST_CLEARED = "LIST_CLEARED";
    public static final String TYPE_EVENT_UPDATED = "EVENT_UPDATED";
    public static final String TYPE_WAITLIST_JOINED = "WAITLIST_JOINED";
    public static final String TYPE_WAITLIST_LEFT = "WAITLIST_LEFT";
    public static final String TYPE_WAITLIST_OFFERED = "WAITLIST_OFFERED";
    public static final String TYPE_WAITLIST_OFFER_EXPIRED = "WAITLIST_OFFER_EXPIRED";
    public static final String TYPE_WAITLIST_PROMOTED = "WAITLIST_PROMOTED";

    private String id;
    private String eventId;
    private String type;
    private String subjectName;
    private String detail;
    private Timestamp createdAt;

    public EventActivityLog() {
    }

    public EventActivityLog(String eventId, String type, String subjectName, String detail) {
        this.eventId = eventId;
        this.type = type;
        this.subjectName = subjectName;
        this.detail = detail;
        this.createdAt = Timestamp.now();
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

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
    }

    @PropertyName("subjectName")
    public String getSubjectName() {
        return subjectName;
    }

    @PropertyName("subjectName")
    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    @PropertyName("detail")
    public String getDetail() {
        return detail;
    }

    @PropertyName("detail")
    public void setDetail(String detail) {
        this.detail = detail;
    }

    @PropertyName("createdAt")
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @PropertyName("createdAt")
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
