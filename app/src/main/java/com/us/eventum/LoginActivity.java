package com.us.eventum;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
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

        if (!validateFields(email, password)) {
            return;
        }

        // Verificar intentos de inicio de sesión
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            Toast.makeText(this, 
                "Demasiados intentos fallidos. Por favor, espera unos minutos.", 
                Toast.LENGTH_LONG).show();
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
                            Toast.makeText(LoginActivity.this,
                                "Por favor, verifica tu email antes de iniciar sesión",
                                Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                            showProgress(false);
                            return;
                        }

                        // Resetear intentos de inicio de sesión
                        loginAttempts = 0;
                        
                        if (rememberMeCheckBox.isChecked()) {
                            saveCredentials(email, password);
                        } else {
                            clearSavedCredentials();
                        }
                        
                        Toast.makeText(LoginActivity.this, 
                            "Bienvenido " + user.getEmail(), 
                            Toast.LENGTH_SHORT).show();
                            
                        // Navegar a HomeActivity y limpiar el stack
                        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                } else {
                    loginAttempts++;
                    showProgress(false);
                    String errorMessage = "Error al iniciar sesión";
                    if (task.getException() != null) {
                        errorMessage = task.getException().getMessage();
                    }
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private boolean validateFields(String email, String password) {
        // Limpiar errores anteriores
        clearError(emailEditText);
        clearError(passwordEditText);

        boolean isValid = true;

        if (!validateEmail(email)) {
            showError(emailEditText, "Email inválido");
            isValid = false;
        }

        return isValid;
    }

    private boolean validateEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void validatePassword(String password) {
        PasswordValidator.PasswordValidationResult result = 
            PasswordValidator.validatePassword(password);
        if (!result.isValid) {
            showError(passwordEditText, result.errorMessage);
        } else {
            clearError(passwordEditText);
        }
    }

    private void showError(TextInputEditText editText, String message) {
        View parent = (View) editText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        if (parent instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) parent;
            layout.setError(message);
            layout.setErrorEnabled(true);
        }
        editText.requestFocus();
    }

    private void clearError(TextInputEditText editText) {
        View parent = (View) editText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        if (parent instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) parent;
            layout.setErrorEnabled(false);
        }
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
        loginButton.setEnabled(!show);
    }
} 