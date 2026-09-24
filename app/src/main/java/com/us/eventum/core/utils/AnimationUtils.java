package com.us.eventum.core.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.google.android.material.textfield.TextInputLayout;

public class AnimationUtils {
    public static void shakeError(TextInputLayout layout) {
        View view = layout.getEditText();
        if (view != null) {
            ObjectAnimator shakeX = ObjectAnimator.ofFloat(view, "translationX", 0f, 10f, -10f, 10f, -10f, 0f);
            shakeX.setDuration(500);
            shakeX.setInterpolator(new AccelerateDecelerateInterpolator());
            shakeX.start();
        }
    }

    public static void showErrorWithAnimation(TextInputLayout layout, String errorMessage) {
        layout.setError(errorMessage);
        layout.setErrorEnabled(true);
        shakeError(layout);
    }

    public static void clearErrorWithAnimation(TextInputLayout layout) {
        layout.setError(null);
    }
} 