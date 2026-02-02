package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.hdodenhof.circleimageview.CircleImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.us.eventum.R;
import com.us.eventum.adapters.EventAdapter;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.utils.NetworkUtils;
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
 * Activity para asistentes: lista eventos disponibles (fecha >= hoy)
 */
public class AttendeeHomeActivity extends AppCompatActivity {

    private EventViewModel eventViewModel;
    private RecyclerView recyclerView;
    private View progressBar;
    private TextView emptyText;
    private EventAdapter adapter;
    private AttendeeViewModel attendeeViewModel;
    private CircleImageView profileImageView;
    private SharedViewModel sharedViewModel = SharedViewModel.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_available_events);

        // Header similar a Home: rellenar nombre/email/rol e imagen de perfil

        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);
        recyclerView = findViewById(R.id.eventsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter();
        adapter.setOnItemClickListener(new EventAdapter.OnEventClickListener() {
            @Override
            public void onEventClick(Event event) {
                showEventDetailsDialog(event);
            }

            @Override
            public void onEventLongClick(View view, Event event) {
                // Si hay email autenticado, usarlo directamente; si no, pedirlo
                String email = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                        : null;
                if (email != null && !email.trim().isEmpty()) {
                    attendeeViewModel.unsubscribeFromEvent(event.getId(), email);
                    attendeeViewModel.getErrorMessage().observe(AttendeeHomeActivity.this, err -> {
                        if (err != null && !err.isEmpty()) {
                            ToastUtils.showCustomToast(AttendeeHomeActivity.this, err, ToastUtils.ToastType.ERROR);
                        }
                    });
                    attendeeViewModel.getAttendeeDeleted().observe(AttendeeHomeActivity.this, deleted -> {
                        if (deleted != null && deleted) {
                            ToastUtils.showCustomToast(AttendeeHomeActivity.this, "Baja realizada", ToastUtils.ToastType.SUCCESS);
                            attendeeViewModel.clearOperationStates();
                            // Recargar eventos para actualizar contadores
                            loadAvailableEvents();
                        }
                    });
                } else {
                    showUnsubscribeDialog(event);
                }
            }

            @Override
            public void onLockIconLongClick(Event event) {
                // Solo el organizador puede modificar la privacidad del evento
                ToastUtils.showCustomToast(AttendeeHomeActivity.this, 
                    "Solo el organizador puede cambiar la privacidad del evento", 
                    ToastUtils.ToastType.INFO);
            }
        });
        recyclerView.setAdapter(adapter);

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

        observeViewModel();
        loadAvailableEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Asegurar que la imagen de perfil se actualiza inmediatamente al volver de Settings
        if (profileImageView != null) {
            ProfileImageManager.loadProfileImage(this, profileImageView);
        }
    }

    private void observeViewModel() {
        eventViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        });

        // Observar estado de carga del AttendeeViewModel para inscripciones/desinscripciones
        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        });

        eventViewModel.getAllEvents().observe(this, events -> {
            if (events == null) {
                showEvents(new ArrayList<>());
                return;
            }
            // Filtro local por fecha >= hoy y ordenar ascendente
            List<Event> future = new ArrayList<>();
            Date now = new Date();
            for (Event e : events) {
                if (e != null && e.getDate() != null && !e.getDate().before(now)) {
                    future.add(e);
                }
            }
            future.sort((a, b) -> a.getDate().compareTo(b.getDate()));
            showEvents(future);
        });
    }

    private void loadAvailableEvents() {
        // Verificar conexión a internet
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        
        // Cargar todos los eventos públicos desde repositorio
        eventViewModel.loadAllEvents();
    }

    private void showEvents(List<Event> events) {
        adapter.setEvents(events);
        boolean empty = events.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
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
                birthDateInput.setText(dd + "/" + mm + "/" + year);
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
                    attendeeViewModel.addAttendee(
                            event.getId(),
                            uid,
                            nameInput.getText().toString().trim(),
                            lastNameInput.getText().toString().trim(),
                            dniInput.getText().toString().trim(),
                            emailInput.getText().toString().trim(),
                            phoneInput.getText().toString().trim(),
                            birthDateInput.getText().toString().trim(),
                            false
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });
        attendeeViewModel.getAttendeeAdded().observe(this, added -> {
            if (added != null && added) {
                ToastUtils.showCustomToast(this, "Inscripción realizada", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
            }
        });
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
                    attendeeViewModel.unsubscribeFromEvent(event.getId(), emailInput.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", null)
                .show();

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });
        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, "Baja realizada", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
                // Recargar eventos para actualizar contadores
                loadAvailableEvents();
            }
        });
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
        
        Button joinButton = dialogView.findViewById(R.id.joinEventButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Rellenar datos del evento
        titleText.setText(event.getTitle());
        descriptionText.setText(event.getDescription());
        
        // Formatear fecha y hora
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        dateText.setText(dateFormat.format(event.getDate()));
        timeText.setText(timeFormat.format(event.getDate()));
        
        locationText.setText(event.getLocation());
        participantsText.setText(String.format("%d/%d asistentes", event.getCurrentParticipants(), event.getMaxParticipants()));
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
            privateText.setText("Evento Privado");
            privateText.setVisibility(View.VISIBLE);
        } else {
            privateText.setVisibility(View.GONE);
        }

        // Cambiar texto del botón cancelar a "Cerrar"
        cancelButton.setText("Cerrar");

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
            loadEventAttendeesAndSetupButton(event, userEmail, joinButton, participantsText, dialog);
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Cargar asistentes del evento y configurar el botón según el estado
     */
    private void loadEventAttendeesAndSetupButton(Event event, String userEmail, Button joinButton, TextView participantsText, AlertDialog dialog) {
        loadEventAttendeesAndSetupButton(event, userEmail, joinButton, participantsText, dialog, true);
    }

    /**
     * Cargar asistentes del evento y configurar el botón según el estado
     * @param showAlreadyJoinedMessage Si debe mostrar el mensaje "Ya estás apuntado" (solo al abrir diálogo)
     */
    private void loadEventAttendeesAndSetupButton(Event event, String userEmail, Button joinButton, TextView participantsText, AlertDialog dialog, boolean showAlreadyJoinedMessage) {
        attendeeViewModel.loadEventAttendees(event.getId());
        
        // Observar asistentes UNA SOLA VEZ para este evento específico
        attendeeViewModel.getAttendees().observe(this, attendees -> {
            if (attendees != null) {
                // Los asistentes ya están filtrados por evento específico
                List<Attendee> eventAttendees = attendees;
                
                // Verificar si el usuario ya está inscrito en este evento
                boolean isAlreadyJoined = eventAttendees.stream()
                        .anyMatch(attendee -> attendee.getEmail().equalsIgnoreCase(userEmail));
                
                // Actualizar contador con el número real de asistentes de este evento
                event.setCurrentParticipants(eventAttendees.size());
                participantsText.setText(String.format("%d/%d asistentes", 
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
                    joinButton.setText("Darme de baja");
                    joinButton.setEnabled(true);
                    joinButton.setAlpha(1.0f);
                    joinButton.setOnClickListener(v -> {
                        unsubscribeFromEvent(event, userEmail, joinButton, participantsText, dialog);
                    });
                    
                    // Mostrar mensaje solo cuando se abre el diálogo inicialmente y ya está inscrito
                    if (showAlreadyJoinedMessage) {
                        ToastUtils.showCustomToast(AttendeeHomeActivity.this, "Ya estás apuntado a este evento", ToastUtils.ToastType.INFO);
                    }
                } else {
                    // Usuario no inscrito - puede inscribirse si hay espacio
                    joinButton.setText("Apuntarse");
                    if (isEventFull) {
                        joinButton.setEnabled(false);
                        joinButton.setAlpha(0.5f);
                    } else {
                        joinButton.setEnabled(true);
                        joinButton.setAlpha(1.0f);
                        joinButton.setOnClickListener(v -> {
                            subscribeToEvent(event, userEmail, joinButton, participantsText, dialog);
                        });
                    }
                }
            }
        });
    }


    /**
     * Desinscribirse del evento
     */
    private void unsubscribeFromEvent(Event event, String userEmail, Button joinButton, TextView participantsText, AlertDialog dialog) {
        // Verificar conexión a internet
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        
        attendeeViewModel.unsubscribeFromEvent(event.getId(), userEmail);
        
        // Observar resultado UNA SOLA VEZ
        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (deleted != null && deleted) {
                ToastUtils.showCustomToast(this, "Te has dado de baja del evento", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
                
                // Recargar asistentes para este evento específico (sin mostrar mensaje azul)
                loadEventAttendeesAndSetupButton(event, userEmail, joinButton, participantsText, dialog, false);
                
                // Recargar lista de eventos para actualizar contadores en background
                loadAvailableEvents();
            }
        });
    }


    /**
     * Suscribirse al evento directamente usando los datos del usuario autenticado
     */
    private void subscribeToEvent(Event event, String userEmail, Button joinButton, TextView participantsText, AlertDialog dialog) {
        // Verificar conexión a internet
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        
        if (userEmail == null || userEmail.trim().isEmpty()) {
            ToastUtils.showCustomToast(this, "Error: No se pudo obtener el email del usuario", ToastUtils.ToastType.ERROR);
            return;
        }

        // Cargar perfil y validar campos obligatorios
        attendeeViewModel.getCurrentAttendee().observe(this, attendee -> {
            if (attendee == null) return;

            String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;

            // Verificar si el perfil está completo
            if (!attendee.isProfileComplete()) {
                showCompleteProfileDialog(event, attendee);
                return;
            }

            // Apuntarse al evento usando los datos del perfil
            attendeeViewModel.addAttendee(
                    event.getId(),
                    userId,
                    attendee.getUsername(),
                    attendee.getPrimerApellido(),
                    attendee.getDni(),
                    userEmail,
                    attendee.getPhone() != null ? attendee.getPhone() : "",
                    attendee.getFechaNacimiento() != null ? attendee.getFechaNacimiento().toDate().toString() : "",
                    false
            );
        });
        
        // Observar el resultado de la operación de añadir asistente UNA SOLA VEZ
        attendeeViewModel.getAttendeeAdded().observe(this, added -> {
            if (added != null && added) {
                ToastUtils.showCustomToast(this, "Te has apuntado al evento correctamente", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
                
                // Recargar asistentes para este evento específico (sin mostrar mensaje azul)
                loadEventAttendeesAndSetupButton(event, userEmail, joinButton, participantsText, dialog, false);
                
                // Recargar eventos para actualizar contadores en la lista principal
                loadAvailableEvents();
            }
        });
        
        // Observar errores UNA SOLA VEZ
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
                attendeeViewModel.clearOperationStates();
            }
        });
    }

    /**
     * Muestra dialog para completar perfil de asistente antes de inscribirse a evento
     */
    private void showCompleteProfileDialog(Event event, com.us.eventum.data.models.Attendee attendee) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_attendee_profile, null);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText("Completar perfil para inscribirte");
        
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
                birthDateInput.setText(dd + "/" + mm + "/" + year);
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

