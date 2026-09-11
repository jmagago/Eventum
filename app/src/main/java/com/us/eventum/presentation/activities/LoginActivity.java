package com.us.eventum.presentation.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.data.repositories.FirebaseManager;
import com.us.eventum.utils.FirebaseAuthErrorHandler;
import com.us.eventum.utils.SecureCredentialsStore;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.WindowInsetsHelper;
import com.us.eventum.presentation.viewmodels.AuthViewModel;
import com.us.eventum.R;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {
    private FirebaseManager firebaseManager;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextView emailErrorText;
    private TextView passwordErrorText;
    private MaterialButton loginButton;
    private TextView registerLink;
    private MaterialCheckBox rememberMeCheckBox;
    private View progressBar;
    private SecureCredentialsStore credentialsStore;
    private AuthViewModel authViewModel;
    private int loginAttempts = 0;
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);

        setupLoginWindowInsets();

        // Inicializar Firebase
        firebaseManager = FirebaseManager.getInstance();
        try {
            credentialsStore = SecureCredentialsStore.create(this);
        } catch (Exception e) {
            credentialsStore = null;
            ToastUtils.showCustomToast(this,
                    "No se pudo inicializar el almacenamiento seguro de credenciales",
                    ToastUtils.ToastType.WARNING);
        }
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Inicializar repositorios en ViewModel
        authViewModel.initializeRepositories(this);

        // Inicializar vistas
        initializeViews();
        
        // Configurar listeners
        setupListeners();
        
        // Cargar datos guardados
        loadSavedData();
        
        // Iniciar animaciones
        startAnimations();
        
        // Observar ViewModel
        observeViewModel();
    }

    private void setupLoginWindowInsets() {
        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);
        }

        View root = findViewById(R.id.loginRoot);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int bottomInset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        emailErrorText = findViewById(R.id.emailErrorText);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        loginButton = findViewById(R.id.loginButton);
        registerLink = findViewById(R.id.registerLink);
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> loginUser());
        registerLink.setOnClickListener(v -> navigateToRegister());
        
        // Listener para el texto de olvidaste tu contraseña
        findViewById(R.id.forgotPasswordTextView).setOnClickListener(v -> {
            Intent intent = new Intent(this, ResetPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void loadSavedData() {
        if (credentialsStore == null || !credentialsStore.isRememberMe()) {
            return;
        }

        emailEditText.setText(credentialsStore.getEmail());
        passwordEditText.setText(credentialsStore.getPassword());
        rememberMeCheckBox.setChecked(true);
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
            findViewById(R.id.registerPromptLayout)
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

    private void observeViewModel() {
        // Observar estado de carga
        authViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                showProgress(loading);
            }
        });

        // Observar errores de Firebase Auth (mensajes claros en español)
        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                clearLoginErrors();
                loginAttempts++;
                switch (FirebaseAuthErrorHandler.getDisplayFieldForMessage(error)) {
                    case PASSWORD:
                        showErrorStable(passwordErrorText, error);
                        break;
                    case EMAIL:
                        showErrorStable(emailErrorText, error);
                        break;
                    default:
                        ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
                        break;
                }
            }
        });

        // Email no verificado: bloquear navegación y mostrar aviso
        authViewModel.getEmailNotVerified().observe(this, notVerified -> {
            if (Boolean.TRUE.equals(notVerified)) {
                showErrorStable(emailErrorText, "Debes verificar tu email para continuar. Revisa tu bandeja.");
            }
        });

        // Observar login exitoso y, cuando cargue el usuario, bifurcar por rol
        authViewModel.getUserLoggedIn().observe(this, loggedIn -> {
            if (loggedIn != null && loggedIn) {
                // Resetear intentos de inicio de sesión
                loginAttempts = 0;

                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (credentialsStore != null) {
                    if (rememberMeCheckBox.isChecked()) {
                        try {
                            credentialsStore.saveCredentials(email, password);
                        } catch (Exception e) {
                            credentialsStore.clearCredentials();
                        }
                    } else {
                        credentialsStore.clearCredentials();
                    }
                }

                // Esperar a que se cargue el usuario y decidir navegación por rol
                authViewModel.getUserType().observe(this, userType -> {
                    if (userType != null) {
                        Intent intent;
                        if ("ATTENDEE".equals(userType)) {
                            intent = new Intent(LoginActivity.this, AttendeeHomeActivity.class);
                        } else {
                            intent = new Intent(LoginActivity.this, OrganizerHomeActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
            }
        });
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

        // Usar AuthViewModel para hacer login
        authViewModel.login(email, password);
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

    private void clearLoginErrors() {
        hideErrorStable(emailErrorText);
        hideErrorStable(passwordErrorText);
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