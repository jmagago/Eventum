package com.us.eventum.ui.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.ui.adapters.AttendeeAdapter;
import com.us.eventum.ui.adapters.EventsPagerAdapter;
import com.us.eventum.core.utils.ActivityLogPanelController;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.models.Event;
import com.us.eventum.R;
import com.us.eventum.ui.viewmodels.AttendeeViewModel;
import com.us.eventum.ui.viewmodels.EventViewModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.widget.PopupMenu;

import android.widget.ImageView;
import com.us.eventum.core.utils.EventCapacityFormatter;
import com.us.eventum.core.utils.EventImageManager;
import com.us.eventum.core.utils.EventPrivateAccessCode;
import com.us.eventum.core.utils.EventUiMerger;
import com.us.eventum.core.utils.LocaleUtils;
import com.us.eventum.core.utils.PrivateAccessCodeDialogHelper;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.NotificationPermissionHelper;
import com.us.eventum.core.utils.NetworkUtils;
import com.us.eventum.core.utils.OrganizerNotificationDispatcher;
import com.us.eventum.core.utils.OrganizerNotificationHelper;
import com.us.eventum.core.utils.OrganizerNotificationWatcher;
import com.us.eventum.core.utils.ProfileImageManager;
import com.us.eventum.core.utils.FabBarController;
import com.us.eventum.core.utils.WindowInsetsHelper;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.core.utils.AttendeeCsvExportHelper;
import com.us.eventum.core.utils.AttendeeSearchFilter;
import com.us.eventum.core.utils.EditEventPanelController;
import com.us.eventum.core.utils.SearchAttendeePanelController;
import com.us.eventum.core.utils.QrCheckInResult;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.core.utils.VibrationUtils;
import com.us.eventum.core.utils.WaitlistDialogHelper;
import com.us.eventum.ui.viewmodels.SharedViewModel;

import de.hdodenhof.circleimageview.CircleImageView;

import androidx.core.util.Consumer;

public class EventDetailsActivity extends AppCompatActivity {
    private Event event;
    
    private ActivityResultLauncher<Intent> qrScannerLauncher;
    private ActivityResultLauncher<String> pickEventImageLauncher;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });
    private AttendeeCsvExportHelper csvExportHelper;
    private EditEventPanelController editEventPanelController;
    private SearchAttendeePanelController searchAttendeePanelController;
    private ActivityLogPanelController activityLogPanelController;
    private Consumer<Uri> eventImagePickHandler;
    private FirebaseFirestore db;
    private AttendeeAdapter attendeeAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Attendee> attendees = new ArrayList<>();
    private int activeWaitlistCount;
    private TextView dateTextView, locationTextView, descriptionTextView;
    private TextView eventTimeTextView;
    private TextView emptyAttendeesTextView;
    private ImageView eventPrivateIconDetails;
    private TextView eventCancelledBanner;
    private MaterialCardView eventInfoCard;
    private FloatingActionButton searchAttendeeFab;
    private FloatingActionButton verifyQrFab;
    private MaterialCardView fabContainer;
    private FabBarController fabBarController;
    private RecyclerView attendeesRecyclerView;
    private SwipeRefreshLayout attendeesSwipeRefresh;
    private ImageButton eventMenuButton;
    private SharedViewModel sharedViewModel;
    private AttendeeViewModel attendeeViewModel;
    private EventViewModel eventViewModel;
    private AlertDialog confirmDialog;
    private AlertDialog attendeeDetailsDialog;
    private String pendingManualCheckInAttendeeId;
    private boolean manualCheckInAwaitingResult;
    
    // Variables para búsqueda de asistentes
    private AttendeeSearchFilter currentSearchFilter = new AttendeeSearchFilter();
    private boolean isSearchActive = false;
    private List<Attendee> allAttendees = new ArrayList<>(); // Lista completa de asistentes
    private List<Attendee> filteredAttendees = new ArrayList<>(); // Lista filtrada
    private Map<String, Boolean> scannedAttendeesMap = new HashMap<>(); // Map de asistentes escaneados (userId -> isScanned)
    
    // Elementos del indicador de filtros
    private LinearLayout filterIndicatorLayout;
    private TextView filterIndicatorText;
    private ImageView clearFiltersButton;
    private OrganizerNotificationWatcher notificationWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);
        setupStatusBarStripe();

        // Inicializar ViewModels
        sharedViewModel = SharedViewModel.getInstance();
            attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
            eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
            
            // Inicializar repositorios en ViewModels
            attendeeViewModel.initializeRepository(this);
            eventViewModel.initializeRepository(this);

        notificationWatcher = new OrganizerNotificationWatcher(this);
        NotificationPermissionHelper.requestIfNeeded(this, notificationPermissionLauncher);
        
        // Inicializar ActivityResultLauncher
        qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && event != null && event.getId() != null) {
                    attendeeViewModel.restartListeningEventAttendees(event.getId());
                }
            }
        );

        pickEventImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && eventImagePickHandler != null) {
                        eventImagePickHandler.accept(uri);
                    }
                });

        csvExportHelper = new AttendeeCsvExportHelper(this);

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
        event = IntentCompat.getParcelableExtra(getIntent(), "event", Event.class);
        String eventIdExtra = getIntent().getStringExtra("eventId");

        if (event != null) {
            setupEventUi(toolbarTitleTextView);
        } else if (eventIdExtra != null && !eventIdExtra.isEmpty()) {
            eventViewModel.getEventRepository().getEventById(eventIdExtra,
                    new com.us.eventum.data.repositories.EventRepository.RepositoryCallback<Event>() {
                        @Override
                        public void onSuccess(Event loaded) {
                            event = loaded;
                            setupEventUi(toolbarTitleTextView);
                        }

                        @Override
                        public void onError(String error) {
                            ToastUtils.showCustomToast(EventDetailsActivity.this, error,
                                    ToastUtils.ToastType.ERROR);
                            finish();
                        }
                    });
        } else {
            ToastUtils.showCustomToast(this, "Error al cargar el evento", ToastUtils.ToastType.ERROR);
            finish();
        }
    }

    private void setupStatusBarStripe() {
        View stripe = findViewById(R.id.statusBarStripe);
        if (stripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(stripe);
        }
    }

    private void setupEventUi(TextView toolbarTitleTextView) {
        if (event == null) {
            finish();
            return;
        }
        toolbarTitleTextView.setText(event.getTitle());
        CircleImageView toolbarEventImageView = findViewById(R.id.toolbarEventImageView);
        if (toolbarEventImageView != null) {
            EventImageManager.loadEventImage(this, toolbarEventImageView, event.getId());
        }
        initializeViews();
        setupRecyclerView();
        setupAttendeesSwipeRefresh();
        displayEventDetails();
        observeViewModels();
        startRealtimeSync();

        if (getIntent().getBooleanExtra("show_clear_dialog", false)) {
            showClearAttendeeListConfirmation();
        } else if (getIntent().getBooleanExtra("show_delete_dialog", false)) {
            showDeleteEventDialog();
        } else if (getIntent().getBooleanExtra("show_activity_log", false)) {
            showActivityLogPanel();
        }
    }

    private void initializeViews() {
        dateTextView = findViewById(R.id.eventDateTextView);
        eventTimeTextView = findViewById(R.id.eventTimeTextView);
        locationTextView = findViewById(R.id.eventLocationTextView);
        descriptionTextView = findViewById(R.id.eventDescriptionTextView);
        emptyAttendeesTextView = findViewById(R.id.emptyAttendeesTextView);
        eventPrivateIconDetails = findViewById(R.id.eventPrivateIconDetails);
        eventCancelledBanner = findViewById(R.id.eventCancelledBanner);
        eventInfoCard = findViewById(R.id.eventInfoCard);
        searchAttendeeFab = findViewById(R.id.searchAttendeeFab);
        verifyQrFab = findViewById(R.id.verifyQrFab);
        View fabDock = findViewById(R.id.fabDock);
        fabContainer = findViewById(R.id.fabContainer);
        View fabBarHandle = findViewById(R.id.fabBarHandle);
        View fabBarCollapse = findViewById(R.id.fabBarCollapse);
        fabBarController = new FabBarController(this, fabDock, fabContainer, fabBarHandle, fabBarCollapse);
        fabBarController.attachToActivity(this);
        attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        if (attendeesRecyclerView != null) {
            WindowInsetsHelper.applyOverlayFabListPadding(attendeesRecyclerView);
        }
        attendeesSwipeRefresh = findViewById(R.id.attendeesSwipeRefresh);
        eventMenuButton = findViewById(R.id.eventMenuButton);
        
        // Elementos del indicador de filtros
        filterIndicatorLayout = findViewById(R.id.filterIndicatorLayout);
        filterIndicatorText = findViewById(R.id.filterIndicatorText);
        clearFiltersButton = findViewById(R.id.clearFiltersButton);

        eventMenuButton.setOnClickListener(v -> showEventMenu());

        // Abrir búsqueda de asistente
        searchAttendeeFab.setOnClickListener(v -> {
            if (searchAttendeePanelController != null) {
                searchAttendeePanelController.show();
            }
        });
        // Abrir lector QR
        verifyQrFab.setOnClickListener(v -> startQRScanner());
        
        // Configurar click listener para limpiar filtros
        if (clearFiltersButton != null) {
            clearFiltersButton.setOnClickListener(v -> clearSearchFilters());
        }

        setupActivityLogPanel();
        setupEditEventPanel();
        setupAttendeePanels();
        setupPanelBackHandler();

        // Configurar clic largo en el icono del candado para cambiar privacidad
        if (eventPrivateIconDetails != null) {
            eventPrivateIconDetails.setOnLongClickListener(v -> {
                if (event != null && event.isCancelled()) {
                    ToastUtils.showCustomToast(this, getString(R.string.event_cancelled_cannot_edit),
                            ToastUtils.ToastType.INFO);
                    return true;
                }
                if (event != null && event.getId() != null) {
                    boolean newPrivateState = !event.getPrivateEvent();
                    if (newPrivateState) {
                        PrivateAccessCodeDialogHelper.show(this, new PrivateAccessCodeDialogHelper.Callback() {
                            @Override
                            public void onCodeConfirmed(@NonNull String accessCode) {
                                applyPrivacyChange(true, accessCode);
                            }

                            @Override
                            public void onCancelled() {
                                // Sin cambios
                            }
                        });
                    } else {
                        applyPrivacyChange(false, null);
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private void applyPrivacyChange(boolean isPrivate, @Nullable String accessCode) {
        if (event == null || event.getId() == null) {
            return;
        }
        event.setPrivateEvent(isPrivate);
        event.setPrivateAccessCode(isPrivate ? EventPrivateAccessCode.normalize(accessCode) : null);
        event.setPrivateAccessCodeHash(isPrivate
                ? EventPrivateAccessCode.hash(EventPrivateAccessCode.normalize(accessCode)) : null);
        updateLockIcon(isPrivate);
        displayEventDetails();
        eventViewModel.updateEventPrivacy(event.getId(), isPrivate, accessCode);
    }

    private void startRealtimeSync() {
        if (event == null || event.getId() == null) {
            return;
        }
        eventViewModel.startListeningEvent(event.getId());
        attendeeViewModel.startListeningEventAttendees(event.getId());
        attendeeViewModel.startListeningEventWaitlist(event.getId());
    }

    private void observeViewModels() {
        eventViewModel.getObservedEvent().observe(this, updated -> {
            if (updated == null) {
                if (event != null) {
                    ToastUtils.showCustomToast(this,
                            "Este evento ya no está disponible",
                            ToastUtils.ToastType.WARNING);
                    finish();
                }
                return;
            }
            event = updated;
            TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
            if (toolbarTitleTextView != null) {
                toolbarTitleTextView.setText(updated.getTitle());
            }
            displayEventDetails();
        });

        attendeeViewModel.getScannedAttendeesMap().observe(this, map -> {
            if (map == null) {
                return;
            }
            scannedAttendeesMap.clear();
            scannedAttendeesMap.putAll(map);
            if (attendeeAdapter != null) {
                attendeeAdapter.setScannedAttendeesMap(scannedAttendeesMap);
            }
            updateAttendeesCount();
        });

        attendeeViewModel.getAttendees().observe(this, attendeesList -> {
            if (attendeesList != null) {
                // Actualizar lista completa de asistentes
                allAttendees.clear();
                allAttendees.addAll(attendeesList);
                
                // Actualizar lista de asistentes para mostrar
                attendees.clear();
                attendees.addAll(attendeesList);
                
                // Aplicar filtros si hay búsqueda activa
                if (isSearchActive) {
                    applySearchFilters();
                } else {
                    // Sin búsqueda activa, mostrar todos los asistentes
                    attendeeAdapter.setAttendees(attendeesList);
                }
                
                updateAttendeesCount();
                updateSearchButtonState();
                updateQRButtonState();
                
                // Mostrar/ocultar mensaje cuando no hay asistentes
                if (attendeesList.isEmpty()) {
                    if (emptyAttendeesTextView != null) {
                        emptyAttendeesTextView.setVisibility(View.VISIBLE);
                    } else {
                        Log.e("EventDetailsActivity", "emptyAttendeesTextView es null");
                    }
                } else {
                    if (emptyAttendeesTextView != null) {
                        emptyAttendeesTextView.setVisibility(View.GONE);
                    } else {
                        Log.e("EventDetailsActivity", "emptyAttendeesTextView es null");
                    }
                }
            }
            if (attendeesSwipeRefresh != null) {
                attendeesSwipeRefresh.setRefreshing(false);
            }
        });

        attendeeViewModel.getEventWaitlist().observe(this, waitlistEntries -> {
            int count = 0;
            if (waitlistEntries != null) {
                for (WaitlistToEvent entry : waitlistEntries) {
                    if (entry != null && entry.isActiveForUser()) {
                        count++;
                    }
                }
            }
            activeWaitlistCount = count;
            if (event != null) {
                event.setWaitlistCount(count);
            }
            bindCapacityText();
        });

        // Observar estado de carga
        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (Boolean.FALSE.equals(loading) && attendeesSwipeRefresh != null) {
                attendeesSwipeRefresh.setRefreshing(false);
            }
        });

        // Observar cuando se elimina un asistente
        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (deleted) {
                if (event != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
                attendeeViewModel.clearOperationStates();
            }
        });

        // Observar errores
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error == null || error.isEmpty()) {
                return;
            }
            if (manualCheckInAwaitingResult) {
                String attendeeId = pendingManualCheckInAttendeeId;
                cancelPendingManualCheckIn();
                VibrationUtils.vibrateError(this);
                showManualCheckInFeedback(error, ToastUtils.ToastType.ERROR);
                refreshVerifyAttendanceButton(attendeeId);
                attendeeViewModel.clearOperationStates();
                return;
            }
            ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
        });

        attendeeViewModel.getAttendeeUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                ToastUtils.showCustomToast(this, "Asistente actualizado con éxito", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
            }
        });

        attendeeViewModel.getQrCheckInResult().observe(this, result -> {
            if (result == null || !manualCheckInAwaitingResult || pendingManualCheckInAttendeeId == null) {
                return;
            }
            manualCheckInAwaitingResult = false;
            String attendeeId = pendingManualCheckInAttendeeId;
            pendingManualCheckInAttendeeId = null;

            switch (result) {
                case VALID:
                    scannedAttendeesMap.put(attendeeId, true);
                    attendeeAdapter.setScannedAttendeesMap(scannedAttendeesMap);
                    updateAttendeesCount();
                    VibrationUtils.vibrateSuccess(this);
                    dismissAttendeeDetailsDialog();
                    showManualCheckInFeedback(R.string.manual_checkin_success, ToastUtils.ToastType.SUCCESS);
                    break;
                case ALREADY_USED:
                    scannedAttendeesMap.put(attendeeId, true);
                    attendeeAdapter.setScannedAttendeesMap(scannedAttendeesMap);
                    updateAttendeesCount();
                    VibrationUtils.vibrateWarning(this);
                    showManualCheckInFeedback(R.string.manual_checkin_already, ToastUtils.ToastType.WARNING);
                    refreshVerifyAttendanceButton(attendeeId);
                    break;
                case NOT_REGISTERED:
                    VibrationUtils.vibrateError(this);
                    showManualCheckInFeedback(R.string.qr_checkin_not_registered, ToastUtils.ToastType.ERROR);
                    refreshVerifyAttendanceButton(attendeeId);
                    break;
                default:
                    VibrationUtils.vibrateError(this);
                    showManualCheckInFeedback(R.string.qr_checkin_error_generic, ToastUtils.ToastType.ERROR);
                    refreshVerifyAttendanceButton(attendeeId);
                    break;
            }
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, "Asistente eliminado con éxito", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
            }
        });

        // Observar limpieza de lista de asistentes
        attendeeViewModel.getAttendeesCleared().observe(this, cleared -> {
            if (cleared != null && cleared) {
                ToastUtils.showCustomToast(this, "Lista de asistentes vaciada con éxito", ToastUtils.ToastType.SUCCESS);
                if (event != null) {
                    sharedViewModel.notifyEventsUpdated();
                }
                attendeeViewModel.clearOperationStates();
            }
        });

        eventViewModel.getEventUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                displayEventDetails();
                sharedViewModel.notifyEventsUpdated();
                if (event != null && event.getId() != null) {
                    attendeeViewModel.promoteWaitlistIfNeeded(event.getId());
                }
            }
        });

        eventViewModel.getEventUpdateMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                ToastUtils.showCustomToast(this, message, ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        // Observar errores del EventViewModel
        eventViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar eliminación de evento
        eventViewModel.getEventDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, "Evento eliminado con éxito", ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
                finish(); // Cerrar la actividad
            }
        });

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

    private void setupRecyclerView() {
        attendeesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendeeAdapter = new AttendeeAdapter(attendee -> showAttendeeDetails(attendee));
        attendeesRecyclerView.setAdapter(attendeeAdapter);
    }

    private void setupAttendeesSwipeRefresh() {
        if (attendeesSwipeRefresh == null) {
            return;
        }
        attendeesSwipeRefresh.setOnRefreshListener(() -> {
            if (event != null && event.getId() != null) {
                attendeeViewModel.restartListeningEventAttendees(event.getId());
                eventViewModel.restartListeningEvent(event.getId());
            } else if (attendeesSwipeRefresh != null) {
                attendeesSwipeRefresh.setRefreshing(false);
            }
        });
        attendeesSwipeRefresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent
        );
    }

    private void updateLockIcon(boolean isPrivate) {
        if (eventPrivateIconDetails != null) {
            if (isPrivate) {
                eventPrivateIconDetails.setImageResource(R.drawable.ic_lock_closed);
                eventPrivateIconDetails.setVisibility(View.VISIBLE);
            } else {
                eventPrivateIconDetails.setImageResource(R.drawable.ic_lock_open);
                eventPrivateIconDetails.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showDatePicker(TextInputEditText dateInput, Calendar calendar) {
        DatePickerDialog datePicker = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                dateInput.setText(dateFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.show();
    }

    private void bindCapacityText() {
        TextView capacityTextView = findViewById(R.id.eventCapacityTextView);
        if (capacityTextView == null || event == null) {
            return;
        }
        String capacityText = getResources().getQuantityString(
                R.plurals.event_capacity_detail,
                event.getMaxParticipants(),
                attendees.size(),
                event.getMaxParticipants());
        capacityTextView.setText(EventCapacityFormatter.appendWaitlist(
                this, capacityText, event.isCancelled() ? 0 : activeWaitlistCount));
        capacityTextView.setVisibility(View.VISIBLE);
    }

    private void displayEventDetails() {
        if (event == null) return;
        
        // Crear formato de fecha más completo
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", com.us.eventum.core.utils.LocaleUtils.spanish());
        String formattedDate = fullDateFormat.format(event.getDate());
        formattedDate = LocaleUtils.capitalizeFirst(formattedDate);
        
        // Formato más profesional para fecha, capacidad y ubicación
        String dateText = LocaleUtils.format("📅  %s", formattedDate);
        String locationText = LocaleUtils.format("📍  %s", event.getLocation());
        
        dateTextView.setText(dateText);
        // Hora en formato 24h HH:mm (solo texto, el icono ya está en la UI como parte del estilo de lista)
        try {
            SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", LocaleUtils.spanish());
            if (eventTimeTextView != null) {
                eventTimeTextView.setText(hourFormat.format(event.getDate()));
            }
        } catch (Exception ignore) {}
        locationTextView.setText(locationText);
        
        bindCapacityText();

        // Mostrar el número de asistentes verificados
        TextView verifiedTextView = findViewById(R.id.verifiedTextView);
        
        if (verifiedTextView != null) {
            // No inicializar con 0 si ya hay un valor (evitar parpadeo)
            String currentText = verifiedTextView.getText().toString();
            String zeroVerified = getResources().getQuantityString(R.plurals.event_verified_attendees, 0, 0);
            if (currentText.isEmpty() || currentText.equals(zeroVerified)) {
                // Solo inicializar con 0 si no hay valor previo
                verifiedTextView.setText(zeroVerified);
            }
            verifiedTextView.setVisibility(View.VISIBLE);
        }
        
        // Obtener lista de UIDs de asistentes actuales para filtrar
        List<String> currentAttendeeIds = new ArrayList<>();
        for (Attendee attendee : attendees) {
            if (attendee.getUid() != null) {
                currentAttendeeIds.add(attendee.getUid());
            }
        }
        
        // Contar asistentes escaneados (asistencia registrada)
        // Solo contamos los que están en la lista actual (filtra registros huérfanos)
        attendeeViewModel.loadScannedAttendeesCount(event.getId(), currentAttendeeIds, new AttendeeRepository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer scannedCount) {
                // Actualizar la UI con el conteo real de asistentes escaneados
                runOnUiThread(() -> {
                    TextView verifiedTextView = findViewById(R.id.verifiedTextView);
                    if (verifiedTextView != null) {
                        int count = scannedCount != null ? scannedCount : 0;
                        String verifiedText = getResources().getQuantityString(
                                R.plurals.event_verified_attendees, count, count);
                        verifiedTextView.setText(verifiedText);
                    }
                });
            }

            @Override
            public void onError(String error) {
                // En caso de error, mantener el valor anterior si existe
                runOnUiThread(() -> {
                    TextView verifiedTextView = findViewById(R.id.verifiedTextView);
                    if (verifiedTextView != null) {
                        String currentText = verifiedTextView.getText().toString();
                        String zeroVerified = getResources().getQuantityString(R.plurals.event_verified_attendees, 0, 0);
                        if (currentText.isEmpty() || currentText.equals(zeroVerified)) {
                            verifiedTextView.setText(zeroVerified);
                        }
                        // Si ya hay un valor, mantenerlo en lugar de poner 0
                    }
                });
            }
        });

        // Mostrar icono del candado según el estado de privacidad
        updateLockIcon(event.getPrivateEvent());
        bindCancelledState();

        // Mostrar descripción si existe
        View descriptionSpacer = findViewById(R.id.descriptionSpacer);
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            descriptionTextView.setText(event.getDescription());
            descriptionTextView.setVisibility(View.VISIBLE);
            if (descriptionSpacer != null) {
                descriptionSpacer.setVisibility(View.VISIBLE);
            }
        } else {
            descriptionTextView.setVisibility(View.GONE);
            if (descriptionSpacer != null) {
                descriptionSpacer.setVisibility(View.GONE);
            }
        }
    }

    private void updateAttendeesCount() {
        displayEventDetails();
    }

    private void showAttendeeDetails(Attendee attendee) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attendee_details, null);

        TextView nameTextView = dialogView.findViewById(R.id.detailNameTextView);
        ImageView profileImageView = dialogView.findViewById(R.id.detailProfileImageView);
        TextView dniTextView = dialogView.findViewById(R.id.detailDniTextView);
        TextView emailTextView = dialogView.findViewById(R.id.detailEmailTextView);
        TextView phoneTextView = dialogView.findViewById(R.id.detailPhoneTextView);
        TextView birthDateTextView = dialogView.findViewById(R.id.detailBirthDateTextView);
        TextView parentalAuthTextView = dialogView.findViewById(R.id.detailParentalAuthTextView);
        MaterialButton verifyAttendanceButton = dialogView.findViewById(R.id.dialog_verify_attendance_button);
        
        // Referencia a los botones personalizados
        MaterialButton cancelButton = dialogView.findViewById(R.id.dialog_cancel_button);
        MaterialButton deleteButton = dialogView.findViewById(R.id.dialog_delete_button);

        nameTextView.setText(attendee.getFullNameLabel());
        ProfileImageManager.loadProfileImageForUserId(this, profileImageView, attendee.getUid(), available -> {
            profileImageView.setClickable(available);
            profileImageView.setFocusable(available);
            if (available) {
                TypedValue ripple = new TypedValue();
                getTheme().resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, ripple, true);
                profileImageView.setBackgroundResource(ripple.resourceId);
                profileImageView.setOnClickListener(v ->
                        ProfileImageManager.showFullScreenProfileImage(
                                EventDetailsActivity.this, attendee.getUid()));
            } else {
                profileImageView.setBackground(null);
                profileImageView.setOnClickListener(null);
            }
        });

        // Mostrar DNI
        String dni = attendee.getDni();
        if (dni != null && !dni.isEmpty()) {
            dniTextView.setText(dni);
            dniTextView.setVisibility(View.VISIBLE);
        } else {
            dniTextView.setVisibility(View.GONE);
        }
        
        emailTextView.setText(attendee.getEmail());

        // Mostrar teléfono
        String phone = attendee.getPhone();
        if (phone != null && !phone.isEmpty()) {
            phoneTextView.setText(phone);
            phoneTextView.setVisibility(View.VISIBLE);
        } else {
            phoneTextView.setVisibility(View.GONE);
        }

        // Mostrar fecha de nacimiento
        if (attendee.getFechaNacimiento() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            birthDateTextView.setText(dateFormat.format(attendee.getFechaNacimiento().toDate()));
        } else {
            birthDateTextView.setText(R.string.birth_date_not_specified);
        }

        // Mostrar estado de autorización parental basado en el evento
        String authStatus = event.getRequiresParentalAuth() ? 
            "Requiere autorización parental" : "No requiere autorización parental";
        parentalAuthTextView.setText(authStatus);

        boolean attendanceVerified = Boolean.TRUE.equals(scannedAttendeesMap.get(attendee.getUid()));
        boolean allowManualVerify = !isEventInactive();
        updateVerifyAttendanceButton(verifyAttendanceButton, attendanceVerified, allowManualVerify);

        if (allowManualVerify && !attendanceVerified) {
            verifyAttendanceButton.setOnClickListener(v -> confirmManualCheckIn(attendee, dialogView));
        }

        // Configurar diálogo sin botones estándar
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        attendeeDetailsDialog = dialog;
        
        // Configurar acciones para los botones personalizados
        cancelButton.setOnClickListener(v -> {
            cancelPendingManualCheckIn();
            attendeeDetailsDialog = null;
            dialog.dismiss();
        });
        
        deleteButton.setOnClickListener(v -> {
            // Mostrar diálogo de confirmación
            View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
            TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
            MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
            MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
            
            confirmMessageTextView.setText(R.string.delete_attendee_message);
            
            AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
            confirmBuilder.setView(confirmDialogView);
            
            confirmDialog = confirmBuilder.create();
            
            confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
            
            confirmDeleteButton.setOnClickListener(cv -> {
                deleteAttendee(attendee, dialog);
                confirmDialog.dismiss();
            });
            
            confirmDialog.show();
        });
        
        dialog.setOnDismissListener(d -> {
            if (attendeeDetailsDialog == dialog) {
                attendeeDetailsDialog = null;
            }
        });

        dialog.show();
    }

    private void confirmManualCheckIn(Attendee attendee, View dialogView) {
        if (event == null || event.getId() == null) {
            return;
        }

        View confirmView = getLayoutInflater().inflate(R.layout.dialog_confirm_manual_checkin, null);
        TextView messageTextView = confirmView.findViewById(R.id.manual_checkin_confirm_message);
        TextView eventHintTextView = confirmView.findViewById(R.id.manual_checkin_event_hint);
        MaterialButton cancelButton = confirmView.findViewById(R.id.manual_checkin_cancel_button);
        MaterialButton confirmButton = confirmView.findViewById(R.id.manual_checkin_confirm_button);

        messageTextView.setText(
                getString(R.string.manual_checkin_confirm_message, attendee.getFullNameLabel()));
        if (event.getTitle() != null && !event.getTitle().trim().isEmpty()) {
            eventHintTextView.setVisibility(View.VISIBLE);
            eventHintTextView.setText(getString(R.string.manual_checkin_event_hint, event.getTitle()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        builder.setView(confirmView);
        AlertDialog confirmDialog = builder.create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        cancelButton.setOnClickListener(v -> confirmDialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            if (!NetworkUtils.isOnline(this)) {
                showManualCheckInFeedback(R.string.manual_checkin_error_network, ToastUtils.ToastType.ERROR);
                return;
            }
            MaterialButton verifyButton = dialogView.findViewById(R.id.dialog_verify_attendance_button);
            if (verifyButton != null) {
                verifyButton.setEnabled(false);
            }
            manualCheckInAwaitingResult = true;
            pendingManualCheckInAttendeeId = attendee.getUid();
            attendeeViewModel.verifyAttendeeCheckIn(
                    attendee.getUid(),
                    event.getId(),
                    AttendeeViewModel.CheckInMethod.MANUAL,
                    attendee.getFullNameLabel());
            confirmDialog.dismiss();
        });

        confirmDialog.show();
    }

    private void cancelPendingManualCheckIn() {
        manualCheckInAwaitingResult = false;
        pendingManualCheckInAttendeeId = null;
    }

    private void dismissAttendeeDetailsDialog() {
        if (attendeeDetailsDialog != null && attendeeDetailsDialog.isShowing()) {
            attendeeDetailsDialog.dismiss();
        }
        attendeeDetailsDialog = null;
    }

    private void showManualCheckInFeedback(int messageResId, ToastUtils.ToastType type) {
        showManualCheckInFeedback(getString(messageResId), type);
    }

    private void showManualCheckInFeedback(String message, ToastUtils.ToastType type) {
        if (message == null || message.isEmpty()) {
            message = getString(R.string.qr_checkin_error_generic);
        }
        View anchor = attendeesRecyclerView != null ? attendeesRecyclerView : findViewById(android.R.id.content);
        String finalMessage = message;
        if (anchor != null) {
            anchor.post(() -> ToastUtils.showCustomToastOnAnchor(anchor, finalMessage, type));
        } else {
            ToastUtils.showCustomToast(this, finalMessage, type);
        }
    }

    private void updateVerifyAttendanceButton(MaterialButton verifyButton,
                                              boolean verified, boolean allowManualVerify) {
        if (verifyButton == null) {
            return;
        }
        boolean showButton = allowManualVerify && !verified;
        verifyButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        verifyButton.setEnabled(showButton);
    }

    private void refreshVerifyAttendanceButton(String attendeeId) {
        if (attendeeDetailsDialog == null || !attendeeDetailsDialog.isShowing()) {
            return;
        }
        MaterialButton verifyButton = attendeeDetailsDialog.findViewById(R.id.dialog_verify_attendance_button);
        boolean verified = Boolean.TRUE.equals(scannedAttendeesMap.get(attendeeId));
        updateVerifyAttendanceButton(verifyButton, verified, !isEventInactive());
    }

    private void deleteAttendee(Attendee attendee, AlertDialog detailsDialog) {
        // Usar AttendeeViewModel para eliminar el asistente del evento
        // Esto elimina tanto el registro en AttendeesToEvent como el perfil del asistente
        if (event != null && event.getId() != null) {
            attendeeViewModel.removeAttendeeFromEvent(
                    attendee.getUid(), event.getId(), attendee.getFullNameLabel());
        } else {
            // Fallback: si no hay evento, solo eliminar el perfil
            attendeeViewModel.deleteAttendee(attendee.getUid());
        }
        
        // Cerrar los diálogos
        if (detailsDialog != null) {
            detailsDialog.dismiss();
        }
        if (confirmDialog != null) {
            confirmDialog.dismiss();
        }
    }

    private void showDeleteEventDialog() {
        // Usar el mismo estilo de diálogo personalizado para eliminar evento
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        
        // Cambiar el título en la cabecera manualmente (está hardcodeado en el XML)
        TextView titleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        if (titleTextView != null) {
            titleTextView.setText(R.string.delete_event_title);
        }
        
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

    private void showCancelEventDialog() {
        if (event == null || event.getId() == null) {
            return;
        }
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        TextView titleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        ImageView confirmIcon = confirmDialogView.findViewById(R.id.confirm_icon);
        if (titleTextView != null) {
            titleTextView.setText(R.string.cancel_event_title);
        }
        if (confirmIcon != null) {
            confirmIcon.setImageResource(R.drawable.ic_warning);
        }
        confirmMessageTextView.setText(R.string.cancel_event_message);
        confirmDeleteButton.setText(R.string.cancel_event_confirm);

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

    private void setupEditEventPanel() {
        editEventPanelController = new EditEventPanelController(this,
                handler -> {
                    eventImagePickHandler = handler;
                    pickEventImageLauncher.launch("image/*");
                },
                eventViewModel);
    }

    private void setupAttendeePanels() {
        searchAttendeePanelController = new SearchAttendeePanelController(
                this,
                filter -> {
                    currentSearchFilter = filter;
                    applySearchFilters();
                },
                () -> currentSearchFilter,
                () -> isSearchActive);
    }

    private void openEditEventPanel() {
        if (event == null || editEventPanelController == null) {
            return;
        }
        if (event.isCancelled()) {
            ToastUtils.showCustomToast(this, getString(R.string.event_cancelled_cannot_edit),
                    ToastUtils.ToastType.INFO);
            return;
        }
        editEventPanelController.show(event, new EditEventPanelController.SaveListener() {
            @Override
            public void onEventSaved(@NonNull Event savedEvent, @Nullable Uri newImageUri) {
                EventUiMerger.copyDocumentFields(event, savedEvent);
                sharedViewModel.notifyEventsUpdated();
                if (savedEvent.getId() != null && attendeeViewModel != null) {
                    attendeeViewModel.promoteWaitlistIfNeeded(savedEvent.getId());
                }
                TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
                if (toolbarTitleTextView != null) {
                    toolbarTitleTextView.setText(savedEvent.getTitle());
                }
                CircleImageView toolbarEventImageView = findViewById(R.id.toolbarEventImageView);
                if (toolbarEventImageView != null && newImageUri != null) {
                    EventImageManager.loadLocalPreview(EventDetailsActivity.this,
                            toolbarEventImageView, newImageUri);
                }
                updateLockIcon(savedEvent.getPrivateEvent());
                displayEventDetails();
            }

            @Override
            public void onEventImageUploadComplete(@NonNull String eventId) {
                sharedViewModel.notifyEventsUpdated();
                CircleImageView toolbarEventImageView = findViewById(R.id.toolbarEventImageView);
                if (toolbarEventImageView != null) {
                    EventImageManager.loadEventImage(EventDetailsActivity.this,
                            toolbarEventImageView, eventId);
                }
            }

            @Override
            public int getMinParticipantsAllowed() {
                return attendees.size();
            }
        });
    }

    private void exportAttendeesCsv() {
        if (csvExportHelper == null || event == null) {
            return;
        }
        csvExportHelper.export(allAttendees, event.getTitle());
    }

    private void showEventMenu() {
        PopupMenu popup = new PopupMenu(this, eventMenuButton);
        popup.getMenuInflater().inflate(R.menu.menu_event_details, popup.getMenu());
        
        EventsPagerAdapter.configureEventMenu(popup.getMenu(), event, this, activeWaitlistCount);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
        }
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_event) {
                openEditEventPanel();
                return true;
            } else if (id == R.id.action_view_waitlist) {
                if (event != null && event.getId() != null) {
                    WaitlistDialogHelper.show(this, event.getId(), attendeeViewModel);
                }
                return true;
            } else if (id == R.id.action_view_private_access_code) {
                PrivateAccessCodeDialogHelper.showViewCode(this,
                        event != null ? event.getPrivateAccessCode() : null);
                return true;
            } else if (id == R.id.action_verify_attendees) {
                startQRScanner();
                return true;
            } else if (id == R.id.action_clear_list) {
                showClearAttendeeListConfirmation();
                return true;
            } else if (id == R.id.action_event_activity_log) {
                showActivityLogPanel();
                return true;
            } else if (id == R.id.action_export_attendees_csv) {
                exportAttendeesCsv();
                return true;
            } else if (id == R.id.action_cancel_event) {
                showCancelEventDialog();
                return true;
            } else if (id == R.id.action_delete_event) {
                showDeleteEventDialog();
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    private void showClearAttendeeListConfirmation() {
        // Usar el mismo estilo de diálogo personalizado para vaciar lista
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
            // Usar AttendeeViewModel para limpiar la lista de asistentes
            attendeeViewModel.clearEventAttendees(event.getId());
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }
    

    private void startQRScanner() {
        if (isEventInactive()) {
            ToastUtils.showCustomToast(this,
                    event != null && event.isCancelled()
                            ? getString(R.string.qr_scanner_event_cancelled)
                            : "Este evento ya ha finalizado. No es posible verificar asistentes por QR.",
                    ToastUtils.ToastType.INFO);
            return;
        }
        if (event == null || event.getId() == null) {
            ToastUtils.showCustomToast(this, "Error: No se puede escanear QR sin un evento válido", ToastUtils.ToastType.ERROR);
            return;
        }
        
        if (qrScannerLauncher == null) {
            ToastUtils.showCustomToast(this, "Error: El escáner QR no está disponible", ToastUtils.ToastType.ERROR);
            return;
        }
        
        Intent intent = new Intent(this, QRScannerActivity.class);
        intent.putExtra("eventId", event.getId());
        qrScannerLauncher.launch(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_verify_attendees) {
            startQRScanner();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    /**
     * Aplicar filtros de búsqueda a los asistentes
     */
    private void applySearchFilters() {
        isSearchActive = currentSearchFilter.hasActiveFilters();
        
        if (isSearchActive) {
            // Aplicar filtros a la lista completa de asistentes
            filteredAttendees = currentSearchFilter.filterAttendees(allAttendees);
            
            // Actualizar la lista de asistentes mostrada
            attendees.clear();
            attendees.addAll(filteredAttendees);
            
            // Actualizar el adaptador con los asistentes filtrados
            attendeeAdapter.setAttendees(filteredAttendees);
            
            // Mostrar indicador de filtros
            if (filterIndicatorLayout != null) {
                filterIndicatorLayout.setVisibility(View.VISIBLE);
                if (filterIndicatorText != null) {
                    filterIndicatorText.setText(getString(R.string.filters_summary, currentSearchFilter.getActiveFiltersSummary()));
                }
            }
            
            // Mostrar mensaje informativo sobre los resultados
            if (filteredAttendees.isEmpty()) {
                ToastUtils.showCustomToast(this, "No se encontraron asistentes con esos criterios", ToastUtils.ToastType.WARNING);
            } else {
                String message = getResources().getQuantityString(
                        R.plurals.search_results_attendees, filteredAttendees.size(), filteredAttendees.size());
                ToastUtils.showCustomToast(this, message, ToastUtils.ToastType.SUCCESS);
            }
        } else {
            // Sin búsqueda activa, mostrar todos los asistentes
            attendees.clear();
            attendees.addAll(allAttendees);
            attendeeAdapter.setAttendees(allAttendees);
            
            // Ocultar indicador de filtros
            if (filterIndicatorLayout != null) {
                filterIndicatorLayout.setVisibility(View.GONE);
            }
        }
        
        updateAttendeesCount();
    }
    
    /**
     * Limpiar filtros de búsqueda
     */
    private void clearSearchFilters() {
        currentSearchFilter.clearFilters();
        isSearchActive = false;
        applySearchFilters();
        ToastUtils.showCustomToast(this, "Filtros limpiados", ToastUtils.ToastType.INFO);
    }
    
    /**
     * Actualizar el estado del botón de búsqueda
     */
    private void updateSearchButtonState() {
        if (searchAttendeeFab != null) {
            // Deshabilitar si no hay asistentes
            boolean hasAttendees = !allAttendees.isEmpty();
            searchAttendeeFab.setEnabled(hasAttendees);
            searchAttendeeFab.setAlpha(hasAttendees ? 1.0f : 0.5f);
        }
    }

    /**
     * Actualizar el estado del botón de QR
     */
    private void updateQRButtonState() {
        if (verifyQrFab != null) {
            if (isEventInactive()) {
                verifyQrFab.setEnabled(false);
                verifyQrFab.setAlpha(0.5f);
                return;
            }
            // Deshabilitar si no hay asistentes
            boolean hasAttendees = !allAttendees.isEmpty();
            verifyQrFab.setEnabled(hasAttendees);
            verifyQrFab.setAlpha(hasAttendees ? 1.0f : 0.5f);
        }
    }

    private boolean isEventPast() {
        return event != null && event.getDate() != null && event.getDate().before(new Date());
    }

    private boolean isEventInactive() {
        return isEventPast() || (event != null && event.isCancelled());
    }

    private void bindCancelledState() {
        boolean cancelled = event != null && event.isCancelled();
        if (eventCancelledBanner != null) {
            eventCancelledBanner.setVisibility(cancelled ? View.VISIBLE : View.GONE);
        }
        if (eventInfoCard != null) {
            int strokePx = cancelled
                    ? Math.round(3f * getResources().getDisplayMetrics().density)
                    : 0;
            eventInfoCard.setStrokeWidth(strokePx);
            eventInfoCard.setStrokeColor(cancelled
                    ? getColor(R.color.event_cancelled_stroke)
                    : getColor(android.R.color.transparent));
        }
        updateQRButtonState();
    }

    private void setupActivityLogPanel() {
        activityLogPanelController = new ActivityLogPanelController(this);
    }

    private void setupPanelBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchAttendeePanelController != null && searchAttendeePanelController.isVisible()) {
                    searchAttendeePanelController.hide();
                } else if (editEventPanelController != null && editEventPanelController.isVisible()) {
                    editEventPanelController.hide();
                } else if (activityLogPanelController != null && activityLogPanelController.isVisible()) {
                    activityLogPanelController.hide();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void showActivityLogPanel() {
        if (event == null || event.getId() == null || activityLogPanelController == null) {
            return;
        }
        activityLogPanelController.show(event.getId(), event.getTitle());
    }

    private void hideActivityLogPanel() {
        if (activityLogPanelController != null) {
            activityLogPanelController.hide();
        }
    }

    @Override
    protected void onDestroy() {
        if (activityLogPanelController != null) {
            activityLogPanelController.destroy();
        }
        if (eventViewModel != null) {
            eventViewModel.stopListeningEvent();
        }
        if (attendeeViewModel != null) {
            attendeeViewModel.stopListeningEventAttendees();
            attendeeViewModel.stopListeningEventWaitlist();
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (notificationWatcher != null
                && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            notificationWatcher.start(
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
    }

    @Override
    protected void onStop() {
        if (notificationWatcher != null) {
            notificationWatcher.stop();
        }
        super.onStop();
    }
} 