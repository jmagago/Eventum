package com.us.eventum.core.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.us.eventum.R;

import java.util.function.Supplier;

public final class SearchAttendeePanelController {

    public interface Host {
        void onSearchApplied(@NonNull AttendeeSearchFilter filter);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final Supplier<AttendeeSearchFilter> currentFilterSupplier;
    private final Supplier<Boolean> isSearchActiveSupplier;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;

    private TextInputEditText dniEditText;
    private TextInputEditText nameEditText;
    private TextInputEditText firstLastNameEditText;
    private TextInputEditText secondLastNameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText phoneEditText;
    private MaterialButton clearButton;
    private MaterialButton searchButton;

    public SearchAttendeePanelController(@NonNull AppCompatActivity activity,
                                         @NonNull Host host,
                                         @NonNull Supplier<AttendeeSearchFilter> currentFilterSupplier,
                                         @NonNull Supplier<Boolean> isSearchActiveSupplier) {
        this.activity = activity;
        this.host = host;
        this.currentFilterSupplier = currentFilterSupplier;
        this.isSearchActiveSupplier = isSearchActiveSupplier;
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show() {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        loadCurrentFilter();
        EventumBottomSheetHelper.configureExpandedPanel(
                activity,
                sheet.dialog,
                sheet.root,
                R.id.searchAttendeeScrollView,
                R.id.searchAttendeeFooter);
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

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_search_attendee);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_search,
                R.string.search_attendee_title,
                R.string.cd_close_search_attendee);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindViews(sheet.root);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearFields());
        }
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> applySearch());
        }
        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void resetPanelState() {
        sheet = null;
    }

    private void bindViews(@NonNull View root) {
        dniEditText = root.findViewById(R.id.searchAttendeeDniEditText);
        nameEditText = root.findViewById(R.id.searchAttendeeNameEditText);
        firstLastNameEditText = root.findViewById(R.id.searchAttendeeFirstLastNameEditText);
        secondLastNameEditText = root.findViewById(R.id.searchAttendeeSecondLastNameEditText);
        emailEditText = root.findViewById(R.id.searchAttendeeEmailEditText);
        phoneEditText = root.findViewById(R.id.searchAttendeePhoneEditText);
        clearButton = root.findViewById(R.id.searchAttendeeClearButton);
        searchButton = root.findViewById(R.id.searchAttendeeSearchButton);
    }

    private void loadCurrentFilter() {
        if (!Boolean.TRUE.equals(isSearchActiveSupplier.get())) {
            clearFields();
            return;
        }
        AttendeeSearchFilter filter = currentFilterSupplier.get();
        if (dniEditText != null) {
            dniEditText.setText(filter.getDni() != null ? filter.getDni() : "");
        }
        if (nameEditText != null) {
            nameEditText.setText(filter.getName() != null ? filter.getName() : "");
        }
        if (firstLastNameEditText != null) {
            firstLastNameEditText.setText(filter.getFirstLastName() != null ? filter.getFirstLastName() : "");
        }
        if (secondLastNameEditText != null) {
            secondLastNameEditText.setText(filter.getSecondLastName() != null ? filter.getSecondLastName() : "");
        }
        if (emailEditText != null) {
            emailEditText.setText(filter.getEmail() != null ? filter.getEmail() : "");
        }
        if (phoneEditText != null) {
            phoneEditText.setText(filter.getPhone() != null ? filter.getPhone() : "");
        }
    }

    private void clearFields() {
        if (dniEditText != null) {
            dniEditText.setText("");
        }
        if (nameEditText != null) {
            nameEditText.setText("");
        }
        if (firstLastNameEditText != null) {
            firstLastNameEditText.setText("");
        }
        if (secondLastNameEditText != null) {
            secondLastNameEditText.setText("");
        }
        if (emailEditText != null) {
            emailEditText.setText("");
        }
        if (phoneEditText != null) {
            phoneEditText.setText("");
        }
    }

    private void applySearch() {
        String dni = textOf(dniEditText);
        String name = textOf(nameEditText);
        String firstLastName = textOf(firstLastNameEditText);
        String secondLastName = textOf(secondLastNameEditText);
        String email = textOf(emailEditText);
        String phone = textOf(phoneEditText);

        boolean hasValidSearch = !dni.isEmpty() || !email.isEmpty() || !phone.isEmpty()
                || (!name.isEmpty() && !firstLastName.isEmpty());

        if (!hasValidSearch) {
            View anchor = EventumBottomSheetHelper.toastAnchor(
                    sheet != null ? sheet.root : null, activity);
            ToastUtils.showPanelToast(anchor,
                    activity.getString(R.string.error_search_attendee_criteria),
                    ToastUtils.ToastType.WARNING);
            return;
        }

        AttendeeSearchFilter newFilter = new AttendeeSearchFilter();
        newFilter.setDni(dni.isEmpty() ? null : dni);
        newFilter.setName(name.isEmpty() ? null : name);
        newFilter.setFirstLastName(firstLastName.isEmpty() ? null : firstLastName);
        newFilter.setSecondLastName(secondLastName.isEmpty() ? null : secondLastName);
        newFilter.setEmail(email.isEmpty() ? null : email);
        newFilter.setPhone(phone.isEmpty() ? null : phone);

        host.onSearchApplied(newFilter);
        hide();
    }

    private static String textOf(TextInputEditText editText) {
        return editText != null && editText.getText() != null
                ? editText.getText().toString().trim()
                : "";
    }
}
