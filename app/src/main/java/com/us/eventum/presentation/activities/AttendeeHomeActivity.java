package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.hdodenhof.circleimageview.CircleImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.us.eventum.R;
import com.us.eventum.adapters.AttendeeEventsPagerAdapter;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.fragments.AttendeeEventsFragment;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.VibrationUtils;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.utils.NetworkUtils;
import com.us.eventum.utils.NotificationPermissionHelper;
import com.us.eventum.utils.AttendeeNotificationWatcher;
import com.us.eventum.utils.AttendeeNotificationDispatcher;
import com.us.eventum.utils.AttendeeNotificationHelper;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.presentation.viewmodels.SharedViewModel;

import android.app.DatePickerDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.Calendar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Home del asistente: pestañas Mis eventos, Descubrir e Historial (check-in QR).
 */
public class AttendeeHomeActivity extends AppCompatActivity
        implements AttendeeEventsFragment.AttendeeEventListener {

    private static final int TAB_MY_EVENTS = 0;
    private static final int TAB_DISCOVER = 1;
    private static final int TAB_HISTORY = 2;

    private EventViewModel eventViewModel;
    private AttendeeViewModel attendeeViewModel;
    private CircleImageView profileImageView;
    private SharedViewModel sharedViewModel = SharedViewModel.getInstance();
    private EventDialogContext eventDialogContext;
    private boolean pendingSubscribeAction;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private AttendeeEventsPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private final List<Event> myEventsList = new ArrayList<>();
    private final List<Event> discoverEventsList = new ArrayList<>();
    private final List<Event> historyEventsList = new ArrayList<>();
    /** Tras inscripción o baja, cambiar a la pestaña correspondiente al recibir datos. */
    private Integer pendingTabAfterRefresh;
    private AttendeeNotificationWatcher attendeeNotificationWatcher;

    private static final class EventDialogContext {
        final Event event;
        final String userEmail;
        final Button joinButton;
        final TextView participantsText;
        final ImageButton showQrButton;
        final AlertDialog dialog;

        EventDialogContext(Event event, String userEmail, Button joinButton,
                           TextView participantsText, ImageButton showQrButton, AlertDialog dialog) {
            this.event = event;
            this.userEmail = userEmail;
            this.joinButton = joinButton;
            this.participantsText = participantsText;
            this.showQrButton = showQrButton;
            this.dialog = dialog;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_available_events);

        setupStatusBarStripe();

        // Header similar a Home: rellenar nombre/email/rol e imagen de perfil

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        eventViewModel.initializeRepository(this);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        attendeeViewModel.initializeRepository(this);

        // Referencia a imagen de perfil y carga inicial
        profileImageView = findViewById(R.id.profileImageView);
        ProfileImageManager.loadProfileImage(this, profileImageView);

        // Observar actualización de imagen de perfil desde Settings
        sharedViewModel.getProfileImageUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                ProfileImageManager.loadProfileImage(this, profileImageView);
                sharedViewModel.resetProfileImageUpdated();
            }
        });

        // Pintar datos de usuario en header
        // Usar attendeeViewModel para datos de usuario
        TextView nameTv = findViewById(R.id.userNameTextView);
        TextView emailTv = findViewById(R.id.userEmailTextView);
        TextView roleTv = findViewById(R.id.userRoleTextView);
        attendeeViewModel.getCurrentAttendee().observe(this, attendee -> {
            if (attendee != null) {
                nameTv.setText(attendee.getUsername());
                emailTv.setText(attendee.getEmail());
                roleTv.setText(R.string.role_attendee);
                roleTv.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                roleTv.setBackgroundResource(R.drawable.bg_role_badge_assistant);
            }
        });
        attendeeViewModel.loadCurrentAttendee();

        // Configurar botón de settings
        setupSettingsButton();
        setupViewPager();
        observeViewModel();
        loadAvailableEvents();

        attendeeNotificationWatcher = new AttendeeNotificationWatcher(this);
        NotificationPermissionHelper.requestIfNeeded(this);
        AttendeeNotificationDispatcher.getLatestNotification().observe(this, notification -> {
            if (notification == null) {
                return;
            }
            String body = AttendeeNotificationHelper.buildBody(this, notification);
            ToastUtils.showCustomToast(this, body, ToastUtils.ToastType.SUCCESS);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (attendeeNotificationWatcher != null
                && FirebaseAuth.getInstance().getCurrentUser() != null) {
            attendeeNotificationWatcher.start(
                    FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
    }

    @Override
    protected void onStop() {
        if (attendeeNotificationWatcher != null) {
            attendeeNotificationWatcher.stop();
        }
        super.onStop();
    }

    private void setupStatusBarStripe() {
        View stripe = findViewById(R.id.statusBarStripe);
        if (stripe == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(stripe, (v, windowInsets) -> {
            int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(stripe);
    }

    private void setupViewPager() {
        pagerAdapter = new AttendeeEventsPagerAdapter(this, myEventsList, discoverEventsList, historyEventsList);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(true);

        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case TAB_MY_EVENTS:
                    tab.setText(getString(R.string.attendee_tab_my_events_count, myEventsList.size()));
                    break;
                case TAB_HISTORY:
                    tab.setText(getString(R.string.attendee_tab_history_count, historyEventsList.size()));
                    break;
                case TAB_DISCOVER:
                default:
                    tab.setText(getString(R.string.attendee_tab_discover_count, discoverEventsList.size()));
                    break;
            }
        });
        tabLayoutMediator.attach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profileImageView != null) {
            ProfileImageManager.loadProfileImage(this, profileImageView);
        }
        loadAvailableEvents();
    }

    private void observeViewModel() {
        eventViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && pagerAdapter != null) {
                pagerAdapter.setRefreshing(loading);
            }
        });

        eventViewModel.getAllEvents().observe(this, events -> {
            partitionAndUpdateTabs(events != null ? events : new ArrayList<>());
            if (pagerAdapter != null) {
                pagerAdapter.setRefreshing(false);
            }
        });

        setupRegistrationResultObservers();
    }

    /** Un solo observer para alta/baja; evita toasts duplicados al abrir diálogos varias veces. */
    private void setupRegistrationResultObservers() {
        attendeeViewModel.getAttendeeAdded().observe(this, added -> {
            if (!Boolean.TRUE.equals(added)) {
                return;
            }
            VibrationUtils.vibrateSuccess(this);
            ToastUtils.showCustomToast(this,
                    "Te has apuntado al evento correctamente", ToastUtils.ToastType.SUCCESS);
            dismissEventDialogIfOpen();
            pendingTabAfterRefresh = TAB_MY_EVENTS;
            refreshAfterRegistrationChange();
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (!Boolean.TRUE.equals(deleted)) {
                return;
            }
            VibrationUtils.vibrateWarning(this);
            ToastUtils.showCustomToast(this,
                    "Te has dado de baja del evento", ToastUtils.ToastType.WARNING);
            dismissEventDialogIfOpen();
            pendingTabAfterRefresh = TAB_DISCOVER;
            refreshAfterRegistrationChange();
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
                pendingSubscribeAction = false;
                attendeeViewModel.clearOperationStates();
            }
        });
    }

    private void dismissEventDialogIfOpen() {
        if (eventDialogContext != null && eventDialogContext.dialog != null
                && eventDialogContext.dialog.isShowing()) {
            eventDialogContext.dialog.dismiss();
        }
        eventDialogContext = null;
    }

    private void refreshAfterRegistrationChange() {
        loadAvailableEvents();
    }

    private void loadAvailableEvents() {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        eventViewModel.loadAllEvents();
    }

    /**
     * Mis eventos: asistente inscrito (independiente de QR), excepto los que van a Historial.
     * Descubrir: no inscrito y fecha de hoy o futura.
     * Historial: fecha pasada (ayer o antes), inscrito y QR validado en entrada.
     */
    private void partitionAndUpdateTabs(List<Event> allEvents) {
        myEventsList.clear();
        discoverEventsList.clear();
        historyEventsList.clear();

        Date now = new Date();
        for (Event event : allEvents) {
            if (event == null || event.getDate() == null) {
                continue;
            }
            if (event.isCurrentUserJoined()) {
                if (isBeforeToday(event.getDate()) && event.isCurrentUserScannedQR()) {
                    historyEventsList.add(event);
                } else {
                    myEventsList.add(event);
                }
            } else if (!event.getDate().before(now)) {
                discoverEventsList.add(event);
            }
        }

        myEventsList.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        discoverEventsList.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        historyEventsList.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        if (pagerAdapter != null) {
            pagerAdapter.updateEvents(myEventsList, discoverEventsList, historyEventsList);
        }
        updateTabLabels();

        if (pendingTabAfterRefresh != null && viewPager != null) {
            int targetTab = pendingTabAfterRefresh;
            pendingTabAfterRefresh = null;
            viewPager.post(() -> viewPager.setCurrentItem(targetTab, true));
        }
    }

    private boolean isBeforeToday(Date date) {
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        return date.before(startOfToday.getTime());
    }

    private void updateTabLabels() {
        if (tabLayout == null) {
            return;
        }
        TabLayout.Tab myTab = tabLayout.getTabAt(TAB_MY_EVENTS);
        TabLayout.Tab discoverTab = tabLayout.getTabAt(TAB_DISCOVER);
        TabLayout.Tab historyTab = tabLayout.getTabAt(TAB_HISTORY);
        if (myTab != null) {
            myTab.setText(getString(R.string.attendee_tab_my_events_count, myEventsList.size()));
        }
        if (discoverTab != null) {
            discoverTab.setText(getString(R.string.attendee_tab_discover_count, discoverEventsList.size()));
        }
        if (historyTab != null) {
            historyTab.setText(getString(R.string.attendee_tab_history_count, historyEventsList.size()));
        }
    }

    @Override
    public void onEventClick(Event event) {
        showEventDetailsDialog(event);
    }

    @Override
    public void onEventLongClick(Event event) {
        if (event.isCurrentUserScannedQR()) {
            ToastUtils.showCustomToast(this,
                    getString(R.string.attendee_history_no_unsubscribe),
                    ToastUtils.ToastType.INFO);
            return;
        }
        if (!event.isCurrentUserJoined()) {
            return;
        }
        String email = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;
        if (email != null && !email.trim().isEmpty()) {
            eventDialogContext = null;
            attendeeViewModel.unsubscribeFromEvent(
                    event.getId(),
                    email,
                    event.getUserId(),
                    event.getTitle(),
                    resolveAttendeeDisplayName());
        } else {
            showUnsubscribeDialog(event);
        }
    }

    @Override
    public void onLockIconLongClick(Event event) {
        ToastUtils.showCustomToast(this,
                "Solo el organizador puede cambiar la privacidad del evento",
                ToastUtils.ToastType.INFO);
    }

    @Override
    public void onRefreshRequested() {
        loadAvailableEvents();
    }

    private void showJoinDialog(Event event) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Nombre");
        layout.addView(nameInput);

        EditText lastNameInput = new EditText(this);
        lastNameInput.setHint("Apellido");
        layout.addView(lastNameInput);

        EditText dniInput = new EditText(this);
        dniInput.setHint("DNI");
        layout.addView(dniInput);

        EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(emailInput);

        EditText phoneInput = new EditText(this);
        phoneInput.setHint("Teléfono");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(phoneInput);

        EditText birthDateInput = new EditText(this);
        birthDateInput.setHint("Fecha de nacimiento (dd/MM/yyyy)");
        birthDateInput.setFocusable(false);
        birthDateInput.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
                String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
                birthDateInput.setText(getString(R.string.date_format_dmy, dd, mm, year));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(birthDateInput);

        // Autocompletar con el usuario autenticado
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String authEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (authEmail != null) {
                emailInput.setText(authEmail);
                emailInput.setEnabled(false);
            }
            String displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                String[] parts = displayName.trim().split(" ", 2);
                nameInput.setText(parts[0]);
                if (parts.length > 1) {
                    lastNameInput.setText(parts[1]);
                }
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Apuntarse al evento")
                .setView(layout)
                .setPositiveButton("Apuntarme", (dialog, which) -> {
                    String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                            : null;
                    String displayName = formatDisplayName(
                            nameInput.getText().toString().trim(),
                            lastNameInput.getText().toString().trim());
                    attendeeViewModel.addAttendee(
                            event.getId(),
                            uid,
                            nameInput.getText().toString().trim(),
                            lastNameInput.getText().toString().trim(),
                            dniInput.getText().toString().trim(),
                            emailInput.getText().toString().trim(),
                            phoneInput.getText().toString().trim(),
                            birthDateInput.getText().toString().trim(),
                            false,
                            event.getUserId(),
                            event.getTitle(),
                            displayName
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showUnsubscribeDialog(Event event) {
        final EditText emailInput = new EditText(this);
        emailInput.setHint("Email con el que te apuntaste");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emailInput.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Desapuntarse del evento")
                .setView(emailInput)
                .setPositiveButton("Desapuntarme", (dialog, which) -> {
                    attendeeViewModel.unsubscribeFromEvent(
                            event.getId(),
                            emailInput.getText().toString().trim(),
                            event.getUserId(),
                            event.getTitle(),
                            resolveAttendeeDisplayName());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Configurar el botón de settings en el header
     */
    private void setupSettingsButton() {
        View settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_USER_TYPE, SettingsActivity.USER_TYPE_ATTENDEE);
                startActivity(intent);
            });
        }
    }

    /**
     * Mostrar diálogo con detalles del evento y opción de apuntarse
     */
    private void showEventDetailsDialog(Event event) {
        // Crear el layout del diálogo
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_event_details_attendee, null);

        // Configurar los elementos del diálogo
        TextView titleText = dialogView.findViewById(R.id.eventTitle);
        TextView descriptionText = dialogView.findViewById(R.id.eventDescription);
        TextView dateText = dialogView.findViewById(R.id.eventDate);
        TextView timeText = dialogView.findViewById(R.id.eventTime);
        TextView locationText = dialogView.findViewById(R.id.eventLocation);
        TextView participantsText = dialogView.findViewById(R.id.eventParticipants);
        TextView eventTypeText = dialogView.findViewById(R.id.eventType);
        TextView privateText = dialogView.findViewById(R.id.eventPrivate);
        
        ImageButton showQrButton = dialogView.findViewById(R.id.showQrButton);
        Button joinButton = dialogView.findViewById(R.id.joinEventButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        showQrButton.setEnabled(false);
        showQrButton.setAlpha(0.5f);
        showQrButton.setOnClickListener(v -> showEventQrDialog(event));

        // Rellenar datos del evento
        titleText.setText(event.getTitle());
        descriptionText.setText(event.getDescription());
        
        // Formatear fecha y hora
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        dateText.setText(dateFormat.format(event.getDate()));
        timeText.setText(timeFormat.format(event.getDate()));
        
        locationText.setText(event.getLocation());
        participantsText.setText(getResources().getQuantityString(
                R.plurals.event_participants_count,
                event.getMaxParticipants(),
                event.getCurrentParticipants(), event.getMaxParticipants()));
        eventTypeText.setText(event.getEventType());
        
        // Verificar si el evento está lleno
        boolean isEventFull = event.getCurrentParticipants() >= event.getMaxParticipants();
        if (isEventFull) {
            participantsText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, getTheme()));
        } else {
            participantsText.setTextColor(getResources().getColor(R.color.colorSecondaryText, getTheme()));
        }
        
        // Mostrar si es privado
        if (event.getPrivateEvent()) {
            privateText.setText(R.string.label_event_private_title);
            privateText.setVisibility(View.VISIBLE);
        } else {
            privateText.setVisibility(View.GONE);
        }

        // Cambiar texto del botón cancelar a "Cerrar"
        cancelButton.setText(R.string.close);

        // Crear el diálogo
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Verificar si el usuario ya está inscrito al evento
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;

        if (userEmail != null) {
            // Cargar asistentes una sola vez y configurar el botón
            loadEventAttendeesAndSetupButton(event, userEmail, joinButton, participantsText, showQrButton, dialog);
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Cargar asistentes del evento y configurar el botón según el estado
     */
    private void loadEventAttendeesAndSetupButton(Event event, String userEmail, Button joinButton, TextView participantsText, ImageButton showQrButton, AlertDialog dialog) {
        attendeeViewModel.getAttendees().removeObservers(this);
        attendeeViewModel.clearAttendeesList();

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        attendeeViewModel.loadEventAttendees(event.getId());

        // Observar asistentes para este evento (un solo observer activo por ciclo de diálogo)
        attendeeViewModel.getAttendees().observe(this, attendees -> {
            if (attendees == null) {
                return;
            }
            // Los asistentes ya están filtrados por evento específico
            List<Attendee> eventAttendees = attendees;

            // Verificar inscripción por UID (datos recién cargados desde Firestore)
            boolean isAlreadyJoined = userId != null && eventAttendees.stream()
                    .anyMatch(attendee -> userId.equals(attendee.getUid()));
                
                // Actualizar contador con el número real de asistentes de este evento
                event.setCurrentParticipants(eventAttendees.size());
                participantsText.setText(getResources().getQuantityString(
                        R.plurals.event_participants_count,
                        event.getMaxParticipants(),
                        event.getCurrentParticipants(), event.getMaxParticipants()));
                
                // Verificar si está lleno
                boolean isEventFull = event.getCurrentParticipants() >= event.getMaxParticipants();
                if (isEventFull) {
                    participantsText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, getTheme()));
                } else {
                    participantsText.setTextColor(getResources().getColor(R.color.colorSecondaryText, getTheme()));
                }
                
                // Configurar botón según el estado
                if (isAlreadyJoined) {
                    // Usuario ya inscrito - puede darse de baja
                    joinButton.setText(R.string.attendee_unsubscribe_short);
                    joinButton.setEnabled(true);
                    joinButton.setAlpha(1.0f);
                    showQrButton.setEnabled(true);
                    showQrButton.setAlpha(1.0f);
                    joinButton.setOnClickListener(v -> {
                        unsubscribeFromEvent(event, userEmail, joinButton, participantsText, showQrButton, dialog);
                    });
                } else {
                    // Usuario no inscrito - puede inscribirse si hay espacio
                    joinButton.setText(R.string.attendee_join_event_short);
                    showQrButton.setEnabled(false);
                    showQrButton.setAlpha(0.5f);
                    if (isEventFull) {
                        joinButton.setEnabled(false);
                        joinButton.setAlpha(0.5f);
                    } else {
                        joinButton.setEnabled(true);
                        joinButton.setAlpha(1.0f);
                        joinButton.setOnClickListener(v -> {
                            subscribeToEvent(event, userEmail, joinButton, participantsText, showQrButton, dialog);
                        });
                    }
                }
        });
    }


    /**
     * Desinscribirse del evento
     */
    private void unsubscribeFromEvent(Event event, String userEmail, Button joinButton, TextView participantsText, ImageButton showQrButton, AlertDialog dialog) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }

        eventDialogContext = new EventDialogContext(
                event, userEmail, joinButton, participantsText, showQrButton, dialog);
        attendeeViewModel.unsubscribeFromEvent(
                event.getId(),
                userEmail,
                event.getUserId(),
                event.getTitle(),
                resolveAttendeeDisplayName());
    }


    /**
     * Suscribirse al evento directamente usando los datos del usuario autenticado
     */
    private void subscribeToEvent(Event event, String userEmail, Button joinButton, TextView participantsText, ImageButton showQrButton, AlertDialog dialog) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }

        if (userEmail == null || userEmail.trim().isEmpty()) {
            ToastUtils.showCustomToast(this, "Error: No se pudo obtener el email del usuario", ToastUtils.ToastType.ERROR);
            return;
        }

        eventDialogContext = new EventDialogContext(
                event, userEmail, joinButton, participantsText, showQrButton, dialog);
        pendingSubscribeAction = true;

        Attendee cachedProfile = attendeeViewModel.getCurrentAttendee().getValue();
        if (cachedProfile != null) {
            executeSubscribeWithProfile(event, userEmail, cachedProfile);
            return;
        }

        Observer<Attendee> profileObserver = new Observer<Attendee>() {
            @Override
            public void onChanged(Attendee attendee) {
                if (!pendingSubscribeAction || attendee == null) {
                    return;
                }
                attendeeViewModel.getCurrentAttendee().removeObserver(this);
                executeSubscribeWithProfile(event, userEmail, attendee);
            }
        };
        attendeeViewModel.getCurrentAttendee().observe(this, profileObserver);
        attendeeViewModel.loadCurrentAttendee();
    }

    private void executeSubscribeWithProfile(Event event, String userEmail, Attendee attendee) {
        if (!pendingSubscribeAction) {
            return;
        }
        pendingSubscribeAction = false;

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (!attendee.isProfileComplete()) {
            showCompleteProfileDialog(event, attendee);
            return;
        }

        attendeeViewModel.addAttendee(
                event.getId(),
                userId,
                attendee.getUsername(),
                attendee.getPrimerApellido(),
                attendee.getDni(),
                userEmail,
                attendee.getPhone() != null ? attendee.getPhone() : "",
                attendee.getFechaNacimiento() != null ? attendee.getFechaNacimiento().toDate().toString() : "",
                false,
                event.getUserId(),
                event.getTitle(),
                attendee.getSortedNameLabel()
        );
    }

    private String resolveAttendeeDisplayName() {
        Attendee attendee = attendeeViewModel.getCurrentAttendee().getValue();
        if (attendee != null) {
            String label = attendee.getSortedNameLabel();
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }
        }
        return getString(R.string.notification_organizer_unknown_attendee);
    }

    private static String formatDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (!last.isEmpty() && !first.isEmpty()) {
            return last + ", " + first;
        }
        if (!first.isEmpty()) {
            return first;
        }
        return last;
    }

    private void showEventQrDialog(Event event) {
        if (event == null || event.getId() == null) {
            ToastUtils.showCustomToast(this, getString(R.string.attendee_qr_error), ToastUtils.ToastType.ERROR);
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            ToastUtils.showCustomToast(this, getString(R.string.attendee_qr_error), ToastUtils.ToastType.ERROR);
            return;
        }
        AttendeeQrDisplayActivity.start(this, event.getId(), event.getTitle());
    }

    /**
     * Muestra dialog para completar perfil de asistente antes de inscribirse a evento
     */
    private void showCompleteProfileDialog(Event event, com.us.eventum.data.models.Attendee attendee) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_attendee_profile, null);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText(R.string.complete_profile_join_title);
        
        com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        com.google.android.material.textfield.TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        com.google.android.material.textfield.TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        com.google.android.material.textfield.TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        com.google.android.material.textfield.TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);

        // Pre-cargar datos actuales si existen
        if (attendee.getUsername() != null) nameInput.setText(attendee.getUsername());
        if (attendee.getPrimerApellido() != null) firstSurnameInput.setText(attendee.getPrimerApellido());
        if (attendee.getSegundoApellido() != null) secondSurnameInput.setText(attendee.getSegundoApellido());
        if (attendee.getDni() != null) dniInput.setText(attendee.getDni());
        if (attendee.getPhone() != null) phoneInput.setText(attendee.getPhone());
        if (attendee.getFechaNacimiento() != null) {
            String fechaStr = attendee.getFechaNacimiento().toDate().toString();
            birthDateInput.setText(fechaStr);
        }

        birthDateInput.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
                String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
                birthDateInput.setText(getString(R.string.date_format_dmy, dd, mm, year));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            String firstSurname = String.valueOf(firstSurnameInput.getText()).trim();
            String secondSurname = String.valueOf(secondSurnameInput.getText()).trim();
            String dni = String.valueOf(dniInput.getText()).trim();
            String phone = String.valueOf(phoneInput.getText()).trim();
            String birth = String.valueOf(birthDateInput.getText()).trim();

            // Validaciones: todos obligatorios
            if (name.isEmpty() || firstSurname.isEmpty() || secondSurname.isEmpty() || dni.isEmpty() || phone.isEmpty() || birth.isEmpty()) {
                ToastUtils.showCustomToast(this, "Por favor, completa todos los campos obligatorios", ToastUtils.ToastType.ERROR);
                return;
            }

            // Mostrar advertencia antes de guardar
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Importante")
                    .setMessage("Los datos personales (nombre, apellidos, DNI y fecha de nacimiento) no se podrán modificar posteriormente. ¿Deseas continuar?")
                    .setPositiveButton("Aceptar", (d, w) -> {
                        // Actualizar perfil
                        attendeeViewModel.updateAttendee(attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                        
                        dialog.dismiss();
                        ToastUtils.showCustomToast(this, "Perfil completado. Ahora puedes inscribirte al evento.", ToastUtils.ToastType.SUCCESS);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        dialog.show();
    }
}

