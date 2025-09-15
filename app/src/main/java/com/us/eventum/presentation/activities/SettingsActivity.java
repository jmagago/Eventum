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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.us.eventum.R;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.SharedViewModel;
import androidx.lifecycle.ViewModelProvider;

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
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CircleImageView profileImageView;
    private CircularProgressIndicator progressIndicator;
    private Uri photoUri;
    private SharedViewModel sharedViewModel;
    
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

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar ViewModel
        sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Configurar botón de retroceso
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        // Configurar botón de información
        ImageButton infoButton = findViewById(R.id.infoButton);
        infoButton.setOnClickListener(v -> showAppInfoDialog());

        // Inicializar vistas
        initializeViews();
        setupListeners();
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

    private void loadUserData() {
        if (mAuth.getCurrentUser() != null) {
            String email = mAuth.getCurrentUser().getEmail();
            TextView userEmailTextView = findViewById(R.id.userEmailTextView);
            TextView userNameTextView = findViewById(R.id.userNameTextView);
            userEmailTextView.setText(email);
            
            db.collection("users").document(mAuth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String displayName = documentSnapshot.getString("nombre");
                    if (displayName != null && !displayName.isEmpty()) {
                        userNameTextView.setText(displayName);
                    } else {
                        userNameTextView.setText(email.substring(0, email.indexOf('@')));
                    }
                })
                .addOnFailureListener(e -> {
                    userNameTextView.setText(email.substring(0, email.indexOf('@')));
                    Log.e(TAG, "Error al obtener el nombre del usuario", e);
                });

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
        if (mAuth.getCurrentUser() == null) return;

        showProgress(true);
        String userId = mAuth.getCurrentUser().getUid();
        StorageReference profileRef = FirebaseStorage.getInstance()
            .getReference()
            .child("profile_images/" + userId + ".jpg");

        try {
            // Comprimir la imagen antes de subirla
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] data = baos.toByteArray();

            // Mostrar la imagen comprimida en el ImageView
            profileImageView.setImageBitmap(bitmap);

            // Subir la imagen comprimida
            UploadTask uploadTask = profileRef.putBytes(data);
            uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    showProgress(false);
                    // Marcar que la foto fue actualizada
                    sharedViewModel.notifyProfileImageUpdated();
                    ToastUtils.showCustomToast(SettingsActivity.this, "Foto de perfil actualizada", ToastUtils.ToastType.SUCCESS);
                    loadProfileImage();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Log.e(TAG, "Error al subir imagen", e);
                    ToastUtils.showCustomToast(SettingsActivity.this, "Error al actualizar la foto de perfil", ToastUtils.ToastType.ERROR);
                })
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Progreso de carga: " + progress + "%");
                });
        } catch (IOException e) {
            showProgress(false);
            Log.e(TAG, "Error al procesar imagen", e);
            ToastUtils.showCustomToast(this, "Error al procesar la imagen", ToastUtils.ToastType.ERROR);
        }
    }

    private void loadProfileImage() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        StorageReference profileRef = FirebaseStorage.getInstance()
            .getReference()
            .child("profile_images/" + userId + ".jpg");

        profileRef.getDownloadUrl()
            .addOnSuccessListener(uri -> {
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(profileImageView);
            })
            .addOnFailureListener(e -> {
                profileImageView.setImageResource(R.drawable.default_profile);
                Log.d(TAG, "No se encontró imagen de perfil: " + e.getMessage());
            });
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
        // TODO: Implementar edición de perfil
        ToastUtils.showCustomToast(this, "Función en desarrollo", ToastUtils.ToastType.INFO);
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
            if (mAuth.getCurrentUser() != null) {
                // Mostrar un indicador de progreso
                ToastUtils.showCustomToast(SettingsActivity.this, "Eliminando cuenta...", ToastUtils.ToastType.INFO);
                
                mAuth.getCurrentUser().delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            ToastUtils.showCustomToast(SettingsActivity.this, "Cuenta eliminada correctamente", ToastUtils.ToastType.SUCCESS);
                            goToLogin();
                        } else {
                            ToastUtils.showCustomToast(SettingsActivity.this, "Error al eliminar la cuenta", ToastUtils.ToastType.ERROR);
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