package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.data.repositories.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.AuthViewModel;

public class ResetPasswordActivity extends AppCompatActivity {
    private FirebaseManager firebaseManager;
    private TextInputEditText emailEditText;
    private TextInputLayout emailLayout;
    private MaterialButton resetPasswordButton;
    private MaterialButton backToLoginButton;
    private View progressBar;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Inicializar Firebase
        firebaseManager = FirebaseManager.getInstance();
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Inicializar repositorios en ViewModel
        authViewModel.initializeRepositories(this);

        // Inicializar vistas
        initializeViews();
        
        // Configurar listeners
        setupListeners();
        
        // Observar ViewModel
        observeViewModel();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        emailLayout = findViewById(R.id.emailLayout);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        backToLoginButton = findViewById(R.id.backToLoginButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        resetPasswordButton.setOnClickListener(v -> resetPassword());
        backToLoginButton.setOnClickListener(v -> finish());
    }

    private void observeViewModel() {
        // Observar estado de carga
        authViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                showProgress(loading);
            }
        });

        // Observar errores
        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar restablecimiento exitoso
        authViewModel.getPasswordResetSent().observe(this, sent -> {
            if (sent != null && sent) {
                ToastUtils.showCustomToast(this, "Correo de recuperación enviado", ToastUtils.ToastType.SUCCESS);
                finish();
            }
        });
    }

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();

        if (!validateEmail(email)) {
            emailLayout.setError("Email inválido");
            return;
        }

        // Usar AuthViewModel para enviar email de restablecimiento
        authViewModel.sendPasswordResetEmail(email);
    }

    private boolean validateEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        resetPasswordButton.setEnabled(!show);
    }
} 