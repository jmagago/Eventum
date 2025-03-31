package com.us.eventum.utils;

import android.text.TextWatcher;
import android.text.Editable;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

public class FieldValidator {
    public static TextWatcher createEmailValidator(TextInputEditText emailEditText, TextInputLayout emailLayout) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                if (email.isEmpty()) {
                    AnimationUtils.showErrorWithAnimation(emailLayout, "El email es requerido");
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    AnimationUtils.showErrorWithAnimation(emailLayout, "Email inválido");
                } else {
                    AnimationUtils.clearErrorWithAnimation(emailLayout);
                }
            }
        };
    }

    public static TextWatcher createNameValidator(TextInputEditText nameEditText, TextInputLayout nameLayout) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String name = s.toString().trim();
                if (name.isEmpty()) {
                    AnimationUtils.showErrorWithAnimation(nameLayout, "El nombre es requerido");
                } else if (name.length() < 2) {
                    AnimationUtils.showErrorWithAnimation(nameLayout, "El nombre debe tener al menos 2 caracteres");
                } else {
                    AnimationUtils.clearErrorWithAnimation(nameLayout);
                }
            }
        };
    }

    public static TextWatcher createLocationValidator(TextInputEditText locationEditText, TextInputLayout locationLayout) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String location = s.toString().trim();
                if (location.isEmpty()) {
                    AnimationUtils.showErrorWithAnimation(locationLayout, "El lugar es requerido");
                } else if (location.length() < 2) {
                    AnimationUtils.showErrorWithAnimation(locationLayout, "El lugar debe tener al menos 2 caracteres");
                } else {
                    AnimationUtils.clearErrorWithAnimation(locationLayout);
                }
            }
        };
    }

    public static void setupValidation(TextInputEditText editText, TextInputLayout layout, TextWatcher validator) {
        editText.addTextChangedListener(validator);
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validator.afterTextChanged(editText.getEditableText());
            }
        });
    }
} 