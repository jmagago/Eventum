package com.us.eventum.ui.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;
import com.us.eventum.ui.viewmodels.AttendeeViewModel;
import com.us.eventum.ui.viewmodels.OrganizerViewModel;
import com.us.eventum.core.config.AppConfig;
import com.us.eventum.core.utils.AgeUtils;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.WindowInsetsHelper;

import android.app.DatePickerDialog;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private static final long USERNAME_CHECK_DELAY_MS = 450L;

    private TextInputEditText usernameInput, emailInput, verifyEmailInput, passwordInput, confirmPasswordInput;
    private TextInputEditText organizerBirthDateInput;
    private TextInputLayout usernameLayout, emailLayout, verifyEmailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputLayout organizerBirthDateLayout;
    private MaterialButton registerButton;
    private TextView loginLink;
    private MaterialCardView organizerCard;
    private MaterialCardView attendeeCard;
    private OrganizerViewModel organizerViewModel;
    private AttendeeViewModel attendeeViewModel;
    private String selectedRole = "ORGANIZER";
    private View progressBar;
    private final Handler usernameCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingUsernameCheck;
    private int usernameCheckSequence = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_register);

        setupWindowInsets();

        organizerViewModel = new ViewModelProvider(this).get(OrganizerViewModel.class);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);

        organizerViewModel.initializeRepository(this);
        attendeeViewModel.initializeRepository(this);

        initializeViews();
        setupListeners();
        startAnimations();
        observeViewModel();
    }

    @Override
    protected void onDestroy() {
        usernameCheckHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void setupWindowInsets() {
        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);
        }

        View root = findViewById(R.id.registerRoot);
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

    private void startAnimations() {
        View[] views = {
                findViewById(R.id.logoImageView),
                findViewById(R.id.titleTextView),
                findViewById(R.id.contentCard),
                findViewById(R.id.loginPromptLayout)
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

    private void initializeViews() {
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        verifyEmailInput = findViewById(R.id.verifyEmailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        verifyEmailLayout = findViewById(R.id.verifyEmailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        organizerBirthDateInput = findViewById(R.id.organizerBirthDateInput);
        organizerBirthDateLayout = findViewById(R.id.organizerBirthDateLayout);

        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
        organizerCard = findViewById(R.id.organizerCard);
        attendeeCard = findViewById(R.id.attendeeCard);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        usernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateUsernameField(s.toString().trim());
            }
        });

        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateEmailField(s.toString().trim());
                validateVerifyEmailField();
            }
        });

        verifyEmailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateVerifyEmailField();
            }
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validatePasswordField();
                validateConfirmPasswordField();
            }
        });

        confirmPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateConfirmPasswordField();
            }
        });

        organizerCard.setOnClickListener(v -> {
            selectedRole = "ORGANIZER";
            updateRoleSelection(true);
        });

        attendeeCard.setOnClickListener(v -> {
            selectedRole = "ATTENDEE";
            updateRoleSelection(false);
        });

        registerButton.setOnClickListener(v -> validateAndRegister(selectedRole));
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        organizerBirthDateInput.setOnClickListener(v -> showOrganizerBirthDatePicker());

        updateRoleSelection(true);
    }

    private void showOrganizerBirthDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
            String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
            organizerBirthDateInput.setText(getString(R.string.date_format_dmy, dd, mm, year));
            organizerBirthDateLayout.setError(null);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(
                AgeUtils.getMaxSelectableBirthDateMillis(AppConfig.ORGANIZER_MIN_AGE));
        dialog.show();
    }

    private void validateUsernameField(String username) {
        if (pendingUsernameCheck != null) {
            usernameCheckHandler.removeCallbacks(pendingUsernameCheck);
            pendingUsernameCheck = null;
        }

        if (username.isEmpty()) {
            usernameCheckSequence++;
            usernameLayout.setError(null);
            return;
        }

        if (username.length() < 3) {
            usernameCheckSequence++;
            usernameLayout.setError(null);
            return;
        }

        scheduleUsernameAvailabilityCheck(username);
    }

    private void scheduleUsernameAvailabilityCheck(String username) {
        final int sequence = ++usernameCheckSequence;
        pendingUsernameCheck = () -> runUsernameAvailabilityCheck(username, sequence);
        usernameCheckHandler.postDelayed(pendingUsernameCheck, USERNAME_CHECK_DELAY_MS);
    }

    private void runUsernameAvailabilityCheck(String username, int sequence) {
        organizerViewModel.checkUsernameAvailability(username, (available, error) -> {
            if (sequence != usernameCheckSequence) {
                return;
            }
            if (!username.equals(usernameInput.getText().toString().trim())) {
                return;
            }
            if (error != null) {
                usernameLayout.setError(error);
            } else if (!available) {
                usernameLayout.setError(getString(R.string.backend_error_username_in_use));
            } else {
                usernameLayout.setError(null);
            }
        });
    }

    private void verifyUsernameAvailableThenRegister(String role, String username, String email,
                                                     String password) {
        if (pendingUsernameCheck != null) {
            usernameCheckHandler.removeCallbacks(pendingUsernameCheck);
            pendingUsernameCheck = null;
        }

        showProgress(true);
        final int sequence = ++usernameCheckSequence;
        organizerViewModel.checkUsernameAvailability(username, (available, error) -> {
            if (sequence != usernameCheckSequence) {
                return;
            }
            if (!username.equals(usernameInput.getText().toString().trim())) {
                showProgress(false);
                return;
            }
            if (error != null) {
                showProgress(false);
                usernameLayout.setError(error);
                return;
            }
            if (!available) {
                showProgress(false);
                usernameLayout.setError(getString(R.string.backend_error_username_in_use));
                return;
            }
            submitRegistration(role, email, password, username);
        });
    }

    private void submitRegistration(String role, String email, String password, String username) {
        if ("ORGANIZER".equals(role)) {
            String birthDate = organizerBirthDateInput.getText().toString().trim();
            organizerViewModel.registerOrganizer(email, password, username, "", "", birthDate);
        } else {
            attendeeViewModel.registerAttendee(email, password, username, "", "", "", "", "", "");
        }
    }

    private void validateEmailField(String email) {
        if (email.isEmpty()) {
            emailLayout.setError(null);
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Email no válido");
        } else {
            emailLayout.setError(null);
        }
    }

    private void validateVerifyEmailField() {
        String verifyEmail = verifyEmailInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        if (verifyEmail.isEmpty()) {
            verifyEmailLayout.setError(null);
        } else if (!verifyEmail.equals(email)) {
            verifyEmailLayout.setError("Los emails no coinciden");
        } else {
            verifyEmailLayout.setError(null);
        }
    }

    private void validatePasswordField() {
        String password = passwordInput.getText().toString();
        if (password.isEmpty()) {
            passwordLayout.setError(null);
        } else if (password.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_min_length));
        } else {
            passwordLayout.setError(null);
        }
    }

    private void validateConfirmPasswordField() {
        String confirmPassword = confirmPasswordInput.getText().toString();
        String password = passwordInput.getText().toString();
        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.setError(null);
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Las contraseñas no coinciden");
        } else {
            confirmPasswordLayout.setError(null);
        }
    }

    private void updateRoleSelection(boolean isOrganizer) {
        if (isOrganizer) {
            organizerCard.setStrokeColor(getResources().getColor(R.color.colorAccent, getTheme()));
            organizerCard.setStrokeWidth(4);

            attendeeCard.setStrokeColor(getResources().getColor(R.color.colorBorder, getTheme()));
            attendeeCard.setStrokeWidth(2);
            organizerBirthDateLayout.setVisibility(View.VISIBLE);
        } else {
            attendeeCard.setStrokeColor(getResources().getColor(R.color.colorAccent, getTheme()));
            attendeeCard.setStrokeWidth(4);

            organizerCard.setStrokeColor(getResources().getColor(R.color.colorBorder, getTheme()));
            organizerCard.setStrokeWidth(2);
            organizerBirthDateLayout.setVisibility(View.GONE);
            organizerBirthDateLayout.setError(null);
        }
    }

    private void validateAndRegister(String role) {
        usernameLayout.setError(null);
        emailLayout.setError(null);
        verifyEmailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);
        if (organizerBirthDateLayout != null) {
            organizerBirthDateLayout.setError(null);
        }

        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String verifyEmail = verifyEmailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (TextUtils.isEmpty(username)) {
            usernameLayout.setError("El nombre de usuario es obligatorio");
            return;
        }

        if (username.length() < 3) {
            usernameLayout.setError("El nombre de usuario debe tener al menos 3 caracteres");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailLayout.setError("El email es obligatorio");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Email no válido");
            return;
        }

        if (TextUtils.isEmpty(verifyEmail)) {
            verifyEmailLayout.setError("Debe verificar el email");
            return;
        }

        if (!verifyEmail.equals(email)) {
            verifyEmailLayout.setError("Los emails no coinciden");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError(getString(R.string.error_password_required));
            return;
        }

        if (password.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_min_length));
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Las contraseñas no coinciden");
            return;
        }

        if ("ORGANIZER".equals(role)) {
            String birthDate = organizerBirthDateInput.getText().toString().trim();
            if (birthDate.isEmpty()) {
                organizerBirthDateLayout.setError(getString(R.string.age_birth_date_required_organizer));
                return;
            }
            Date parsedBirth = AgeUtils.parseBirthDate(birthDate);
            if (parsedBirth == null) {
                organizerBirthDateLayout.setError(getString(R.string.age_birth_date_invalid));
                return;
            }
            if (!AgeUtils.isOrganizerAgeValid(parsedBirth)) {
                organizerBirthDateLayout.setError(getString(R.string.age_organizer_min_error));
                return;
            }
        }

        verifyUsernameAvailableThenRegister(role, username, email, password);
    }

    private void observeViewModel() {
        organizerViewModel.getOrganizerRegistered().observe(this, registered -> {
            if (registered != null && registered) {
                onRegistrationSuccess();
            }
        });

        attendeeViewModel.getAttendeeRegistered().observe(this, registered -> {
            if (registered != null && registered) {
                onRegistrationSuccess();
            }
        });

        organizerViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && "ORGANIZER".equals(selectedRole)) {
                handleRegistrationError(error);
            }
        });

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && "ATTENDEE".equals(selectedRole)) {
                handleRegistrationError(error);
            }
        });

        organizerViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && "ORGANIZER".equals(selectedRole)) {
                showProgress(loading);
            }
        });

        attendeeViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null && "ATTENDEE".equals(selectedRole)) {
                showProgress(loading);
            }
        });
    }

    private void onRegistrationSuccess() {
        ToastUtils.showCustomToast(RegisterActivity.this,
                "Registro exitoso. Por favor, verifica tu email antes de iniciar sesión",
                ToastUtils.ToastType.SUCCESS);

        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleRegistrationError(String error) {
        ToastUtils.showCustomToast(RegisterActivity.this, error, ToastUtils.ToastType.ERROR);
        mapRegistrationErrorToField(error);
        registerButton.setEnabled(true);
        showProgress(false);
    }

    private void mapRegistrationErrorToField(String error) {
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("nombre de usuario") || lower.contains("usuario ya está")) {
            usernameLayout.setError(error);
        } else if (lower.contains("email") || lower.contains("correo")) {
            emailLayout.setError(error);
        } else if (lower.contains("contraseña")) {
            passwordLayout.setError(error);
        } else if (lower.contains("nacimiento") || lower.contains("18 años")) {
            organizerBirthDateLayout.setError(error);
        }
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        registerButton.setEnabled(!show);
    }

}
