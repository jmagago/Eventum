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
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;
import com.us.eventum.R;
import com.us.eventum.adapters.EventAdapter;
import com.us.eventum.adapters.EventsPagerAdapter;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.activities.EventDetailsActivity;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.utils.ToastUtils;
import java.util.ArrayList;
import java.util.List;

public class EventsFragment extends Fragment implements EventAdapter.OnEventClickListener {
    private static final String ARG_EVENTS = "events";
    private static final String ARG_IS_FUTURE = "is_future";
    
    public interface OnRefreshListener {
        void onRefreshRequested();
    }
    
    private List<Event> events;
    private boolean isFuture;
    private TextView noEventsText;
    private LinearLayout noEventsLayout;
    private RecyclerView eventsRecyclerView;
    private EventAdapter eventAdapter;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EventViewModel eventViewModel;
    private OnRefreshListener refreshListener;
    private boolean dataLoaded = false;
    private boolean isSearchResult = false;

    public static EventsFragment newInstance(List<Event> events, boolean isFuture) {
        EventsFragment fragment = new EventsFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_EVENTS, new ArrayList<>(events));
        args.putBoolean(ARG_IS_FUTURE, isFuture);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            events = BundleCompat.getParcelableArrayList(
                    getArguments(), ARG_EVENTS, Event.class);
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
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        
        // Inicializar EventViewModel
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);
        
        setupRecyclerView();
        setupSwipeRefresh();
        
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

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Notificar a la actividad para que recargue los eventos
            if (refreshListener != null) {
                refreshListener.onRefreshRequested();
            }
        });
        
        // Configurar colores del indicador de refresh
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

    private void updateUI() {
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false); // Detener el indicador de refresh
        
        if (events == null || events.isEmpty()) {
            eventsRecyclerView.setVisibility(View.GONE);
            noEventsLayout.setVisibility(View.VISIBLE);
            
            if (isSearchResult) {
                noEventsText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0, R.drawable.ic_search_cancelled, 0, 0);
                noEventsText.setText("No se encontraron eventos que coincidan con los criterios de búsqueda\n\nIntenta ajustar los filtros o limpiar la búsqueda para ver todos los eventos");
            } else {
                noEventsText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0, R.drawable.ic_calendar, 0, 0);
                noEventsText.setText(isFuture ? 
                    "Aún no has creado ningún evento\n\n¡Crea tu primer evento y comienza a gestionarlos de manera sencilla y eficiente!" : 
                    "No tienes eventos pasados\n\nLos eventos que hayas completado aparecerán aquí");
            }
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
    
    public void setEvents(List<Event> events, boolean isSearchResult) {
        this.events = events;
        this.isSearchResult = isSearchResult;
        this.dataLoaded = true;
        if (isAdded() && getView() != null) {
            updateUI();
        }
    }
    
    public void setOnRefreshListener(OnRefreshListener listener) {
        this.refreshListener = listener;
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

    @Override
    public void onLockIconLongClick(Event event) {
        // Invierto el estado de privacidad del evento
        boolean newPrivateState = !event.getPrivateEvent();
        // Actualizo el estado del evento local inmediatamente para feedback visual
        event.setPrivateEvent(newPrivateState);
        // Busco la posición del evento en la lista
        int position = -1;
        if (events != null) {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getId() != null && events.get(i).getId().equals(event.getId())) {
                    position = i;
                    break;
                }
            }
        }
        // Actualizo el adaptador para que el icono cambie de inmediato
        if (position != -1 && eventAdapter != null) {
            eventAdapter.notifyItemChanged(position);
        }
        // Luego actualizo en el servidor
        eventViewModel.toggleEventPrivacy(event.getId(), newPrivateState);
    }
} 