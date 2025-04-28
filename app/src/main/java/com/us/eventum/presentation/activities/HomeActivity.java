package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.adapters.EventsPagerAdapter;
import com.us.eventum.models.Event;
import com.us.eventum.R;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import androidx.viewpager2.widget.ViewPager2;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.us.eventum.utils.PermissionUtils;
import android.content.pm.PackageManager;
import android.view.MenuItem;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.view.View;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import com.google.android.material.button.MaterialButton;
import com.us.eventum.utils.ToastUtils;
import android.content.SharedPreferences;

public class HomeActivity extends AppCompatActivity implements EventsPagerAdapter.EventContextMenuListener {
    private static final String TAG = "HomeActivity";
    private FloatingActionButton settingsButton, createEventButton;
    private FirebaseFirestore db;
    private String userId;
    private FirebaseAuth mAuth;
    private List<Event> futureEvents = new ArrayList<>();
    private List<Event> pastEvents = new ArrayList<>();
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private EventsPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private CircleImageView profileImageView;
    private FirebaseStorage storage;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Inicializar vistas
        initializeViews();
        setupClickListeners();
        setupViewPager();
        loadEvents();
        loadProfileImage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Solo recargar eventos si hubo cambios
        SharedPreferences prefs = getSharedPreferences("eventum_prefs", MODE_PRIVATE);
        boolean eventsUpdated = prefs.getBoolean("events_updated", false);
        if (eventsUpdated) {
            loadEvents();
            prefs.edit().putBoolean("events_updated", false).apply();
        }
        // Solo recargar la foto si fue actualizada
        boolean profileImageUpdated = prefs.getBoolean("profile_image_updated", false);
        if (profileImageUpdated) {
            loadProfileImage();
            prefs.edit().putBoolean("profile_image_updated", false).apply();
        }
        // Si no, Glide y la lista en memoria mostrarán lo cacheado
    }

    private void initializeViews() {
        createEventButton = findViewById(R.id.createEventFab);
        settingsButton = findViewById(R.id.settingsButton);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        profileImageView = findViewById(R.id.profileImageView);
        
        // Inicializar vistas de información de usuario
        TextView userNameTextView = findViewById(R.id.userNameTextView);
        TextView userEmailTextView = findViewById(R.id.userEmailTextView);
        
        // Obtener y mostrar información del usuario
        if (mAuth.getCurrentUser() != null) {
            String email = mAuth.getCurrentUser().getEmail();
            
            // Establecer el email inmediatamente
            userEmailTextView.setText(email);
            
            // Obtener el nombre del usuario desde Firestore
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                    String displayName = documentSnapshot.getString("nombre");
                    
                    // Si no hay nombre en Firestore, usar la parte antes del @ del email
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = email.substring(0, email.indexOf('@'));
                    }
                    
                    userNameTextView.setText(displayName);
                })
                .addOnFailureListener(e -> {
                    // En caso de error, usar la parte antes del @ del email
                    String defaultName = email.substring(0, email.indexOf('@'));
                    userNameTextView.setText(defaultName);
                    Log.e(TAG, "Error al obtener el nombre del usuario", e);
                });
        }
    }

    private void setupViewPager() {
        pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
        viewPager.setAdapter(pagerAdapter);

        // Configurar el TabLayout
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("PRÓXIMOS (" + futureEvents.size() + ")");
                    } else {
                        tab.setText("ARCHIVADOS (" + pastEvents.size() + ")");
                    }
                }
        ).attach();

        // Configurar el comportamiento del ViewPager2
        viewPager.setUserInputEnabled(true);
    }

    private void setupClickListeners() {
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        createEventButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEventActivity.class);
            startActivity(intent);
        });
    }

    private void loadEvents() {
        if (mAuth.getCurrentUser() == null) return;

        // Obtener la fecha actual
        Date now = new Date();
        
        // Obtener todos los eventos del usuario
        db.collection("events")
            .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Limpiar las listas actuales
                futureEvents.clear();
                pastEvents.clear();
                
                // Clasificar los eventos según su fecha
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Event event = document.toObject(Event.class);
                    event.setId(document.getId());
                    
                    if (event.getDate().after(now)) {
                        futureEvents.add(event);
                    } else {
                        pastEvents.add(event);
                    }
                }
                
                // Ordenar eventos futuros por fecha ascendente
                futureEvents.sort((e1, e2) -> e1.getDate().compareTo(e2.getDate()));
                
                // Ordenar eventos pasados por fecha descendente
                pastEvents.sort((e1, e2) -> e2.getDate().compareTo(e1.getDate()));
                
                // Actualizar el adaptador
                pagerAdapter = new EventsPagerAdapter(this, futureEvents, pastEvents);
                viewPager.setAdapter(pagerAdapter);
                
                // Actualizar los textos de las pestañas
                TabLayout.Tab tab1 = tabLayout.getTabAt(0);
                TabLayout.Tab tab2 = tabLayout.getTabAt(1);
                if (tab1 != null) tab1.setText("PRÓXIMOS (" + futureEvents.size() + ")");
                if (tab2 != null) tab2.setText("ARCHIVADOS (" + pastEvents.size() + ")");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error al cargar eventos", e);
                ToastUtils.showCustomToast(this, "Error al cargar eventos: " + e.getMessage(), 
                    ToastUtils.ToastType.ERROR);
            });
    }

    private void loadProfileImage() {
        // Establecer imagen por defecto
        profileImageView.setImageResource(R.drawable.default_profile);

        // Intentar cargar la imagen del usuario si existe
        StorageReference profileRef = storage.getReference().child("profile_images/" + userId + ".jpg");
        profileRef.getDownloadUrl()
            .addOnSuccessListener(uri -> {
                // Cargar la imagen usando Glide
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(profileImageView);
                
                Log.d(TAG, "Imagen de perfil cargada exitosamente");
            })
            .addOnFailureListener(e -> {
                Log.d(TAG, "No se encontró imagen de perfil, usando imagen por defecto: " + e.getMessage());
            });
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
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        
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
                ToastUtils.showCustomToast(this, "Todos los campos son obligatorios", ToastUtils.ToastType.WARNING);
                return;
            }
            
            try {
                Date newDate = dateFormat.parse(dateStr);
                int maxParticipants = Integer.parseInt(maxParticipantsStr);
                
                // Actualizar el evento en Firestore
                Map<String, Object> updates = new HashMap<>();
                updates.put("title", title);
                updates.put("location", location);
                updates.put("date", newDate);
                updates.put("maxParticipants", maxParticipants);
                
                db.collection("events").document(event.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        ToastUtils.showCustomToast(this, "Evento actualizado correctamente", ToastUtils.ToastType.SUCCESS);
                        getSharedPreferences("eventum_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("events_updated", true)
                            .apply();
                        loadEvents();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        ToastUtils.showCustomToast(this, "Error al actualizar el evento: " + e.getMessage(), 
                            ToastUtils.ToastType.ERROR);
                    });
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
            deleteEvent(event);
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

    private void deleteEvent(Event event) {
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
                        showCustomToast("Evento eliminado correctamente", ToastType.SUCCESS);
                        getSharedPreferences("eventum_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("events_updated", true)
                            .apply();
                        loadEvents(); // Recargar la lista de eventos
                    })
                    .addOnFailureListener(e -> {
                        showCustomToast("Error al eliminar el evento: " + e.getMessage(), ToastType.ERROR);
                    });
            })
            .addOnFailureListener(e -> {
                showCustomToast("Error al eliminar asistentes: " + e.getMessage(), ToastType.ERROR);
            });
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
        db.collection("attendees")
            .whereEqualTo("eventId", event.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots.isEmpty()) {
                    showCustomToast("No hay asistentes para eliminar", ToastType.INFO);
                    return;
                }
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    document.getReference().delete();
                }
                
                showCustomToast("Lista de asistentes vaciada", ToastType.SUCCESS);
                getSharedPreferences("eventum_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("events_updated", true)
                    .apply();
                loadEvents(); // Recargar la lista de eventos
            })
            .addOnFailureListener(e -> {
                showCustomToast("Error al vaciar la lista: " + e.getMessage(), ToastType.ERROR);
            });
    }
} 