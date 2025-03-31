package com.us.eventum;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.models.Event;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CreateEventActivity extends AppCompatActivity {
    private TextInputEditText nombreEventoEditText, fechaEventoEditText, maxParticipantesEditText, lugarEventoEditText;
    private TextInputLayout nombreEventoLayout, fechaEventoLayout, maxParticipantesLayout, lugarEventoLayout;
    private MaterialButton crearEventoButton;
    private CircularProgressIndicator progressBar;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private Toolbar toolbar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        initializeViews();
        setupDatePicker();
        setupClickListeners();
        setupToolbar();
    }

    private void initializeViews() {
        // Inicializar campos de texto
        nombreEventoEditText = findViewById(R.id.nombreEventoEditText);
        fechaEventoEditText = findViewById(R.id.fechaEventoEditText);
        maxParticipantesEditText = findViewById(R.id.maxParticipantesEditText);
        lugarEventoEditText = findViewById(R.id.lugarEventoEditText);

        // Inicializar layouts
        nombreEventoLayout = findViewById(R.id.nombreEventoLayout);
        fechaEventoLayout = findViewById(R.id.fechaEventoLayout);
        maxParticipantesLayout = findViewById(R.id.maxParticipantesLayout);
        lugarEventoLayout = findViewById(R.id.lugarEventoLayout);

        // Inicializar botones y barra de progreso
        crearEventoButton = findViewById(R.id.crearEventoButton);
        progressBar = findViewById(R.id.progressBar);

        // Inicializar calendario y formato de fecha
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        dateFormat.setCalendar(calendar);
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

        return isValid;
    }

    private void createEvent() {
        progressBar.setVisibility(View.VISIBLE);
        crearEventoButton.setEnabled(false);

        String userId = mAuth.getCurrentUser().getUid();

        Event event = new Event(
            nombreEventoEditText.getText().toString().trim(),
            "", // Descripción vacía por ahora
            calendar.getTime(),
            lugarEventoEditText.getText().toString().trim(),
            userId,
            Integer.parseInt(maxParticipantesEditText.getText().toString())
        );

        db.collection("events")
            .add(event)
            .addOnSuccessListener(documentReference -> {
                event.setId(documentReference.getId());
                Toast.makeText(this, "Evento creado exitosamente", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Error al crear el evento: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                crearEventoButton.setEnabled(true);
            });
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
} 