package com.us.eventum.presentation.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.us.eventum.utils.EventPrivateAccessCode;
import com.us.eventum.utils.PrivateAccessCodeDialogHelper;
import com.us.eventum.utils.ToastUtils;

import androidx.appcompat.app.AppCompatActivity;
import com.us.eventum.utils.WindowInsetsHelper;
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
    private ImageView noEventsIcon;
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
        noEventsIcon = view.findViewById(R.id.noEventsIcon);
        noEventsLayout = view.findViewById(R.id.noEventsLayout);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        View eventsListContainer = view.findViewById(R.id.eventsListContainer);
        if (eventsListContainer != null) {
            WindowInsetsHelper.applyOverlayFabListPadding(eventsListContainer);
        }
        
        // Inicializar EventViewModel
        eventViewModel = new ViewModelProvider(requireActivity()).get(EventViewModel.class);
        
        setupRecyclerView();
        setupSwipeRefresh();

        if (events != null) {
            dataLoaded = true;
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
                noEventsIcon.setImageResource(R.drawable.ic_search_cancelled);
                noEventsText.setText("No se encontraron eventos que coincidan con los criterios de búsqueda\n\nIntenta ajustar los filtros o limpiar la búsqueda para ver todos los eventos");
            } else {
                noEventsIcon.setImageResource(R.drawable.ic_calendar);
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
        if (getActivity() == null || event.getId() == null) {
            return;
        }
        boolean newPrivateState = !event.getPrivateEvent();
        if (newPrivateState) {
            PrivateAccessCodeDialogHelper.show((AppCompatActivity) requireActivity(),
                    new PrivateAccessCodeDialogHelper.Callback() {
                        @Override
                        public void onCodeConfirmed(@NonNull String accessCode) {
                            applyPrivacyChange(event, true, accessCode);
                        }

                        @Override
                        public void onCancelled() {
                            // Sin cambios
                        }
                    });
            return;
        }
        applyPrivacyChange(event, false, null);
    }

    private void applyPrivacyChange(Event event, boolean isPrivate, @Nullable String accessCode) {
        event.setPrivateEvent(isPrivate);
        event.setPrivateAccessCode(isPrivate ? EventPrivateAccessCode.normalize(accessCode) : null);
        int position = findEventPosition(event);
        if (position != -1 && eventAdapter != null) {
            eventAdapter.notifyItemChanged(position);
        }
        eventViewModel.updateEventPrivacy(event.getId(), isPrivate, accessCode);
    }

    private int findEventPosition(Event event) {
        if (events == null || event.getId() == null) {
            return -1;
        }
        for (int i = 0; i < events.size(); i++) {
            if (event.getId().equals(events.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }
} 