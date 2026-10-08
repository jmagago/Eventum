package com.us.eventum.ui.activities;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import de.hdodenhof.circleimageview.CircleImageView;
import com.us.eventum.data.repositories.FirebaseManager;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.us.eventum.core.utils.FirebaseAuthErrorHandler;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.us.eventum.R;
import com.us.eventum.core.utils.FirebaseBackendErrorHandler;
import com.us.eventum.core.utils.SecureCredentialsStore;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.ProfileImageManager;
import com.us.eventum.core.utils.WindowInsetsHelper;
import com.us.eventum.ui.viewmodels.AuthViewModel;
import com.us.eventum.ui.viewmodels.OrganizerViewModel;
import com.us.eventum.ui.viewmodels.AttendeeViewModel;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.models.Attendee;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_USER_TYPE = "extra_user_type";
    public static final String EXTRA_PROFILE_IMAGE_UPDATED_AT = "extra_profile_image_updated_at";
    public static final String USER_TYPE_ORGANIZER = "ORGANIZER";
    public static final String USER_TYPE_ATTENDEE = "ATTENDEE";

    private static final String TAG = "SettingsActivity";

    private LinearLayout btnEditProfile, btnChangePassword, btnDeleteAccount, btnLogout;
    private FirebaseManager firebaseManager;
    private CircleImageView profileImageView;
    private CircularProgressIndicator progressIndicator;
    private Uri photoUri;
    private AuthViewModel authViewModel;
    private OrganizerViewModel organizerViewModel;
    private AttendeeViewModel attendeeViewModel;
    private SecureCredentialsStore credentialsStore;
    private String currentUserType = null; // Se determinará dinámicamente
    private boolean pendingAccountDeletion;

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                launchCamera();
            } else {
                ToastUtils.showCustomToast(this,
                        getString(R.string.error_camera_permission_needed),
                        ToastUtils.ToastType.ERROR);
            }
        });

    private final ActivityResultLauncher<Intent> takePictureLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                handleImageCapture();
            }
        });

    private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickImageLauncher =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                handleImageSelection(uri);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);
        setupWindowInsets();

        firebaseManager = FirebaseManager.getInstance();
        try {
            credentialsStore = SecureCredentialsStore.create(this);
        } catch (Exception e) {
            credentialsStore = null;
            Log.w(TAG, "No se pudo inicializar almacenamiento seguro de credenciales", e);
        }

        // Inicializar ViewModels
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        organizerViewModel = new ViewModelProvider(this).get(OrganizerViewModel.class);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        
        // Inicializar repositorios en ViewModels
        authViewModel.initializeRepositories(this);
        organizerViewModel.initializeRepository(this);
        attendeeViewModel.initializeRepository(this);

        // Configurar botón de información
        android.widget.TextView infoButton = findViewById(R.id.infoButton);
        infoButton.setOnClickListener(v -> showAppInfoDialog());

        // Inicializar vistas
        initializeViews();
        setupListeners();
        startAnimations();
        observeViewModel();
        loadUserData();
    }

    private void startAnimations() {
        View[] views = {
                findViewById(R.id.settingsHeaderBar),
                findViewById(R.id.profileImageContainer),
                findViewById(R.id.settingsCard),
                findViewById(R.id.infoButton)
        };

        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view == null) {
                continue;
            }
            view.setAlpha(0f);
            view.setTranslationY(50f);

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(alphaAnimator, translationAnimator);
            animatorSet.setDuration(500);
            animatorSet.setStartDelay(i * 100L);
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.start();
        }
    }

    private void setupWindowInsets() {
        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);
        }

        View root = findViewById(R.id.settingsRoot);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int bottomInset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
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
        editProfileImageButton.setOnClickListener(v -> showImagePickerDialog());
        
        btnEditProfile.setOnClickListener(v -> editProfile());
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnDeleteAccount.setOnClickListener(v -> deleteAccount());
        btnLogout.setOnClickListener(v -> logout());

        View homeButton = findViewById(R.id.homeButton);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> goToHome());
        }
    }

    private void goToHome() {
        // Solo finish: Home abrió Settings con Activity Result; hay que devolver el result.
        // startActivity(CLEAR_TOP) impedía que Home recibiera EXTRA_PROFILE_IMAGE_UPDATED_AT.
        finish();
    }

    private void observeViewModel() {
        organizerViewModel.getCurrentOrganizer().observe(this, organizer -> {
            if (organizer != null) {
                currentUserType = USER_TYPE_ORGANIZER;
                updateUserInfo(organizer.getUsername(), organizer.getEmail(), USER_TYPE_ORGANIZER);
                updateProfileIncompleteBadge(null);
            }
        });

        attendeeViewModel.getCurrentAttendee().observe(this, attendee -> {
            if (attendee != null) {
                currentUserType = USER_TYPE_ATTENDEE;
                updateUserInfo(attendee.getUsername(), attendee.getEmail(), USER_TYPE_ATTENDEE);
                updateProfileIncompleteBadge(attendee);
                ProfileImageManager.loadProfileImage(this, profileImageView, attendee.getImageUpdatedAt());
            }
        });

        attendeeViewModel.getAttendeeLoggedIn().observe(this, loggedIn -> {
            if (pendingAccountDeletion && loggedIn != null && !loggedIn) {
                onAccountDeleted();
            }
        });

        organizerViewModel.getOrganizerLoggedIn().observe(this, loggedIn -> {
            if (pendingAccountDeletion && loggedIn != null && !loggedIn) {
                onAccountDeleted();
            }
        });

        organizerViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && loading && USER_TYPE_ORGANIZER.equals(currentUserType)) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else if (loading != null && !loading && USER_TYPE_ORGANIZER.equals(currentUserType)) {
                progressIndicator.setVisibility(View.GONE);
            }
        });

        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && loading && USER_TYPE_ATTENDEE.equals(currentUserType)) {
                progressIndicator.setVisibility(View.VISIBLE);
            } else if (loading != null && !loading && USER_TYPE_ATTENDEE.equals(currentUserType)) {
                progressIndicator.setVisibility(View.GONE);
            }
        });

        organizerViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && USER_TYPE_ORGANIZER.equals(currentUserType)) {
                if (pendingAccountDeletion) {
                    pendingAccountDeletion = false;
                }
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && USER_TYPE_ATTENDEE.equals(currentUserType)) {
                if (pendingAccountDeletion) {
                    pendingAccountDeletion = false;
                }
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });
    }

    private void loadUserData() {
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            finish();
            return;
        }
        authViewModel.determineUserType();
        authViewModel.getUserType().observe(this, userType -> {
            if (userType != null) {
                currentUserType = userType;
                if (USER_TYPE_ATTENDEE.equals(userType)) {
                    attendeeViewModel.loadCurrentAttendee();
                } else {
                    organizerViewModel.loadCurrentOrganizer();
                }
            }
        });
        loadProfileImage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (USER_TYPE_ATTENDEE.equals(currentUserType)) {
            attendeeViewModel.loadCurrentAttendee();
        }
    }

    private void updateProfileIncompleteBadge(@Nullable Attendee attendee) {
        Chip badge = findViewById(R.id.profileIncompleteBadge);
        if (badge == null) {
            return;
        }
        boolean show = attendee != null && !attendee.isProfileComplete();
        badge.setVisibility(show ? View.VISIBLE : View.GONE);
        badge.setOnClickListener(show ? v -> editProfile() : null);
    }

    private void updateUserInfo(String username, String email, String userType) {
        TextView userNameTextView = findViewById(R.id.userNameTextView);
        TextView userEmailTextView = findViewById(R.id.userEmailTextView);
        Chip userRoleChip = findViewById(R.id.userRoleChip);

        if (userNameTextView != null) {
            userNameTextView.setText(username);
        }
        if (userEmailTextView != null) {
            userEmailTextView.setText(email);
        }
        if (userRoleChip != null) {
            boolean isAttendee = USER_TYPE_ATTENDEE.equals(userType);
            userRoleChip.setText(isAttendee ? R.string.role_attendee : R.string.role_organizer);
            int chipColor = ContextCompat.getColor(this,
                    isAttendee ? R.color.colorAccent : R.color.colorPrimary);
            userRoleChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
            userRoleChip.setClickable(false);
            userRoleChip.setFocusable(false);
        }
    }

    private void showImagePickerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_profile_photo, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialogView.findViewById(R.id.profilePhotoCameraOption).setOnClickListener(v -> {
            dialog.dismiss();
            if (checkCameraPermission()) {
                launchCamera();
            }
        });
        dialogView.findViewById(R.id.profilePhotoGalleryOption).setOnClickListener(v -> {
            dialog.dismiss();
            launchGallery();
        });
        dialog.show();
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return false;
        }
        return true;
    }

    private void launchGallery() {
        pickImageLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error al crear el archivo de imagen", ex);
                ToastUtils.showCustomToast(this, getString(R.string.error_create_image_file), ToastUtils.ToastType.ERROR);
                return;
            }

            photoUri = FileProvider.getUriForFile(this,
                    "com.us.eventum.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureLauncher.launch(takePictureIntent);
        } else {
            ToastUtils.showCustomToast(this, getString(R.string.error_no_camera_app), ToastUtils.ToastType.ERROR);
        }
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

    private Bitmap decodeBitmapFromUri(Uri imageUri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException(getString(R.string.error_open_image));
            }
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                throw new IOException(getString(R.string.error_decode_image));
            }
            return bitmap;
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
            Bitmap bitmap = decodeBitmapFromUri(imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] data = baos.toByteArray();

            // Mostrar la imagen comprimida en el ImageView con circleCrop
            Glide.with(this)
                .load(bitmap)
                .circleCrop()
                .into(profileImageView);

            // Subir la imagen comprimida
            UploadTask uploadTask = profileRef.putBytes(data);
            uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    showProgress(false);
                    long version = System.currentTimeMillis();
                    if (USER_TYPE_ATTENDEE.equals(currentUserType) && attendeeViewModel != null) {
                        attendeeViewModel.persistProfileImageUpdatedAt(userId, version);
                    }
                    ProfileImageManager.loadProfileImage(SettingsActivity.this, profileImageView, version);
                    Intent result = new Intent();
                    result.putExtra(EXTRA_PROFILE_IMAGE_UPDATED_AT, version);
                    setResult(RESULT_OK, result);
                    ToastUtils.showCustomToast(SettingsActivity.this, getString(R.string.toast_profile_photo_updated), ToastUtils.ToastType.SUCCESS);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    ToastUtils.showCustomToast(SettingsActivity.this,
                            FirebaseBackendErrorHandler.getErrorMessage(
                                    SettingsActivity.this, e, R.string.backend_op_upload_profile_image),
                            ToastUtils.ToastType.ERROR);
                });
        } catch (IOException e) {
            showProgress(false);
            ToastUtils.showCustomToast(this,
                    FirebaseBackendErrorHandler.getProcessImageFailedMessage(this),
                    ToastUtils.ToastType.ERROR);
        }
    }

    private void loadProfileImage() {
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            return;
        }
        long version = 0L;
        if (USER_TYPE_ATTENDEE.equals(currentUserType) && attendeeViewModel != null) {
            Attendee attendee = attendeeViewModel.getCurrentAttendee().getValue();
            if (attendee != null) {
                version = attendee.getImageUpdatedAt();
            }
        }
        ProfileImageManager.loadProfileImage(this, profileImageView, version);
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        profileImageView.setEnabled(!show);
    }

    private void showAppInfoDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_info, null);
        TextView versionText = dialogView.findViewById(R.id.versionText);
        TextView buildText = dialogView.findViewById(R.id.buildText);
        MaterialCardView appInfoCard = dialogView.findViewById(R.id.appInfoCard);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            long versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo);

            versionText.setText(versionName);
            buildText.setText(String.valueOf(versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "Error al obtener información de versión", e);
            versionText.setText("—");
            buildText.setText("—");
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.CustomTransparentDialog)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View.OnClickListener dismissListener = v -> dialog.dismiss();
        dialogView.findViewById(R.id.closeButton).setOnClickListener(dismissListener);
        MaterialButton dismissButton = dialogView.findViewById(R.id.appInfoDismissButton);
        dismissButton.setOnClickListener(dismissListener);

        MaterialCardView emailRow = dialogView.findViewById(R.id.emailRow);
        emailRow.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + getString(R.string.app_info_email)));
            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(emailIntent);
            } else {
                ToastUtils.showCustomToast(this, getString(R.string.app_info_email),
                        ToastUtils.ToastType.INFO);
            }
        });

        dialog.show();

        if (appInfoCard != null) {
            appInfoCard.setScaleX(0.88f);
            appInfoCard.setScaleY(0.88f);
            appInfoCard.setAlpha(0f);
            appInfoCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(320)
                    .setInterpolator(new OvershootInterpolator(0.9f))
                    .start();
        }
    }

    private void editProfile() {
        // Verificar que el tipo de usuario esté determinado
        if (currentUserType == null) {
            ToastUtils.showCustomToast(this, getString(R.string.toast_loading_user_data), ToastUtils.ToastType.INFO);
            return;
        }

        // Detectar rol del usuario según el tipo actual
        if (USER_TYPE_ATTENDEE.equals(currentUserType)) {
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
        } else if (USER_TYPE_ORGANIZER.equals(currentUserType)) {
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
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_edit_attendee_profile, new android.widget.FrameLayout(this), false);
        TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);
        TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        TextInputLayout dniLayout = dialogView.findViewById(R.id.dniLayout);
        TextInputLayout birthDateLayout = dialogView.findViewById(R.id.birthDateLayout);

        com.us.eventum.core.utils.AttendeeProfileDialogHelper.populateFields(dialogView, attendee);
        com.us.eventum.core.utils.AttendeeProfileDialogHelper.applyReadOnlyIfNeeded(dialogView, attendee);
        com.us.eventum.core.utils.AttendeeProfileDialogHelper.bindDniValidation(dniInput, dniLayout);
        com.us.eventum.core.utils.AttendeeProfileDialogHelper.setupBirthDatePicker(
                this, birthDateInput, birthDateLayout);

        Dialog dialog = new Dialog(this);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        com.us.eventum.core.utils.AttendeeProfileDialogHelper.applyTransparentWindow(dialog);

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            String firstSurname = String.valueOf(firstSurnameInput.getText()).trim();
            String secondSurname = String.valueOf(secondSurnameInput.getText()).trim();
            String dni = String.valueOf(dniInput.getText()).trim().toUpperCase(java.util.Locale.ROOT);
            String phone = String.valueOf(phoneInput.getText()).trim();
            String birth = String.valueOf(birthDateInput.getText()).trim();

            if (name.isEmpty() || firstSurname.isEmpty() || secondSurname.isEmpty() || dni.isEmpty() || phone.isEmpty() || birth.isEmpty()) {
                com.us.eventum.core.utils.AttendeeProfileDialogHelper.showError(
                        dialogView, getString(R.string.profile_complete_all_required));
                return;
            }

            if (dniInput.isEnabled()
                    && !com.us.eventum.core.utils.AttendeeProfileDialogHelper.validateDniForSave(this, dniLayout, dni)) {
                return;
            }

            if (birthDateInput.isEnabled()
                    && !com.us.eventum.core.utils.AttendeeProfileDialogHelper.validateAttendeeBirthDateForSave(
                    this, birthDateLayout, birth)) {
                return;
            }

            Runnable persistProfile = () -> {
                if (!attendee.isProfileComplete()) {
                    com.us.eventum.core.utils.AttendeeProfileDialogHelper.showSaveConfirm(this, () -> {
                        attendeeViewModel.updateAttendee(attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                        dialog.dismiss();
                        ToastUtils.showCustomToast(this, getString(R.string.toast_profile_updated), ToastUtils.ToastType.SUCCESS);
                    });
                } else {
                    attendeeViewModel.updateAttendee(attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                    dialog.dismiss();
                    ToastUtils.showCustomToast(this, getString(R.string.toast_profile_updated), ToastUtils.ToastType.SUCCESS);
                }
            };

            if (dniInput.isEnabled()) {
                com.us.eventum.core.utils.AttendeeProfileDialogHelper.ensureDniUniqueThen(
                        this,
                        attendeeViewModel,
                        dniLayout,
                        dialogView.findViewById(R.id.saveButton),
                        dni,
                        attendee.getUid(),
                        persistProfile);
            } else {
                persistProfile.run();
            }
        });

        dialog.show();
    }

    private void showOrganizerProfileDialog(Organizer organizer) {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_edit_organizer_profile, new android.widget.FrameLayout(this), false);
        com.google.android.material.textfield.TextInputEditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        com.google.android.material.textfield.TextInputEditText cifInput = dialogView.findViewById(R.id.cifInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);

        // Pre-cargar datos actuales
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
                ToastUtils.showCustomToast(this, getString(R.string.error_company_name_required), ToastUtils.ToastType.ERROR);
                return;
            }

            // Para organizador: solo actualizamos username, cif y phone
            organizerViewModel.updateOrganizer(username, cif, phone);
            dialog.dismiss();
            ToastUtils.showCustomToast(this, getString(R.string.toast_profile_updated), ToastUtils.ToastType.SUCCESS);
        });

        dialog.show();
    }

    private void changePassword() {
        Intent intent = new Intent(this, ChangePasswordActivity.class);
        startActivity(intent);
    }

    private void deleteAccount() {
        if (currentUserType == null) {
            ToastUtils.showCustomToast(this, getString(R.string.toast_loading_user_data), ToastUtils.ToastType.INFO);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomTransparentDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        TextView messageTextView = dialogView.findViewById(R.id.deleteAccountMessageTextView);
        if (messageTextView != null) {
            messageTextView.setText(buildDeleteAccountMessage());
        }
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteAccountPasswordDialog();
        });

        dialog.show();
    }

    @NonNull
    private String buildDeleteAccountMessage() {
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.delete_account_dialog_intro));
        message.append("\n\n").append(getString(R.string.delete_account_dialog_list_header));
        message.append("\n• ").append(getString(R.string.delete_account_dialog_bullet_profile));
        message.append("\n• ").append(getString(R.string.delete_account_dialog_bullet_photo));
        if (USER_TYPE_ORGANIZER.equals(currentUserType)) {
            message.append("\n• ").append(getString(R.string.delete_account_dialog_bullet_events_organizer));
        } else if (USER_TYPE_ATTENDEE.equals(currentUserType)) {
            message.append("\n• ").append(getString(R.string.delete_account_dialog_bullet_registrations_attendee));
        }
        message.append("\n\n").append(getString(R.string.delete_account_dialog_footer));
        return message.toString();
    }

    private void showDeleteAccountPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete_account_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.deleteAccountPasswordLayout);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.deleteAccountPasswordInput);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomTransparentDialog)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            String password = passwordInput.getText() != null
                    ? passwordInput.getText().toString().trim()
                    : "";
            if (password.isEmpty()) {
                passwordLayout.setError(getString(R.string.hint_current_password));
                return;
            }
            passwordLayout.setError(null);

            FirebaseUser user = firebaseManager.getAuth().getCurrentUser();
            if (user == null || user.getEmail() == null) {
                ToastUtils.showCustomToast(this,
                        FirebaseAuthErrorHandler.getNotAuthenticatedMessage(this),
                        ToastUtils.ToastType.ERROR);
                return;
            }

            dialogView.findViewById(R.id.btnConfirmDelete).setEnabled(false);
            user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password))
                    .addOnCompleteListener(task -> {
                        dialogView.findViewById(R.id.btnConfirmDelete).setEnabled(true);
                        if (task.isSuccessful()) {
                            dialog.dismiss();
                            executeAccountDeletion();
                        } else {
                            passwordLayout.setError(
                                    FirebaseAuthErrorHandler.getErrorMessage(this, task.getException()));
                        }
                    });
        });

        dialog.show();
    }

    private void executeAccountDeletion() {
        if (currentUserType == null) {
            ToastUtils.showCustomToast(this, getString(R.string.error_user_type_unknown),
                    ToastUtils.ToastType.ERROR);
            return;
        }

        pendingAccountDeletion = true;
        ToastUtils.showCustomToast(this, getString(R.string.toast_deleting_account),
                ToastUtils.ToastType.INFO);

        if (USER_TYPE_ATTENDEE.equals(currentUserType)) {
            attendeeViewModel.deleteAccount();
        } else if (USER_TYPE_ORGANIZER.equals(currentUserType)) {
            organizerViewModel.deleteAccount();
        }
    }

    private void onAccountDeleted() {
        pendingAccountDeletion = false;
        clearSessionLocalData();
        ToastUtils.showCustomToast(this, getString(R.string.toast_account_deleted), ToastUtils.ToastType.SUCCESS);
        goToLogin();
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
            clearSessionLocalData();
            authViewModel.logout();
            ToastUtils.showCustomToast(this, getString(R.string.toast_session_closed), ToastUtils.ToastType.INFO);
            goToLogin();
            dialog.dismiss();
        });
        
        // Mostrar el diálogo
        dialog.show();
    }

    private void clearSessionLocalData() {
        if (credentialsStore != null) {
            credentialsStore.clearCredentials();
        }
        ProfileImageManager.clearCache();
    }

    private void goToLogin() {
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 