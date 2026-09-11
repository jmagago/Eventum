package com.us.eventum.utils;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class PrivateAccessCodeInputHelper {

    private PrivateAccessCodeInputHelper() {
    }

    public static void attachGenerateEndIcon(@NonNull TextInputLayout layout,
                                             @NonNull TextInputEditText input) {
        layout.setEndIconOnClickListener(v -> {
            String code = EventPrivateAccessCode.generateRandom();
            input.setText(code);
            input.setSelection(code.length());
            AnimationUtils.clearErrorWithAnimation(layout);
        });
    }
}
