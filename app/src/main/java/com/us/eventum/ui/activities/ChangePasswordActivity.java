package com.us.eventum.ui.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.WindowInsetsHelper;
import com.us.eventum.data.repositories.FirebaseManager;
import com.us.eventum.ui.viewmodels.AuthViewModel;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputLayout currentPasswordLayout;
    private TextInputLayout newPasswordLayout;
    private TextInputLayout confirmPasswordLayout;
    private MaterialButton changePasswordButton;
    private MaterialButton backButton;
    private View progressBar;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_change_password);

        setupWindowInsets();

        FirebaseManager.getInstance();
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.initializeRepositories(this);

        initializeViews();
        setupListeners();
        startAnimations();
        observeViewModel();
    }

    private void setupWindowInsets() {
        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);
        }

        View root = findViewById(R.id.changePasswordRoot);
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
        currentPasswordLayout = findViewById(R.id.currentPasswordLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        changePasswordButton.setOnClickListener(v -> validateAndChangePassword());
        backButton.setOnClickListener(v -> finish());
    }

    private void startAnimations() {
        View[] views = {
                findViewById(R.id.logoImageView),
                findViewById(R.id.titleTextView),
                findViewById(R.id.headerSloganTextView),
                findViewById(R.id.contentCard),
                backButton
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

    private void observeViewModel() {
        authViewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                showLoading(loading);
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        authViewModel.getPasswordChanged().observe(this, changed -> {
            if (changed != null && changed) {
                ToastUtils.showCustomToast(this, getString(R.string.toast_password_updated), ToastUtils.ToastType.SUCCESS);
                finish();
            }
        });
    }

    private void validateAndChangePassword() {
        clearErrors();

        String currentPassword = getTextFromInput(currentPasswordLayout);
        String newPassword = getTextFromInput(newPasswordLayout);
        String confirmPassword = getTextFromInput(confirmPasswordLayout);

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
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        changePasswordButton.setEnabled(!show);
        backButton.setEnabled(!show);
    }
}
