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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private FirebaseFirestore db;
    private AttendeeAdapter attendeeAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Attendee> attendees = new ArrayList<>();
    private TextView dateTextView, locationTextView, descriptionTextView;
    private TextView emptyAttendeesTextView;
    private FloatingActionButton addAttendeeButton;
    private RecyclerView attendeesRecyclerView;
    private ImageButton eventMenuButton;
    private static final int QR_SCANNER_REQUEST_CODE = 100;
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

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Configurar botón de retroceso
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        // Obtener el evento y configurar el título
        event = (Event) getIntent().getSerializableExtra("event");
        TextView toolbarTitleTextView = findViewById(R.id.toolbarTitleTextView);
        
        if (event != null) {
            toolbarTitleTextView.setText(event.getTitle());
            // Inicializar el resto de la UI
            initializeViews();
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
        locationTextView = findViewById(R.id.eventLocationTextView);
        descriptionTextView = findViewById(R.id.eventDescriptionTextView);
        emptyAttendeesTextView = findViewById(R.id.emptyAttendeesTextView);
        addAttendeeButton = findViewById(R.id.addAttendeeButton);
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
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                ToastUtils.showCustomToast(this, "Por favor, completa los campos obligatorios", ToastUtils.ToastType.INFO);
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

            Attendee attendee = new Attendee(name, lastName, email, phone, birthDate);
            attendee.setRequiresParentalAuthorization(requiresAuth);
            attendee.setEventId(event.getId());

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

    private void showAttendeeDetails(Attendee attendee) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attendee_details, null);

        TextView nameTextView = dialogView.findViewById(R.id.detailNameTextView);
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
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveButton);
        
        // Rellenar los campos con los datos actuales del evento
        titleInput.setText(event.getTitle());
        locationInput.setText(event.getLocation());
        dateInput.setText(dateFormat.format(event.getDate()));
        maxParticipantsInput.setText(String.valueOf(event.getMaxParticipants()));
        
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
                    ToastUtils.showCustomToast(this, "El número de plazas no puede ser menor que el número actual de asistentes", ToastUtils.ToastType.INFO);
                    return;
                }
                
                // Actualizar el evento en Firestore
                Map<String, Object> updates = new HashMap<>();
                updates.put("title", title);
                updates.put("location", location);
                updates.put("date", newDate);
                updates.put("maxParticipants", maxParticipants);
                
                db.collection("events").document(event.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Actualizar el objeto evento local
                        event.setTitle(title);
                        event.setLocation(location);
                        event.setDate(newDate);
                        event.setMaxParticipants(maxParticipants);
                        
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
        popup.setForceShowIcon(true);
        
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
        startActivityForResult(intent, QR_SCANNER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCANNER_REQUEST_CODE && resultCode == RESULT_OK) {
            // Recargar la lista de asistentes
            loadAttendees();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
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
        onBackPressed();
        return true;
    }
} 