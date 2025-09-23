package com.us.eventum.presentation.activities;

import android.app.DatePickerDialog;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.adapters.AttendeeAdapter;
import com.us.eventum.models.Attendee;
import com.us.eventum.models.Event;
import com.us.eventum.R;

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.ImageView;
import com.us.eventum.utils.QRCodeGenerator;
import com.us.eventum.utils.ToastUtils;
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
    private FloatingActionButton addAttendeeButton;
    private FloatingActionButton searchAttendeeFab;
    private FloatingActionButton verifyQrFab;
    private MaterialCardView fabContainer;
    private View gestureOverlay;
    private GestureDetector gestureDetector;
    private boolean isFabContainerVisible = true;
    private RecyclerView attendeesRecyclerView;
    private ImageButton eventMenuButton;
    private SharedViewModel sharedViewModel;
    private AlertDialog confirmDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        // Inicializar Firestore
        db = FirebaseFirestore.getInstance();

        // Inicializar ViewModel
        sharedViewModel = SharedViewModel.getInstance();
        
        // Inicializar ActivityResultLauncher
        qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Recargar la lista de asistentes
                    loadAttendees();
                }
            }
        );

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Configurar botón de retroceso
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Obtener el evento y configurar el título
        event = getIntent().getParcelableExtra("event");
        TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
        
        if (event != null) {
            toolbarTitleTextView.setText(event.getTitle());
            // Inicializar el resto de la UI
            initializeViews();
            setupGesture();
            setupRecyclerView();
            displayEventDetails();
            loadAttendees();

            // Manejar extras para mostrar diálogos
            if (getIntent().getBooleanExtra("show_clear_dialog", false)) {
                showClearAttendeeListConfirmation();
            } else if (getIntent().getBooleanExtra("show_delete_dialog", false)) {
                showDeleteEventDialog();
            }
        } else {
            ToastUtils.showCustomToast(this, "Error al cargar el evento", ToastUtils.ToastType.ERROR);
            finish();
        }
    }

    private void initializeViews() {
        dateTextView = findViewById(R.id.eventDateTextView);
        eventTimeTextView = findViewById(R.id.eventTimeTextView);
        locationTextView = findViewById(R.id.eventLocationTextView);
        descriptionTextView = findViewById(R.id.eventDescriptionTextView);
        emptyAttendeesTextView = findViewById(R.id.emptyAttendeesTextView);
        addAttendeeButton = findViewById(R.id.addAttendeeButton);
        searchAttendeeFab = findViewById(R.id.searchAttendeeFab);
        verifyQrFab = findViewById(R.id.verifyQrFab);
        fabContainer = findViewById(R.id.fabContainer);
        gestureOverlay = findViewById(R.id.gestureOverlay);
        attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);
        eventMenuButton = findViewById(R.id.eventMenuButton);

        // Deshabilitar visualmente el botón "+" si el evento ya ha pasado, pero permitiendo click para informar
        try {
            if (event != null && event.getDate() != null && event.getDate().before(new Date())) {
                addAttendeeButton.setAlpha(0.5f);
            }
        } catch (Exception ignore) {}

        addAttendeeButton.setOnClickListener(v -> {
            // Si el evento ya finalizó, solo informar
            if (event != null && event.getDate() != null && event.getDate().before(new Date())) {
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
    }

    private void setupGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 30) {
                    if (dy > 0) hideFabContainer(); else showFabContainer();
                    return true;
                }
                return false;
            }
        });
        if (gestureOverlay != null) {
            gestureOverlay.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void hideFabContainer() {
        if (isFabContainerVisible && fabContainer != null) {
            isFabContainerVisible = false;
            fabContainer.animate().translationY(fabContainer.getHeight() + 50).setDuration(300).start();
        }
    }

    private void showFabContainer() {
        if (!isFabContainerVisible && fabContainer != null) {
            isFabContainerVisible = true;
            fabContainer.animate().translationY(0).setDuration(300).start();
        }
    }

    private void showSearchAttendeeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_attendee, null);
        TextInputEditText dniEditText = dialogView.findViewById(R.id.dniEditText);
        TextInputEditText nameEditText = dialogView.findViewById(R.id.nameEditText);
        TextInputEditText lastNameEditText = dialogView.findViewById(R.id.lastNameEditText);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton searchButton = dialogView.findViewById(R.id.searchButton);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        searchButton.setOnClickListener(v -> {
            String dni = dniEditText.getText() != null ? dniEditText.getText().toString().trim() : "";
            String name = nameEditText.getText() != null ? nameEditText.getText().toString().trim() : "";
            String lastName = lastNameEditText.getText() != null ? lastNameEditText.getText().toString().trim() : "";

            // Filtrar en memoria por ahora (luego añadimos restricciones avanzadas)
            List<Attendee> filtered = new ArrayList<>();
            for (Attendee a : attendees) {
                boolean matches = true;
                if (!dni.isEmpty()) matches &= a.getDni() != null && a.getDni().toUpperCase().contains(dni.toUpperCase());
                if (!name.isEmpty()) matches &= a.getName() != null && a.getName().toUpperCase().contains(name.toUpperCase());
                if (!lastName.isEmpty()) matches &= a.getLastName() != null && a.getLastName().toUpperCase().contains(lastName.toUpperCase());
                if (matches) filtered.add(a);
            }

            attendeeAdapter.setAttendees(filtered);
            dialog.dismiss();
            ToastUtils.showCustomToast(this, "Búsqueda aplicada", ToastUtils.ToastType.INFO);
        });

        dialog.show();
    }

    private void setupRecyclerView() {
        attendeesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendeeAdapter = new AttendeeAdapter(attendee -> showAttendeeDetails(attendee));
        attendeesRecyclerView.setAdapter(attendeeAdapter);
    }

    private void displayEventDetails() {
        if (event == null) return;
        
        // Crear formato de fecha más completo
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        String formattedDate = fullDateFormat.format(event.getDate());
        formattedDate = formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1);
        
        // Contar asistentes verificados
        long verifiedCount = attendees.stream().filter(Attendee::isVerified).count();
        
        // Formato más profesional para fecha, capacidad y ubicación
        String dateText = String.format("📅  %s", formattedDate);
        String capacityText = String.format("👥  %d de %d plazas ocupadas", 
            attendees.size(),
            event.getMaxParticipants());
        String verifiedText = String.format("%d asistentes verificados", verifiedCount);
        String locationText = String.format("📍  %s", event.getLocation());
        
        dateTextView.setText(dateText);
        // Hora en formato 24h HH:mm (solo texto, el icono ya está en la UI como parte del estilo de lista)
        try {
            SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            if (eventTimeTextView != null) {
                eventTimeTextView.setText(String.format("%s", hourFormat.format(event.getDate())));
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
        View verifiedLayout = findViewById(R.id.verifiedLayout);
        TextView verifiedTextView = findViewById(R.id.verifiedTextView);
        
        if (verifiedLayout != null && verifiedTextView != null) {
            verifiedTextView.setText(verifiedText);
            verifiedLayout.setVisibility(View.VISIBLE);
        }
        
        // Mostrar descripción si existe
        View descriptionLayout = findViewById(R.id.descriptionLayout);
        View descriptionSpacer = findViewById(R.id.descriptionSpacer);
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            descriptionTextView.setText(event.getDescription());
            if (descriptionLayout != null) {
                descriptionLayout.setVisibility(View.VISIBLE);
                if (descriptionSpacer != null) {
                    descriptionSpacer.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (descriptionLayout != null) {
                descriptionLayout.setVisibility(View.GONE);
                if (descriptionSpacer != null) {
                    descriptionSpacer.setVisibility(View.GONE);
                }
            }
        }
    }

    private void loadAttendees() {
        db.collection("attendees")
            .whereEqualTo("eventId", event.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                attendees.clear();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Attendee attendee = document.toObject(Attendee.class);
                    attendee.setId(document.getId());
                    attendees.add(attendee);
                }
                attendeeAdapter.setAttendees(attendees);
                updateAttendeesCount();
                
                // Mostrar mensaje cuando no hay asistentes
                if (attendees.isEmpty()) {
                    emptyAttendeesTextView.setVisibility(View.VISIBLE);
                    attendeesRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyAttendeesTextView.setVisibility(View.GONE);
                    attendeesRecyclerView.setVisibility(View.VISIBLE);
                }
            })
            .addOnFailureListener(e -> {
                ToastUtils.showCustomToast(this, "Error al cargar asistentes: " + e.getMessage(), ToastUtils.ToastType.ERROR);
            });
    }

    private void updateAttendeesCount() {
        // Solo actualizar la información de capacidad en displayEventDetails
        displayEventDetails();
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
        CheckBox parentalAuthCheckBox = dialogView.findViewById(R.id.parentalAuthCheckBox);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton addButton = dialogView.findViewById(R.id.addAttendeeButton);
        
        // Obtener los TextInputLayout para mostrar errores
        com.google.android.material.textfield.TextInputLayout dniLayout = dialogView.findViewById(R.id.dniLayout);
        com.google.android.material.textfield.TextInputLayout phoneLayout = dialogView.findViewById(R.id.phoneLayout);
        com.google.android.material.textfield.TextInputLayout birthDateLayout = dialogView.findViewById(R.id.birthDateLayout);

        // Por defecto, deshabilitar la autorización parental hasta seleccionar fecha
        parentalAuthCheckBox.setEnabled(false);

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
                                parentalAuthCheckBox.setEnabled(false);
                                parentalAuthCheckBox.setChecked(false);
                            } else {
                                birthDateLayout.setError(null);
                                if (age < 18) {
                                    parentalAuthCheckBox.setEnabled(true);
                                } else {
                                    parentalAuthCheckBox.setChecked(false);
                                    parentalAuthCheckBox.setEnabled(false);
                                }
                            }
                        }
                    } catch (ParseException e) {
                        birthDateLayout.setError("Formato inválido (dd/MM/yyyy)");
                        parentalAuthCheckBox.setEnabled(false);
                    }
                } else if (!dateText.isEmpty()) {
                    birthDateLayout.setError("Formato: dd/MM/yyyy");
                    parentalAuthCheckBox.setEnabled(false);
                } else {
                    birthDateLayout.setError(null);
                    parentalAuthCheckBox.setEnabled(false);
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

                    // Habilitar/deshabilitar autorización parental según edad
                    int age = calculateAge(calendar.getTime());
                    if (age < 18) {
                        parentalAuthCheckBox.setEnabled(true);
                    } else {
                        parentalAuthCheckBox.setChecked(false);
                        parentalAuthCheckBox.setEnabled(false);
                    }
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
            String dni = dniEditText.getText().toString().trim().toUpperCase();
            String email = emailEditText.getText().toString().trim();
            String phone = phoneEditText.getText().toString().trim();
            String birthDate = birthDateEditText.getText().toString().trim();
            boolean requiresAuth = parentalAuthCheckBox.isChecked();

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

            // El apellido puede estar vacío, solo lo concatenamos si existe
            String lastName = "";
            if (!firstLastName.isEmpty()) {
                lastName = firstLastName;
                if (!secondLastName.isEmpty()) {
                    lastName += " " + secondLastName;
                }
            }

            Attendee attendee = new Attendee(name, lastName, dni, email, phone, birthDate);
            attendee.setRequiresParentalAuthorization(requiresAuth);
            attendee.setEventId(event.getId());

            // Parsear y guardar fecha de nacimiento como Timestamp si está informada
            if (!birthDate.isEmpty()) {
                try {
                    Date parsed = dateFormat.parse(birthDate);
                    if (parsed != null) {
                        // Validación extra: no menores de 16 años
                        int age = calculateAge(parsed);
                        if (age < 16) {
                            ToastUtils.showCustomToast(this, "El asistente debe tener al menos 16 años", ToastUtils.ToastType.INFO);
                            return;
                        }
                        attendee.setBirthDate(new Timestamp(parsed));
                    }
                } catch (ParseException e) {
                    ToastUtils.showCustomToast(this, "Formato de fecha inválido (usa dd/MM/yyyy)", ToastUtils.ToastType.INFO);
                    return;
                }
            }

            // Guardar en Firestore
            db.collection("attendees")
                .add(attendee)
                .addOnSuccessListener(documentReference -> {
                    attendee.setId(documentReference.getId());
                    sharedViewModel.notifyEventsUpdated();
                    loadAttendees();
                    dialog.dismiss();
                    ToastUtils.showCustomToast(this, "Asistente agregado con éxito", ToastUtils.ToastType.SUCCESS);
                })
                .addOnFailureListener(e -> {
                    ToastUtils.showCustomToast(this, "Error al agregar asistente: " + e.getMessage(), ToastUtils.ToastType.ERROR);
                });
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
        ImageView qrCodeImageView = dialogView.findViewById(R.id.detailQRCodeImageView);
        
        // Referencia a los botones personalizados
        MaterialButton cancelButton = dialogView.findViewById(R.id.dialog_cancel_button);
        MaterialButton deleteButton = dialogView.findViewById(R.id.dialog_delete_button);

        // Mostrar nombre completo
        String fullName = attendee.getName() + " " + (attendee.getLastName() != null ? attendee.getLastName() : "");
        nameTextView.setText(fullName.trim());
        
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
        if (attendee.getBirthDate() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            birthDateTextView.setText(dateFormat.format(attendee.getBirthDate().toDate()));
        } else {
            birthDateTextView.setText("No especificada");
        }

        // Mostrar estado de autorización parental
        String authStatus = attendee.isRequiresParentalAuthorization() ? 
            "Requiere autorización parental" : "No requiere autorización parental";
        parentalAuthTextView.setText(authStatus);
        
        // Generar y mostrar el código QR
        int qrSize = 800; // Tamaño del código QR en píxeles
        qrCodeImageView.setImageBitmap(QRCodeGenerator.generateQRCode(attendee, qrSize));

        // Configurar diálogo sin botones estándar
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Configurar acciones para los botones personalizados
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        deleteButton.setOnClickListener(v -> {
            // Mostrar diálogo de confirmación
            View confirmDialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
            TextView confirmMessageTextView = confirmDialogView.findViewById(R.id.confirm_message);
            MaterialButton confirmCancelButton = confirmDialogView.findViewById(R.id.confirm_cancel_button);
            MaterialButton confirmDeleteButton = confirmDialogView.findViewById(R.id.confirm_delete_button);
            
            confirmMessageTextView.setText("¿Estás seguro de que deseas eliminar a este asistente?");
            
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
        
        dialog.show();
    }

    private void deleteAttendee(Attendee attendee, AlertDialog detailsDialog) {
        db.collection("attendees")
            .document(attendee.getId())
            .delete()
            .addOnSuccessListener(aVoid -> {
                // Actualizar la lista local primero
                attendees.remove(attendee);
                attendeeAdapter.setAttendees(attendees);
                updateAttendeesCount();
                
                // Cerrar los diálogos
                if (detailsDialog != null) {
                    detailsDialog.dismiss();
                }
                if (confirmDialog != null) {
                    confirmDialog.dismiss();
                }
                
                // Notificar al ViewModel para actualizar Home
                Log.d("EventDetailsActivity", "Notificando al ViewModel que los eventos se actualizaron");
                sharedViewModel.notifyEventsUpdated();
                
                // Mostrar mensaje de éxito
                ToastUtils.showCustomToast(this, "Asistente eliminado correctamente", ToastUtils.ToastType.SUCCESS);
                
                // Actualizar la visibilidad del mensaje sin asistentes
                if (attendees.isEmpty()) {
                    emptyAttendeesTextView.setVisibility(View.VISIBLE);
                    attendeesRecyclerView.setVisibility(View.GONE);
                }
            })
            .addOnFailureListener(e -> {
                ToastUtils.showCustomToast(this, "Error al eliminar asistente: " + e.getMessage(), ToastUtils.ToastType.ERROR);
            });
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
            titleTextView.setText("Eliminar evento");
        }
        
        confirmMessageTextView.setText("¿Estás seguro de que deseas eliminar este evento? Esta acción no se puede deshacer.");
        
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        confirmBuilder.setView(confirmDialogView);
        
        AlertDialog confirmDialog = confirmBuilder.create();
        
        confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
        
        confirmDeleteButton.setOnClickListener(cv -> {
            deleteEvent();
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }

    private void deleteEvent() {
        // Primero eliminar todos los asistentes del evento
        db.collection("attendees")
            .whereEqualTo("eventId", event.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    document.getReference().delete();
                }
                
                // Luego eliminar el evento
                db.collection("events").document(event.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        sharedViewModel.notifyEventsUpdated();
                        ToastUtils.showCustomToast(this, "Evento eliminado correctamente", ToastUtils.ToastType.SUCCESS);
                        // Volver a la pantalla principal
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        ToastUtils.showCustomToast(this, "Error al eliminar el evento: " + e.getMessage(), ToastUtils.ToastType.ERROR);
                    });
            })
            .addOnFailureListener(e -> {
                ToastUtils.showCustomToast(this, "Error al eliminar asistentes: " + e.getMessage(), ToastUtils.ToastType.ERROR);
            });
    }

    private void showEditEventDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_event, null);
        
        TextInputEditText titleInput = dialogView.findViewById(R.id.titleInput);
        TextInputEditText locationInput = dialogView.findViewById(R.id.locationInput);
        TextInputEditText dateInput = dialogView.findViewById(R.id.dateInput);
        TextInputEditText maxParticipantsInput = dialogView.findViewById(R.id.maxParticipantsInput);
        CheckBox eventoPrivadoCheckBox = dialogView.findViewById(R.id.eventoPrivadoCheckBox);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        
        // Rellenar los campos con los datos actuales del evento
        titleInput.setText(event.getTitle());
        locationInput.setText(event.getLocation());
        dateInput.setText(dateFormat.format(event.getDate()));
        maxParticipantsInput.setText(String.valueOf(event.getMaxParticipants()));
        eventoPrivadoCheckBox.setChecked(event.isPrivate());
        
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
                ToastUtils.showCustomToast(this, "Todos los campos son obligatorios", ToastUtils.ToastType.INFO);
                return;
            }
            
            try {
                Date newDate = dateFormat.parse(dateStr);
                int maxParticipants = Integer.parseInt(maxParticipantsStr);
                
                if (maxParticipants < attendees.size()) {
                    ToastUtils.showCustomToast(this, 
                        "El número de plazas no puede ser menor que el número actual de asistentes (" + 
                        attendees.size() + ")", 
                        ToastUtils.ToastType.WARNING);
                    return;
                }
                
                // Actualizar el evento en Firestore
                Map<String, Object> updates = new HashMap<>();
                updates.put("title", title);
                updates.put("location", location);
                updates.put("date", newDate);
                updates.put("maxParticipants", maxParticipants);
                updates.put("privateEvent", eventoPrivadoCheckBox.isChecked());
                
                db.collection("events").document(event.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Actualizar el objeto evento local
                        event.setTitle(title);
                        event.setLocation(location);
                        event.setDate(newDate);
                        event.setMaxParticipants(maxParticipants);
                        event.setPrivate(eventoPrivadoCheckBox.isChecked());
                        
                        // Actualizar la UI
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(title);
                        }
                        displayEventDetails();
                        
                        sharedViewModel.notifyEventsUpdated();
                        ToastUtils.showCustomToast(this, "Evento actualizado correctamente", ToastUtils.ToastType.SUCCESS);
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        ToastUtils.showCustomToast(this, "Error al actualizar el evento: " + e.getMessage(), ToastUtils.ToastType.ERROR);
                    });
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
        
        // Forzar que se muestren los iconos
        try {
            Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceShowIcon.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Aplicar el tema del popup
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
        
        confirmTitleTextView.setText("Vaciar lista de asistentes");
        confirmMessageTextView.setText("¿Estás seguro de que deseas eliminar todos los asistentes? Esta acción no se puede deshacer.");
        confirmDeleteButton.setText("Vaciar lista");
        
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        confirmBuilder.setView(confirmDialogView);
        
        AlertDialog confirmDialog = confirmBuilder.create();
        
        confirmCancelButton.setOnClickListener(cv -> confirmDialog.dismiss());
        
        confirmDeleteButton.setOnClickListener(cv -> {
            clearAttendeeList();
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }
    
    private void clearAttendeeList() {
        // Eliminar todos los asistentes del evento actual
        db.collection("attendees")
            .whereEqualTo("eventId", event.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Utilizamos un contador para saber cuándo se completan todas las eliminaciones
                int totalToDelete = queryDocumentSnapshots.size();
                
                if (totalToDelete == 0) {
                    ToastUtils.showCustomToast(this, "No hay asistentes para eliminar", ToastUtils.ToastType.INFO);
                    return;
                }
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    document.getReference().delete();
                }
                
                // Refrescar la lista de asistentes
                attendees.clear();
                attendeeAdapter.setAttendees(attendees);
                updateAttendeesCount();
                
                // Actualizar la visibilidad del mensaje sin asistentes
                emptyAttendeesTextView.setVisibility(View.VISIBLE);
                attendeesRecyclerView.setVisibility(View.GONE);
                
                sharedViewModel.notifyEventsUpdated();
                ToastUtils.showCustomToast(this, "Lista de asistentes vaciada", ToastUtils.ToastType.SUCCESS);
            })
            .addOnFailureListener(e -> {
                ToastUtils.showCustomToast(this, "Error al vaciar la lista: " + e.getMessage(), ToastUtils.ToastType.ERROR);
            });
    }

    private void startQRScanner() {
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
} 