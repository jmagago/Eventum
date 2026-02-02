package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.card.MaterialCardView;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.OrganizerViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText usernameInput, emailInput, verifyEmailInput, passwordInput, confirmPasswordInput;
    private TextInputLayout usernameLayout, emailLayout, verifyEmailLayout, passwordLayout, confirmPasswordLayout;
    private MaterialButton registerButton;
    private MaterialCardView organizerCard;
    private MaterialCardView attendeeCard;
    private TextView loginLink;
    private OrganizerViewModel organizerViewModel;
    private AttendeeViewModel attendeeViewModel;
    private String selectedRole = "ORGANIZER"; // Por defecto organizador

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar ViewModels
        organizerViewModel = new ViewModelProvider(this).get(OrganizerViewModel.class);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        
        // Inicializar repositorios en ViewModels
        organizerViewModel.initializeRepository(this);
        attendeeViewModel.initializeRepository(this);

        // Inicializar vistas
        initializeViews();
        setupListeners();
        observeViewModel();
    }

    private void initializeViews() {
        // EditText
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        verifyEmailInput = findViewById(R.id.verifyEmailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        // TextInputLayout
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        verifyEmailLayout = findViewById(R.id.verifyEmailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        // Buttons
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
        organizerCard = findViewById(R.id.organizerCard);
        attendeeCard = findViewById(R.id.attendeeCard);
    }

    private void setupListeners() {

        // Validación en tiempo real del email
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                if (email.isEmpty()) {
                    emailLayout.setError("El email es obligatorio");
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.setError("Email no válido");
                } else {
                    emailLayout.setError(null);
                }
            }
        });

        // Validación en tiempo real del email de verificación
        verifyEmailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String verifyEmail = s.toString().trim();
                String email = emailInput.getText().toString().trim();
                if (verifyEmail.isEmpty()) {
                    verifyEmailLayout.setError("Debe verificar el email");
                } else if (!verifyEmail.equals(email)) {
                    verifyEmailLayout.setError("Los emails no coinciden");
                } else {
                    verifyEmailLayout.setError(null);
                }
            }
        });

        // Validación en tiempo real de la confirmación de contraseña
        confirmPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String confirmPassword = s.toString();
                String password = passwordInput.getText().toString();
                if (!password.equals(confirmPassword)) {
                    confirmPasswordLayout.setError("Las contraseñas no coinciden");
                } else {
                    confirmPasswordLayout.setError(null);
                }
            }
        });

        // Selección de rol (por defecto ORGANIZADOR)
        organizerCard.setOnClickListener(v -> {
            selectedRole = "ORGANIZER";
            usernameLayout.setHint("Nombre de la empresa");
            updateRoleSelection(true);
        });

        attendeeCard.setOnClickListener(v -> {
            selectedRole = "ATTENDEE";
            usernameLayout.setHint("Nombre de usuario");
            updateRoleSelection(false);
        });

        registerButton.setOnClickListener(v -> validateAndRegister(selectedRole));
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Inicializar selección por defecto (Organizador)
        updateRoleSelection(true);
    }

    private void updateRoleSelection(boolean isOrganizer) {
        if (isOrganizer) {
            // Seleccionar organizador - borde fucsia más grueso que los campos
            organizerCard.setStrokeColor(getResources().getColor(R.color.colorAccent, getTheme()));
            organizerCard.setStrokeWidth(4);
            
            // Deseleccionar asistente - borde normal igual que los campos
            attendeeCard.setStrokeColor(getResources().getColor(R.color.colorBorder, getTheme()));
            attendeeCard.setStrokeWidth(2);
        } else {
            // Seleccionar asistente - borde fucsia más grueso que los campos
            attendeeCard.setStrokeColor(getResources().getColor(R.color.colorAccent, getTheme()));
            attendeeCard.setStrokeWidth(4);
            
            // Deseleccionar organizador - borde normal igual que los campos
            organizerCard.setStrokeColor(getResources().getColor(R.color.colorBorder, getTheme()));
            organizerCard.setStrokeWidth(2);
        }
    }

    private void validateAndRegister(String role) {
        // Limpiar errores previos
        usernameLayout.setError(null);
        emailLayout.setError(null);
        verifyEmailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        // Obtener valores
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String verifyEmail = verifyEmailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        // Validar campos
        if (TextUtils.isEmpty(username)) {
            usernameLayout.setError("El usuario es obligatorio");
            return;
        }

        if (username.length() < 3) {
            usernameLayout.setError("El usuario debe tener al menos 3 caracteres");
            return;
        }


        if (TextUtils.isEmpty(email)) {
            emailLayout.setError("El email es obligatorio");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Email no válido");
            return;
        }

        if (TextUtils.isEmpty(verifyEmail)) {
            verifyEmailLayout.setError("Debe verificar el email");
            return;
        }

        if (!verifyEmail.equals(email)) {
            verifyEmailLayout.setError("Los emails no coinciden");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError("La contraseña es obligatoria");
            return;
        }

        if (password.length() < 6) {
            passwordLayout.setError("La contraseña debe tener al menos 6 caracteres");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Las contraseñas no coinciden");
            return;
        }

        // Registrar usuario según el rol seleccionado
        if ("ORGANIZER".equals(role)) {
            organizerViewModel.registerOrganizer(email, password, username, "", "");
        } else {
            attendeeViewModel.registerAttendee(email, password, username, "", "", "", "", "", "");
        }
    }

    private void observeViewModel() {
        // Observar registro exitoso de organizador
        organizerViewModel.getOrganizerRegistered().observe(this, registered -> {
            if (registered != null && registered) {
                ToastUtils.showCustomToast(RegisterActivity.this,
                        "Registro exitoso. Por favor, verifica tu email antes de iniciar sesión",
                        ToastUtils.ToastType.SUCCESS);
                
                // Redirigir al login
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Observar registro exitoso de asistente
        attendeeViewModel.getAttendeeRegistered().observe(this, registered -> {
            if (registered != null && registered) {
                ToastUtils.showCustomToast(RegisterActivity.this,
                        "Registro exitoso. Por favor, verifica tu email antes de iniciar sesión",
                        ToastUtils.ToastType.SUCCESS);
                
                // Redirigir al login
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Observar errores de organizador
        organizerViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(RegisterActivity.this, error, ToastUtils.ToastType.ERROR);
                
                // Mostrar error específico en el campo correspondiente
                if (error.contains("username")) {
                    usernameLayout.setError("Este usuario ya está en uso");
                } else if (error.contains("email")) {
                    emailLayout.setError("Email no válido");
                } else if (error.contains("contraseña")) {
                    passwordLayout.setError("La contraseña debe tener al menos 6 caracteres");
                }
                registerButton.setEnabled(true);
            }
        });

        // Observar errores de asistente
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(RegisterActivity.this, error, ToastUtils.ToastType.ERROR);
                
                // Mostrar error específico en el campo correspondiente
                if (error.contains("username")) {
                    usernameLayout.setError("Este usuario ya está en uso");
                } else if (error.contains("email")) {
                    emailLayout.setError("Email no válido");
                } else if (error.contains("contraseña")) {
                    passwordLayout.setError("La contraseña debe tener al menos 6 caracteres");
                }
                registerButton.setEnabled(true);
            }
        });

        // Observar estado de carga de organizador
        organizerViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && selectedRole.equals("ORGANIZER")) {
                registerButton.setEnabled(!loading);
            }
        });

        // Observar estado de carga de asistente
        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && selectedRole.equals("ATTENDEE")) {
                registerButton.setEnabled(!loading);
            }
        });
    }



} 