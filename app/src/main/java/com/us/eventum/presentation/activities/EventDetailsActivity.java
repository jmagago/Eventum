package com.us.eventum.presentation.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.IntentCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import com.us.eventum.adapters.AttendeeAdapter;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.models.Event;
import com.us.eventum.R;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.presentation.viewmodels.EventViewModel;

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
import com.us.eventum.utils.LocaleUtils;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.NotificationPermissionHelper;
import com.us.eventum.utils.OrganizerNotificationDispatcher;
import com.us.eventum.utils.OrganizerNotificationHelper;
import com.us.eventum.utils.OrganizerNotificationWatcher;
import com.us.eventum.utils.FabBarController;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.utils.AttendeeSearchFilter;
import com.us.eventum.utils.QrCheckInResult;
import com.us.eventum.utils.VibrationUtils;
import com.us.eventum.presentation.viewmodels.SharedViewModel;

public class EventDetailsActivity extends AppCompatActivity {
    private static final String TAG = "EventDetailsActivity";
    private Event event;
    
    private ActivityResultLauncher<Intent> qrScannerLauncher;
    private FirebaseFirestore db;
    private AttendeeAdapter attendeeAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Attendee> attendees = new ArrayList<>();
    private TextView dateTextView, locationTextView, descriptionTextView;
    private TextView eventTimeTextView;
    private TextView emptyAttendeesTextView;
    private ImageView eventPrivateIconDetails;
    private FloatingActionButton addAttendeeButton;
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
        NotificationPermissionHelper.requestIfNeeded(this);
        
        // Inicializar ActivityResultLauncher
        qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Recargar la lista de asistentes y la información de escaneado
                    loadAttendees();
                    loadScannedAttendeesInfo();
                }
            }
        );

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
                            ToastUtils.showCustomToast(EventDetailsActivity.this,
                                    "Error al cargar el evento", ToastUtils.ToastType.ERROR);
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
        if (stripe == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(stripe, (v, windowInsets) -> {
            int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(stripe);
    }

    private void setupEventUi(TextView toolbarTitleTextView) {
        if (event == null) {
            finish();
            return;
        }
        toolbarTitleTextView.setText(event.getTitle());
        initializeViews();
        setupRecyclerView();
        setupAttendeesSwipeRefresh();
        displayEventDetails();
        observeViewModels();
        loadAttendees();
        updateAddAttendeeButtonState();

        if (getIntent().getBooleanExtra("show_clear_dialog", false)) {
            showClearAttendeeListConfirmation();
        } else if (getIntent().getBooleanExtra("show_delete_dialog", false)) {
            showDeleteEventDialog();
        }
    }

    private void initializeViews() {
        dateTextView = findViewById(R.id.eventDateTextView);
        eventTimeTextView = findViewById(R.id.eventTimeTextView);
        locationTextView = findViewById(R.id.eventLocationTextView);
        descriptionTextView = findViewById(R.id.eventDescriptionTextView);
        emptyAttendeesTextView = findViewById(R.id.emptyAttendeesTextView);
        eventPrivateIconDetails = findViewById(R.id.eventPrivateIconDetails);
        Log.d("EventDetailsActivity", "emptyAttendeesTextView inicializado: " + (emptyAttendeesTextView != null ? "OK" : "NULL"));
        addAttendeeButton = findViewById(R.id.addAttendeeButton);
        searchAttendeeFab = findViewById(R.id.searchAttendeeFab);
        verifyQrFab = findViewById(R.id.verifyQrFab);
        View fabDock = findViewById(R.id.fabDock);
        fabContainer = findViewById(R.id.fabContainer);
        View fabBarHandle = findViewById(R.id.fabBarHandle);
        View fabBarCollapse = findViewById(R.id.fabBarCollapse);
        fabBarController = new FabBarController(this, fabDock, fabContainer, fabBarHandle, fabBarCollapse);
        fabBarController.attachToActivity(this);
        attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        attendeesSwipeRefresh = findViewById(R.id.attendeesSwipeRefresh);
        eventMenuButton = findViewById(R.id.eventMenuButton);
        
        // Elementos del indicador de filtros
        filterIndicatorLayout = findViewById(R.id.filterIndicatorLayout);
        filterIndicatorText = findViewById(R.id.filterIndicatorText);
        clearFiltersButton = findViewById(R.id.clearFiltersButton);

        addAttendeeButton.setOnClickListener(v -> {
            // Validación defensiva: un evento pasado no admite altas manuales.
            if (isEventPast()) {
                ToastUtils.showCustomToast(this, "Este evento ya ha finalizado. No es posible añadir asistentes.", ToastUtils.ToastType.INFO);
                return;
            }
            // Verificar si ya se alcanzó el máximo de asistentes
            if (attendees.size() >= event.getMaxParticipants()) {
                ToastUtils.showCustomToast(this, "Capacidad máxima alcanzada (" + event.getMaxParticipants() + "). Edite el evento para aumentar el límite.", ToastUtils.ToastType.WARNING);
                return;
            }
            showAddAttendeeDialog();
        });
        eventMenuButton.setOnClickListener(v -> showEventMenu());

        // Abrir búsqueda de asistente
        searchAttendeeFab.setOnClickListener(v -> showSearchAttendeeDialog());
        // Abrir lector QR
        verifyQrFab.setOnClickListener(v -> startQRScanner());
        
        // Configurar click listener para limpiar filtros
        if (clearFiltersButton != null) {
            clearFiltersButton.setOnClickListener(v -> clearSearchFilters());
        }

        // Configurar clic largo en el icono del candado para cambiar privacidad
        if (eventPrivateIconDetails != null) {
            eventPrivateIconDetails.setOnLongClickListener(v -> {
                if (event != null) {
                    boolean newPrivateState = !event.getPrivateEvent();
                    // Actualizo el estado del evento local inmediatamente para que el icono se actualice
                    event.setPrivateEvent(newPrivateState);
                    // Actualizo el icono visualmente de inmediato
                    updateLockIcon(newPrivateState);
                    // Luego actualizo en el servidor
                    eventViewModel.toggleEventPrivacy(event.getId(), newPrivateState);
                    return true;
                }
                return false;
            });
        }
    }

    private void observeViewModels() {
        // Observar asistentes
        attendeeViewModel.getAttendees().observe(this, attendeesList -> {
            Log.d("EventDetailsActivity", "Observando asistentes: " + (attendeesList != null ? attendeesList.size() : 0));
            if (attendeesList != null) {
                // Actualizar lista completa de asistentes
                allAttendees.clear();
                allAttendees.addAll(attendeesList);
                
                // Actualizar lista de asistentes para mostrar
                attendees.clear();
                attendees.addAll(attendeesList);
                
                Log.d("EventDetailsActivity", "Asistentes actualizados en adapter: " + attendees.size());
                
                // Cargar información de escaneado para actualizar bordes
                loadScannedAttendeesInfo();
                
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
                updateAddAttendeeButtonState();
                
                // Mostrar/ocultar mensaje cuando no hay asistentes
                if (attendeesList.isEmpty()) {
                    Log.d("EventDetailsActivity", "Lista de asistentes vacía, mostrando mensaje");
                    if (emptyAttendeesTextView != null) {
                        emptyAttendeesTextView.setVisibility(View.VISIBLE);
                        Log.d("EventDetailsActivity", "emptyAttendeesTextView mostrado");
                    } else {
                        Log.e("EventDetailsActivity", "emptyAttendeesTextView es null");
                    }
                } else {
                    Log.d("EventDetailsActivity", "Lista de asistentes con " + attendeesList.size() + " elementos, ocultando mensaje");
                    if (emptyAttendeesTextView != null) {
                        emptyAttendeesTextView.setVisibility(View.GONE);
                        Log.d("EventDetailsActivity", "emptyAttendeesTextView ocultado");
                    } else {
                        Log.e("EventDetailsActivity", "emptyAttendeesTextView es null");
                    }
                }
            }
            if (attendeesSwipeRefresh != null) {
                attendeesSwipeRefresh.setRefreshing(false);
            }
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
                Log.d("EventDetailsActivity", "Asistente eliminado, recargando lista...");
                // Recargar la lista de asistentes (esto disparará el observer de getAttendees() 
                // que actualizará el contador automáticamente)
                loadAttendees();
                // Notificar al EventViewModel para actualizar el contador
                if (event != null) {
                    eventViewModel.loadUserEvents();
                }
                // Notificar al SharedViewModel para actualizar la actividad de origen
                sharedViewModel.notifyEventsUpdated();
                // Limpiar el flag de operación
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

        // Observar operaciones exitosas
        attendeeViewModel.getAttendeeAdded().observe(this, added -> {
            if (added != null && added) {
                ToastUtils.showCustomToast(this, "Asistente añadido con éxito", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
            }
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
                    loadScannedAttendeesInfo();
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
                // Recargar la lista de asistentes (esto disparará el observer de getAttendees() 
                // que actualizará el contador automáticamente)
                loadAttendees();
                // Notificar al EventViewModel para actualizar el contador
                if (event != null) {
                    eventViewModel.loadUserEvents();
                }
                // Notificar al SharedViewModel para actualizar la actividad de origen
                sharedViewModel.notifyEventsUpdated();
                attendeeViewModel.clearOperationStates();
            }
        });

        eventViewModel.getEventUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                // El mensaje se muestra en el observer de eventUpdateMessage
                // El estado del evento ya se actualizó en el listener, solo actualizo la UI completa
                displayEventDetails();
                sharedViewModel.notifyEventsUpdated();
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
            if (event != null && notification.getEventId() != null
                    && notification.getEventId().equals(event.getId())) {
                loadAttendees();
            }
        });
    }

    private void showSearchAttendeeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomSearchDialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_attendee, null);
        TextInputEditText dniEditText = dialogView.findViewById(R.id.dniEditText);
        TextInputEditText nameEditText = dialogView.findViewById(R.id.nameEditText);
        TextInputEditText firstLastNameEditText = dialogView.findViewById(R.id.firstLastNameEditText);
        TextInputEditText secondLastNameEditText = dialogView.findViewById(R.id.secondLastNameEditText);
        TextInputEditText emailEditText = dialogView.findViewById(R.id.emailEditText);
        TextInputEditText phoneEditText = dialogView.findViewById(R.id.phoneEditText);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton searchButton = dialogView.findViewById(R.id.searchButton);

        // Cargar filtros actuales si hay búsqueda activa
        if (isSearchActive) {
            if (currentSearchFilter.getDni() != null) {
                dniEditText.setText(currentSearchFilter.getDni());
            }
            if (currentSearchFilter.getName() != null) {
                nameEditText.setText(currentSearchFilter.getName());
            }
            if (currentSearchFilter.getFirstLastName() != null) {
                firstLastNameEditText.setText(currentSearchFilter.getFirstLastName());
            }
            if (currentSearchFilter.getSecondLastName() != null) {
                secondLastNameEditText.setText(currentSearchFilter.getSecondLastName());
            }
            if (currentSearchFilter.getEmail() != null) {
                emailEditText.setText(currentSearchFilter.getEmail());
            }
            if (currentSearchFilter.getPhone() != null) {
                phoneEditText.setText(currentSearchFilter.getPhone());
            }
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        searchButton.setOnClickListener(v -> {
            String dni = dniEditText.getText() != null ? dniEditText.getText().toString().trim() : "";
            String name = nameEditText.getText() != null ? nameEditText.getText().toString().trim() : "";
            String firstLastName = firstLastNameEditText.getText() != null ? firstLastNameEditText.getText().toString().trim() : "";
            String secondLastName = secondLastNameEditText.getText() != null ? secondLastNameEditText.getText().toString().trim() : "";
            String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
            String phone = phoneEditText.getText() != null ? phoneEditText.getText().toString().trim() : "";

            // Validar que se introduzca al menos: DNI O email O teléfono O (nombre + primer apellido)
            boolean hasValidSearch = !dni.isEmpty() || !email.isEmpty() || !phone.isEmpty() || (!name.isEmpty() && !firstLastName.isEmpty());
            
            if (!hasValidSearch) {
                ToastUtils.showCustomToast(this, "Debe introducir al menos: DNI, email, teléfono o nombre + primer apellido", ToastUtils.ToastType.WARNING);
                return;
            }

            // Crear nuevo filtro
            AttendeeSearchFilter newFilter = new AttendeeSearchFilter();
            newFilter.setDni(dni.isEmpty() ? null : dni);
            newFilter.setName(name.isEmpty() ? null : name);
            newFilter.setFirstLastName(firstLastName.isEmpty() ? null : firstLastName);
            newFilter.setSecondLastName(secondLastName.isEmpty() ? null : secondLastName);
            newFilter.setEmail(email.isEmpty() ? null : email);
            newFilter.setPhone(phone.isEmpty() ? null : phone);

            // Aplicar filtros
            currentSearchFilter = newFilter;
            applySearchFilters();
            
            // Cerrar diálogo
            dialog.dismiss();
        });

        dialog.show();
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
            if (event != null) {
                loadAttendees();
                loadScannedAttendeesInfo();
            } else {
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

    private void displayEventDetails() {
        if (event == null) return;
        
        // Crear formato de fecha más completo
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", com.us.eventum.utils.LocaleUtils.spanish());
        String formattedDate = fullDateFormat.format(event.getDate());
        formattedDate = LocaleUtils.capitalizeFirst(formattedDate);
        
        // Formato más profesional para fecha, capacidad y ubicación
        String dateText = LocaleUtils.format("📅  %s", formattedDate);
        String capacityText = getResources().getQuantityString(
                R.plurals.event_capacity_detail,
                event.getMaxParticipants(),
                attendees.size(),
                event.getMaxParticipants());
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
        
        // Actualizar el TextView para la capacidad
        TextView capacityTextView = findViewById(R.id.eventCapacityTextView);
        if (capacityTextView != null) {
            capacityTextView.setText(capacityText);
            capacityTextView.setVisibility(View.VISIBLE);
        }

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

    private void loadAttendees() {
        if (event != null) {
            Log.d("EventDetailsActivity", "Cargando asistentes para evento ID: " + event.getId() + ", Título: " + event.getTitle());
            attendeeViewModel.loadEventAttendees(event.getId());
        } else {
            Log.e("EventDetailsActivity", "Event es null, no se pueden cargar asistentes");
        }
    }

    private void updateAttendeesCount() {
        // Solo actualizar la información de capacidad en displayEventDetails
        displayEventDetails();
    }

    private void loadScannedAttendeesInfo() {
        if (event == null || event.getId() == null) {
            return;
        }
        
        // Usar el repositorio directamente para obtener la lista de AttendeesToEvent
        com.us.eventum.data.repositories.firebase.FirebaseAttendeesToEventRepository repository = 
            new com.us.eventum.data.repositories.firebase.FirebaseAttendeesToEventRepository();
        
        repository.loadAttendeesToEvent(event.getId(), 
            new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
                @Override
                public void onSuccess(List<AttendeesToEvent> result) {
                    // Crear el Map con la información de escaneado
                    scannedAttendeesMap.clear();
                    for (AttendeesToEvent attendeeToEvent : result) {
                        scannedAttendeesMap.put(attendeeToEvent.getUserId(), attendeeToEvent.isScannedQR());
                    }
                    
                    // Actualizar el adapter con la información de escaneado
                    attendeeAdapter.setScannedAttendeesMap(scannedAttendeesMap);
                }

                @Override
                public void onError(String error) {
                    Log.e("EventDetailsActivity", "Error al cargar AttendeesToEvent: " + error);
                }
            });
    }

    private void showAddAttendeeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_attendee, null);

        TextInputEditText nameEditText = dialogView.findViewById(R.id.nameEditText);
        TextInputEditText firstLastNameEditText = dialogView.findViewById(R.id.firstLastNameEditText);
        TextInputEditText secondLastNameEditText = dialogView.findViewById(R.id.secondLastNameEditText);
        TextInputEditText dniEditText = dialogView.findViewById(R.id.dniEditText);
        TextInputEditText emailEditText = dialogView.findViewById(R.id.emailEditText);
        TextInputEditText phoneEditText = dialogView.findViewById(R.id.phoneEditText);
        TextInputEditText birthDateEditText = dialogView.findViewById(R.id.birthDateEditText);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton addButton = dialogView.findViewById(R.id.addAttendeeButton);
        
        // Obtener los TextInputLayout para mostrar errores
        com.google.android.material.textfield.TextInputLayout dniLayout = dialogView.findViewById(R.id.dniLayout);
        com.google.android.material.textfield.TextInputLayout phoneLayout = dialogView.findViewById(R.id.phoneLayout);
        com.google.android.material.textfield.TextInputLayout birthDateLayout = dialogView.findViewById(R.id.birthDateLayout);


        // Configurar validador de DNI (solo cuando pierde el foco)
        dniEditText.setOnFocusChangeListener(com.us.eventum.utils.DniValidator.createDniFocusValidator(dniLayout));

        // Validar teléfono español al perder el foco
        phoneEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String phoneText = phoneEditText.getText().toString().trim();
                // Eliminar todos los caracteres que no sean números
                String cleanPhone = phoneText.replaceAll("[^0-9]", "");
                if (!cleanPhone.equals(phoneText)) {
                    phoneEditText.setText(cleanPhone);
                }
                
                // Validar después de limpiar
                if (!cleanPhone.isEmpty()) {
                    if (cleanPhone.length() == 9) {
                        // Validar que empiece por 6, 7, 8 o 9 (móviles españoles)
                        if (cleanPhone.matches("^[6-9]\\d{8}$")) {
                            phoneLayout.setError(null);
                        } else {
                            phoneLayout.setError("Debe empezar por 6, 7, 8 o 9");
                        }
                    } else {
                        phoneLayout.setError("Debe tener 9 dígitos");
                    }
                } else {
                    phoneLayout.setError(null);
                }
            }
        });

        // Validar edad al escribir manualmente
        birthDateEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String dateText = s.toString().trim();
                if (!dateText.isEmpty() && dateText.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    try {
                        Date parsed = dateFormat.parse(dateText);
                        if (parsed != null) {
                            int age = calculateAge(parsed);
                            if (age < 16) {
                                birthDateLayout.setError("Debe tener al menos 16 años");
                            } else {
                                birthDateLayout.setError(null);
                            }
                        }
                    } catch (ParseException e) {
                        birthDateLayout.setError("Formato inválido (dd/MM/yyyy)");
                    }
                } else if (!dateText.isEmpty()) {
                    birthDateLayout.setError("Formato: dd/MM/yyyy");
                } else {
                    birthDateLayout.setError(null);
                }
            }
        });

        // Configurar el selector de fecha
        birthDateEditText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    birthDateEditText.setText(dateFormat.format(calendar.getTime()));

                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );

            // Restringir a mayores o iguales a 16 años (no permitir seleccionar menos de 16)
            Calendar maxSelectable = Calendar.getInstance();
            maxSelectable.add(Calendar.YEAR, -16);
            datePickerDialog.getDatePicker().setMaxDate(maxSelectable.getTimeInMillis());
            datePickerDialog.show();
        });

        AlertDialog dialog = builder
            .setView(dialogView)
            .create();

        // Configurar botones
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        addButton.setOnClickListener(view -> {
            String name = nameEditText.getText().toString().trim();
            String firstLastName = firstLastNameEditText.getText().toString().trim();
            String secondLastName = secondLastNameEditText.getText().toString().trim();
            String dni = dniEditText.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String email = emailEditText.getText().toString().trim();
            String phone = phoneEditText.getText().toString().trim();
            String birthDate = birthDateEditText.getText().toString().trim();

            // Validar campos obligatorios
            if (name.isEmpty() || firstLastName.isEmpty() || dni.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                ToastUtils.showCustomToast(this, "Por favor, completa los campos obligatorios (nombre, primer apellido, DNI, email y teléfono)", ToastUtils.ToastType.INFO);
                return;
            }

            // Validar DNI
            if (!com.us.eventum.utils.DniValidator.isValidDni(dni)) {
                ToastUtils.showCustomToast(this, "El DNI no es válido", ToastUtils.ToastType.INFO);
                return;
            }

            // Verificar si el DNI ya existe en la lista de asistentes
            boolean dniExists = allAttendees.stream()
                .anyMatch(attendee -> attendee.getDni() != null && attendee.getDni().equalsIgnoreCase(dni));
            
            if (dniExists) {
                ToastUtils.showCustomToast(this, "Ya existe un asistente con este DNI en la lista", ToastUtils.ToastType.WARNING);
                return;
            }

            // El apellido puede estar vacío, solo lo concatenamos si existe
            String lastName = "";
            if (!firstLastName.isEmpty()) {
                lastName = firstLastName;
                if (!secondLastName.isEmpty()) {
                    lastName += " " + secondLastName;
                }
            }

            // Validar edad si se proporciona fecha de nacimiento
            if (!birthDate.isEmpty()) {
                try {
                    Date parsed = dateFormat.parse(birthDate);
                    if (parsed != null) {
                        int age = calculateAge(parsed);
                        if (age < 16) {
                            ToastUtils.showCustomToast(this, "El asistente debe tener al menos 16 años", ToastUtils.ToastType.INFO);
                            return;
                        }
                    }
                } catch (ParseException e) {
                    ToastUtils.showCustomToast(this, "Formato de fecha inválido (usa dd/MM/yyyy)", ToastUtils.ToastType.INFO);
                    return;
                }
            }

            // Usar AttendeeViewModel para añadir el asistente (incluye userId)
            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;
            attendeeViewModel.addAttendee(event.getId(), uid, name, lastName, dni, email, phone, birthDate, event.getRequiresParentalAuth());
            dialog.dismiss();
        });

        dialog.show();
    }

    private int calculateAge(Date birthDate) {
        if (birthDate == null) return 0;
        Calendar today = Calendar.getInstance();
        Calendar dob = Calendar.getInstance();
        dob.setTime(birthDate);

        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return Math.max(age, 0);
    }

    private void showAttendeeDetails(Attendee attendee) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attendee_details, null);

        TextView nameTextView = dialogView.findViewById(R.id.detailNameTextView);
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
        boolean allowManualVerify = !isEventPast();
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
            MaterialButton verifyButton = dialogView.findViewById(R.id.dialog_verify_attendance_button);
            if (verifyButton != null) {
                verifyButton.setEnabled(false);
            }
            manualCheckInAwaitingResult = true;
            pendingManualCheckInAttendeeId = attendee.getUid();
            attendeeViewModel.verifyAttendeeCheckIn(attendee.getUid(), event.getId());
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
        updateVerifyAttendanceButton(verifyButton, verified, !isEventPast());
    }

    private void deleteAttendee(Attendee attendee, AlertDialog detailsDialog) {
        // Usar AttendeeViewModel para eliminar el asistente del evento
        // Esto elimina tanto el registro en AttendeesToEvent como el perfil del asistente
        if (event != null && event.getId() != null) {
            attendeeViewModel.removeAttendeeFromEvent(attendee.getUid(), event.getId());
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


    private void showEditEventDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_event, null);
        
        TextInputEditText titleInput = dialogView.findViewById(R.id.titleInput);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.descriptionInput);
        TextInputEditText locationInput = dialogView.findViewById(R.id.locationInput);
        TextInputEditText dateInput = dialogView.findViewById(R.id.dateInput);
        TextInputEditText timeInput = dialogView.findViewById(R.id.timeInput);
        TextInputEditText maxParticipantsInput = dialogView.findViewById(R.id.maxParticipantsInput);
        CheckBox eventoPrivadoCheckBox = dialogView.findViewById(R.id.eventoPrivadoCheckBox);
        CheckBox requiresParentalAuthCheckBox = dialogView.findViewById(R.id.requiresParentalAuthCheckBox);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        
        // Preparar Calendar con la fecha del evento
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(event.getDate());
        
        // Formato para hora
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        
        // Guardar valores iniciales para comparar cambios
        String initialTitle = event.getTitle();
        String initialDescription = event.getDescription() != null ? event.getDescription() : "";
        String initialLocation = event.getLocation();
        String initialDate = dateFormat.format(event.getDate());
        String initialTime = timeFormat.format(event.getDate());
        int initialMaxParticipants = event.getMaxParticipants();
        boolean initialPrivateEvent = event.getPrivateEvent();
        boolean initialRequiresParentalAuth = event.getRequiresParentalAuth();
        
        // Rellenar los campos con los datos actuales del evento
        titleInput.setText(initialTitle);
        descriptionInput.setText(initialDescription);
        locationInput.setText(initialLocation);
        dateInput.setText(initialDate);
        timeInput.setText(initialTime);
        maxParticipantsInput.setText(String.valueOf(initialMaxParticipants));
        eventoPrivadoCheckBox.setChecked(initialPrivateEvent);
        requiresParentalAuthCheckBox.setChecked(initialRequiresParentalAuth);
        
        // Función para verificar si hay cambios y habilitar/deshabilitar el botón
        Runnable checkChanges = () -> {
            String currentTitle = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
            String currentDescription = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";
            String currentLocation = locationInput.getText() != null ? locationInput.getText().toString().trim() : "";
            String currentDate = dateInput.getText() != null ? dateInput.getText().toString().trim() : "";
            String currentTime = timeInput.getText() != null ? timeInput.getText().toString().trim() : "";
            String currentMaxParticipantsStr = maxParticipantsInput.getText() != null ? maxParticipantsInput.getText().toString().trim() : "";
            boolean currentPrivateEvent = eventoPrivadoCheckBox.isChecked();
            boolean currentRequiresParentalAuth = requiresParentalAuthCheckBox.isChecked();
            
            boolean hasChanges = false;
            
            // Comparar cada campo
            if (!currentTitle.equals(initialTitle) || 
                !currentDescription.equals(initialDescription) ||
                !currentLocation.equals(initialLocation) ||
                !currentDate.equals(initialDate) ||
                !currentTime.equals(initialTime) ||
                currentPrivateEvent != initialPrivateEvent ||
                currentRequiresParentalAuth != initialRequiresParentalAuth) {
                hasChanges = true;
            } else {
                // Comparar maxParticipants (puede ser un número)
                try {
                    int currentMaxParticipants = Integer.parseInt(currentMaxParticipantsStr);
                    if (currentMaxParticipants != initialMaxParticipants) {
                        hasChanges = true;
                    }
                } catch (NumberFormatException e) {
                    // Si no es un número válido, no hay cambios reales
                }
            }
            
            // Habilitar/deshabilitar botón según haya cambios
            saveButton.setEnabled(hasChanges);
            saveButton.setAlpha(hasChanges ? 1.0f : 0.5f);
        };
        
        // Inicialmente deshabilitar el botón (no hay cambios al inicio)
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.5f);
        
        // Agregar listeners para detectar cambios
        titleInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        descriptionInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        locationInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        dateInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        timeInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        maxParticipantsInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkChanges.run();
            }
        });
        
        eventoPrivadoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> checkChanges.run());
        requiresParentalAuthCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> checkChanges.run());
        
        // Configurar el DatePicker para la fecha (solo se abre al hacer clic en el icono del calendario)
        // El campo es editable para poder escribir directamente
        TextInputLayout dateLayout = dialogView.findViewById(R.id.dateLayout);
        if (dateLayout != null) {
            // Configurar el icono de inicio (calendario) para abrir el DatePicker
            dateLayout.setStartIconOnClickListener(v -> {
                DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        dateInput.setText(dateFormat.format(calendar.getTime()));
                        checkChanges.run(); // Verificar cambios después de actualizar la fecha
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePicker.show();
            });
        }
        
        // Configurar el TimePicker para la hora
        timeInput.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    timeInput.setText(timeFormat.format(calendar.getTime()));
                    checkChanges.run(); // Verificar cambios después de actualizar la hora
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            );
            // No establecer título para que sea similar al DatePicker
            timePicker.show();
        });
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.setView(dialogView).create();
        
        // Configurar los botones
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";
            String location = locationInput.getText().toString().trim();
            String dateStr = dateInput.getText().toString().trim();
            String timeStr = timeInput.getText().toString().trim();
            String maxParticipantsStr = maxParticipantsInput.getText().toString().trim();
            
            if (title.isEmpty() || location.isEmpty() || dateStr.isEmpty() || timeStr.isEmpty() || maxParticipantsStr.isEmpty()) {
                ToastUtils.showCustomToast(this, "Todos los campos son obligatorios", ToastUtils.ToastType.INFO);
                return;
            }
            
            try {
                // Parsear fecha y hora por separado y combinarlas
                Date dateOnly = dateFormat.parse(dateStr);
                Calendar dateCalendar = Calendar.getInstance();
                dateCalendar.setTime(dateOnly);
                
                // Parsear la hora (formato HH:mm)
                String[] timeParts = timeStr.split(":");
                if (timeParts.length != 2) {
                    ToastUtils.showCustomToast(this, "Formato de hora inválido. Use HH:mm", ToastUtils.ToastType.INFO);
                    return;
                }
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                
                // Combinar fecha y hora
                dateCalendar.set(Calendar.HOUR_OF_DAY, hour);
                dateCalendar.set(Calendar.MINUTE, minute);
                dateCalendar.set(Calendar.SECOND, 0);
                dateCalendar.set(Calendar.MILLISECOND, 0);
                
                Date newDate = dateCalendar.getTime();
                int maxParticipants = Integer.parseInt(maxParticipantsStr);
                
                if (maxParticipants < attendees.size()) {
                    ToastUtils.showCustomToast(this, 
                        "El número de plazas no puede ser menor que el número actual de asistentes (" + 
                        attendees.size() + ")", 
                        ToastUtils.ToastType.WARNING);
                    return;
                }
                
                // Actualizar el objeto evento local inmediatamente para feedback visual
                boolean newPrivateState = eventoPrivadoCheckBox.isChecked();
                event.setTitle(title);
                event.setDescription(description);
                event.setLocation(location);
                event.setDate(newDate);
                event.setMaxParticipants(maxParticipants);
                event.setPrivateEvent(newPrivateState);
                event.setRequiresParentalAuth(requiresParentalAuthCheckBox.isChecked());
                
                // Actualizar el título en la toolbar inmediatamente
                TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
                if (toolbarTitleTextView != null) {
                    toolbarTitleTextView.setText(title);
                }
                
                // Actualizar el icono del candado inmediatamente
                updateLockIcon(newPrivateState);
                
                // Actualizar la UI inmediatamente con los nuevos valores
                displayEventDetails();
                
                // Usar EventViewModel para actualizar el evento en el servidor
                eventViewModel.updateEvent(event.getId(), title, description, newDate, 
                    location, maxParticipants, event.getEventType(), newPrivateState, requiresParentalAuthCheckBox.isChecked());
                
                dialog.dismiss();
            } catch (ParseException e) {
                ToastUtils.showCustomToast(this, "Error en el formato de fecha", ToastUtils.ToastType.INFO);
            } catch (NumberFormatException e) {
                ToastUtils.showCustomToast(this, "El número de plazas debe ser un número válido", ToastUtils.ToastType.INFO);
            }
        });
        
        dialog.show();
    }

    private void showEventMenu() {
        PopupMenu popup = new PopupMenu(this, eventMenuButton);
        popup.getMenuInflater().inflate(R.menu.menu_event_details, popup.getMenu());
        
        // Ocultar acciones que no aplican para eventos pasados
        try {
            if (event != null && event.getDate() != null && event.getDate().before(new Date())) {
                if (popup.getMenu().findItem(R.id.action_send_invitations) != null) {
                    popup.getMenu().findItem(R.id.action_send_invitations).setVisible(false);
                }
                if (popup.getMenu().findItem(R.id.action_verify_attendees) != null) {
                    popup.getMenu().findItem(R.id.action_verify_attendees).setVisible(false);
                }
                if (popup.getMenu().findItem(R.id.action_clear_list) != null) {
                    popup.getMenu().findItem(R.id.action_clear_list).setVisible(false);
                }
            }
        } catch (Exception ignore) {}
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
        }
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_event) {
                showEditEventDialog();
                return true;
            } else if (id == R.id.action_send_invitations) {
                ToastUtils.showCustomToast(this, "Enviar invitaciones", ToastUtils.ToastType.INFO);
                return true;
            } else if (id == R.id.action_verify_attendees) {
                startQRScanner();
                return true;
            } else if (id == R.id.action_clear_list) {
                showClearAttendeeListConfirmation();
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
        if (isEventPast()) {
            ToastUtils.showCustomToast(this, "Este evento ya ha finalizado. No es posible verificar asistentes por QR.", ToastUtils.ToastType.INFO);
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
            if (isEventPast()) {
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

    /**
     * Actualizar el estado del botón de añadir asistente
     */
    private void updateAddAttendeeButtonState() {
        if (addAttendeeButton != null && event != null) {
            if (isEventPast()) {
                addAttendeeButton.setEnabled(false);
                addAttendeeButton.setAlpha(0.5f);
                return;
            }
            // Verificar si el evento está completo
            boolean isEventFull = event.getCurrentParticipants() >= event.getMaxParticipants();
            
            // Deshabilitar si el evento está completo
            addAttendeeButton.setEnabled(!isEventFull);
            addAttendeeButton.setAlpha(isEventFull ? 0.5f : 1.0f);
        }
    }

    private boolean isEventPast() {
        return event != null && event.getDate() != null && event.getDate().before(new Date());
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