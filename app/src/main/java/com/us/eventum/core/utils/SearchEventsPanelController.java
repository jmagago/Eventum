package com.us.eventum.core.utils;

import android.app.DatePickerDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.us.eventum.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

public final class SearchEventsPanelController {

    public interface Host {
        void onSearchApplied(@NonNull EventSearchFilter filter);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final Supplier<EventSearchFilter> currentFilterSupplier;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;

    private TextInputEditText keywordsEditText;
    private AutoCompleteTextView eventTypeAutoComplete;
    private TextInputEditText locationEditText;
    private TextInputEditText dateFromEditText;
    private TextInputEditText dateToEditText;
    private MaterialButton clearButton;
    private MaterialButton searchButton;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public SearchEventsPanelController(@NonNull AppCompatActivity activity,
                                       @NonNull Host host,
                                       @NonNull Supplier<EventSearchFilter> currentFilterSupplier) {
        this.activity = activity;
        this.host = host;
        this.currentFilterSupplier = currentFilterSupplier;
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

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_search_events);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_search,
                R.string.search_events_title,
                R.string.cd_close_search_events);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindViews(sheet.root);
        setupEventTypeDropdown();
        setupDatePickers();
        setupListeners();
        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void resetPanelState() {
        sheet = null;
    }

    private void bindViews(@NonNull View root) {
        keywordsEditText = root.findViewById(R.id.searchEventsKeywordsEditText);
        eventTypeAutoComplete = root.findViewById(R.id.searchEventsTypeAutoComplete);
        locationEditText = root.findViewById(R.id.searchEventsLocationEditText);
        dateFromEditText = root.findViewById(R.id.searchEventsDateFromEditText);
        dateToEditText = root.findViewById(R.id.searchEventsDateToEditText);
        clearButton = root.findViewById(R.id.searchEventsClearButton);
        searchButton = root.findViewById(R.id.searchEventsSearchButton);
    }

    private void setupListeners() {
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearFields());
        }
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> applySearch());
        }
    }

    private void setupEventTypeDropdown() {
        if (eventTypeAutoComplete == null) {
            return;
        }
        String[] eventTypes = {
                "Concierto", "Graduación", "Fiesta", "Despedida",
                "Aniversario", "Conferencia", "Seminario", "Taller",
                "Exposición", "Feria", "Congreso", "Ceremonia", "Otro"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                R.layout.dropdown_item,
                eventTypes
        );
        eventTypeAutoComplete.setAdapter(adapter);
        KeyboardUtils.bindExposedDropdown(eventTypeAutoComplete);
    }

    private void setupDatePickers() {
        if (dateFromEditText != null) {
            dateFromEditText.setOnClickListener(v -> openDatePicker(dateFromEditText, true));
        }
        if (dateToEditText != null) {
            dateToEditText.setOnClickListener(v -> openDatePicker(dateToEditText, false));
        }
    }

    private void openDatePicker(TextInputEditText editText, boolean isFromDate) {
        EventSearchFilter current = currentFilterSupplier.get();
        Calendar calendar = Calendar.getInstance();
        if (isFromDate && current.getDateFrom() != null) {
            calendar.setTime(current.getDateFrom());
        } else if (!isFromDate && current.getDateTo() != null) {
            calendar.setTime(current.getDateTo());
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                activity,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth);
                    editText.setText(dateFormat.format(selected.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        KeyboardUtils.runAfterHidingIme(editText, datePickerDialog::show);
    }

    private void loadCurrentFilter() {
        EventSearchFilter filter = currentFilterSupplier.get();
        if (keywordsEditText != null) {
            keywordsEditText.setText(filter.getKeywords() != null ? filter.getKeywords() : "");
        }
        if (eventTypeAutoComplete != null) {
            eventTypeAutoComplete.setText(filter.getEventType() != null ? filter.getEventType() : "", false);
        }
        if (locationEditText != null) {
            locationEditText.setText(filter.getLocation() != null ? filter.getLocation() : "");
        }
        if (dateFromEditText != null) {
            dateFromEditText.setText(filter.getDateFrom() != null ? dateFormat.format(filter.getDateFrom()) : "");
        }
        if (dateToEditText != null) {
            dateToEditText.setText(filter.getDateTo() != null ? dateFormat.format(filter.getDateTo()) : "");
        }
    }

    private void clearFields() {
        if (keywordsEditText != null) {
            keywordsEditText.setText("");
        }
        if (eventTypeAutoComplete != null) {
            eventTypeAutoComplete.setText("", false);
        }
        if (locationEditText != null) {
            locationEditText.setText("");
        }
        if (dateFromEditText != null) {
            dateFromEditText.setText("");
        }
        if (dateToEditText != null) {
            dateToEditText.setText("");
        }
    }

    private void applySearch() {
        String keywords = keywordsEditText != null && keywordsEditText.getText() != null
                ? keywordsEditText.getText().toString().trim() : "";
        String eventType = eventTypeAutoComplete != null && eventTypeAutoComplete.getText() != null
                ? eventTypeAutoComplete.getText().toString().trim() : "";
        String location = locationEditText != null && locationEditText.getText() != null
                ? locationEditText.getText().toString().trim() : "";
        String dateFromStr = dateFromEditText != null && dateFromEditText.getText() != null
                ? dateFromEditText.getText().toString().trim() : "";
        String dateToStr = dateToEditText != null && dateToEditText.getText() != null
                ? dateToEditText.getText().toString().trim() : "";

        boolean hasValidSearch = !keywords.isEmpty() || !eventType.isEmpty() || !location.isEmpty()
                || !dateFromStr.isEmpty() || !dateToStr.isEmpty();

        if (!hasValidSearch) {
            showSheetToast("Debe introducir al menos un criterio de búsqueda", ToastUtils.ToastType.WARNING);
            return;
        }

        EventSearchFilter newFilter = new EventSearchFilter();
        newFilter.setKeywords(keywords.isEmpty() ? null : keywords);
        newFilter.setEventType(eventType.isEmpty() ? null : eventType);
        newFilter.setLocation(location.isEmpty() ? null : location);

        try {
            if (!dateFromStr.isEmpty()) {
                newFilter.setDateFrom(dateFormat.parse(dateFromStr));
            }
            if (!dateToStr.isEmpty()) {
                newFilter.setDateTo(dateFormat.parse(dateToStr));
            }
        } catch (ParseException e) {
            showSheetToast("Formato de fecha inválido", ToastUtils.ToastType.ERROR);
            return;
        }

        host.onSearchApplied(newFilter);
        hide();
    }

    private void showSheetToast(@NonNull String message, @NonNull ToastUtils.ToastType type) {
        View anchor = EventumBottomSheetHelper.toastAnchor(
                sheet != null ? sheet.root : null, activity);
        ToastUtils.showPanelToast(anchor, message, type);
    }
}
