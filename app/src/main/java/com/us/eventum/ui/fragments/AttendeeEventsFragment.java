package com.us.eventum.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.us.eventum.R;
import com.us.eventum.ui.adapters.EventAdapter;
import com.us.eventum.data.models.Event;

import java.util.ArrayList;
import java.util.List;

public class AttendeeEventsFragment extends Fragment implements EventAdapter.OnEventClickListener {

    public enum TabType {
        MY_EVENTS,
        DISCOVER,
        HISTORY
    }

    public interface AttendeeEventListener {
        void onEventClick(Event event);

        void onEventLongClick(Event event);

        void onLockIconLongClick(Event event);

        void onEventQrClick(Event event);

        void onRefreshRequested();

        default boolean hasActiveAttendeeFilters(@NonNull TabType tabType) {
            return false;
        }
    }

    private static final String ARG_TAB_TYPE = "tab_type";
    private static final String ARG_EVENTS = "events";

    private TabType tabType;
    private List<Event> events = new ArrayList<>();
    private TextView noEventsText;
    private ImageView noEventsIcon;
    private LinearLayout noEventsLayout;
    private RecyclerView eventsRecyclerView;
    private EventAdapter eventAdapter;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AttendeeEventListener eventListener;
    private boolean dataLoaded;

    public static AttendeeEventsFragment newInstance(TabType tabType, List<Event> initialEvents) {
        AttendeeEventsFragment fragment = new AttendeeEventsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TAB_TYPE, tabType.name());
        args.putParcelableArrayList(ARG_EVENTS, new ArrayList<>(initialEvents != null ? initialEvents : new ArrayList<>()));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AttendeeEventListener) {
            eventListener = (AttendeeEventListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabType = TabType.valueOf(getArguments().getString(ARG_TAB_TYPE, TabType.DISCOVER.name()));
            List<Event> parsed = BundleCompat.getParcelableArrayList(
                    getArguments(), ARG_EVENTS, Event.class);
            if (parsed != null) {
                events = parsed;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events, container, false);

        noEventsText = view.findViewById(R.id.noEventsText);
        noEventsIcon = view.findViewById(R.id.noEventsIcon);
        noEventsLayout = view.findViewById(R.id.noEventsLayout);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupRecyclerView();
        setupSwipeRefresh();

        dataLoaded = true;
        updateUI();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dataLoaded && getView() != null) {
            updateUI();
        }
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(new ArrayList<>(), this);
        eventAdapter.setOnItemClickListener(this);
        if (tabType == TabType.MY_EVENTS) {
            eventAdapter.setShowQrQuickAction(true);
        } else if (tabType == TabType.HISTORY) {
            eventAdapter.setCardDisplayMode(EventAdapter.CardDisplayMode.HISTORY);
        }
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        eventsRecyclerView.setAdapter(eventAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (eventListener != null) {
                eventListener.onRefreshRequested();
            }
        });
        swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent
        );
    }

    private void showLoadingState() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        eventsRecyclerView.setVisibility(View.GONE);
        noEventsLayout.setVisibility(View.GONE);
    }

    public void setRefreshing(boolean refreshing) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(refreshing);
        }
        if (refreshing && progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateUI() {
        progressBar.setVisibility(View.GONE);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }

        if (events == null || events.isEmpty()) {
            eventsRecyclerView.setVisibility(View.GONE);
            noEventsLayout.setVisibility(View.VISIBLE);
            noEventsText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            if (noEventsIcon != null) {
                noEventsIcon.setImageResource(tabType == TabType.DISCOVER
                        ? R.drawable.ic_search
                        : R.drawable.ic_calendar);
            }
            if (eventListener != null && eventListener.hasActiveAttendeeFilters(tabType)) {
                noEventsText.setText(R.string.attendee_empty_filters);
            } else {
                switch (tabType) {
                    case MY_EVENTS:
                        noEventsText.setText(R.string.attendee_empty_my_events);
                        break;
                    case HISTORY:
                        noEventsText.setText(R.string.attendee_empty_history);
                        break;
                    case DISCOVER:
                    default:
                        noEventsText.setText(R.string.attendee_empty_discover);
                        break;
                }
            }
        } else {
            eventsRecyclerView.setVisibility(View.VISIBLE);
            noEventsLayout.setVisibility(View.GONE);
            eventAdapter.setEvents(events);
        }
    }

    public void setEvents(List<Event> newEvents) {
        this.events = newEvents != null ? newEvents : new ArrayList<>();
        this.dataLoaded = true;
        if (isAdded() && getView() != null && eventsRecyclerView != null) {
            eventsRecyclerView.post(this::updateUI);
        }
    }

    public TabType getTabType() {
        return tabType;
    }

    @Override
    public void onEventClick(Event event) {
        if (eventListener != null) {
            eventListener.onEventClick(event);
        }
    }

    @Override
    public void onEventLongClick(View view, Event event) {
        if (eventListener != null) {
            eventListener.onEventLongClick(event);
        }
    }

    @Override
    public void onLockIconLongClick(Event event) {
        if (eventListener != null) {
            eventListener.onLockIconLongClick(event);
        }
    }

    @Override
    public void onEventQrClick(Event event) {
        if (eventListener != null) {
            eventListener.onEventQrClick(event);
        }
    }
}
