package com.us.eventum;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private MaterialButton deleteAccountButton;
    private MaterialButton logoutButton;
    private SwitchMaterial notificationsSwitch;
    private SwitchMaterial privateProfileSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Inicializar vistas
        initializeViews();
        setupToolbar();
        setupListeners();
    }

    private void initializeViews() {
        deleteAccountButton = findViewById(R.id.deleteAccountButton);
        logoutButton = findViewById(R.id.logoutButton);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        privateProfileSwitch = findViewById(R.id.privateProfileSwitch);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
        logoutButton.setOnClickListener(v -> logoutUser());
        
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Implementar la lógica de notificaciones
            Toast.makeText(this, "Notificaciones " + (isChecked ? "activadas" : "desactivadas"), 
                Toast.LENGTH_SHORT).show();
        });
        
        privateProfileSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Implementar la lógica de privacidad
            Toast.makeText(this, "Perfil " + (isChecked ? "privado" : "público"), 
                Toast.LENGTH_SHORT).show();
        });
    }

    private void showDeleteAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialog);
        AlertDialog dialog = builder
            .setTitle("Eliminar cuenta")
            .setMessage("¿Estás seguro de que deseas eliminar tu cuenta? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar", (dialog1, which) -> deleteAccount())
            .setNegativeButton("Cancelar", null)
            .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorError));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorPrimary));
        });

        dialog.show();
    }

    private void deleteAccount() {
        if (currentUser != null) {
            // Primero eliminar los datos del usuario en Firestore
            db.collection("users").document(currentUser.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Luego eliminar la cuenta de Firebase Auth
                    currentUser.delete()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(SettingsActivity.this, 
                                    "Cuenta eliminada correctamente", 
                                    Toast.LENGTH_SHORT).show();
                                navigateToLogin();
                            } else {
                                Toast.makeText(SettingsActivity.this, 
                                    "Error al eliminar la cuenta: " + task.getException().getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                            }
                        });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(SettingsActivity.this, 
                        "Error al eliminar datos: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void logoutUser() {
        mAuth.signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
} 