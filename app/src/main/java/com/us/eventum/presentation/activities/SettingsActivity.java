package com.us.eventum.presentation.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import de.hdodenhof.circleimageview.CircleImageView;
import com.us.eventum.data.repositories.FirebaseManager;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.us.eventum.R;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.presentation.viewmodels.SharedViewModel;
import com.us.eventum.presentation.viewmodels.UserViewModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    
    private LinearLayout btnEditProfile, btnChangePassword, btnDeleteAccount, btnLogout;
    private FirebaseManager firebaseManager;
    private CircleImageView profileImageView;
    private CircularProgressIndicator progressIndicator;
    private Uri photoUri;
    private SharedViewModel sharedViewModel;
    private UserViewModel userViewModel;
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showImagePickerDialog();
            } else {
                ToastUtils.showCustomToast(this, "Se necesitan permisos para esta función", ToastUtils.ToastType.ERROR);
            }
        });

    private final ActivityResultLauncher<Intent> takePictureLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                handleImageCapture();
            }
        });

    private final ActivityResultLauncher<Intent> pickImageLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri selectedImageUri = result.getData().getData();
                handleImageSelection(selectedImageUri);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        firebaseManager = FirebaseManager.getInstance();

        // Inicializar ViewModels
        sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Inicializar repositorio en ViewModel
        userViewModel.initializeRepository(this);

        // Configurar Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // Configurar botón de retroceso (ahora se maneja automáticamente con MaterialToolbar)
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Configurar botón de información
        android.widget.TextView infoButton = findViewById(R.id.infoButton);
        infoButton.setOnClickListener(v -> showAppInfoDialog());

        // Inicializar vistas
        initializeViews();
        setupListeners();
        observeViewModel();
        loadUserData();
        
    }

    private void initializeViews() {
        profileImageView = findViewById(R.id.profileImageView);
        progressIndicator = findViewById(R.id.progressIndicator);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupListeners() {
        FloatingActionButton editProfileImageButton = findViewById(R.id.editProfileImageButton);
        editProfileImageButton.setOnClickListener(v -> checkPermissionAndShowPicker());
        
        btnEditProfile.setOnClickListener(v -> editProfile());
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnDeleteAccount.setOnClickListener(v -> deleteAccount());
        btnLogout.setOnClickListener(v -> logout());
    }

    private void observeViewModel() {
        // Observar datos del usuario actual
        userViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                TextView userNameTextView = findViewById(R.id.userNameTextView);
                TextView userEmailTextView = findViewById(R.id.userEmailTextView);
                TextView userRoleTextView = findViewById(R.id.userRoleTextView);
                
                userEmailTextView.setText(user.getEmail());
                if (user.getNombre() != null && !user.getNombre().isEmpty()) {
                    userNameTextView.setText(user.getNombre());
                } else {
                    userNameTextView.setText(user.getEmail().substring(0, user.getEmail().indexOf('@')));
                }
                if (userRoleTextView != null) {
                    String role = user.getRole() != null ? user.getRole() : UserRole.ORGANIZER;
                    boolean isAssistant = UserRole.ATTENDEE.equalsIgnoreCase(role);
                    userRoleTextView.setText(isAssistant ? R.string.role_attendee : R.string.role_organizer);
                    userRoleTextView.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                    userRoleTextView.setBackgroundResource(isAssistant ? R.drawable.bg_role_badge_assistant : R.drawable.bg_role_badge_organizer);
                }
            }
        });

        // Observar estado de carga
        userViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && loading) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else {
                progressIndicator.setVisibility(View.GONE);
            }
        });

        // Observar errores
        userViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar logout
        userViewModel.getUserLoggedIn().observe(this, loggedIn -> {
            if (loggedIn != null && !loggedIn) {
                // Usuario cerró sesión o eliminó cuenta
                ToastUtils.showCustomToast(this, "Sesión cerrada", ToastUtils.ToastType.SUCCESS);
                goToLogin();
            }
        });
    }

    private void loadUserData() {
        if (firebaseManager.getAuth().getCurrentUser() != null) {
            // Usar UserViewModel para cargar datos del usuario
            userViewModel.loadCurrentUser();
            loadProfileImage();
        }
    }

    private void checkPermissionAndShowPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            showImagePickerDialog();
        }
    }

    private void showImagePickerDialog() {
        String[] options = {"Tomar foto", "Elegir de la galería"};
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Cambiar foto de perfil")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        if (checkCameraPermission()) {
                        launchCamera();
                        }
                        break;
                    case 1:
                        if (checkStoragePermission()) {
                        launchGallery();
                        }
                        break;
                }
            })
            .show();
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            return false;
        }
        return true;
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return false;
            }
        }
        return true;
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error al crear el archivo de imagen", ex);
                ToastUtils.showCustomToast(this, "Error al crear archivo de imagen", ToastUtils.ToastType.ERROR);
                return;
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                    "com.us.eventum.fileprovider",
                    photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                takePictureLauncher.launch(takePictureIntent);
            }
        } else {
            ToastUtils.showCustomToast(this, "No se encontró una aplicación de cámara", ToastUtils.ToastType.ERROR);
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void handleImageCapture() {
        if (photoUri != null) {
            handleImageSelection(photoUri);
        }
    }

    private void handleImageSelection(Uri imageUri) {
        if (firebaseManager.getAuth().getCurrentUser() == null) return;

        showProgress(true);
        String userId = firebaseManager.getAuth().getCurrentUser().getUid();
        StorageReference profileRef = firebaseManager.getStorage()
            .getReference()
            .child("profile_images/" + userId + ".jpg");

        try {
            // Comprimir la imagen antes de subirla
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] data = baos.toByteArray();

            // Mostrar la imagen comprimida en el ImageView con circleCrop
            Glide.with(this)
                .load(bitmap)
                .circleCrop()
                .into(profileImageView);

            // Guardar la URI de la imagen en SharedPreferences para sincronización inmediata
            ProfileImageManager.saveImageUri(this, userId, imageUri.toString());
            ProfileImageManager.updateCurrentUri(imageUri.toString(), profileImageView);

            // Subir la imagen comprimida
            UploadTask uploadTask = profileRef.putBytes(data);
            uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    showProgress(false);
                    // Marcar que la foto fue actualizada
                    sharedViewModel.notifyProfileImageUpdated();
                    ToastUtils.showCustomToast(SettingsActivity.this, "Foto de perfil actualizada", ToastUtils.ToastType.SUCCESS);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    ToastUtils.showCustomToast(SettingsActivity.this, "Error al actualizar la foto de perfil", ToastUtils.ToastType.ERROR);
                });
        } catch (IOException e) {
            showProgress(false);
            ToastUtils.showCustomToast(this, "Error al procesar la imagen", ToastUtils.ToastType.ERROR);
        }
    }
    
    private String getImageUri() {
        String userId = firebaseManager.getAuth().getCurrentUser().getUid();
        return ProfileImageManager.getImageUri(this, userId);
    }

    private void loadProfileImage() {
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            return;
        }

        String userId = firebaseManager.getAuth().getCurrentUser().getUid();
        
        // Primero intentar cargar la imagen desde URI guardada
        String savedUri = getImageUri();
        
        if (savedUri != null) {
            ProfileImageManager.loadImageFromUri(this, profileImageView, savedUri);
            return;
        }
        
        // Si no hay URI guardada, establecer imagen por defecto
        // SettingsActivity NUNCA debería cargar desde Firebase Storage
        // porque OrganizerHomeActivity siempre carga primero y guarda la URI local
        profileImageView.setImageResource(R.drawable.default_profile);
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        profileImageView.setEnabled(!show);
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
        // Detectar rol del usuario
        userViewModel.getCurrentUser().observe(this, user -> {
            if (user == null) return;
            String role = user.getRole();
            boolean isAttendee = UserRole.ATTENDEE.equalsIgnoreCase(role);
            
            if (isAttendee) {
                showAttendeeProfileDialog(user);
            } else {
                showOrganizerProfileDialog(user);
            }
        });
    }

    private void showAttendeeProfileDialog(com.us.eventum.data.models.User user) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_attendee_profile, null);
        com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        com.google.android.material.textfield.TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        com.google.android.material.textfield.TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        com.google.android.material.textfield.TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        com.google.android.material.textfield.TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);

        // Pre-cargar datos actuales
        if (user.getNombre() != null) nameInput.setText(user.getNombre());
        if (user.getPrimerApellido() != null) firstSurnameInput.setText(user.getPrimerApellido());
        if (user.getSegundoApellido() != null) secondSurnameInput.setText(user.getSegundoApellido());
        if (user.getDni() != null) dniInput.setText(user.getDni());
        if (user.getPhone() != null) phoneInput.setText(user.getPhone());
        if (user.getFechaNacimiento() != null) birthDateInput.setText(user.getFechaNacimiento());

        // Write-once: bloquear si ya informados
        boolean lockPersonal =
                (user.getNombre() != null && !user.getNombre().trim().isEmpty()) ||
                (user.getPrimerApellido() != null && !user.getPrimerApellido().trim().isEmpty()) ||
                (user.getDni() != null && !user.getDni().trim().isEmpty()) ||
                (user.getFechaNacimiento() != null && !user.getFechaNacimiento().trim().isEmpty());

        if (lockPersonal) {
            nameInput.setEnabled(false);
            firstSurnameInput.setEnabled(false);
            dniInput.setEnabled(false);
            birthDateInput.setEnabled(false);
            TextView title = dialogView.findViewById(R.id.dialogTitle);
            title.setText("Perfil (datos no modificables)");
        }

        birthDateInput.setOnClickListener(v -> {
            if (!birthDateInput.isEnabled()) return;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
                String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
                birthDateInput.setText(dd + "/" + mm + "/" + year);
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            String firstSurname = String.valueOf(firstSurnameInput.getText()).trim();
            String secondSurname = String.valueOf(secondSurnameInput.getText()).trim();
            String dni = String.valueOf(dniInput.getText()).trim();
            String phone = String.valueOf(phoneInput.getText()).trim();
            String birth = String.valueOf(birthDateInput.getText()).trim();

            // Validaciones: todos obligatorios excepto segundo apellido
            if (name.isEmpty() || firstSurname.isEmpty() || dni.isEmpty() || phone.isEmpty() || birth.isEmpty()) {
                ToastUtils.showCustomToast(this, "Por favor, completa todos los campos obligatorios", ToastUtils.ToastType.ERROR);
                return;
            }

            // Si es la primera vez que completa el perfil, mostrar advertencia
            if (!user.isProfileComplete()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Importante")
                        .setMessage("Los datos personales (nombre, apellidos, DNI y fecha de nacimiento) solo pueden introducirse una vez y no podrán modificarse posteriormente.")
                        .setPositiveButton("Aceptar", (d, w) -> {
                            userViewModel.updateUser(user.getUsername(), name, firstSurname, secondSurname, birth, dni, phone);
                            dialog.dismiss();
                            ToastUtils.showCustomToast(this, "Perfil actualizado", ToastUtils.ToastType.SUCCESS);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            } else {
                // Si ya está completo, guardar directamente
                userViewModel.updateUser(user.getUsername(), name, firstSurname, secondSurname, birth, dni, phone);
                dialog.dismiss();
                ToastUtils.showCustomToast(this, "Perfil actualizado", ToastUtils.ToastType.SUCCESS);
            }
        });

        dialog.show();
    }

    private void showOrganizerProfileDialog(com.us.eventum.data.models.User user) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_organizer_profile, null);
        com.google.android.material.textfield.TextInputEditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        com.google.android.material.textfield.TextInputEditText cifInput = dialogView.findViewById(R.id.cifInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        com.google.android.material.textfield.TextInputEditText addressInput = dialogView.findViewById(R.id.addressInput);

        // Pre-cargar datos actuales
        usernameInput.setText(user.getUsername());
        if (user.getDni() != null) cifInput.setText(user.getDni()); // Reutilizamos dni para CIF
        if (user.getPhone() != null) phoneInput.setText(user.getPhone());
        // addressInput se puede mapear a un nuevo campo en User si se desea en el futuro

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String username = String.valueOf(usernameInput.getText()).trim();
            String cif = String.valueOf(cifInput.getText()).trim();
            String phone = String.valueOf(phoneInput.getText()).trim();

            if (username.isEmpty()) {
                ToastUtils.showCustomToast(this, "El nombre de la empresa es obligatorio", ToastUtils.ToastType.ERROR);
                return;
            }

            // Para organizador: solo actualizamos username, dni (como CIF) y phone
            // Los demás campos se mantienen vacíos
            userViewModel.updateUser(username, "", "", "", "", cif, phone);
            dialog.dismiss();
            ToastUtils.showCustomToast(this, "Perfil actualizado", ToastUtils.ToastType.SUCCESS);
        });

        dialog.show();
    }

    private void changePassword() {
        Intent intent = new Intent(this, ChangePasswordActivity.class);
        startActivity(intent);
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
            // Usar UserViewModel para eliminar cuenta
            userViewModel.deleteAccount();
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
            // Usar UserViewModel para cerrar sesión
            userViewModel.logout();
            dialog.dismiss();
        });
        
        // Mostrar el diálogo
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        finish(); // Simple y efectivo - vuelve a la pantalla anterior
    }

    private void goToLogin() {
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 