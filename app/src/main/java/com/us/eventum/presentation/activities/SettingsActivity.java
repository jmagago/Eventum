package com.us.eventum.presentation.activities;

import android.Manifest;
import android.app.Dialog;
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
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.presentation.viewmodels.SharedViewModel;
import com.us.eventum.presentation.viewmodels.AuthViewModel;
import com.us.eventum.presentation.viewmodels.OrganizerViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.models.Attendee;

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
    private AuthViewModel authViewModel;
    private OrganizerViewModel organizerViewModel;
    private AttendeeViewModel attendeeViewModel;
    private String currentUserType = null; // Se determinará dinámicamente
    
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
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        organizerViewModel = new ViewModelProvider(this).get(OrganizerViewModel.class);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        
        // Inicializar repositorios en ViewModels
        authViewModel.initializeRepositories(this);
        organizerViewModel.initializeRepository(this);
        attendeeViewModel.initializeRepository(this);

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
        // Observar datos del organizador actual
        organizerViewModel.getCurrentOrganizer().observe(this, organizer -> {
            if (organizer != null) {
                currentUserType = "ORGANIZER";
                updateUserInfo(organizer.getUsername(), organizer.getEmail(), "ORGANIZER");
            }
        });

        // Observar datos del asistente actual
        attendeeViewModel.getCurrentAttendee().observe(this, attendee -> {
            if (attendee != null) {
                currentUserType = "ATTENDEE";
                updateUserInfo(attendee.getUsername(), attendee.getEmail(), "ATTENDEE");
            }
        });

        // Observar estado de carga de organizador
        organizerViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && loading && "ORGANIZER".equals(currentUserType)) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else if (loading != null && !loading && "ORGANIZER".equals(currentUserType)) {
                progressIndicator.setVisibility(View.GONE);
            }
        });

        // Observar estado de carga de asistente
        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && loading && "ATTENDEE".equals(currentUserType)) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else if (loading != null && !loading && "ATTENDEE".equals(currentUserType)) {
                progressIndicator.setVisibility(View.GONE);
            }
        });

        // Observar errores de organizador
        organizerViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && "ORGANIZER".equals(currentUserType)) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar errores de asistente
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && "ATTENDEE".equals(currentUserType)) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        // Observar logout
        authViewModel.getUserLoggedIn().observe(this, loggedIn -> {
            if (loggedIn != null && !loggedIn) {
                // Usuario cerró sesión o eliminó cuenta
                ToastUtils.showCustomToast(this, "Sesión cerrada", ToastUtils.ToastType.INFO);
                goToLogin();
            }
        });
    }

    private void loadUserData() {
        if (firebaseManager.getAuth().getCurrentUser() != null) {
            // Determinar tipo de usuario y cargar datos correspondientes
            authViewModel.determineUserType();
            
            // Observar el tipo de usuario determinado y cargar solo los datos correspondientes
            authViewModel.getUserType().observe(this, userType -> {
                if (userType != null) {
                    currentUserType = userType;
                    if ("ATTENDEE".equals(userType)) {
                        attendeeViewModel.loadCurrentAttendee();
                    } else {
                        organizerViewModel.loadCurrentOrganizer();
                    }
                }
            });
            
            loadProfileImage();
        }
    }

    private void updateUserInfo(String username, String email, String userType) {
        TextView userNameTextView = findViewById(R.id.userNameTextView);
        TextView userEmailTextView = findViewById(R.id.userEmailTextView);
        TextView userRoleTextView = findViewById(R.id.userRoleTextView);
        
        if (userNameTextView != null) {
            userNameTextView.setText(username);
        }
        if (userEmailTextView != null) {
            userEmailTextView.setText(email);
        }
        if (userRoleTextView != null) {
            boolean isAttendee = "ATTENDEE".equals(userType);
            userRoleTextView.setText(isAttendee ? R.string.role_attendee : R.string.role_organizer);
            userRoleTextView.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
            userRoleTextView.setBackgroundResource(isAttendee ? R.drawable.bg_role_badge_assistant : R.drawable.bg_role_badge_organizer);
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

            // Actualizar cache para sincronización inmediata
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

    private void loadProfileImage() {
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            return;
        }

        // Cargar directamente desde Firebase Storage (online-only)
        ProfileImageManager.loadProfileImage(this, profileImageView);
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
        // Verificar que el tipo de usuario esté determinado
        if (currentUserType == null) {
            ToastUtils.showCustomToast(this, "Cargando datos del usuario...", ToastUtils.ToastType.INFO);
            return;
        }

        // Detectar rol del usuario según el tipo actual
        if ("ATTENDEE".equals(currentUserType)) {
            // Obtener datos actuales del asistente
            Attendee attendee = attendeeViewModel.getCurrentAttendee().getValue();
            if (attendee != null) {
                showAttendeeProfileDialog(attendee);
            } else {
                // Si no hay datos, cargar y luego abrir
                attendeeViewModel.loadCurrentAttendee();
                attendeeViewModel.getCurrentAttendee().observe(this, attendeeData -> {
                    if (attendeeData != null) {
                        showAttendeeProfileDialog(attendeeData);
                    }
                });
            }
        } else if ("ORGANIZER".equals(currentUserType)) {
            // Obtener datos actuales del organizador
            Organizer organizer = organizerViewModel.getCurrentOrganizer().getValue();
            if (organizer != null) {
                showOrganizerProfileDialog(organizer);
            } else {
                // Si no hay datos, cargar y luego abrir
                organizerViewModel.loadCurrentOrganizer();
                organizerViewModel.getCurrentOrganizer().observe(this, organizerData -> {
                    if (organizerData != null) {
                        showOrganizerProfileDialog(organizerData);
                    }
                });
            }
        }
    }

    private void showAttendeeProfileDialog(Attendee attendee) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_attendee_profile, null);
        com.google.android.material.textfield.TextInputEditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        com.google.android.material.textfield.TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        com.google.android.material.textfield.TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        com.google.android.material.textfield.TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        com.google.android.material.textfield.TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);

        // Pre-cargar datos actuales
        Log.d("SettingsActivity", "Cargando datos del asistente: " + attendee.getUsername());
        if (attendee.getUsername() != null) usernameInput.setText(attendee.getUsername());
        if (attendee.getNombre() != null) nameInput.setText(attendee.getNombre());
        if (attendee.getPrimerApellido() != null) firstSurnameInput.setText(attendee.getPrimerApellido());
        if (attendee.getSegundoApellido() != null) secondSurnameInput.setText(attendee.getSegundoApellido());
        if (attendee.getDni() != null) dniInput.setText(attendee.getDni());
        if (attendee.getPhone() != null) phoneInput.setText(attendee.getPhone());
        if (attendee.getFechaNacimiento() != null) {
            // Convertir Timestamp a String para mostrar en formato legible
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            String fechaStr = sdf.format(attendee.getFechaNacimiento().toDate());
            birthDateInput.setText(fechaStr);
        }

        // Write-once: bloquear si ya informados
        boolean lockPersonal =
                (attendee.getNombre() != null && !attendee.getNombre().trim().isEmpty()) ||
                (attendee.getPrimerApellido() != null && !attendee.getPrimerApellido().trim().isEmpty()) ||
                (attendee.getSegundoApellido() != null && !attendee.getSegundoApellido().trim().isEmpty()) ||
                (attendee.getDni() != null && !attendee.getDni().trim().isEmpty()) ||
                (attendee.getFechaNacimiento() != null);

        if (lockPersonal) {
            nameInput.setEnabled(false);
            firstSurnameInput.setEnabled(false);
            secondSurnameInput.setEnabled(false);
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

        // Crear Dialog personalizado con solo MaterialCardView
        Dialog dialog = new Dialog(this);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        
        // Configurar ventana para fondo transparente y tamaño
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            String firstSurname = String.valueOf(firstSurnameInput.getText()).trim();
            String secondSurname = String.valueOf(secondSurnameInput.getText()).trim();
            String dni = String.valueOf(dniInput.getText()).trim();
            String phone = String.valueOf(phoneInput.getText()).trim();
            String birth = String.valueOf(birthDateInput.getText()).trim();

            // Validaciones: todos obligatorios
            if (name.isEmpty() || firstSurname.isEmpty() || secondSurname.isEmpty() || dni.isEmpty() || phone.isEmpty() || birth.isEmpty()) {
                ToastUtils.showCustomToast(this, "Por favor, completa todos los campos obligatorios", ToastUtils.ToastType.ERROR);
                return;
            }

            // Si es la primera vez que completa el perfil, mostrar advertencia
            if (!attendee.isProfileComplete()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Importante")
                        .setMessage("Los datos personales (nombre, apellidos, DNI y fecha de nacimiento) no se podrán modificar posteriormente.")
                        .setPositiveButton("Aceptar", (d, w) -> {
                            attendeeViewModel.updateAttendee(attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                            dialog.dismiss();
                            ToastUtils.showCustomToast(this, "Perfil actualizado", ToastUtils.ToastType.SUCCESS);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            } else {
                // Si ya está completo, guardar directamente
                attendeeViewModel.updateAttendee(attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                dialog.dismiss();
                ToastUtils.showCustomToast(this, "Perfil actualizado", ToastUtils.ToastType.SUCCESS);
            }
        });

        dialog.show();
    }

    private void showOrganizerProfileDialog(Organizer organizer) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_organizer_profile, null);
        com.google.android.material.textfield.TextInputEditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        com.google.android.material.textfield.TextInputEditText cifInput = dialogView.findViewById(R.id.cifInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);

        // Pre-cargar datos actuales
        Log.d("SettingsActivity", "Cargando datos del organizador: " + organizer.getUsername());
        usernameInput.setText(organizer.getUsername());
        if (organizer.getCif() != null) cifInput.setText(organizer.getCif());
        if (organizer.getPhone() != null) phoneInput.setText(organizer.getPhone());

        // Crear Dialog personalizado con solo MaterialCardView
        Dialog dialog = new Dialog(this);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        
        // Configurar ventana para fondo transparente y tamaño
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String username = String.valueOf(usernameInput.getText()).trim();
            String cif = String.valueOf(cifInput.getText()).trim();
            String phone = String.valueOf(phoneInput.getText()).trim();

            if (username.isEmpty()) {
                ToastUtils.showCustomToast(this, "El nombre de la empresa es obligatorio", ToastUtils.ToastType.ERROR);
                return;
            }

            // Para organizador: solo actualizamos username, cif y phone
            organizerViewModel.updateOrganizer(username, cif, phone);
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
            // Mostrar mensaje de confirmación más detallado
            String message = "¿Estás seguro de que quieres eliminar tu cuenta?\n\n" +
                           "Esta acción eliminará:\n" +
                           "• Tu perfil y datos personales\n" +
                           "• Tu foto de perfil\n" +
                           "• Todos tus eventos (si eres organizador)\n" +
                           "• Todas tus inscripciones (si eres asistente)\n\n" +
                           "Esta acción NO se puede deshacer.";
            
            new AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage(message)
                .setPositiveButton("Eliminar cuenta", (dialog1, which) -> {
                    // Mostrar progreso
                    ToastUtils.showCustomToast(this, "Eliminando cuenta y datos relacionados...", ToastUtils.ToastType.INFO);
            if ("ATTENDEE".equals(currentUserType)) {
                attendeeViewModel.deleteAccount();
            } else if ("ORGANIZER".equals(currentUserType)) {
                organizerViewModel.deleteAccount();
            } else {
                ToastUtils.showCustomToast(this, "Error: Tipo de usuario no determinado", ToastUtils.ToastType.ERROR);
            }
            dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
            // Usar AuthViewModel para cerrar sesión
            authViewModel.logout();
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