package com.us.eventum.data.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

/**
 * Notificación para el asistente (p. ej. asistencia verificada en un evento).
 */
public class AttendeeNotification {

    public static final String TYPE_ATTENDANCE_VERIFIED = "ATTENDANCE_VERIFIED";
    public static final String TYPE_REMOVED_BY_ORGANIZER = "REMOVED_BY_ORGANIZER";
    public static final String TYPE_WAITLIST_SPOT_AVAILABLE = "WAITLIST_SPOT_AVAILABLE";
    public static final String TYPE_EVENT_CHANGED = "EVENT_CHANGED";
    public static final String TYPE_EVENT_CANCELLED = "EVENT_CANCELLED";

    private String id;
    private String attendeeId;
    private String eventId;
    private String eventTitle;
    private String type;
    private Timestamp createdAt;

    public AttendeeNotification() {
    }

    public AttendeeNotification(String attendeeId, String eventId, String eventTitle, String type) {
        this.attendeeId = attendeeId;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.type = type;
        this.createdAt = Timestamp.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("attendeeId")
    public String getAttendeeId() {
        return attendeeId;
    }

    @PropertyName("attendeeId")
    public void setAttendeeId(String attendeeId) {
        this.attendeeId = attendeeId;
    }

    @PropertyName("eventId")
    public String getEventId() {
        return eventId;
    }

    @PropertyName("eventId")
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @PropertyName("eventTitle")
    public String getEventTitle() {
        return eventTitle;
    }

    @PropertyName("eventTitle")
    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    @PropertyName("type")
    public String getType() {
        return type;
    }

    @PropertyName("type")
    public void setType(String type) {
        this.type = type;
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
