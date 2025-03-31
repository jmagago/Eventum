package com.us.eventum.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import com.google.android.material.textfield.TextInputEditText;

public class KeyboardUtils {
    public static void hideKeyboard(Activity activity) {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void showKeyboard(Activity activity, EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void setupKeyboardNavigation(Activity activity, TextInputEditText[] fields) {
        for (int i = 0; i < fields.length; i++) {
            final TextInputEditText currentField = fields[i];
            final TextInputEditText nextField = (i < fields.length - 1) ? fields[i + 1] : null;

            currentField.setOnEditorActionListener((v, actionId, event) -> {
                if (nextField != null) {
                    nextField.requestFocus();
                    showKeyboard(activity, nextField);
                    return true;
                } else {
                    hideKeyboard(activity);
                    return true;
                }
            });
        }
    }
} 