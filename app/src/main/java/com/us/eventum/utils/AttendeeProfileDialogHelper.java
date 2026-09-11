package com.us.eventum.utils;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.annotation.NonNull;

import com.us.eventum.R;
import com.us.eventum.config.AppConfig;
import com.us.eventum.data.models.Attendee;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class AttendeeProfileDialogHelper {

    private AttendeeProfileDialogHelper() {
    }

    public static void applyTransparentWindow(Dialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    /**
     * Envuelve el contenido en un scrim a pantalla completa que bloquea toques al fondo.
     */
    @NonNull
    public static View wrapWithModalScrim(@NonNull Activity activity, @NonNull View content) {
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        overlay.addView(content, contentLp);
        return overlay;
    }

    public static void applyModalDialogWindow(@NonNull Dialog dialog) {
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    public static void bindDniValidation(TextInputEditText dniInput, TextInputLayout dniLayout) {
        dniInput.setOnFocusChangeListener(DniValidator.createDniFocusValidator(dniLayout));
    }

    public static boolean validateDniForSave(Context context, TextInputLayout dniLayout, String dni) {
        String normalized = dni.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            AnimationUtils.showErrorWithAnimation(dniLayout, context.getString(R.string.dni_required));
            return false;
        }
        if (!DniValidator.isValidDni(normalized)) {
            AnimationUtils.showErrorWithAnimation(dniLayout, context.getString(R.string.dni_invalid_letter));
            return false;
        }
        AnimationUtils.clearErrorWithAnimation(dniLayout);
        return true;
    }

    public static void setupBirthDatePicker(Activity activity, TextInputEditText birthDateInput) {
        setupBirthDatePicker(activity, birthDateInput, null);
    }

    public static void setupBirthDatePicker(Activity activity, TextInputEditText birthDateInput,
                                            TextInputLayout birthDateLayout) {
        birthDateInput.setOnClickListener(v -> {
            if (!birthDateInput.isEnabled()) {
                return;
            }
            Calendar cal = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(activity, (view, year, month, dayOfMonth) -> {
                String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
                String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
                String formatted = activity.getString(R.string.date_format_dmy, dd, mm, year);
                birthDateInput.setText(formatted);
                if (birthDateLayout != null) {
                    validateAttendeeBirthDateForSave(activity, birthDateLayout, formatted);
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(
                    AgeUtils.getMaxSelectableBirthDateMillis(AppConfig.ATTENDEE_MIN_AGE));
            dialog.show();
        });
    }

    public static boolean validateAttendeeBirthDateForSave(Context context,
                                                           TextInputLayout birthDateLayout,
                                                           String birthDateText) {
        Date parsed = AgeUtils.parseBirthDate(birthDateText);
        if (parsed == null) {
            AnimationUtils.showErrorWithAnimation(
                    birthDateLayout, context.getString(R.string.age_birth_date_invalid));
            return false;
        }
        if (!AgeUtils.isAttendeeAgeValid(parsed, new Date())) {
            AnimationUtils.showErrorWithAnimation(
                    birthDateLayout, context.getString(R.string.age_attendee_min_error));
            return false;
        }
        AnimationUtils.clearErrorWithAnimation(birthDateLayout);
        return true;
    }

    public static void populateFields(View dialogView, Attendee attendee) {
        TextInputEditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);

        if (attendee.getUsername() != null) {
            usernameInput.setText(attendee.getUsername());
        }
        if (attendee.getNombre() != null) {
            nameInput.setText(attendee.getNombre());
        }
        if (attendee.getPrimerApellido() != null) {
            firstSurnameInput.setText(attendee.getPrimerApellido());
        }
        if (attendee.getSegundoApellido() != null) {
            secondSurnameInput.setText(attendee.getSegundoApellido());
        }
        if (attendee.getDni() != null) {
            dniInput.setText(attendee.getDni());
        }
        if (attendee.getPhone() != null) {
            phoneInput.setText(attendee.getPhone());
        }
        if (attendee.getFechaNacimiento() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            birthDateInput.setText(sdf.format(attendee.getFechaNacimiento().toDate()));
        }
    }

    public static void showMessage(View dialogView, String message, ToastUtils.ToastType type) {
        View anchor = dialogView.getRootView() != null ? dialogView.getRootView() : dialogView;
        ToastUtils.showCustomToastOnAnchor(anchor, message, type);
    }

    public static void showError(View dialogView, String message) {
        showMessage(dialogView, message, ToastUtils.ToastType.ERROR);
    }

    public static void applyReadOnlyIfNeeded(View dialogView, Attendee attendee) {
        boolean lockPersonal =
                (attendee.getNombre() != null && !attendee.getNombre().trim().isEmpty())
                        || (attendee.getPrimerApellido() != null && !attendee.getPrimerApellido().trim().isEmpty())
                        || (attendee.getSegundoApellido() != null && !attendee.getSegundoApellido().trim().isEmpty())
                        || (attendee.getDni() != null && !attendee.getDni().trim().isEmpty())
                        || (attendee.getFechaNacimiento() != null);

        if (!lockPersonal) {
            return;
        }

        dialogView.findViewById(R.id.nameInput).setEnabled(false);
        dialogView.findViewById(R.id.firstSurnameInput).setEnabled(false);
        dialogView.findViewById(R.id.secondSurnameInput).setEnabled(false);
        dialogView.findViewById(R.id.dniInput).setEnabled(false);
        dialogView.findViewById(R.id.birthDateInput).setEnabled(false);

        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText(R.string.profile_readonly_title);

        TextView message = dialogView.findViewById(R.id.dialogMessage);
        message.setVisibility(View.GONE);
    }
}
