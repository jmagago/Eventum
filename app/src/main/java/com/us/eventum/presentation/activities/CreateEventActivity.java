package com.us.eventum.presentation.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.models.Event;
import com.us.eventum.R;
import com.us.eventum.presentation.viewmodels.SharedViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.us.eventum.utils.ToastUtils;

public class CreateEventActivity extends AppCompatActivity {
    private TextInputEditText nombreEventoEditText, fechaEventoEditText, horaEventoEditText, maxParticipantesEditText, 
                            lugarEventoEditText, descripcionEventoEditText;
    private TextInputLayout nombreEventoLayout, fechaEventoLayout, horaEventoLayout, maxParticipantesLayout, 
                         lugarEventoLayout, descripcionEventoLayout, tipoEventoLayout;
    private AutoCompleteTextView tipoEventoAutoComplete;
    private MaterialCheckBox eventoPrivadoCheckBox;
    private MaterialButton crearEventoButton;
    private CircularProgressIndicator progressBar;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private Toolbar toolbar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String selectedEventType;
    private SharedViewModel sharedViewModel;
    private List<String> eventTypes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        sharedViewModel = SharedViewModel.getInstance();
        initializeViews();
        setupDatePicker();
        setupEventTypeDropdown();
        setupClickListeners();
        setupToolbar();
    }

    private void initializeViews() {
        // Inicializar campos de texto
        nombreEventoEditText = findViewById(R.id.nombreEventoEditText);
        fechaEventoEditText = findViewById(R.id.fechaEventoEditText);
        horaEventoEditText = findViewById(R.id.horaEventoEditText);
        maxParticipantesEditText = findViewById(R.id.maxParticipantesEditText);
        lugarEventoEditText = findViewById(R.id.lugarEventoEditText);
        descripcionEventoEditText = findViewById(R.id.descripcionEventoEditText);
        tipoEventoAutoComplete = findViewById(R.id.tipoEventoAutoComplete);
        eventoPrivadoCheckBox = findViewById(R.id.eventoPrivadoCheckBox);

        // Inicializar layouts
        nombreEventoLayout = findViewById(R.id.nombreEventoLayout);
        fechaEventoLayout = findViewById(R.id.fechaEventoLayout);
        horaEventoLayout = findViewById(R.id.horaEventoLayout);
        maxParticipantesLayout = findViewById(R.id.maxParticipantesLayout);
        lugarEventoLayout = findViewById(R.id.lugarEventoLayout);
        descripcionEventoLayout = findViewById(R.id.descripcionEventoLayout);
        tipoEventoLayout = findViewById(R.id.tipoEventoLayout);

        // Inicializar botones y barra de progreso
        crearEventoButton = findViewById(R.id.crearEventoButton);
        progressBar = findViewById(R.id.progressBar);

        // Inicializar calendario y formato de fecha
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        dateFormat.setCalendar(calendar);
    }

    private void setupEventTypeDropdown() {
        eventTypes = Arrays.asList(
            "Boda", "Comunión", "Reunión", "Cumpleaños", "Festival", 
            "Concierto", "Graduación", "Fiesta", "Despedida", 
            "Aniversario", "Conferencia", "Seminario", "Taller", 
            "Exposición", "Feria", "Congreso", "Ceremonia", "Otro"
        );
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            R.layout.dropdown_item,
            eventTypes
        );
        
        tipoEventoAutoComplete.setAdapter(adapter);
        tipoEventoAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            selectedEventType = parent.getItemAtPosition(position).toString();
        });
    }

    private void setupDatePicker() {
        fechaEventoEditText.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    fechaEventoEditText.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );

            // Configurar el DatePicker para mostrar la vista de calendario
            datePickerDialog.getDatePicker().setCalendarViewShown(true);
            datePickerDialog.getDatePicker().setSpinnersShown(false);
            
            // Establecer la fecha mínima como hoy
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
            
            // Cambiar los textos de los botones
            datePickerDialog.setButton(DatePickerDialog.BUTTON_POSITIVE, "Aceptar", datePickerDialog);
            datePickerDialog.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Cancelar", datePickerDialog);
            
            datePickerDialog.show();
        });

        // Selector de hora
        horaEventoEditText.setOnClickListener(v -> {
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
                    calendar.set(Calendar.MINUTE, selectedMinute);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    horaEventoEditText.setText(timeFormat.format(calendar.getTime()));
                },
                hour,
                minute,
                true
            );
            timePickerDialog.setTitle("Selecciona la hora");
            timePickerDialog.show();
        });
    }

    private void setupClickListeners() {
        crearEventoButton.setOnClickListener(v -> {
            if (validateFields()) {
                createEvent();
            }
        });
    }

    private boolean validateFields() {
        boolean isValid = true;

        // Validar nombre del evento
        if (nombreEventoEditText.getText().toString().trim().isEmpty()) {
            nombreEventoLayout.setError("El nombre del evento es obligatorio");
            isValid = false;
        } else {
            nombreEventoLayout.setError(null);
        }

        // Validar fecha
        if (fechaEventoEditText.getText().toString().trim().isEmpty()) {
            fechaEventoLayout.setError("La fecha es obligatoria");
            isValid = false;
        } else {
            fechaEventoLayout.setError(null);
        }

        // Validar hora
        if (horaEventoEditText.getText().toString().trim().isEmpty()) {
            horaEventoLayout.setError("La hora es obligatoria");
            isValid = false;
        } else {
            horaEventoLayout.setError(null);
        }

        // Validar tipo de evento
        if (selectedEventType == null || selectedEventType.isEmpty()) {
            tipoEventoLayout.setError("El tipo de evento es obligatorio");
            isValid = false;
        } else {
            tipoEventoLayout.setError(null);
        }

        // Validar número máximo de participantes
        if (maxParticipantesEditText.getText().toString().trim().isEmpty()) {
            maxParticipantesLayout.setError("El número máximo de participantes es obligatorio");
            isValid = false;
        } else {
            try {
                int maxParticipantes = Integer.parseInt(maxParticipantesEditText.getText().toString());
                if (maxParticipantes <= 0) {
                    maxParticipantesLayout.setError("El número debe ser mayor que 0");
                    isValid = false;
                } else {
                    maxParticipantesLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                maxParticipantesLayout.setError("Debe ser un número válido");
                isValid = false;
            }
        }

        // Validar lugar
        if (lugarEventoEditText.getText().toString().trim().isEmpty()) {
            lugarEventoLayout.setError("El lugar es obligatorio");
            isValid = false;
        } else {
            lugarEventoLayout.setError(null);
        }

        // La descripción es opcional, no necesita validación

        return isValid;
    }

    private void createEvent() {
        progressBar.setVisibility(View.VISIBLE);
        crearEventoButton.setEnabled(false);

        String userId = mAuth.getCurrentUser().getUid();
        String description = descripcionEventoEditText.getText().toString().trim();

        Event event = new Event(
            nombreEventoEditText.getText().toString().trim(),
            description,
            calendar.getTime(),
            lugarEventoEditText.getText().toString().trim(),
            userId,
            Integer.parseInt(maxParticipantesEditText.getText().toString()),
            selectedEventType
        );
        
        // Configurar si el evento es privado
        boolean privateEvent = eventoPrivadoCheckBox.isChecked();
        event.setPrivate(privateEvent);

        db.collection("events")
            .add(event)
            .addOnSuccessListener(documentReference -> {
                event.setId(documentReference.getId());
                ToastUtils.showCustomToast(this, "Evento creado exitosamente", ToastUtils.ToastType.SUCCESS);
                sharedViewModel.notifyEventsUpdated();
                finish();
            })
            .addOnFailureListener(e -> {
                ToastUtils.showCustomToast(this, "Error al crear el evento: " + e.getMessage(), ToastUtils.ToastType.ERROR);
                progressBar.setVisibility(View.GONE);
                crearEventoButton.setEnabled(true);
            });
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Crear un nuevo Evento");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
} 