package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.us.eventum.R;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.UserViewModel;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText usernameInput, nameInput, primerApellidoInput, segundoApellidoInput, emailInput, verifyEmailInput, passwordInput, confirmPasswordInput;
    private TextInputLayout usernameLayout, nameLayout, primerApellidoLayout, segundoApellidoLayout, emailLayout, verifyEmailLayout, passwordLayout, confirmPasswordLayout;
    private MaterialButton registerButton;
    private MaterialButton organizerRoleButton;
    private MaterialButton attendeeRoleButton;
    private TextView loginLink;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar ViewModel
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Inicializar repositorio en ViewModel
        userViewModel.initializeRepository(this);

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Inicializar vistas
        initializeViews();
        setupListeners();
        observeViewModel();
    }

    private void initializeViews() {
        // EditText
        usernameInput = findViewById(R.id.usernameInput);
        nameInput = findViewById(R.id.nameInput);
        primerApellidoInput = findViewById(R.id.primerApellidoInput);
        segundoApellidoInput = findViewById(R.id.segundoApellidoInput);
        emailInput = findViewById(R.id.emailInput);
        verifyEmailInput = findViewById(R.id.verifyEmailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        // TextInputLayout
        usernameLayout = findViewById(R.id.usernameLayout);
        nameLayout = findViewById(R.id.nameLayout);
        primerApellidoLayout = findViewById(R.id.primerApellidoLayout);
        segundoApellidoLayout = findViewById(R.id.segundoApellidoLayout);
        emailLayout = findViewById(R.id.emailLayout);
        verifyEmailLayout = findViewById(R.id.verifyEmailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        // Buttons
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
        organizerRoleButton = findViewById(R.id.organizerRoleButton);
        attendeeRoleButton = findViewById(R.id.attendeeRoleButton);
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
        final String[] selectedRole = new String[]{UserRole.ORGANIZER};

        organizerRoleButton.setOnClickListener(v -> {
            selectedRole[0] = UserRole.ORGANIZER;
            organizerRoleButton.setEnabled(false);
            attendeeRoleButton.setEnabled(true);
        });

        attendeeRoleButton.setOnClickListener(v -> {
            selectedRole[0] = UserRole.ATTENDEE;
            attendeeRoleButton.setEnabled(false);
            organizerRoleButton.setEnabled(true);
        });

        registerButton.setOnClickListener(v -> validateAndRegister(selectedRole[0]));
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void validateAndRegister(String role) {
        // Limpiar errores previos
        usernameLayout.setError(null);
        nameLayout.setError(null);
        primerApellidoLayout.setError(null);
        segundoApellidoLayout.setError(null);
        emailLayout.setError(null);
        verifyEmailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        // Obtener valores
        String username = usernameInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String primerApellido = primerApellidoInput.getText().toString().trim();
        String segundoApellido = segundoApellidoInput.getText().toString().trim();
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

        if (TextUtils.isEmpty(name)) {
            nameLayout.setError("El nombre es obligatorio");
            return;
        }

        if (TextUtils.isEmpty(primerApellido)) {
            primerApellidoLayout.setError("El primer apellido es obligatorio");
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

        // Registrar usuario usando ViewModel
        userViewModel.registerUser(email, password, username, name, primerApellido, segundoApellido, role);
    }

    private void observeViewModel() {
        // Observar registro exitoso
        userViewModel.getUserRegistered().observe(this, registered -> {
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

        // Observar errores
        userViewModel.getErrorMessage().observe(this, error -> {
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
            }
        });

        // Observar estado de carga
        userViewModel.getIsLoading().observe(this, loading -> {
            registerButton.setEnabled(!loading);
        });
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 