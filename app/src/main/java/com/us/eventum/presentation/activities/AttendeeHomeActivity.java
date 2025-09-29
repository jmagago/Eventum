package com.us.eventum.presentation.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.us.eventum.R;
import com.us.eventum.adapters.EventAdapter;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.ToastUtils;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_available_events);

        // Header similar a Home: rellenar nombre/email/rol

        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);
        recyclerView = findViewById(R.id.eventsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter();
        adapter.setOnItemClickListener(new EventAdapter.OnEventClickListener() {
            @Override
            public void onEventClick(Event event) {
                showJoinDialog(event);
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
                        }
                    });
                } else {
                    showUnsubscribeDialog(event);
                }
            }
        });
        recyclerView.setAdapter(adapter);

        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        eventViewModel.initializeRepository(this);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        attendeeViewModel.initializeRepository(this);

        // Pintar datos de usuario en header
        com.us.eventum.presentation.viewmodels.UserViewModel userViewModel = new ViewModelProvider(this).get(com.us.eventum.presentation.viewmodels.UserViewModel.class);
        userViewModel.initializeRepository(this);
        TextView nameTv = findViewById(R.id.userNameTextView);
        TextView emailTv = findViewById(R.id.userEmailTextView);
        TextView roleTv = findViewById(R.id.userRoleTextView);
        userViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                if (user.getNombre() != null && !user.getNombre().isEmpty()) {
                    nameTv.setText(user.getNombre());
                } else if (user.getEmail() != null && user.getEmail().contains("@")) {
                    nameTv.setText(user.getEmail().substring(0, user.getEmail().indexOf('@')));
                }
                emailTv.setText(user.getEmail());
                roleTv.setText(R.string.role_attendee);
                roleTv.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                roleTv.setBackgroundResource(R.drawable.bg_role_badge_assistant);
            }
        });
        userViewModel.loadCurrentUser();

        observeViewModel();
        loadAvailableEvents();
    }

    private void observeViewModel() {
        eventViewModel.getIsLoading().observe(this, loading -> {
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
                    attendeeViewModel.addAttendee(
                            event.getId(),
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
}

