package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.R;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout btnEditProfile, btnChangePassword, btnDeleteAccount, btnLogout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Inicializar vistas
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        btnLogout = findViewById(R.id.btnLogout);

        // Configurar listeners
        btnEditProfile.setOnClickListener(v -> editProfile());
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnDeleteAccount.setOnClickListener(v -> deleteAccount());
        btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_info) {
            showAppInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAppInfoDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_info, null);
        TextView versionText = dialogView.findViewById(R.id.versionText);
        TextView buildText = dialogView.findViewById(R.id.buildText);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            int versionCode = packageInfo.versionCode;
            
            versionText.setText(getString(R.string.app_info_version, versionName));
            buildText.setText(getString(R.string.app_info_build, versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "Error al obtener información de versión", e);
            versionText.setText("Versión: No disponible");
            buildText.setText("Compilación: No disponible");
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomTransparentDialog)
                .setView(dialogView)
                .create();
        
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // Configurar el botón de cerrar
        dialogView.findViewById(R.id.closeButton).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void editProfile() {
        // TODO: Implementar edición de perfil
        Toast.makeText(this, "Función en desarrollo", Toast.LENGTH_SHORT).show();
    }

    private void changePassword() {
        // TODO: Implementar cambio de contraseña
        Toast.makeText(this, "Función en desarrollo", Toast.LENGTH_SHORT).show();
    }

    private void deleteAccount() {
        // Crear el diálogo personalizado
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        builder.setView(dialogView);
        
        // Crear el diálogo
        AlertDialog dialog = builder.create();
        
        // Configurar fondo transparente
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Configurar botones
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) {
                // Mostrar un indicador de progreso
                Toast.makeText(SettingsActivity.this, "Eliminando cuenta...", Toast.LENGTH_SHORT).show();
                
                mAuth.getCurrentUser().delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(SettingsActivity.this, 
                                          "Cuenta eliminada correctamente", 
                                          Toast.LENGTH_SHORT).show();
                            goToLogin();
                        } else {
                            Toast.makeText(SettingsActivity.this, 
                                          "Error al eliminar la cuenta", 
                                          Toast.LENGTH_SHORT).show();
                        }
                    });
            }
            dialog.dismiss();
        });
        
        // Mostrar el diálogo
        dialog.show();
    }

    private void logout() {
        // Crear el diálogo personalizado
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        builder.setView(dialogView);
        
        // Crear el diálogo
        AlertDialog dialog = builder.create();
        
        // Configurar fondo transparente
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Configurar botones
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            mAuth.signOut();
            goToLogin();
            dialog.dismiss();
        });
        
        // Mostrar el diálogo
        dialog.show();
    }

    private void goToLogin() {
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 