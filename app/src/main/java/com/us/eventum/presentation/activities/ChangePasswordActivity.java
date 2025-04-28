package com.us.eventum.presentation.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputLayout currentPasswordLayout;
    private TextInputLayout newPasswordLayout;
    private TextInputLayout confirmPasswordLayout;
    private MaterialButton changePasswordButton;
    private CircularProgressIndicator progressIndicator;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();

        // Inicializar vistas
        currentPasswordLayout = findViewById(R.id.currentPasswordLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        progressIndicator = findViewById(R.id.progressIndicator);

        // Configurar toolbar
        findViewById(R.id.topAppBar).setOnClickListener(v -> onBackPressed());

        // Configurar botón de cambio de contraseña
        changePasswordButton.setOnClickListener(v -> validateAndChangePassword());
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

        // Proceder con el cambio de contraseña
        changePassword(currentPassword, newPassword);
    }

    private void changePassword(String currentPassword, String newPassword) {
        showLoading(true);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            // Reautenticar al usuario
            user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), currentPassword))
                .addOnSuccessListener(aVoid -> {
                    // Cambiar la contraseña
                    user.updatePassword(newPassword)
                        .addOnSuccessListener(aVoid1 -> {
                            showLoading(false);
                            ToastUtils.showCustomToast(ChangePasswordActivity.this, 
                                "Contraseña actualizada correctamente", ToastUtils.ToastType.SUCCESS);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            ToastUtils.showCustomToast(ChangePasswordActivity.this, 
                                "Error al actualizar la contraseña: " + e.getMessage(), ToastUtils.ToastType.ERROR);
                        });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    currentPasswordLayout.setError("Contraseña actual incorrecta");
                });
        }
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