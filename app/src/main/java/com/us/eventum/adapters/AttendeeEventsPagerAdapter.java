package com.us.eventum.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.fragments.AttendeeEventsFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttendeeEventsPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 3;

    private final List<Event> myEvents;
    private final List<Event> discoverEvents;
    private final List<Event> historyEvents;
    private final Map<Integer, AttendeeEventsFragment> fragmentsMap = new HashMap<>();

    public AttendeeEventsPagerAdapter(@NonNull FragmentActivity activity,
                                      List<Event> myEvents,
                                      List<Event> discoverEvents,
                                      List<Event> historyEvents) {
        super(activity);
        this.myEvents = myEvents;
        this.discoverEvents = discoverEvents;
        this.historyEvents = historyEvents;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        AttendeeEventsFragment.TabType tabType;
        List<Event> initial;
        switch (position) {
            case 0:
                tabType = AttendeeEventsFragment.TabType.MY_EVENTS;
                initial = myEvents;
                break;
            case 2:
                tabType = AttendeeEventsFragment.TabType.HISTORY;
                initial = historyEvents;
                break;
            case 1:
            default:
                tabType = AttendeeEventsFragment.TabType.DISCOVER;
                initial = discoverEvents;
                break;
        }
        AttendeeEventsFragment fragment = AttendeeEventsFragment.newInstance(tabType, initial);
        fragmentsMap.put(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }

    public void updateEvents(List<Event> newMyEvents, List<Event> newDiscoverEvents, List<Event> newHistoryEvents) {
        updateList(0, newMyEvents);
        updateList(1, newDiscoverEvents);
        updateList(2, newHistoryEvents);
    }

    private void updateList(int position, List<Event> events) {
        AttendeeEventsFragment fragment = fragmentsMap.get(position);
        if (fragment != null) {
            fragment.setEvents(events);
        }
    }

    public void setRefreshing(boolean refreshing) {
        for (AttendeeEventsFragment fragment : fragmentsMap.values()) {
            if (fragment != null) {
                fragment.setRefreshing(refreshing);
            }
        }
    }
}
