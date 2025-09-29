package com.us.eventum.presentation.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.card.MaterialCardView;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.R;
import com.us.eventum.adapters.EventsPagerAdapter;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.presentation.fragments.EventsFragment;
import com.us.eventum.presentation.viewmodels.SharedViewModel;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.presentation.viewmodels.UserViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.PermissionUtils;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.data.repositories.FirebaseManager;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.material.button.MaterialButton;
import com.us.eventum.utils.EventSearchFilter;
import com.google.android.material.textfield.TextInputEditText;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import androidx.fragment.app.Fragment;

public class OrganizerHomeActivity extends AppCompatActivity implements EventsPagerAdapter.EventContextMenuListener {
    private static final String TAG = "OrganizerHomeActivity";
    private FloatingActionButton settingsButton, createEventButton, searchEventsButton;
    private FirebaseManager firebaseManager;
    private String userId;
    private List<Event> futureEvents = new ArrayList<>();
    private List<Event> pastEvents = new ArrayList<>();
    private EventSearchFilter currentSearchFilter = new EventSearchFilter();
    private boolean isSearchActive = false;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private EventsPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private CircleImageView profileImageView;
    private LinearLayout filterIndicatorLayout;
    private TextView filterIndicatorText;
    private View clearFiltersButton;
    private SimpleDateFormat dateFormat;
    private SharedViewModel sharedViewModel;
    private EventViewModel eventViewModel;
    private UserViewModel userViewModel;
    private AttendeeViewModel attendeeViewModel;
    private boolean isLoadingEvents = false;
    
    // MaterialCardView y GestureDetector para FABs
    private MaterialCardView fabContainer;
    private View gestureOverlay;
    private GestureDetector gestureDetector;
    private boolean isFabContainerVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        firebaseManager = FirebaseManager.getInstance();
        userId = firebaseManager.getAuth().getCurrentUser().getUid();

        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            // Inicializar ViewModels
        sharedViewModel = SharedViewModel.getInstance();
            eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
            userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
            attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
            
            // Inicializar repositorios en ViewModels
            eventViewModel.initializeRepository(this);
            userViewModel.initializeRepository(this);
            attendeeViewModel.initializeRepository(this);

        // Inicializar vistas
        initializeViews();
        setupGestureDetector();
        setupClickListeners();
        setupViewPager();
        observeViewModels();
        loadEvents();
        
        
        // Configurar observador de actualización de imagen de perfil
        setupProfileImageObserver();
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
                    if (event.getDate().after(now)) {
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

        // Observar operaciones exitosas
        eventViewModel.getEventCreated().observe(this, created -> {
            if (created != null && created) {
                ToastUtils.showCustomToast(this, "Evento creado con éxito", ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        eventViewModel.getEventUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                ToastUtils.showCustomToast(this, "Evento actualizado con éxito", ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        eventViewModel.getEventDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, "Evento eliminado con éxito", ToastUtils.ToastType.SUCCESS);
                eventViewModel.clearOperationStates();
            }
        });

        // Observar datos del usuario del UserViewModel
        userViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                TextView userNameTextView = findViewById(R.id.userNameTextView);
                if (user.getNombre() != null && !user.getNombre().isEmpty()) {
                    userNameTextView.setText(user.getNombre());
                } else {
                    // Usar email como fallback
                    String email = user.getEmail();
                    if (email != null && email.contains("@")) {
                        userNameTextView.setText(email.substring(0, email.indexOf('@')));
                    }
                }

                // Mostrar etiqueta de rol (esta pantalla es para ORGANIZADOR)
                String role = user.getRole() != null ? user.getRole() : UserRole.ORGANIZER;
                boolean isAssistant = UserRole.ATTENDEE.equalsIgnoreCase(role);
                TextView userRoleTextView = findViewById(R.id.userRoleTextView);
                if (userRoleTextView != null) {
                    userRoleTextView.setText(isAssistant ? R.string.role_attendee : R.string.role_organizer);
                    userRoleTextView.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                    userRoleTextView.setBackgroundResource(isAssistant ? R.drawable.bg_role_badge_assistant : R.drawable.bg_role_badge_organizer);
                }
            }
        });

        // Observar errores del UserViewModel
        userViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar limpieza de asistentes del AttendeeViewModel
        attendeeViewModel.getAttendeesCleared().observe(this, cleared -> {
            if (cleared != null && cleared) {
                ToastUtils.showCustomToast(this, "Lista de asistentes vaciada", ToastUtils.ToastType.SUCCESS);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Siempre recargar eventos al volver a OrganizerHomeActivity
        loadEvents();
        
        // Observar cambios en los flags de actualización
        sharedViewModel.getEventsUpdated().observe(this, eventsUpdated -> {
            if (eventsUpdated) {
                // Guardar el estado de búsqueda antes de recargar
                boolean wasSearchActive = isSearchActive;
                EventSearchFilter savedFilter = currentSearchFilter;
                
                // Limpiar las listas antes de recargar para evitar duplicados
                futureEvents.clear();
                pastEvents.clear();
                loadEvents();
                
                // Restaurar el estado de búsqueda después de recargar
                if (wasSearchActive && savedFilter != null) {
                    isSearchActive = true;
                    currentSearchFilter = savedFilter;
                }
                
                sharedViewModel.resetEventsUpdated();
            }
        });

        // Cargar imagen de perfil
        ProfileImageManager.loadProfileImage(this, profileImageView);
    }
    
    private void setupProfileImageObserver() {
        sharedViewModel.getProfileImageUpdated().observe(this, profileImageUpdated -> {
            if (profileImageUpdated) {
                ProfileImageManager.loadProfileImage(this, profileImageView);
                sharedViewModel.resetProfileImageUpdated();
            }
        });
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                
                // Detectar gestos en el overlay (parte inferior)
                float deltaY = e2.getY() - e1.getY();
                float deltaX = e2.getX() - e1.getX();
                
                // Verificar que sea un movimiento vertical significativo
                if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 30) {
                    if (deltaY > 0) {
                        // Swipe hacia abajo - ocultar
                        hideFabContainer();
                    } else {
                        // Swipe hacia arriba - mostrar
                        showFabContainer();
                    }
                    return true;
                }
                return false;
            }
        });
        
        // Aplicar al overlay transparente
        gestureOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true; // Consumir el evento para que no pase a otros elementos
        });
    }
    
    private void hideFabContainer() {
        if (isFabContainerVisible) {
            isFabContainerVisible = false;
            fabContainer.animate()
                    .translationY(fabContainer.getHeight() + 50)
                    .setDuration(300)
                    .start();
        }
    }
    
    private void showFabContainer() {
        if (!isFabContainerVisible) {
            isFabContainerVisible = true;
            fabContainer.animate()
                    .translationY(0)
                    .setDuration(300)
                    .start();
        }
    }

    private void initializeViews() {
        createEventButton = findViewById(R.id.createEventFab);
        searchEventsButton = findViewById(R.id.searchEventsFab);
        settingsButton = findViewById(R.id.settingsButton);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        profileImageView = findViewById(R.id.profileImageView);
        filterIndicatorLayout = findViewById(R.id.filterIndicatorLayout);
        filterIndicatorText = findViewById(R.id.filterIndicatorText);
        clearFiltersButton = findViewById(R.id.clearFiltersButton);
        fabContainer = findViewById(R.id.fabContainer);
        gestureOverlay = findViewById(R.id.gestureOverlay);
        
        // Inicializar vistas de información de usuario
        TextView userNameTextView = findViewById(R.id.userNameTextView);
        TextView userEmailTextView = findViewById(R.id.userEmailTextView);
        
        // Obtener y mostrar información del usuario
        if (firebaseManager.getAuth().getCurrentUser() != null) {
            String email = firebaseManager.getAuth().getCurrentUser().getEmail();
            
            // Establecer el email inmediatamente
            userEmailTextView.setText(email);
            
            // Usar UserViewModel para cargar datos del usuario
            userViewModel.loadCurrentUser();
        }

        // Ajustar estado del FAB según la pestaña seleccionada
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean isArchivedTab = position == 1;
                createEventButton.setAlpha(isArchivedTab ? 0.5f : 1f);
            }
        });
    }

    private void setupViewPager() {
        pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
        pagerAdapter.setOnRefreshListener(this::onRefreshRequested);
        viewPager.setAdapter(pagerAdapter);

        // Configurar el TabLayout
        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("PRÓXIMOS (" + futureEvents.size() + ")");
                    } else {
                        tab.setText("ARCHIVADOS (" + pastEvents.size() + ")");
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
            startActivity(intent);
        });

        createEventButton.setOnClickListener(v -> {
            int currentTab = tabLayout.getSelectedTabPosition();
            if (currentTab == 1) { // Archivados
                ToastUtils.showCustomToast(this, "No se pueden crear eventos desde Archivados. Cambia a Próximos.", ToastUtils.ToastType.INFO);
                return;
            }
            Intent intent = new Intent(this, CreateEventActivity.class);
            startActivity(intent);
        });

        searchEventsButton.setOnClickListener(v -> showSearchDialog());
        
        clearFiltersButton.setOnClickListener(v -> clearSearchFilters());
    }

    private void loadEvents() {
        if (firebaseManager.getAuth().getCurrentUser() == null) return;
        
        // Usar EventViewModel para cargar eventos
        eventViewModel.loadUserEvents();
    }

    private void addEventToList(Event event, Date now) {
        if (event.getDate().after(now)) {
            futureEvents.add(event);
        } else {
            pastEvents.add(event);
        }
    }

    private void updateUI() {
        // Ordenar eventos futuros por fecha ascendente
        futureEvents.sort((e1, e2) -> e1.getDate().compareTo(e2.getDate()));
        
        // Ordenar eventos pasados por fecha descendente
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
            futureTab.setText("PRÓXIMOS (" + futureEvents.size() + ")");
        }
        
        if (pastTab != null) {
            pastTab.setText("ARCHIVADOS (" + pastEvents.size() + ")");
        }
        
        // Actualizar datos en los fragmentos o crear adaptador si es null
        if (pagerAdapter == null) {
            pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
            pagerAdapter.setOnRefreshListener(this::onRefreshRequested);
            viewPager.setAdapter(pagerAdapter);
        } else {
            pagerAdapter.updateEvents();
        }
        
        // Forzar actualización del adaptador para refrescar los iconos
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }
        
        // Actualizar títulos de pestañas
        updateTabTitles(futureEvents.size(), pastEvents.size());
    }

    

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (!allPermissionsGranted) {
                ToastUtils.showCustomToast(this, 
                    "Se requieren permisos para el funcionamiento completo de la aplicación", 
                    ToastUtils.ToastType.WARNING);
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item, Event event) {
        int id = item.getItemId();
        Intent intent;
        if (id == R.id.action_edit_event) {
            showEditEventDialog(event);
            return true;
        } else if (id == R.id.action_delete_event) {
            showDeleteEventDialog(event);
            return true;
        } else if (id == R.id.action_send_invitations) {
            ToastUtils.showCustomToast(this, "Próximamente: Enviar invitaciones", ToastUtils.ToastType.INFO);
            return true;
        } else if (id == R.id.action_verify_attendees) {
            intent = new Intent(this, QRScannerActivity.class);
            intent.putExtra("eventId", event.getId());
        startActivity(intent);
            return true;
        } else if (id == R.id.action_clear_list) {
            showClearAttendeeListConfirmation(event);
            return true;
        }
        return false;
    }

    private void showEditEventDialog(Event event) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_event, null);
        
        EditText titleInput = dialogView.findViewById(R.id.titleInput);
        EditText locationInput = dialogView.findViewById(R.id.locationInput);
        EditText dateInput = dialogView.findViewById(R.id.dateInput);
        EditText maxParticipantsInput = dialogView.findViewById(R.id.maxParticipantsInput);
        CheckBox eventoPrivadoCheckBox = dialogView.findViewById(R.id.eventoPrivadoCheckBox);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        
        // Rellenar los campos con los datos actuales del evento
        titleInput.setText(event.getTitle());
        locationInput.setText(event.getLocation());
        dateInput.setText(dateFormat.format(event.getDate()));
        maxParticipantsInput.setText(String.valueOf(event.getMaxParticipants()));
        eventoPrivadoCheckBox.setChecked(event.getPrivateEvent());
        
        // Configurar el DatePicker para la fecha
        dateInput.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(event.getDate());
            
            DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    dateInput.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.setView(dialogView).create();
        
        // Configurar los botones
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String location = locationInput.getText().toString().trim();
            String dateStr = dateInput.getText().toString().trim();
            String maxParticipantsStr = maxParticipantsInput.getText().toString().trim();
            
            if (title.isEmpty() || location.isEmpty() || dateStr.isEmpty() || maxParticipantsStr.isEmpty()) {
                ToastUtils.showCustomToast(this, "Todos los campos son obligatorios", ToastUtils.ToastType.WARNING);
                return;
            }
            
            try {
                Date newDate = dateFormat.parse(dateStr);
                int maxParticipants = Integer.parseInt(maxParticipantsStr);
                
                // Verificar que el número de plazas no sea menor al número actual de asistentes
                if (maxParticipants < event.getCurrentParticipants()) {
                    ToastUtils.showCustomToast(this, 
                        "El número de plazas no puede ser menor que el número actual de asistentes (" + 
                        event.getCurrentParticipants() + ")", 
                        ToastUtils.ToastType.WARNING);
                    return;
                }
                
                // Usar EventViewModel para actualizar el evento
                eventViewModel.updateEvent(event.getId(), title, event.getDescription(), newDate, 
                    location, maxParticipants, event.getEventType(), eventoPrivadoCheckBox.isChecked());
                
                        dialog.dismiss();
            } catch (ParseException e) {
                ToastUtils.showCustomToast(this, "Error en el formato de fecha", ToastUtils.ToastType.ERROR);
            } catch (NumberFormatException e) {
                ToastUtils.showCustomToast(this, "El número de plazas debe ser un número válido", ToastUtils.ToastType.ERROR);
            }
        });
        
        dialog.show();
    }

    private void showDeleteEventDialog(Event event) {
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmTitleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        
        confirmTitleTextView.setText("Eliminar evento");
        confirmMessageTextView.setText("¿Estás seguro de que deseas eliminar este evento? Esta acción no se puede deshacer.");
        
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

    private void showSuccessToast(String message) {
        View toastView = getLayoutInflater().inflate(R.layout.toast_success, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(toastView);
        toast.show();
    }

    public enum ToastType {
        SUCCESS, ERROR, WARNING, INFO
    }

    private void showCustomToast(String message, ToastType type) {
        int layoutRes;
        switch (type) {
            case SUCCESS:
                layoutRes = R.layout.toast_success;
                break;
            case ERROR:
                layoutRes = R.layout.toast_error;
                break;
            case WARNING:
                layoutRes = R.layout.toast_warning;
                break;
            case INFO:
                layoutRes = R.layout.toast_info;
                break;
            default:
                layoutRes = R.layout.toast_success;
        }

        View toastView = getLayoutInflater().inflate(layoutRes, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(toastView);
        toast.show();
    }


    private void showClearAttendeeListConfirmation(Event event) {
        View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView confirmTitleTextView = confirmDialogView.findViewById(R.id.confirm_title);
        TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
        MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
        MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
        
        confirmTitleTextView.setText("Vaciar lista de asistentes");
        confirmMessageTextView.setText("¿Estás seguro de que deseas eliminar todos los asistentes? Esta acción no se puede deshacer.");
        confirmDeleteButton.setText("Vaciar lista");
        
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

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomSearchDialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_events, null);
        
        // Obtener referencias a los campos
        TextInputEditText keywordsEditText = dialogView.findViewById(R.id.keywordsEditText);
        AutoCompleteTextView eventTypeAutoComplete = dialogView.findViewById(R.id.eventTypeAutoComplete);
        TextInputEditText locationEditText = dialogView.findViewById(R.id.locationEditText);
        TextInputEditText dateFromEditText = dialogView.findViewById(R.id.dateFromEditText);
        TextInputEditText dateToEditText = dialogView.findViewById(R.id.dateToEditText);
        MaterialButton clearButton = dialogView.findViewById(R.id.clearButton);
        MaterialButton searchButton = dialogView.findViewById(R.id.searchButton);
        View closeButton = dialogView.findViewById(R.id.closeButton);
        
        // Configurar AutoCompleteTextView para tipo de evento
        String[] eventTypes = {
            "Concierto", "Graduación", "Fiesta", "Despedida", 
            "Aniversario", "Conferencia", "Seminario", "Taller", 
            "Exposición", "Feria", "Congreso", "Ceremonia", "Otro"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            R.layout.dropdown_item,
            eventTypes
        );
        eventTypeAutoComplete.setAdapter(adapter);
        
        // Cargar filtros actuales
        if (currentSearchFilter.getKeywords() != null) {
            keywordsEditText.setText(currentSearchFilter.getKeywords());
        }
        if (currentSearchFilter.getEventType() != null) {
            eventTypeAutoComplete.setText(currentSearchFilter.getEventType());
        }
        if (currentSearchFilter.getLocation() != null) {
            locationEditText.setText(currentSearchFilter.getLocation());
        }
        if (currentSearchFilter.getDateFrom() != null) {
            dateFromEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentSearchFilter.getDateFrom()));
        }
        if (currentSearchFilter.getDateTo() != null) {
            dateToEditText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentSearchFilter.getDateTo()));
        }
        
        // Configurar DatePickers
        setupDatePicker(dateFromEditText, true);
        setupDatePicker(dateToEditText, false);
        
        // Configurar botones
        clearButton.setOnClickListener(v -> {
            keywordsEditText.setText("");
            eventTypeAutoComplete.setText("");
            locationEditText.setText("");
            dateFromEditText.setText("");
            dateToEditText.setText("");
        });
        
        searchButton.setOnClickListener(v -> {
            // Aplicar filtros
            String keywords = keywordsEditText.getText().toString().trim();
            String eventType = eventTypeAutoComplete.getText().toString().trim();
            String location = locationEditText.getText().toString().trim();
            String dateFromStr = dateFromEditText.getText().toString().trim();
            String dateToStr = dateToEditText.getText().toString().trim();
            
            // Validar que se introduzca al menos un campo
            boolean hasValidSearch = !keywords.isEmpty() || !eventType.isEmpty() || !location.isEmpty() || !dateFromStr.isEmpty() || !dateToStr.isEmpty();
            
            if (!hasValidSearch) {
                ToastUtils.showCustomToast(this, "Debe introducir al menos un criterio de búsqueda", ToastUtils.ToastType.WARNING);
                return;
            }
            
            // Crear nuevo filtro
            EventSearchFilter newFilter = new EventSearchFilter();
            newFilter.setKeywords(keywords.isEmpty() ? null : keywords);
            newFilter.setEventType(eventType.isEmpty() ? null : eventType);
            newFilter.setLocation(location.isEmpty() ? null : location);
            
            try {
                if (!dateFromStr.isEmpty()) {
                    newFilter.setDateFrom(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateFromStr));
                }
                if (!dateToStr.isEmpty()) {
                    newFilter.setDateTo(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateToStr));
                }
            } catch (ParseException e) {
                ToastUtils.showCustomToast(this, "Formato de fecha inválido", ToastUtils.ToastType.ERROR);
                return;
            }
            
            // Aplicar filtros
            currentSearchFilter = newFilter;
            applySearchFilters();
            
            // Cerrar diálogo
            if (dialog != null) {
                dialog.dismiss();
            }
        });
        
        closeButton.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });
        
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        this.dialog = dialog; // Guardar referencia para poder cerrarlo
        dialog.show();
    }
    
    private AlertDialog dialog; // Variable para guardar referencia del diálogo
    
    private void setupDatePicker(TextInputEditText editText, boolean isFromDate) {
        editText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (isFromDate && currentSearchFilter.getDateFrom() != null) {
                calendar.setTime(currentSearchFilter.getDateFrom());
            } else if (!isFromDate && currentSearchFilter.getDateTo() != null) {
                calendar.setTime(currentSearchFilter.getDateTo());
            }
            
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);
                    editText.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedCalendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
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
            filterIndicatorText.setText("Filtros: " + currentSearchFilter.getActiveFiltersSummary());
            
            // Mostrar mensaje con número de resultados
            if (filteredEvents.isEmpty()) {
                ToastUtils.showCustomToast(this, "No se encontraron eventos que coincidan con los criterios de búsqueda en esta pestaña", ToastUtils.ToastType.INFO);
            } else {
                String message = String.format("Se encontraron %d evento%s que coinciden con los criterios de búsqueda", 
                    filteredEvents.size(), filteredEvents.size() == 1 ? "" : "s");
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
    }
    
    private void clearSearchFilters() {
        currentSearchFilter.clearFilters();
        applySearchFilters();
        ToastUtils.showCustomToast(this, "Filtros limpiados", ToastUtils.ToastType.INFO);
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
                        futureTab.setText(String.format("Próximos (%d*)", futureCount));
                    } else {
                        futureTab.setText(String.format("Próximos (%d)", futureCount));
                    }
                }
                if (pastTab != null) {
                    if (currentTab == 1) {
                        pastTab.setText(String.format("Archivados (%d*)", pastCount));
                    } else {
                        pastTab.setText(String.format("Archivados (%d)", pastCount));
                    }
                }
            } else {
                if (futureTab != null) {
                    futureTab.setText(String.format("Próximos (%d)", futureCount));
                }
                if (pastTab != null) {
                    pastTab.setText(String.format("Archivados (%d)", pastCount));
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
        Log.d(TAG, "Refresh solicitado desde fragment");
        if (eventViewModel != null) {
            eventViewModel.loadUserEvents();
        }
    }
} 