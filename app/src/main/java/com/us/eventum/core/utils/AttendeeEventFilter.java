package com.us.eventum.core.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.us.eventum.R;
import com.us.eventum.data.models.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AttendeeEventFilter {

    public static final int TAB_MY_EVENTS = 0;
    public static final int TAB_DISCOVER = 1;
    public static final int TAB_HISTORY = 2;

    public enum Status {
        ALL,
        JOINED,
        WAITLIST,
        HAS_SPOTS,
        FULL
    }

    public enum Sort {
        DATE_ASC,
        DATE_DESC,
        FREE_SPOTS_DESC;

        @NonNull
        public java.util.Comparator<Event> comparator() {
            switch (this) {
                case DATE_DESC:
                    return (a, b) -> compareDates(b, a);
                case FREE_SPOTS_DESC:
                    return (a, b) -> {
                        int bySpots = Integer.compare(b.getFreeSpots(), a.getFreeSpots());
                        return bySpots != 0 ? bySpots : compareDates(a, b);
                    };
                case DATE_ASC:
                default:
                    return AttendeeEventFilter::compareDates;
            }
        }
    }

    private static int compareDates(Event a, Event b) {
        Date da = a.getDate();
        Date db = b.getDate();
        if (da == null && db == null) {
            return 0;
        }
        if (da == null) {
            return 1;
        }
        if (db == null) {
            return -1;
        }
        return da.compareTo(db);
    }

    private final List<String> eventTypes = new ArrayList<>();
    private String location;
    private Date dateFrom;
    private Date dateTo;
    private Status myStatus = Status.ALL;
    private Status discoverStatus = Status.ALL;
    private Sort mySort = Sort.DATE_ASC;
    private Sort discoverSort = Sort.DATE_ASC;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy", LocaleUtils.spanish());

    @NonNull
    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(@Nullable List<String> eventTypes) {
        this.eventTypes.clear();
        if (eventTypes == null) {
            return;
        }
        for (String type : eventTypes) {
            if (type != null && !type.trim().isEmpty()) {
                this.eventTypes.add(type.trim());
            }
        }
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(Date dateFrom) {
        this.dateFrom = dateFrom == null ? null : startOfDay(dateFrom);
    }

    public Date getDateTo() {
        return dateTo;
    }

    public void setDateTo(Date dateTo) {
        this.dateTo = dateTo == null ? null : endOfDay(dateTo);
    }

    public Status getMyStatus() {
        return myStatus;
    }

    public void setMyStatus(@NonNull Status myStatus) {
        this.myStatus = myStatus;
    }

    public Status getDiscoverStatus() {
        return discoverStatus;
    }

    public void setDiscoverStatus(@NonNull Status discoverStatus) {
        this.discoverStatus = discoverStatus;
    }

    public Sort getMySort() {
        return mySort;
    }

    public void setMySort(@NonNull Sort mySort) {
        this.mySort = mySort;
    }

    public Sort getDiscoverSort() {
        return discoverSort;
    }

    public void setDiscoverSort(@NonNull Sort discoverSort) {
        this.discoverSort = discoverSort;
    }

    @NonNull
    public Status statusForTab(int tab) {
        if (tab == TAB_DISCOVER) {
            return discoverStatus;
        }
        if (tab == TAB_MY_EVENTS) {
            return myStatus;
        }
        return Status.ALL;
    }

    public void setStatusForTab(int tab, @NonNull Status status) {
        if (tab == TAB_DISCOVER) {
            discoverStatus = status;
        } else if (tab == TAB_MY_EVENTS) {
            myStatus = status;
        }
    }

    @NonNull
    public Sort sortForTab(int tab) {
        if (tab == TAB_DISCOVER) {
            return discoverSort;
        }
        if (tab == TAB_MY_EVENTS) {
            return mySort;
        }
        return Sort.DATE_DESC;
    }

    public void setSortForTab(int tab, @NonNull Sort sort) {
        if (tab == TAB_DISCOVER) {
            discoverSort = sort;
        } else if (tab == TAB_MY_EVENTS) {
            mySort = sort;
        }
    }

    public boolean hasActiveFilters(int tab) {
        return !eventTypes.isEmpty()
                || (location != null && !location.trim().isEmpty())
                || dateFrom != null
                || dateTo != null
                || statusForTab(tab) != Status.ALL;
    }

    public void clearSharedCriteria() {
        eventTypes.clear();
        location = null;
        dateFrom = null;
        dateTo = null;
    }

    public void clearTabStatus(int tab) {
        setStatusForTab(tab, Status.ALL);
    }

    public boolean matches(@NonNull Event event, int tab) {
        if (!eventTypes.isEmpty()) {
            String actual = event.getEventType() != null
                    ? event.getEventType().trim() : "";
            boolean matchesType = false;
            for (String type : eventTypes) {
                if (type.equalsIgnoreCase(actual)) {
                    matchesType = true;
                    break;
                }
            }
            if (!matchesType) {
                return false;
            }
        }
        if (location != null && !location.trim().isEmpty()) {
            String wanted = location.toLowerCase(Locale.ROOT).trim();
            String eventLocation = event.getLocation() != null
                    ? event.getLocation().toLowerCase(Locale.ROOT) : "";
            if (!eventLocation.contains(wanted)) {
                return false;
            }
        }
        if (event.getDate() != null) {
            if (dateFrom != null && event.getDate().before(dateFrom)) {
                return false;
            }
            if (dateTo != null && event.getDate().after(dateTo)) {
                return false;
            }
        } else if (dateFrom != null || dateTo != null) {
            return false;
        }

        Status status = statusForTab(tab);
        switch (status) {
            case JOINED:
                return event.isCurrentUserJoined() && !event.isCurrentUserOnWaitlist();
            case WAITLIST:
                return event.isCurrentUserOnWaitlist();
            case HAS_SPOTS:
                return event.hasFreeSpot();
            case FULL:
                return event.isAtCapacity();
            case ALL:
            default:
                return true;
        }
    }

    @NonNull
    public List<Event> apply(@Nullable List<Event> events, int tab) {
        List<Event> result = new ArrayList<>();
        if (events == null) {
            return result;
        }
        for (Event event : events) {
            if (event != null && matches(event, tab)) {
                result.add(event);
            }
        }
        result.sort(sortForTab(tab).comparator());
        return result;
    }

    @NonNull
    public String getActiveFiltersSummary(@NonNull Context context, int tab) {
        List<String> parts = new ArrayList<>();
        if (!eventTypes.isEmpty()) {
            parts.add(context.getString(R.string.filter_attendee_summary_type,
                    String.join(", ", eventTypes)));
        }
        if (location != null && !location.trim().isEmpty()) {
            parts.add(context.getString(R.string.filter_attendee_summary_location, location.trim()));
        }
        if (dateFrom != null) {
            parts.add(context.getString(R.string.filter_attendee_summary_from, DATE_FORMAT.format(dateFrom)));
        }
        if (dateTo != null) {
            parts.add(context.getString(R.string.filter_attendee_summary_to, DATE_FORMAT.format(dateTo)));
        }
        Status status = statusForTab(tab);
        if (status != Status.ALL) {
            parts.add(labelForStatus(context, status));
        }
        return parts.isEmpty()
                ? context.getString(R.string.filter_active_indicator)
                : String.join(", ", parts);
    }

    @NonNull
    public static String labelForStatus(@NonNull Context context, @NonNull Status status) {
        switch (status) {
            case JOINED:
                return context.getString(R.string.filter_attendee_status_joined);
            case WAITLIST:
                return context.getString(R.string.filter_attendee_status_waitlist);
            case HAS_SPOTS:
                return context.getString(R.string.filter_attendee_status_has_spots);
            case FULL:
                return context.getString(R.string.filter_attendee_status_full);
            case ALL:
            default:
                return context.getString(R.string.filter_attendee_status_all);
        }
    }

    @NonNull
    public static Date startOfDay(@NonNull Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    @NonNull
    public static Date endOfDay(@NonNull Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    public int sortLabelRes(@NonNull Sort sort) {
        switch (sort) {
            case DATE_DESC:
                return R.string.sort_date_desc;
            case FREE_SPOTS_DESC:
                return R.string.sort_free_spots_desc;
            case DATE_ASC:
            default:
                return R.string.sort_date_asc;
        }
    }
}
