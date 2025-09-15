package com.us.eventum.presentation.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.us.eventum.R;
import com.us.eventum.adapters.EventAdapter;
import com.us.eventum.adapters.EventsPagerAdapter;
import com.us.eventum.models.Event;
import com.us.eventum.presentation.activities.EventDetailsActivity;
import java.util.ArrayList;
import java.util.List;

public class EventsFragment extends Fragment implements EventAdapter.OnEventClickListener {
    private static final String ARG_EVENTS = "events";
    private static final String ARG_IS_FUTURE = "is_future";
    
    private List<Event> events;
    private boolean isFuture;
    private TextView noEventsText;
    private LinearLayout noEventsLayout;
    private RecyclerView eventsRecyclerView;
    private EventAdapter eventAdapter;
    private ProgressBar progressBar;
    private boolean dataLoaded = false;

    public static EventsFragment newInstance(List<Event> events, boolean isFuture) {
        EventsFragment fragment = new EventsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_EVENTS, new ArrayList<>(events));
        args.putBoolean(ARG_IS_FUTURE, isFuture);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            events = (List<Event>) getArguments().getSerializable(ARG_EVENTS);
            isFuture = getArguments().getBoolean(ARG_IS_FUTURE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events, container, false);
        
        noEventsText = view.findViewById(R.id.noEventsText);
        noEventsLayout = view.findViewById(R.id.noEventsLayout);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        
        setupRecyclerView();
        
        // Si ya tenemos eventos, mostrarlos inmediatamente
        if (events != null && !events.isEmpty()) {
            updateUI();
        } else {
            showLoadingState();
        }
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dataLoaded) {
            updateUI();
        }
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(new ArrayList<>(), this);
        eventAdapter.setOnItemClickListener(this);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        eventsRecyclerView.setAdapter(eventAdapter);
    }

    private void showLoadingState() {
        progressBar.setVisibility(View.VISIBLE);
        eventsRecyclerView.setVisibility(View.GONE);
        noEventsLayout.setVisibility(View.GONE);
    }

    private void updateUI() {
        progressBar.setVisibility(View.GONE);
        
        if (events == null || events.isEmpty()) {
            eventsRecyclerView.setVisibility(View.GONE);
            noEventsLayout.setVisibility(View.VISIBLE);
            noEventsText.setText(isFuture ? 
                "Aún no has creado ningún evento\n\n¡Crea tu primer evento y comienza a gestionarlos de manera sencilla y eficiente!" : 
                "No tienes eventos pasados\n\nLos eventos que hayas completado aparecerán aquí");
        } else {
            eventsRecyclerView.setVisibility(View.VISIBLE);
            noEventsLayout.setVisibility(View.GONE);
            eventAdapter.setEvents(events);
        }
    }

    public void setEvents(List<Event> events) {
        this.events = events;
        this.dataLoaded = true;
        if (isAdded() && getView() != null) {
            updateUI();
        }
    }

    @Override
    public void onEventClick(Event event) {
        Log.d("EventsFragment", "Event clicked: " + event.getTitle());
        Intent intent = new Intent(getActivity(), EventDetailsActivity.class);
        intent.putExtra("event", event);
        startActivity(intent);
    }

    @Override
    public void onEventLongClick(View view, Event event) {
        if (getActivity() instanceof EventsPagerAdapter.EventContextMenuListener) {
            ViewPager2 viewPager = getActivity().findViewById(R.id.viewPager);
            if (viewPager != null) {
                EventsPagerAdapter pagerAdapter = (EventsPagerAdapter) viewPager.getAdapter();
                if (pagerAdapter != null) {
                    pagerAdapter.showEventContextMenu(view, event, 
                        (EventsPagerAdapter.EventContextMenuListener) getActivity());
                } else {
                    Log.e("EventsFragment", "PagerAdapter is null");
                }
            } else {
                Log.e("EventsFragment", "ViewPager2 not found");
            }
        } else {
            Log.e("EventsFragment", "Activity does not implement EventContextMenuListener");
        }
    }
} 