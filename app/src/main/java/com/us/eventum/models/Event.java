package com.us.eventum.models;

import android.os.Parcel;
import android.os.Parcelable;
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
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getCurrentParticipants() {
        return currentParticipants;
    }

    public void setCurrentParticipants(int currentParticipants) {
        this.currentParticipants = currentParticipants;
    }

    public boolean isPrivate() {
        return privateEvent;
    }

    public void setPrivate(boolean privateEvent) {
        this.privateEvent = privateEvent;
    }
    
    public boolean getPrivateEvent() {
        return privateEvent;
    }
    
    public void setPrivateEvent(boolean privateEvent) {
        this.privateEvent = privateEvent;
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
    }
}