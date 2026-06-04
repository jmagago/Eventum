package com.us.eventum.data.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

/**
 * Notificación para el organizador (inscripción o baja de un asistente).
 */
public class OrganizerNotification {

    public static final String TYPE_ATTENDEE_JOINED = "ATTENDEE_JOINED";
    public static final String TYPE_ATTENDEE_LEFT = "ATTENDEE_LEFT";

    private String id;
    private String organizerId;
    private String eventId;
    private String eventTitle;
    private String attendeeDisplayName;
    private String type;
    private Timestamp createdAt;

    public OrganizerNotification() {
    }

    public OrganizerNotification(String organizerId, String eventId, String eventTitle,
                                 String attendeeDisplayName, String type) {
        this.organizerId = organizerId;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.attendeeDisplayName = attendeeDisplayName;
        this.type = type;
        this.createdAt = Timestamp.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("organizerId")
    public String getOrganizerId() {
        return organizerId;
    }

    @PropertyName("organizerId")
    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
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

    @PropertyName("attendeeDisplayName")
    public String getAttendeeDisplayName() {
        return attendeeDisplayName;
    }

    @PropertyName("attendeeDisplayName")
    public void setAttendeeDisplayName(String attendeeDisplayName) {
        this.attendeeDisplayName = attendeeDisplayName;
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
