package com.us.eventum.data.models;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
import java.util.Date;

public class Event implements Parcelable {
    private String id;
    private String title;
    private String description;
    private Date date;
    private String location;
    private String userId;
    private int maxParticipants;
    private String eventType;
    private int currentParticipants;
    private boolean privateEvent;
    private boolean requiresParentalAuth;
    /** Solo UI (lista de asistente): inscripción del usuario actual; no se persiste en Firestore. */
    private boolean currentUserJoined;
    /** Solo UI (lista de asistente): check-in QR validado; no se persiste en Firestore. */
    private boolean currentUserScannedQR;

    public Event() {
        // Constructor vacío requerido para Firestore
    }

    public Event(String title, String description, Date date, String location, String userId, int maxParticipants) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.userId = userId;
        this.maxParticipants = maxParticipants;
        this.eventType = "Otro"; // Valor predeterminado
        this.currentParticipants = 0;
        this.privateEvent = false; // Valor predeterminado
        this.requiresParentalAuth = false; // Valor predeterminado
    }

    public Event(String title, String description, Date date, String location, String userId, int maxParticipants, String eventType) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.userId = userId;
        this.maxParticipants = maxParticipants;
        this.eventType = eventType;
        this.currentParticipants = 0;
        this.privateEvent = false; // Valor predeterminado
        this.requiresParentalAuth = false; // Valor predeterminado
    }

    // Getters y Setters
    @PropertyName("id")
    public String getId() {
        return id;
    }

    @PropertyName("id")
    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("title")
    public String getTitle() {
        return title;
    }

    @PropertyName("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @PropertyName("description")
    public String getDescription() {
        return description;
    }

    @PropertyName("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @PropertyName("date")
    public Date getDate() {
        return date;
    }

    @PropertyName("date")
    public void setDate(Date date) {
        this.date = date;
    }

    @PropertyName("location")
    public String getLocation() {
        return location;
    }

    @PropertyName("location")
    public void setLocation(String location) {
        this.location = location;
    }

    @PropertyName("userId")
    public String getUserId() {
        return userId;
    }

    @PropertyName("userId")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @PropertyName("maxParticipants")
    public int getMaxParticipants() {
        return maxParticipants;
    }

    @PropertyName("maxParticipants")
    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    @PropertyName("eventType")
    public String getEventType() {
        return eventType;
    }

    @PropertyName("eventType")
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @PropertyName("currentParticipants")
    public int getCurrentParticipants() {
        return currentParticipants;
    }

    @PropertyName("currentParticipants")
    public void setCurrentParticipants(int currentParticipants) {
        this.currentParticipants = currentParticipants;
    }

    @PropertyName("privateEvent")
    public boolean getPrivateEvent() {
        return privateEvent;
    }

    @PropertyName("privateEvent")
    public void setPrivateEvent(boolean privateEvent) {
        this.privateEvent = privateEvent;
    }

    @PropertyName("requiresParentalAuth")
    public boolean getRequiresParentalAuth() {
        return requiresParentalAuth;
    }

    @PropertyName("requiresParentalAuth")
    public void setRequiresParentalAuth(boolean requiresParentalAuth) {
        this.requiresParentalAuth = requiresParentalAuth;
    }

    @Exclude
    public boolean isCurrentUserJoined() {
        return currentUserJoined;
    }

    @Exclude
    public void setCurrentUserJoined(boolean currentUserJoined) {
        this.currentUserJoined = currentUserJoined;
    }

    @Exclude
    public boolean isCurrentUserScannedQR() {
        return currentUserScannedQR;
    }

    @Exclude
    public void setCurrentUserScannedQR(boolean currentUserScannedQR) {
        this.currentUserScannedQR = currentUserScannedQR;
    }

    // Constructor para Parcelable
    protected Event(Parcel in) {
        id = in.readString();
        title = in.readString();
        description = in.readString();
        long tmpDate = in.readLong();
        date = tmpDate != -1 ? new Date(tmpDate) : null;
        location = in.readString();
        userId = in.readString();
        maxParticipants = in.readInt();
        eventType = in.readString();
        currentParticipants = in.readInt();
        privateEvent = in.readByte() != 0;
        requiresParentalAuth = in.readByte() != 0;
        currentUserJoined = in.readByte() != 0;
        currentUserScannedQR = in.readByte() != 0;
    }

    public static final Creator<Event> CREATOR = new Creator<Event>() {
        @Override
        public Event createFromParcel(Parcel in) {
            return new Event(in);
        }

        @Override
        public Event[] newArray(int size) {
            return new Event[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeLong(date != null ? date.getTime() : -1);
        dest.writeString(location);
        dest.writeString(userId);
        dest.writeInt(maxParticipants);
        dest.writeString(eventType);
        dest.writeInt(currentParticipants);
        dest.writeByte((byte) (privateEvent ? 1 : 0));
        dest.writeByte((byte) (requiresParentalAuth ? 1 : 0));
        dest.writeByte((byte) (currentUserJoined ? 1 : 0));
        dest.writeByte((byte) (currentUserScannedQR ? 1 : 0));
    }
}

