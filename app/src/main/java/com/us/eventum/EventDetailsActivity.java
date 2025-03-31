package com.us.eventum;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.adapters.AttendeeAdapter;
import com.us.eventum.models.Attendee;
import com.us.eventum.models.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventDetailsActivity extends AppCompatActivity {
    private Event event;
    private FirebaseFirestore db;
    private AttendeeAdapter attendeeAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Attendee> attendees = new ArrayList<>();
    private TextView titleTextView, dateTextView, locationTextView, descriptionTextView;
    private Button addAttendeeButton;
    private RecyclerView attendeesRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        // Inicializar Firestore
        db = FirebaseFirestore.getInstance();

        // Obtener el evento de los extras
        event = (Event) getIntent().getSerializableExtra("event");
        if (event == null) {
            Toast.makeText(this, "Error al cargar el evento", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Configurar toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // Establecer el título del evento
        setTitle(event.getTitle());

        // Inicializar vistas
        initializeViews();
        setupRecyclerView();
        displayEventDetails();
        
        // Cargar asistentes y actualizar la interfaz
        loadAttendees();
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.eventTitleTextView);
        dateTextView = findViewById(R.id.eventDateTextView);
        locationTextView = findViewById(R.id.eventLocationTextView);
        descriptionTextView = findViewById(R.id.eventDescriptionTextView);
        addAttendeeButton = findViewById(R.id.addAttendeeButton);
        attendeesRecyclerView = findViewById(R.id.attendeesRecyclerView);

        addAttendeeButton.setOnClickListener(v -> showAddAttendeeDialog());
    }

    private void setupRecyclerView() {
        attendeesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendeeAdapter = new AttendeeAdapter(attendee -> showAttendeeDetails(attendee));
        attendeesRecyclerView.setAdapter(attendeeAdapter);
    }

    private void displayEventDetails() {
        // El título ya no es necesario aquí ya que se muestra en la barra superior
        titleTextView.setVisibility(View.GONE);
        
        // Crear formato de fecha más completo
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        String formattedDate = fullDateFormat.format(event.getDate());
        formattedDate = formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1);
        
        // Formato más profesional para fecha, capacidad y ubicación
        String dateText = String.format("📅  %s", formattedDate);
        String capacityText = String.format("👥  %d de %d plazas ocupadas", 
            attendees.size(),
            event.getMaxParticipants());
        String locationText = String.format("📍  %s", event.getLocation());
        
        dateTextView.setText(dateText);
        locationTextView.setText(locationText);
        
        // Actualizar el TextView para la capacidad
        TextView capacityTextView = findViewById(R.id.eventCapacityTextView);
        if (capacityTextView != null) {
            capacityTextView.setText(capacityText);
            capacityTextView.setVisibility(View.VISIBLE);
        }
        
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            descriptionTextView.setText(event.getDescription());
            descriptionTextView.setVisibility(View.VISIBLE);
        } else {
            descriptionTextView.setVisibility(View.GONE);
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
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Error al cargar asistentes: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            });
    }

    private void updateAttendeesCount() {
        // Solo actualizar la información de capacidad en displayEventDetails
        displayEventDetails();
    }

    private void showAddAttendeeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_attendee, null);

        EditText nameEditText = dialogView.findViewById(R.id.nameEditText);
        EditText firstLastNameEditText = dialogView.findViewById(R.id.firstLastNameEditText);
        EditText secondLastNameEditText = dialogView.findViewById(R.id.secondLastNameEditText);
        EditText emailEditText = dialogView.findViewById(R.id.emailEditText);
        EditText phoneEditText = dialogView.findViewById(R.id.phoneEditText);
        EditText birthDateEditText = dialogView.findViewById(R.id.birthDateEditText);
        CheckBox parentalAuthCheckBox = dialogView.findViewById(R.id.parentalAuthCheckBox);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button addButton = dialogView.findViewById(R.id.addAttendeeButton);

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
            String email = emailEditText.getText().toString().trim();
            String phone = phoneEditText.getText().toString().trim();
            String birthDate = birthDateEditText.getText().toString().trim();
            boolean requiresAuth = parentalAuthCheckBox.isChecked();

            // Validar campos obligatorios
            if (name.isEmpty() || firstLastName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Por favor, completa los campos obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            // Construir apellido completo
            String lastName = firstLastName + (secondLastName.isEmpty() ? "" : " " + secondLastName);

            Attendee attendee = new Attendee(name, lastName, email, phone, birthDate);
            attendee.setRequiresParentalAuthorization(requiresAuth);
            attendee.setEventId(event.getId());

            // Guardar en Firestore
            db.collection("attendees")
                .add(attendee)
                .addOnSuccessListener(documentReference -> {
                    attendee.setId(documentReference.getId());
                    loadAttendees(); // Recargar la lista de asistentes
                    dialog.dismiss();
                    Toast.makeText(this, "Asistente agregado con éxito", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al agregar asistente: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        });

        dialog.show();
    }

    private void showAttendeeDetails(Attendee attendee) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attendee_details, null);

        TextView nameTextView = dialogView.findViewById(R.id.detailNameTextView);
        TextView emailTextView = dialogView.findViewById(R.id.detailEmailTextView);
        TextView phoneTextView = dialogView.findViewById(R.id.detailPhoneTextView);
        TextView birthDateTextView = dialogView.findViewById(R.id.detailBirthDateTextView);
        TextView parentalAuthTextView = dialogView.findViewById(R.id.detailParentalAuthTextView);

        // Mostrar nombre completo
        String fullName = attendee.getName() + " " + (attendee.getLastName() != null ? attendee.getLastName() : "");
        nameTextView.setText(fullName.trim());
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

        builder.setView(dialogView)
               .setTitle("Detalles del Asistente")
               .setPositiveButton("Cerrar", null)
               .setNegativeButton("Eliminar", (dialog, which) -> {
                    // Mostrar diálogo de confirmación
                    new AlertDialog.Builder(this)
                        .setTitle("Eliminar asistente")
                        .setMessage("¿Estás seguro de que deseas eliminar a este asistente?")
                        .setPositiveButton("Eliminar", (confirmDialog, confirmWhich) -> {
                            // Eliminar asistente
                            db.collection("attendees")
                                .document(attendee.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Asistente eliminado correctamente", Toast.LENGTH_SHORT).show();
                                    loadAttendees(); // Recargar la lista de asistentes
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Error al eliminar asistente: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                                });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
               });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Configurar colores de los botones
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorError));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorPrimary));
        });
        dialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 