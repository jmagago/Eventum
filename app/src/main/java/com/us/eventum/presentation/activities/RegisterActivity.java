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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameInput, surnameInput, emailInput, passwordInput, confirmPasswordInput;
    private TextInputLayout nameLayout, surnameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private MaterialButton registerButton;
    private TextView loginLink;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Inicializar vistas
        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        // EditText
        nameInput = findViewById(R.id.nameInput);
        surnameInput = findViewById(R.id.surnameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        // TextInputLayout
        nameLayout = findViewById(R.id.nameLayout);
        surnameLayout = findViewById(R.id.surnameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        // Buttons
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
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

        registerButton.setOnClickListener(v -> validateAndRegister());
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void validateAndRegister() {
        // Limpiar errores previos
        nameLayout.setError(null);
        surnameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        // Obtener valores
        String name = nameInput.getText().toString().trim();
        String surname = surnameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        // Validar campos
        if (TextUtils.isEmpty(name)) {
            nameLayout.setError("El nombre es obligatorio");
            return;
        }

        if (TextUtils.isEmpty(surname)) {
            surnameLayout.setError("Los apellidos son obligatorios");
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

        // Registrar usuario
        registerUser(name, surname, email, password);
    }

    private void registerUser(String name, String surname, String email, String password) {
        registerButton.setEnabled(false); // Deshabilitar botón mientras se procesa

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Guardar información adicional en Firestore
                            String userId = user.getUid();
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("name", name);
                            userData.put("surname", surname);
                            userData.put("email", email);

                            db.collection("users").document(userId)
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        // Enviar email de verificación
                                        user.sendEmailVerification()
                                                .addOnCompleteListener(emailTask -> {
                                                    registerButton.setEnabled(true);
                                                    if (emailTask.isSuccessful()) {
                                                        ToastUtils.showCustomToast(RegisterActivity.this,
                                                                "Registro exitoso. Por favor, verifica tu email antes de iniciar sesión",
                                                                ToastUtils.ToastType.SUCCESS);

                                                        // Cerrar sesión y redirigir al login
                                                        mAuth.signOut();
                                                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                        startActivity(intent);
                                                        finish();
                                                    } else {
                                                        ToastUtils.showCustomToast(RegisterActivity.this,
                                                                "Error al enviar email de verificación",
                                                                ToastUtils.ToastType.ERROR);
                                                    }
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        registerButton.setEnabled(true);
                                        ToastUtils.showCustomToast(RegisterActivity.this,
                                                "Error al guardar los datos: " + e.getMessage(),
                                                ToastUtils.ToastType.ERROR);
                                    });
                        }
                    } else {
                        registerButton.setEnabled(true);
                        ToastUtils.showCustomToast(RegisterActivity.this,
                                "Error en el registro: " + task.getException().getMessage(),
                                ToastUtils.ToastType.ERROR);
                    }
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