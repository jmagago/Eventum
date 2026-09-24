package com.us.eventum.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.us.eventum.data.repositories.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.R;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.ui.viewmodels.AuthViewModel;

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
        setupStatusBarStripe();

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

        // Configurar botón de cambio de contraseña
        changePasswordButton.setOnClickListener(v -> validateAndChangePassword());
        
        // Observar ViewModel
        observeViewModel();
    }

    private void setupStatusBarStripe() {
        View stripe = findViewById(R.id.statusBarStripe);
        if (stripe == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(stripe, (v, windowInsets) -> {
            int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(stripe);
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
                ToastUtils.showCustomToast(this, getString(R.string.toast_password_updated), ToastUtils.ToastType.SUCCESS);
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
            currentPasswordLayout.setError(getString(R.string.error_password_current_required));
            return;
        }

        if (newPassword.isEmpty()) {
            newPasswordLayout.setError(getString(R.string.error_password_new_required));
            return;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.setError(getString(R.string.error_password_confirm_required));
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_passwords_mismatch));
            return;
        }

        if (newPassword.length() < 6) {
            newPasswordLayout.setError(getString(R.string.error_password_min_length));
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