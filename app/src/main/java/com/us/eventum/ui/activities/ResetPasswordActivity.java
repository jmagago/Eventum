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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;
import com.us.eventum.data.repositories.FirebaseManager;
import com.us.eventum.ui.viewmodels.AuthViewModel;
import com.us.eventum.core.utils.ToastUtils;
import com.us.eventum.core.utils.WindowInsetsHelper;

public class ResetPasswordActivity extends AppCompatActivity {

    private TextInputEditText emailEditText;
    private TextInputLayout emailLayout;
    private MaterialButton resetPasswordButton;
    private MaterialButton backToLoginButton;
    private View progressBar;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_reset_password);

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

        View root = findViewById(R.id.resetPasswordRoot);
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

    private void startAnimations() {
        View[] views = {
                findViewById(R.id.logoImageView),
                findViewById(R.id.titleTextView),
                findViewById(R.id.headerSloganTextView),
                findViewById(R.id.contentCard),
                backToLoginButton
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
                showProgress(loading);
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
            }
        });

        authViewModel.getPasswordResetSent().observe(this, sent -> {
            if (sent != null && sent) {
                ToastUtils.showCustomToast(this, getString(R.string.toast_reset_email_sent), ToastUtils.ToastType.SUCCESS);
                finish();
            }
        });
    }

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();

        if (!validateEmail(email)) {
            emailLayout.setError(getString(R.string.error_email_invalid));
            return;
        }

        emailLayout.setError(null);
        authViewModel.sendPasswordResetEmail(email);
    }

    private boolean validateEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        resetPasswordButton.setEnabled(!show);
        backToLoginButton.setEnabled(!show);
    }
}
