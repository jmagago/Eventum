package com.us.eventum.presentation.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.us.eventum.data.repositories.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.AuthViewModel;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputLayout currentPasswordLayout;
    private TextInputLayout newPasswordLayout;
    private TextInputLayout confirmPasswordLayout;
    private MaterialButton changePasswordButton;
    private CircularProgressIndicator progressIndicator;
    private FirebaseManager firebaseManager;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        firebaseManager = FirebaseManager.getInstance();
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Inicializar repositorios en ViewModel
        authViewModel.initializeRepositories(this);

        // Inicializar vistas
        currentPasswordLayout = findViewById(R.id.currentPasswordLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Configurar toolbar
        findViewById(R.id.topAppBar).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Configurar botón de cambio de contraseña
        changePasswordButton.setOnClickListener(v -> validateAndChangePassword());
        
        // Observar ViewModel
        observeViewModel();
    }

    private void observeViewModel() {
        // Observar estado de carga
        authViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                showLoading(loading);
            }
        });

        // Observar errores
        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar cambio de contraseña exitoso
        authViewModel.getPasswordChanged().observe(this, changed -> {
            if (changed != null && changed) {
                ToastUtils.showCustomToast(this, "Contraseña actualizada correctamente", ToastUtils.ToastType.SUCCESS);
                finish();
            }
        });
    }

    private void validateAndChangePassword() {
        // Limpiar errores previos
        clearErrors();

        // Obtener valores de los campos
        String currentPassword = getTextFromInput(currentPasswordLayout);
        String newPassword = getTextFromInput(newPasswordLayout);
        String confirmPassword = getTextFromInput(confirmPasswordLayout);

        // Validar campos
        if (currentPassword.isEmpty()) {
            currentPasswordLayout.setError("Ingresa tu contraseña actual");
            return;
        }

        if (newPassword.isEmpty()) {
            newPasswordLayout.setError("Ingresa una nueva contraseña");
            return;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.setError("Confirma tu nueva contraseña");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Las contraseñas no coinciden");
            return;
        }

        if (newPassword.length() < 6) {
            newPasswordLayout.setError("La contraseña debe tener al menos 6 caracteres");
            return;
        }

        // Usar AuthViewModel para cambiar la contraseña
        authViewModel.changePassword(currentPassword, newPassword);
    }


    private String getTextFromInput(TextInputLayout inputLayout) {
        if (inputLayout.getEditText() != null) {
            return inputLayout.getEditText().getText().toString().trim();
        }
        return "";
    }

    private void clearErrors() {
        currentPasswordLayout.setError(null);
        newPasswordLayout.setError(null);
        confirmPasswordLayout.setError(null);
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        changePasswordButton.setEnabled(!show);
    }
} 