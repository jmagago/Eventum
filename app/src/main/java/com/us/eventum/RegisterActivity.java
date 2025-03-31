package com.us.eventum;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.utils.PasswordValidator;
import com.us.eventum.utils.PasswordStrengthIndicator;
import java.util.Locale;
import android.text.Editable;

public class RegisterActivity extends AppCompatActivity {
    // Variables para Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Variables para los campos de texto
    private TextInputEditText usernameInput, emailInput, passwordInput, confirmPasswordInput;
    
    // Variables para la UI
    private MaterialButton registerButton, loginButton;
    private ProgressBar passwordStrengthIndicator;
    private TextView passwordStrengthText;
    private TextInputLayout usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configurar el idioma español para toda la app
        Locale locale = new Locale("es", "ES");
        Locale.setDefault(locale);
        
        setContentView(R.layout.activity_register);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Configurar el teclado virtual para que no tape los campos
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Inicializar todas las vistas
        initializeViews();
        
        // Configurar los listeners de los botones
        setupListeners();
        
        // Iniciar las animaciones de entrada
        startAnimations();
    }

    private void initializeViews() {
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        registerButton = findViewById(R.id.registerButton);
        loginButton = findViewById(R.id.loginButton);
        passwordStrengthIndicator = findViewById(R.id.passwordStrengthIndicator);
        passwordStrengthText = findViewById(R.id.passwordStrengthText);
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
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

        // Validación en tiempo real del nombre de usuario
        usernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String username = s.toString().trim();
                if (username.isEmpty()) {
                    usernameLayout.setError("El nombre de usuario es obligatorio");
                } else if (username.length() < 5) {
                    usernameLayout.setError("El nombre de usuario debe tener al menos 5 caracteres");
                } else {
                    usernameLayout.setError(null);
                    checkUsernameExists(username);
                }
            }
        });

        // Validación en tiempo real de la contraseña
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                if (password.isEmpty()) {
                    passwordLayout.setError("La contraseña es obligatoria");
                    passwordStrengthIndicator.setProgress(0);
                    passwordStrengthText.setText("");
                } else if (password.length() < 6) {
                    passwordLayout.setError("La contraseña debe tener al menos 6 caracteres");
                    passwordStrengthIndicator.setProgress(0);
                    passwordStrengthText.setText("");
                } else {
                    int strength = PasswordValidator.calculateStrength(password);
                    updatePasswordStrengthIndicator(strength);
                    if (strength < 3) {
                        passwordLayout.setError("La contraseña es demasiado débil");
                    } else {
                        passwordLayout.setError(null);
                    }
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

        registerButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            String username = usernameInput.getText().toString().trim();

            if (validateFields(email, password, confirmPassword, username)) {
                registerUser(email, password, username);
            }
        });

        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void updatePasswordStrengthIndicator(int strength) {
        // La barra de progreso debe tener un máximo de 100
        int progress = (strength * 20); // Cada nivel es 20% (0-4 = 0-80%)
        passwordStrengthIndicator.setMax(100);
        passwordStrengthIndicator.setProgress(progress);
        
        // Cambiar el color según la fortaleza
        int color = PasswordStrengthIndicator.getStrengthColor(strength);
        passwordStrengthIndicator.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        
        String strengthText = PasswordStrengthIndicator.getStrengthText(strength);
        passwordStrengthText.setText(strengthText);
    }

    private void checkUsernameExists(String username) {
        db.collection("users")
            .whereEqualTo("nombre", username)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (!task.getResult().isEmpty()) {
                        usernameLayout.setError("Este nombre de usuario ya está en uso");
                    } else {
                        usernameLayout.setError(null);
                    }
                }
            });
    }

    private boolean validateFields(String email, String password, String confirmPassword, String username) {
        boolean isValid = true;

        // Validar email
        if (email.isEmpty()) {
            emailLayout.setError("El email es obligatorio");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Email no válido");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        // Validar contraseña
        if (password.isEmpty()) {
            passwordLayout.setError("La contraseña es obligatoria");
            isValid = false;
        } else if (password.length() < 6) {
            passwordLayout.setError("La contraseña debe tener al menos 6 caracteres");
            isValid = false;
        } else if (PasswordValidator.calculateStrength(password) < 3) {
            passwordLayout.setError("La contraseña es demasiado débil");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        // Validar confirmación de contraseña
        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Las contraseñas no coinciden");
            isValid = false;
        } else {
            confirmPasswordLayout.setError(null);
        }

        // Validar nombre de usuario
        if (username.isEmpty()) {
            usernameLayout.setError("El nombre de usuario es obligatorio");
            isValid = false;
        } else if (username.length() < 5) {
            usernameLayout.setError("El nombre de usuario debe tener al menos 5 caracteres");
            isValid = false;
        } else {
            usernameLayout.setError(null);
        }

        return isValid;
    }

    private void showProgress(boolean show) {
        registerButton.setEnabled(!show);
    }

    private void registerUser(String email, String password, String username) {
        showProgress(true);

        // Crear usuario en Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // Crear objeto de usuario
                        User newUser = new User();
                        newUser.setUid(user.getUid());
                        newUser.setEmail(email);
                        newUser.setNombre(username);

                        // Guardar datos en Firestore
                        db.collection("users").document(user.getUid())
                            .set(newUser)
                            .addOnSuccessListener(aVoid -> {
                                // Enviar email de verificación
                                user.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        showProgress(false);
                                        if (emailTask.isSuccessful()) {
                                            Toast.makeText(RegisterActivity.this, 
                                                "Registro exitoso. Por favor, verifica tu email antes de iniciar sesión", 
                                                Toast.LENGTH_LONG).show();
                                            
                                            // Cerrar sesión
                                            mAuth.signOut();
                                            
                                            // Crear el intent para el login
                                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            
                                            // Iniciar la actividad
                                startActivity(intent);
                                        } else {
                                            Toast.makeText(RegisterActivity.this, 
                                                "Error al enviar email de verificación", 
                                                Toast.LENGTH_SHORT).show();
                                        }
                                    });
                            })
                            .addOnFailureListener(e -> {
                                showProgress(false);
                                Toast.makeText(RegisterActivity.this, 
                                    "Error al guardar datos: " + e.getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                            });
                    }
                } else {
                    showProgress(false);
                    Toast.makeText(RegisterActivity.this, 
                        "Error al registrar usuario: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void startAnimations() {
        // Configurar el estado inicial de las vistas
        usernameLayout.setAlpha(0f);
        emailLayout.setAlpha(0f);
        passwordLayout.setAlpha(0f);
        confirmPasswordLayout.setAlpha(0f);
        registerButton.setAlpha(0f);
        loginButton.setAlpha(0f);
        passwordStrengthIndicator.setAlpha(0f);
        passwordStrengthText.setAlpha(0f);

        // Crear el conjunto de animaciones
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        
        // Crear animaciones para cada elemento
        ObjectAnimator[] animations = {
            ObjectAnimator.ofFloat(usernameLayout, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(emailLayout, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(passwordLayout, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(confirmPasswordLayout, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(passwordStrengthIndicator, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(passwordStrengthText, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(registerButton, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(loginButton, "alpha", 0f, 1f)
        };
        
        // Configurar la secuencia de animaciones con un pequeño retraso entre cada elemento
        for (int i = 0; i < animations.length; i++) {
            animations[i].setStartDelay(i * 100); // 100ms de retraso entre cada elemento
        }
        
        // Ejecutar todas las animaciones en secuencia
        animatorSet.playTogether(animations);
        animatorSet.setDuration(800);
        animatorSet.start();
    }
} 