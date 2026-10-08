package com.us.eventum.ui.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.graphics.Typeface;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.card.MaterialCardView;
import de.hdodenhof.circleimageview.CircleImageView;
import com.us.eventum.R;
import com.us.eventum.ui.adapters.EventsPagerAdapter;
import com.us.eventum.data.models.Event;
import com.us.eventum.ui.fragments.EventsFragment;
import com.us.eventum.ui.viewmodels.SharedViewModel;
import com.us.eventum.ui.viewmodels.EventViewModel;
import com.us.eventum.ui.viewmodels.OrganizerViewModel;
import com.us.eventum.ui.viewmodels.AttendeeViewModel;
import com.us.eventum.core.utils.PrivateAccessCodeDialogHelper;
import com.us.eventum.core.utils.WaitlistDialogHelper;
import com.us.eventum.core.utils.ProfileImageManager;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.NotificationPermissionHelper;
import com.us.eventum.core.utils.OrganizerNotificationDispatcher;
import com.us.eventum.core.utils.OrganizerNotificationHelper;
import com.us.eventum.core.utils.OrganizerNotificationWatcher;
import com.us.eventum.core.utils.FabBarController;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.data.repositories.FirebaseManager;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.us.eventum.core.utils.CreateEventPanelController;
import com.us.eventum.core.utils.AttendeeCsvExportHelper;
import com.us.eventum.core.utils.EditEventPanelController;
import com.us.eventum.core.utils.EventImageManager;
import com.us.eventum.core.utils.EventSearchFilter;
import com.us.eventum.core.utils.SearchEventsPanelController;
import com.us.eventum.core.utils.WindowInsetsHelper;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import androidx.core.util.Consumer;
import android.widget.ImageView;

public class OrganizerHomeActivity extends AppCompatActivity implements EventsPagerAdapter.EventContextMenuListener {
    private enum FutureEventSort {
        DATE_ASC,
        PLACES_ASC,
        PLACES_DESC,
        ATTENDEES_ASC,
        ATTENDEES_DESC,
        OCCUPANCY_ASC,
        OCCUPANCY_DESC
    }

    private FloatingActionButton settingsButton, createEventButton, searchEventsButton;
    private FirebaseManager firebaseManager;
    private String userId;
    private List<Event> futureEvents = new ArrayList<>();
    private List<Event> pastEvents = new ArrayList<>();
    private EventSearchFilter currentSearchFilter = new EventSearchFilter();
    private boolean isSearchActive = false;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ImageButton sortFutureEventsButton;
    private FutureEventSort futureEventSort = FutureEventSort.DATE_ASC;
    private EventsPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private CircleImageView profileImageView;
    private LinearLayout filterIndicatorLayout;
    private TextView filterIndicatorText;
    private View clearFiltersButton;
    private SimpleDateFormat dateFormat;
    private SharedViewModel sharedViewModel;
    private EventViewModel eventViewModel;
    private OrganizerViewModel organizerViewModel;
    private AttendeeViewModel attendeeViewModel;
    private boolean isLoadingEvents = false;
    
    private MaterialCardView fabContainer;
    private FabBarController fabBarController;
    private OrganizerNotificationWatcher notificationWatcher;
    private ActivityResultLauncher<String> pickEventImageLauncher;
    private Consumer<Uri> eventImagePickHandler;
    private CreateEventPanelController createEventPanelController;
    private EditEventPanelController editEventPanelController;
    private SearchEventsPanelController searchEventsPanelController;
    private AttendeeCsvExportHelper csvExportHelper;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });
    private long lastKnownProfileImageUpdatedAt;
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null
                        || profileImageView == null) {
                    return;
                }
                long version = result.getData().getLongExtra(
                        SettingsActivity.EXTRA_PROFILE_IMAGE_UPDATED_AT, 0L);
                if (version > lastKnownProfileImageUpdatedAt) {
                    lastKnownProfileImageUpdatedAt = version;
                    ProfileImageManager.loadProfileImage(this, profileImageView, version);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);
        }

        pickEventImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && eventImagePickHandler != null) {
                        eventImagePickHandler.accept(uri);
                    }
                });

        // Inicializar Firebase
        firebaseManager = FirebaseManager.getInstance();
        userId = firebaseManager.getAuth().getCurrentUser().getUid();

        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Inicializar ViewModels
        sharedViewModel = SharedViewModel.getInstance();
        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        organizerViewModel = new ViewModelProvider(this).get(OrganizerViewModel.class);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);

        // Inicializar repositorios en ViewModels
        eventViewModel.initializeRepository(this);
        organizerViewModel.initializeRepository(this);
        attendeeViewModel.initializeRepository(this);

        // Inicializar vistas
        initializeViews();
        setupClickListeners();
        setupViewPager();
        observeViewModels();
        loadEvents();

        createEventPanelController = new CreateEventPanelController(
                this,
                handler -> {
                    eventImagePickHandler = handler;
                    pickEventImageLauncher.launch("image/*");
                },
                eventViewModel,
                sharedViewModel);

        editEventPanelController = new EditEventPanelController(
                this,
                handler -> {
                    eventImagePickHandler = handler;
                    pickEventImageLauncher.launch("image/*");
                },
                eventViewModel);

        searchEventsPanelController = new SearchEventsPanelController(
                this,
                filter -> {
                    currentSearchFilter = filter;
                    applySearchFilters();
                },
                () -> currentSearchFilter);

        csvExportHelper = new AttendeeCsvExportHelper(this);
        setupPanelBackHandler();

        notificationWatcher = new OrganizerNotificationWatcher(this);
        NotificationPermissionHelper.requestIfNeeded(this, notificationPermissionLauncher);
    }

    private void setupPanelBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (editEventPanelController != null && editEventPanelController.isVisible()) {
                    editEventPanelController.hide();
                } else if (searchEventsPanelController != null && searchEventsPanelController.isVisible()) {
                    searchEventsPanelController.hide();
                } else if (createEventPanelController != null && createEventPanelController.isVisible()) {
                    createEventPanelController.hide();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void observeViewModels() {
        // Observar eventos del EventViewModel
        eventViewModel.getEvents().observe(this, events -> {
            if (events != null) {
                // Clasificar eventos en futuros y pasados
                Date now = new Date();
                futureEvents.clear();
                pastEvents.clear();
                
                for (Event event : events) {
                    if (event.isUpcoming(now)) {
                        futureEvents.add(event);
                    } else {
                        pastEvents.add(event);
                    }
                }
                
                updateUI();
            }
        });

        // Observar estado de carga
        eventViewModel.getIsLoading().observe(this, loading -> {
            // Aquí podrías mostrar/ocultar un indicador de carga
        });

        // Observar errores
        eventViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        eventViewModel.getEventUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                // El mensaje se muestra en el observer de eventUpdateMessage
            }
        });

        eventViewModel.getEventUpdateMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                ToastUtils.showCustomToast(this, message, ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        eventViewModel.getEventDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, getString(R.string.toast_event_deleted), ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        sharedViewModel.getEventsUpdated().observe(this, eventsUpdated -> {
            if (!Boolean.TRUE.equals(eventsUpdated)) {
                return;
            }
            boolean wasSearchActive = isSearchActive;
            EventSearchFilter savedFilter = currentSearchFilter;
            eventViewModel.restartListeningUserEvents();
            if (wasSearchActive && savedFilter != null) {
                isSearchActive = true;
                currentSearchFilter = savedFilter;
            }
            sharedViewModel.resetEventsUpdated();
        });

        // Observar datos del organizador
        organizerViewModel.getCurrentOrganizer().observe(this, organizer -> {
            if (organizer != null) {
                TextView userNameTextView = findViewById(R.id.userNameTextView);
                userNameTextView.setText(organizer.getUsername());

                // Mostrar etiqueta de rol (esta pantalla es para ORGANIZADOR)
                TextView userRoleTextView = findViewById(R.id.userRoleTextView);
                if (userRoleTextView != null) {
                    userRoleTextView.setText(R.string.role_organizer);
                    userRoleTextView.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                    userRoleTextView.setBackgroundResource(R.drawable.bg_role_badge_organizer);
                }
            }
        });

        // Observar errores del OrganizerViewModel
        organizerViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar limpieza de asistentes del AttendeeViewModel
        attendeeViewModel.getAttendeesCleared().observe(this, cleared -> {
            if (cleared != null && cleared) {
                ToastUtils.showCustomToast(this, getString(R.string.event_log_action_list_cleared), ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
                sharedViewModel.notifyEventsUpdated();
                loadEvents();
            }
        });

        // Observar errores del AttendeeViewModel
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Aviso en pantalla si la notificación del sistema no se muestra
        OrganizerNotificationDispatcher.getLatestNotification().observe(this, notification -> {
            if (notification == null) {
                return;
            }
            String body = OrganizerNotificationHelper.buildBody(this, notification);
            ToastUtils.ToastType type = OrganizerNotification.TYPE_ATTENDEE_LEFT.equals(notification.getType())
                    ? ToastUtils.ToastType.WARNING
                    : ToastUtils.ToastType.INFO;
            ToastUtils.showCustomToast(this, body, type);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
        if (profileImageView != null) {
            ProfileImageManager.loadProfileImage(this, profileImageView, lastKnownProfileImageUpdatedAt);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (notificationWatcher != null && userId != null) {
            notificationWatcher.start(userId);
        }
    }

    @Override
    protected void onStop() {
        if (notificationWatcher != null) {
            notificationWatcher.stop();
        }
        super.onStop();
    }

    private void initializeViews() {
        createEventButton = findViewById(R.id.createEventFab);
        searchEventsButton = findViewById(R.id.searchEventsFab);
        settingsButton = findViewById(R.id.settingsButton);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        sortFutureEventsButton = findViewById(R.id.sortFutureEventsButton);
        profileImageView = findViewById(R.id.profileImageView);
        filterIndicatorLayout = findViewById(R.id.filterIndicatorLayout);
        filterIndicatorText = findViewById(R.id.filterIndicatorText);
        clearFiltersButton = findViewById(R.id.clearFiltersButton);
        View fabDock = findViewById(R.id.fabDock);
        fabContainer = findViewById(R.id.fabContainer);
        View fabBarHandle = findViewById(R.id.fabBarHandle);
        View fabBarCollapse = findViewById(R.id.fabBarCollapse);
        fabBarController = new FabBarController(this, fabDock, fabContainer, fabBarHandle, fabBarCollapse);
        fabBarController.attachToActivity(this);

        // Inicializar vistas de información de usuario
        TextView userNameTextView = findViewById(R.id.userNameTextView);
        TextView userEmailTextView = findViewById(R.id.userEmailTextView);
        
        // Obtener y mostrar información del usuario
        if (firebaseManager.getAuth().getCurrentUser() != null) {
            String email = firebaseManager.getAuth().getCurrentUser().getEmail();
            
            // Establecer el email inmediatamente
            userEmailTextView.setText(email);
            
            // Usar OrganizerViewModel para cargar datos del organizador
            organizerViewModel.loadCurrentOrganizer();
        }

        // Ajustar estado del FAB según la pestaña seleccionada
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean isArchivedTab = position == 1;
                createEventButton.setAlpha(isArchivedTab ? 0.5f : 1f);
                updateActionButtonsState(position);
            }
        });

        if (sortFutureEventsButton != null) {
            sortFutureEventsButton.setOnClickListener(v -> showEventsSortMenu());
            updateActionButtonsState(viewPager.getCurrentItem());
        }
    }

    private void updateActionButtonsState(int tabPosition) {
        int eventCount = tabPosition == 0 ? futureEvents.size() : pastEvents.size();
        boolean sortEnabled = tabPosition == 0 && futureEvents.size() > 1;
        boolean searchEnabled = eventCount > 1;

        if (sortFutureEventsButton != null) {
            sortFutureEventsButton.setEnabled(sortEnabled);
            sortFutureEventsButton.setAlpha(sortEnabled ? 1.0f : 0.38f);
        }

        if (searchEventsButton != null) {
            searchEventsButton.setEnabled(searchEnabled);
            searchEventsButton.setAlpha(searchEnabled ? 1.0f : 0.38f);
        }
    }

    private void showEventsSortMenu() {
        if (sortFutureEventsButton == null || !sortFutureEventsButton.isEnabled()) {
            return;
        }

        final FutureEventSort[] options = FutureEventSort.values();
        final FutureEventSort activeSort = futureEventSort;
        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(sortFutureEventsButton);
        popup.setModal(true);
        popup.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_sort_popup));
        float density = getResources().getDisplayMetrics().density;
        popup.setWidth((int) (240 * density));
        popup.setVerticalOffset((int) (4 * density));
        popup.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return options.length;
            }

            @Override
            public Object getItem(int position) {
                return options[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View optionView = convertView;
                if (optionView == null) {
                    optionView = getLayoutInflater().inflate(R.layout.item_sort_option, parent, false);
                }
                FutureEventSort sort = options[position];
                TextView titleView = optionView.findViewById(R.id.sortOptionTitle);
                ImageView checkView = optionView.findViewById(R.id.sortOptionCheck);
                titleView.setText(getSortOptionLabel(sort));
                boolean selected = sort == activeSort;
                checkView.setVisibility(selected ? View.VISIBLE : View.GONE);
                titleView.setTextColor(getResources().getColor(
                        selected ? R.color.colorAccent : R.color.colorPrimary, getTheme()));
                titleView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
                return optionView;
            }
        });
        popup.setOnItemClickListener((parent, view, position, id) -> {
            futureEventSort = options[position];
            updateUI();
            popup.dismiss();
        });
        popup.show();
    }

    private int getSortOptionLabel(FutureEventSort sort) {
        switch (sort) {
            case DATE_ASC:
                return R.string.sort_date_asc;
            case PLACES_ASC:
                return R.string.sort_places_asc;
            case PLACES_DESC:
                return R.string.sort_places_desc;
            case ATTENDEES_ASC:
                return R.string.sort_attendees_asc;
            case ATTENDEES_DESC:
                return R.string.sort_attendees_desc;
            case OCCUPANCY_ASC:
                return R.string.sort_occupancy_asc;
            case OCCUPANCY_DESC:
                return R.string.sort_occupancy_desc;
            default:
                return R.string.sort_date_asc;
        }
    }

    private void sortFutureEventsList() {
        futureEvents.sort(buildEventComparator(futureEventSort));
    }

    private Comparator<Event> buildEventComparator(FutureEventSort sort) {
        switch (sort) {
            case PLACES_ASC:
                return Comparator.comparingInt(Event::getMaxParticipants);
            case PLACES_DESC:
                return Comparator.comparingInt(Event::getMaxParticipants).reversed();
            case ATTENDEES_ASC:
                return Comparator.comparingInt(Event::getCurrentParticipants);
            case ATTENDEES_DESC:
                return Comparator.comparingInt(Event::getCurrentParticipants).reversed();
            case OCCUPANCY_ASC:
                return Comparator.comparingDouble(this::occupancyRate);
            case OCCUPANCY_DESC:
                return Comparator.comparingDouble(this::occupancyRate).reversed();
            case DATE_ASC:
            default:
                return Comparator.comparing(Event::getDate, Comparator.nullsLast(Comparator.naturalOrder()));
        }
    }

    private double occupancyRate(Event event) {
        if (event == null || event.getMaxParticipants() <= 0) {
            return 0d;
        }
        return (double) event.getCurrentParticipants() / (double) event.getMaxParticipants();
    }

    private void setupViewPager() {
        pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
        pagerAdapter.setOnRefreshListener(this::onRefreshRequested);
        viewPager.setAdapter(pagerAdapter);

        // Configurar el TabLayout
        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText(getString(R.string.organizer_tab_future, futureEvents.size()));
                    } else {
                        tab.setText(getString(R.string.organizer_tab_past, pastEvents.size()));
                    }
                }
        );
        tabLayoutMediator.attach();

        // Configurar el comportamiento del ViewPager2
        viewPager.setUserInputEnabled(true);
    }

    private void setupClickListeners() {
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrganizerHomeActivity.this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_USER_TYPE, SettingsActivity.USER_TYPE_ORGANIZER);
            settingsLauncher.launch(intent);
        });

        createEventButton.setOnClickListener(v -> {
            int currentTab = tabLayout.getSelectedTabPosition();
            if (currentTab == 1) { // Archivados
                ToastUtils.showCustomToast(this, getString(R.string.toast_cannot_create_from_archived), ToastUtils.ToastType.INFO);
                return;
            }
            if (createEventPanelController != null) {
                createEventPanelController.show();
            }
        });

        searchEventsButton.setOnClickListener(v -> {
            if (!searchEventsButton.isEnabled()) {
                return;
            }
            if (searchEventsPanelController != null) {
                searchEventsPanelController.show();
            }
        });
        
        clearFiltersButton.setOnClickListener(v -> clearSearchFilters());
    }

    private void loadEvents() {
        if (firebaseManager.getAuth().getCurrentUser() == null) return;
        
        // Usar EventViewModel para cargar eventos
        eventViewModel.loadUserEvents();
    }

    private void addEventToList(Event event, Date now) {
        if (event.isUpcoming(now)) {
            futureEvents.add(event);
        } else {
            pastEvents.add(event);
        }
    }

    private void updateUI() {
        sortFutureEventsList();
        pastEvents.sort((e1, e2) -> e2.getDate().compareTo(e1.getDate()));
        
        // Si hay una búsqueda activa, aplicar filtros en lugar de mostrar todos los eventos
        if (isSearchActive && currentSearchFilter.hasActiveFilters()) {
            applySearchFilters();
            return;
        }
        
        // Actualizar etiquetas de pestañas con el número de eventos
        TabLayout.Tab futureTab = tabLayout.getTabAt(0);
        TabLayout.Tab pastTab = tabLayout.getTabAt(1);
        
        if (futureTab != null) {
            futureTab.setText(getString(R.string.organizer_tab_future, futureEvents.size()));
        }
        
        if (pastTab != null) {
            pastTab.setText(getString(R.string.organizer_tab_past, pastEvents.size()));
        }
        
        // Actualizar datos en los fragmentos o crear adaptador si es null
        if (pagerAdapter == null) {
            pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
            pagerAdapter.setOnRefreshListener(this::onRefreshRequested);
            viewPager.setAdapter(pagerAdapter);
        } else {
            pagerAdapter.updateEvents();
        }
        
        // Actualizar títulos de pestañas
        updateTabTitles(futureEvents.size(), pastEvents.size());
        updateActionButtonsState(viewPager != null ? viewPager.getCurrentItem() : 0);
    }

    

    @Override
    protected void onDestroy() {
        if (eventViewModel != null) {
            eventViewModel.stopListeningUserEvents();
        }
        super.onDestroy();
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item, Event event) {
        int id = item.getItemId();
        Intent intent;
        if (id == R.id.action_edit_event) {
            openEditEventPanel(event);
            return true;
        } else if (id == R.id.action_delete_event) {
            showDeleteEventDialog(event);
            return true;
        } else if (id == R.id.action_cancel_event) {
            showCancelEventDialog(event);
            return true;
        } else if (id == R.id.action_view_waitlist) {
            if (event != null && event.getId() != null && attendeeViewModel != null) {
                WaitlistDialogHelper.show(this, event.getId(), attendeeViewModel);
            }
            return true;
        } else if (id == R.id.action_view_private_access_code) {
            PrivateAccessCodeDialogHelper.showViewCode(this, event.getPrivateAccessCode());
            return true;
        } else if (id == R.id.action_verify_attendees) {
            if (event != null && event.isCancelled()) {
                ToastUtils.showCustomToast(this, getString(R.string.qr_scanner_event_cancelled),
                        ToastUtils.ToastType.INFO);
                return true;
            }
            if (event.getId() == null || event.getId().isEmpty()) {
                ToastUtils.showCustomToast(this, getString(R.string.error_event_no_id), ToastUtils.ToastType.ERROR);
                return true;
            }
            intent = new Intent(this, QRScannerActivity.class);
            intent.putExtra("eventId", event.getId());
            startActivity(intent);
            return true;
        } else if (id == R.id.action_clear_list) {
            showClearAttendeeListConfirmation(event);
            return true;
        } else if (id == R.id.action_event_activity_log) {
            intent = new Intent(this, EventDetailsActivity.class);
            intent.putExtra("event", event);
            intent.putExtra("show_activity_log", true);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_export_attendees_csv) {
            if (event.getId() == null || event.getId().isEmpty()) {
                ToastUtils.showCustomToast(this, getString(R.string.error_event_no_id), ToastUtils.ToastType.ERROR);
                return true;
            }
            csvExportHelper.exportForEvent(attendeeViewModel, event.getId(), event.getTitle());
            return true;
        }
        return false;
    }

    private void openEditEventPanel(Event event) {
        if (editEventPanelController == null || event == null) {
            return;
        }
        if (event.isCancelled()) {
            ToastUtils.showCustomToast(this, getString(R.string.event_cancelled_cannot_edit),
                    ToastUtils.ToastType.INFO);
            return;
        }
        int currentParticipants = Math.max(0, event.getCurrentParticipants());
        editEventPanelController.show(event, new EditEventPanelController.SaveListener() {
            @Override
            public void onEventSaved(@NonNull Event savedEvent, @Nullable Uri newImageUri) {
                sharedViewModel.notifyEventsUpdated();
                if (savedEvent.getId() != null && attendeeViewModel != null) {
                    attendeeViewModel.promoteWaitlistIfNeeded(savedEvent.getId());
                }
            }

            @Override
            public void onEventImageUploadComplete(@NonNull String eventId, long imageUpdatedAt) {
                sharedViewModel.notifyEventsUpdated();
            }

            @Override
            public int getMinParticipantsAllowed() {
                return currentParticipants;
            }
        });
    }

    private void showDeleteEventDialog(Event event) {
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmTitleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        
        confirmTitleTextView.setText(R.string.delete_event_title);
        confirmMessageTextView.setText(R.string.delete_event_message);
        
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        confirmBuilder.setView(confirmDialogView);
        
        AlertDialog confirmDialog = confirmBuilder.create();
        
        confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
        
        confirmDeleteButton.setOnClickListener(cv -> {
            // Usar EventViewModel para eliminar el evento
            eventViewModel.deleteEvent(event.getId());
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }

    private void showCancelEventDialog(Event event) {
        if (event == null || event.getId() == null) {
            return;
        }
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmTitleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        ImageView confirmIcon = confirmDialogView.findViewById(R.id.confirm_icon);
        confirmTitleTextView.setText(R.string.cancel_event_title);
        confirmMessageTextView.setText(R.string.cancel_event_message);
        confirmDeleteButton.setText(R.string.cancel_event_confirm);
        if (confirmIcon != null) {
            confirmIcon.setImageResource(R.drawable.ic_warning);
        }

        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        confirmBuilder.setView(confirmDialogView);
        AlertDialog confirmDialog = confirmBuilder.create();
        confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
        confirmDeleteButton.setOnClickListener(cv -> {
            eventViewModel.cancelEvent(event.getId());
            confirmDialog.dismiss();
        });
        confirmDialog.show();
    }

    private void showClearAttendeeListConfirmation(Event event) {
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmTitleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        
        confirmTitleTextView.setText(R.string.clear_attendees_title);
        confirmMessageTextView.setText(R.string.clear_attendees_message);
        confirmDeleteButton.setText(R.string.menu_clear_list);
        
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        confirmBuilder.setView(confirmDialogView);
        
        AlertDialog confirmDialog = confirmBuilder.create();
        
        confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
        
        confirmDeleteButton.setOnClickListener(cv -> {
            clearAttendeeList(event);
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }

    private void clearAttendeeList(Event event) {
        // Usar AttendeeViewModel para limpiar la lista de asistentes
        attendeeViewModel.clearEventAttendees(event.getId());
    }

    private void applySearchFilters() {
        isSearchActive = currentSearchFilter.hasActiveFilters();
        
        if (isSearchActive) {
            // Durante búsqueda activa, filtrar solo la pestaña actual
            int currentTab = tabLayout.getSelectedTabPosition();
            List<Event> currentEvents = (currentTab == 0) ? futureEvents : pastEvents;
            List<Event> filteredEvents = currentSearchFilter.filterEvents(currentEvents);
            
            // Actualizar el adaptador con solo los eventos de la pestaña actual
            if (pagerAdapter != null) {
                if (currentTab == 0) {
                    pagerAdapter.updateEvents(filteredEvents, new ArrayList<>(), true);
                } else {
                    pagerAdapter.updateEvents(new ArrayList<>(), filteredEvents, true);
                }
            }
            
            // Actualizar títulos de pestañas - solo la pestaña activa
            if (currentTab == 0) {
                updateTabTitles(filteredEvents.size(), pastEvents.size());
            } else {
                updateTabTitles(futureEvents.size(), filteredEvents.size());
            }
            
            // Deshabilitar cambio de pestañas
            disableTabSwitching();
            
            // Mostrar indicador de filtros
            filterIndicatorLayout.setVisibility(View.VISIBLE);
            filterIndicatorText.setText(getString(R.string.filters_summary, currentSearchFilter.getActiveFiltersSummary(this)));
            
            // Mostrar mensaje con número de resultados
            if (filteredEvents.isEmpty()) {
                ToastUtils.showCustomToast(this, getString(R.string.toast_no_events_match_tab), ToastUtils.ToastType.INFO);
            } else {
                String message = getResources().getQuantityString(
                        R.plurals.search_results_events, filteredEvents.size(), filteredEvents.size());
                ToastUtils.showCustomToast(this, message, ToastUtils.ToastType.INFO);
            }
        } else {
            // Sin búsqueda activa, mostrar todos los eventos
            if (pagerAdapter != null) {
                pagerAdapter.updateEvents(futureEvents, pastEvents, false);
            }
            
            // Actualizar títulos de pestañas
            updateTabTitles(futureEvents.size(), pastEvents.size());
            
            // Habilitar cambio de pestañas
            enableTabSwitching();
            
            // Ocultar indicador de filtros
            filterIndicatorLayout.setVisibility(View.GONE);
        }

        updateActionButtonsState(tabLayout.getSelectedTabPosition());
    }
    
    private void clearSearchFilters() {
        currentSearchFilter.clearFilters();
        applySearchFilters();
        ToastUtils.showCustomToast(this, getString(R.string.toast_filters_cleared), ToastUtils.ToastType.INFO);
    }
    
    private void updateTabTitles(int futureCount, int pastCount) {
        if (tabLayout != null) {
            TabLayout.Tab futureTab = tabLayout.getTabAt(0);
            TabLayout.Tab pastTab = tabLayout.getTabAt(1);
            
            if (currentSearchFilter.hasActiveFilters()) {
                // Solo la pestaña activa tiene asterisco
                int currentTab = tabLayout.getSelectedTabPosition();
                if (futureTab != null) {
                    if (currentTab == 0) {
                        futureTab.setText(getString(R.string.organizer_tab_future_filtered, futureCount));
                    } else {
                        futureTab.setText(getString(R.string.organizer_tab_future, futureCount));
                    }
                }
                if (pastTab != null) {
                    if (currentTab == 1) {
                        pastTab.setText(getString(R.string.organizer_tab_past_filtered, pastCount));
                    } else {
                        pastTab.setText(getString(R.string.organizer_tab_past, pastCount));
                    }
                }
            } else {
                if (futureTab != null) {
                    futureTab.setText(getString(R.string.organizer_tab_future, futureCount));
                }
                if (pastTab != null) {
                    pastTab.setText(getString(R.string.organizer_tab_past, pastCount));
                }
            }
        }
    }
    
    private void disableTabSwitching() {
        if (tabLayout != null) {
            int currentTab = tabLayout.getSelectedTabPosition();
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null) {
                    if (i == currentTab) {
                        // Pestaña activa: habilitada y opacidad normal
                        tab.view.setEnabled(true);
                        tab.view.setAlpha(1.0f);
                    } else {
                        // Pestañas no activas: deshabilitadas y opacidad reducida
                        tab.view.setEnabled(false);
                        tab.view.setAlpha(0.5f);
                    }
                }
            }
        }
    }
    
    private void enableTabSwitching() {
        if (tabLayout != null) {
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null) {
                    tab.view.setEnabled(true);
                    tab.view.setAlpha(1.0f);
                }
            }
        }
    }
    
    /**
     * Método llamado cuando se solicita un refresh desde un fragment
     */
    public void onRefreshRequested() {
        if (eventViewModel != null) {
            eventViewModel.restartListeningUserEvents();
        }
    }
} 