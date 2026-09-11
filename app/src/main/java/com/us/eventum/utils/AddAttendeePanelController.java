package com.us.eventum.utils;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.R;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class AddAttendeePanelController {

    private final AppCompatActivity activity;
    private final AttendeeViewModel attendeeViewModel;
    private final Supplier<Event> eventSupplier;
    private final Supplier<List<Attendee>> allAttendeesSupplier;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;
    private CircularProgressIndicator progressBar;
    private MaterialButton submitButton;

    private TextInputEditText nameEditText;
    private TextInputEditText firstLastNameEditText;
    private TextInputEditText secondLastNameEditText;
    private TextInputEditText dniEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText birthDateEditText;
    private TextInputLayout dniLayout;
    private TextInputLayout phoneLayout;
    private TextInputLayout birthDateLayout;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public AddAttendeePanelController(@NonNull AppCompatActivity activity,
                                      @NonNull AttendeeViewModel attendeeViewModel,
                                      @NonNull Supplier<Event> eventSupplier,
                                      @NonNull Supplier<List<Attendee>> allAttendeesSupplier) {
        this.activity = activity;
        this.attendeeViewModel = attendeeViewModel;
        this.eventSupplier = eventSupplier;
        this.allAttendeesSupplier = allAttendeesSupplier;
        setupObservers(activity);
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show() {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        resetForm();
        EventumBottomSheetHelper.configureExpandedPanel(
                activity,
                sheet.dialog,
                sheet.root,
                R.id.addAttendeeScrollView,
                R.id.addAttendeeFooter);
        sheet.dialog.show();
    }

    public void hide() {
        if (sheet != null && sheet.dialog.isShowing()) {
            sheet.dialog.dismiss();
        } else {
            resetPanelState();
        }
    }

    private void ensureDialog() {
        if (sheet != null) {
            return;
        }

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_add_attendee);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_person,
                R.string.add_attendee_title,
                R.string.cd_close_add_attendee);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindFormViews(sheet.root);
        setupValidators();

        if (submitButton != null) {
            submitButton.setOnClickListener(v -> submitAttendee());
        }
        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void resetPanelState() {
        sheet = null;
    }

    private void bindFormViews(@NonNull View root) {
        progressBar = root.findViewById(R.id.addAttendeeProgress);
        submitButton = root.findViewById(R.id.addAttendeeSubmitButton);
        nameEditText = root.findViewById(R.id.addAttendeeNameEditText);
        firstLastNameEditText = root.findViewById(R.id.addAttendeeFirstLastNameEditText);
        secondLastNameEditText = root.findViewById(R.id.addAttendeeSecondLastNameEditText);
        dniEditText = root.findViewById(R.id.addAttendeeDniEditText);
        emailEditText = root.findViewById(R.id.addAttendeeEmailEditText);
        phoneEditText = root.findViewById(R.id.addAttendeePhoneEditText);
        birthDateEditText = root.findViewById(R.id.addAttendeeBirthDateEditText);
        dniLayout = root.findViewById(R.id.addAttendeeDniLayout);
        phoneLayout = root.findViewById(R.id.addAttendeePhoneLayout);
        birthDateLayout = root.findViewById(R.id.addAttendeeBirthDateLayout);
    }

    private void resetForm() {
        if (nameEditText != null) {
            nameEditText.setText("");
        }
        if (firstLastNameEditText != null) {
            firstLastNameEditText.setText("");
        }
        if (secondLastNameEditText != null) {
            secondLastNameEditText.setText("");
        }
        if (dniEditText != null) {
            dniEditText.setText("");
        }
        if (emailEditText != null) {
            emailEditText.setText("");
        }
        if (phoneEditText != null) {
            phoneEditText.setText("");
        }
        if (birthDateEditText != null) {
            birthDateEditText.setText("");
        }
        if (dniLayout != null) {
            dniLayout.setError(null);
        }
        if (phoneLayout != null) {
            phoneLayout.setError(null);
        }
        if (birthDateLayout != null) {
            birthDateLayout.setError(null);
        }
    }

    private void setupValidators() {
        if (dniEditText != null && dniLayout != null) {
            dniEditText.setOnFocusChangeListener(DniValidator.createDniFocusValidator(dniLayout));
        }

        if (phoneEditText != null && phoneLayout != null) {
            phoneEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String phoneText = phoneEditText.getText().toString().trim();
                    String cleanPhone = phoneText.replaceAll("[^0-9]", "");
                    if (!cleanPhone.equals(phoneText)) {
                        phoneEditText.setText(cleanPhone);
                    }
                    if (!cleanPhone.isEmpty()) {
                        if (cleanPhone.length() == 9) {
                            if (cleanPhone.matches("^[6-9]\\d{8}$")) {
                                phoneLayout.setError(null);
                            } else {
                                phoneLayout.setError("Debe empezar por 6, 7, 8 o 9");
                            }
                        } else {
                            phoneLayout.setError("Debe tener 9 dígitos");
                        }
                    } else {
                        phoneLayout.setError(null);
                    }
                }
            });
        }

        if (birthDateEditText != null && birthDateLayout != null) {
            birthDateEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    validateBirthDateText(s.toString().trim());
                }
            });

            birthDateEditText.setOnClickListener(v -> openBirthDatePicker());
        }
    }

    private void validateBirthDateText(String dateText) {
        if (birthDateLayout == null) {
            return;
        }
        if (!dateText.isEmpty() && dateText.matches("\\d{2}/\\d{2}/\\d{4}")) {
            try {
                Date parsed = dateFormat.parse(dateText);
                if (parsed != null) {
                    if (!AgeUtils.isAttendeeAgeValid(parsed, new Date())) {
                        birthDateLayout.setError(activity.getString(R.string.age_attendee_min_error));
                    } else {
                        birthDateLayout.setError(null);
                    }
                }
            } catch (ParseException e) {
                birthDateLayout.setError("Formato inválido (dd/MM/yyyy)");
            }
        } else if (!dateText.isEmpty()) {
            birthDateLayout.setError("Formato: dd/MM/yyyy");
        } else {
            birthDateLayout.setError(null);
        }
    }

    private void openBirthDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                activity,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    if (birthDateEditText != null) {
                        birthDateEditText.setText(dateFormat.format(calendar.getTime()));
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMaxDate(
                AgeUtils.getMaxSelectableBirthDateMillis(
                        com.us.eventum.config.AppConfig.ATTENDEE_MIN_AGE));
        datePickerDialog.show();
    }

    private void setupObservers(@NonNull LifecycleOwner owner) {
        attendeeViewModel.getIsLoading().observe(owner, loading -> {
            if (loading == null || progressBar == null || submitButton == null) {
                return;
            }
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            submitButton.setEnabled(!loading);
        });

        attendeeViewModel.getAttendeeAdded().observe(owner, added -> {
            if (added != null && added && isVisible()) {
                hide();
            }
        });
    }

    private void submitAttendee() {
        Event event = eventSupplier.get();
        if (event == null || event.getId() == null) {
            return;
        }

        String name = textOf(nameEditText);
        String firstLastName = textOf(firstLastNameEditText);
        String secondLastName = textOf(secondLastNameEditText);
        String dni = textOf(dniEditText).toUpperCase(Locale.ROOT);
        String email = textOf(emailEditText);
        String phone = textOf(phoneEditText);
        String birthDate = textOf(birthDateEditText);

        if (name.isEmpty() || firstLastName.isEmpty() || dni.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            ToastUtils.showCustomToast(activity,
                    "Por favor, completa los campos obligatorios (nombre, primer apellido, DNI, email y teléfono)",
                    ToastUtils.ToastType.INFO);
            return;
        }

        if (!DniValidator.isValidDni(dni)) {
            ToastUtils.showCustomToast(activity, "El DNI no es válido", ToastUtils.ToastType.INFO);
            return;
        }

        List<Attendee> allAttendees = allAttendeesSupplier.get();
        boolean dniExists = allAttendees != null && allAttendees.stream()
                .anyMatch(attendee -> attendee.getDni() != null && attendee.getDni().equalsIgnoreCase(dni));

        if (dniExists) {
            ToastUtils.showCustomToast(activity,
                    "Ya existe un asistente con este DNI en la lista",
                    ToastUtils.ToastType.WARNING);
            return;
        }

        String lastName = firstLastName;
        if (!secondLastName.isEmpty()) {
            lastName += " " + secondLastName;
        }

        if (!birthDate.isEmpty()) {
            try {
                Date parsed = dateFormat.parse(birthDate);
                if (parsed != null && !AgeUtils.isAttendeeAgeValid(parsed, new Date())) {
                    ToastUtils.showCustomToast(activity,
                            activity.getString(R.string.age_attendee_min_error),
                            ToastUtils.ToastType.INFO);
                    return;
                }
            } catch (ParseException e) {
                ToastUtils.showCustomToast(activity,
                        "Formato de fecha inválido (usa dd/MM/yyyy)",
                        ToastUtils.ToastType.INFO);
                return;
            }
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        attendeeViewModel.addAttendee(event.getId(), uid, name, lastName, dni, email, phone,
                birthDate, event.getRequiresParentalAuth());
    }

    private static String textOf(@Nullable TextInputEditText editText) {
        return editText != null && editText.getText() != null
                ? editText.getText().toString().trim()
                : "";
    }

}
