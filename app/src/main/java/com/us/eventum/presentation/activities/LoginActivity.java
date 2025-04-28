package com.us.eventum.presentation.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.us.eventum.config.AppConfig;
import com.us.eventum.utils.PasswordValidator;
import com.us.eventum.utils.ToastUtils;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.us.eventum.R;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextView emailErrorText;
    private TextView passwordErrorText;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialCheckBox rememberMeCheckBox;
    private View progressBar;
    private SharedPreferences sharedPreferences;
    private int loginAttempts = 0;
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(AppConfig.APP_PREFS_NAME, MODE_PRIVATE);

        // Inicializar vistas
        initializeViews();
        
        // Configurar listeners
        setupListeners();
        
        // Cargar datos guardados
        loadSavedData();
        
        // Iniciar animaciones
        startAnimations();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        emailErrorText = findViewById(R.id.emailErrorText);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> loginUser());
        registerButton.setOnClickListener(v -> navigateToRegister());
        
        // Listener para el texto de olvidaste tu contraseña
        findViewById(R.id.forgotPasswordTextView).setOnClickListener(v -> {
            Intent intent = new Intent(this, ResetPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void loadSavedData() {
        String savedEmail = sharedPreferences.getString("email", "");
        String savedPassword = sharedPreferences.getString("password", "");
        boolean rememberMe = sharedPreferences.getBoolean("rememberMe", false);

        if (rememberMe) {
            emailEditText.setText(savedEmail);
            passwordEditText.setText(savedPassword);
            rememberMeCheckBox.setChecked(true);
        }
    }

    private void startAnimations() {
        View[] views = {
            findViewById(R.id.logoImageView),
            findViewById(R.id.titleTextView),
            findViewById(R.id.emailLayout),
            findViewById(R.id.passwordLayout),
            findViewById(R.id.rememberMeCheckBox),
            findViewById(R.id.forgotPasswordTextView),
            loginButton,
            registerButton
        };

        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view.getAlpha() == 0f) {
                view.setAlpha(0f);
                view.setTranslationY(50f);

                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
                ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(alphaAnimator, translationAnimator);
                animatorSet.setDuration(500);
                animatorSet.setStartDelay(i * 100);
                animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
                animatorSet.start();
            }
        }
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (!validateFieldsStable(email, password)) {
            return;
        }

        // Verificar intentos de inicio de sesión
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            ToastUtils.showCustomToast(this, "Demasiados intentos fallidos. Por favor, espera unos minutos.", ToastUtils.ToastType.WARNING);
            return;
        }

        showProgress(true);

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        if (!user.isEmailVerified()) {
                            // Si el email no está verificado, mostrar mensaje y cerrar sesión
                            showProgress(false);
                            showErrorStable(emailErrorText, "Por favor, verifica tu email antes de iniciar sesión");
                            mAuth.signOut();
                            return;
                        }

                        // Resetear intentos de inicio de sesión
                        loginAttempts = 0;
                        
                        if (rememberMeCheckBox.isChecked()) {
                            saveCredentials(email, password);
                        } else {
                            clearSavedCredentials();
                        }
                        
                        // Obtener el nombre del usuario de Firestore y mostrar un mensaje de bienvenida personalizado
                        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String userName = "";
                                if (documentSnapshot.exists()) {
                                    // Los datos actualmente están guardados en el campo "nombre"
                                    userName = documentSnapshot.getString("nombre");
                                }
                                
                                // Si no se encuentra el nombre, usar la primera parte del email
                                if (userName == null || userName.isEmpty()) {
                                    userName = user.getEmail().split("@")[0];
                                }
                                
                                // Mostrar toast personalizado con el nombre
                                ToastUtils.showWelcomeToast(LoginActivity.this, "¡Bienvenido/a " + userName + "!");
                                
                                // Navegar a HomeActivity y limpiar el stack
                                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                // En caso de error, mostrar mensaje genérico y continuar
                                ToastUtils.showWelcomeToast(LoginActivity.this, "¡Bienvenido/a a Eventum!");
                                
                                // Navegar a HomeActivity y limpiar el stack
                                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                    }
                } else {
                    loginAttempts++;
                    showProgress(false);
                    
                    // Mostrar el error en el campo correspondiente
                    if (task.getException() != null) {
                        if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                            showErrorStable(emailErrorText, "El usuario no existe o ha sido deshabilitado");
                        } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            showErrorStable(passwordErrorText, "Contraseña incorrecta");
                        } else {
                            String errorMessage = task.getException().getMessage();
                            if (errorMessage != null && errorMessage.toLowerCase().contains("password")) {
                                showErrorStable(passwordErrorText, "Error: " + errorMessage);
                            } else {
                                showErrorStable(emailErrorText, "Error: " + errorMessage);
                            }
                        }
                    }
                    ToastUtils.showCustomToast(this, "Error al iniciar sesión", ToastUtils.ToastType.ERROR);
                }
            });
    }

    // Método de validación estable que no causa reajustes del layout
    private boolean validateFieldsStable(String email, String password) {
        boolean isValid = true;

        // Validar email
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showErrorStable(emailErrorText, "Email inválido");
            isValid = false;
        } else {
            hideErrorStable(emailErrorText);
        }
        
        // Validar contraseña (mínimo 6 caracteres)
        if (password.isEmpty()) {
            showErrorStable(passwordErrorText, "La contraseña no puede estar vacía");
            isValid = false;
        } else if (password.length() < 6) {
            showErrorStable(passwordErrorText, "La contraseña debe tener al menos 6 caracteres");
            isValid = false;
        } else {
            hideErrorStable(passwordErrorText);
        }

        return isValid;
    }

    // Métodos para mostrar/ocultar errores sin causar reajustes
    private void showErrorStable(TextView errorTextView, String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
    }

    private void hideErrorStable(TextView errorTextView) {
        errorTextView.setVisibility(View.INVISIBLE);
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("email", email);
        editor.putString("password", password);
        editor.putBoolean("rememberMe", true);
        editor.apply();
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("email");
        editor.remove("password");
        editor.remove("rememberMe");
        editor.apply();
    }

    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
} 