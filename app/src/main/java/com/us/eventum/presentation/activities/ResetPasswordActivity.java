package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;

public class ResetPasswordActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private TextInputEditText emailEditText;
    private TextInputLayout emailLayout;
    private MaterialButton resetPasswordButton;
    private MaterialButton backToLoginButton;
    private View progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Inicializar vistas
        initializeViews();
        
        // Configurar listeners
        setupListeners();
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

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();

        if (!validateEmail(email)) {
            emailLayout.setError("Email inválido");
            return;
        }

        showProgress(true);

        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    ToastUtils.showCustomToast(ResetPasswordActivity.this, "Correo de recuperación enviado", ToastUtils.ToastType.SUCCESS);
                    finish();
                } else {
                    String errorMessage = "Error al enviar el email de restablecimiento";
                    if (task.getException() != null) {
                        errorMessage = task.getException().getMessage();
                    }
                    ToastUtils.showCustomToast(ResetPasswordActivity.this, errorMessage, ToastUtils.ToastType.ERROR);
                }
                showProgress(false);
            });
    }

    private boolean validateEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        resetPasswordButton.setEnabled(!show);
    }
} 