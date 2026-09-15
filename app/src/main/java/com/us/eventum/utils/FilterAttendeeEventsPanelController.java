package com.us.eventum.utils;

import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.ListPopupWindow;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FilterAttendeeEventsPanelController {

    public interface Host {
        void onAttendeeFilterApplied(@NonNull AttendeeEventFilter filter);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final Supplier<AttendeeEventFilter> currentFilterSupplier;
    private final IntSupplier currentTabSupplier;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;

    private TextInputLayout statusLayout;
    private AutoCompleteTextView statusAutoComplete;
    private ListPopupWindow statusPopup;
    private ArrayAdapter<String> statusAdapter;
    private TextInputLayout typeLayout;
    private AutoCompleteTextView typeAutoComplete;
    private ListPopupWindow typePopup;
    private TypeCheckAdapter typeAdapter;
    private final LinkedHashSet<String> selectedEventTypes = new LinkedHashSet<>();
    private TextInputEditText locationEditText;
    private TextInputEditText dateFromEditText;
    private TextInputEditText dateToEditText;
    private MaterialButton clearButton;
    private MaterialButton applyButton;

    private final List<AttendeeEventFilter.Status> statusOptions = new ArrayList<>();
    private int boundTab = AttendeeEventFilter.TAB_MY_EVENTS;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public FilterAttendeeEventsPanelController(@NonNull AppCompatActivity activity,
                                               @NonNull Host host,
                                               @NonNull Supplier<AttendeeEventFilter> currentFilterSupplier,
                                               @NonNull IntSupplier currentTabSupplier) {
        this.activity = activity;
        this.host = host;
        this.currentFilterSupplier = currentFilterSupplier;
        this.currentTabSupplier = currentTabSupplier;
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show() {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        boundTab = currentTabSupplier.getAsInt();
        setupStatusDropdown();
        loadCurrentFilter();
        sheet.dialog.show();
    }

    public void hide() {
        dismissStatusPopup();
        dismissTypePopup();
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

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_filter_attendee_events);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_search,
                R.string.filter_attendee_title,
                R.string.cd_close_filter_attendee_events);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindViews(sheet.root);
        setupEventTypeDropdown();
        setupDatePickers();
        setupListeners();
        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void resetPanelState() {
        dismissStatusPopup();
        dismissTypePopup();
        statusPopup = null;
        statusAdapter = null;
        sheet = null;
    }

    private void bindViews(@NonNull View root) {
        statusLayout = root.findViewById(R.id.filterAttendeeStatusLayout);
        statusAutoComplete = root.findViewById(R.id.filterAttendeeStatusAutoComplete);
        typeLayout = root.findViewById(R.id.filterAttendeeTypeLayout);
        typeAutoComplete = root.findViewById(R.id.filterAttendeeTypeAutoComplete);
        locationEditText = root.findViewById(R.id.filterAttendeeLocationEditText);
        dateFromEditText = root.findViewById(R.id.filterAttendeeDateFromEditText);
        dateToEditText = root.findViewById(R.id.filterAttendeeDateToEditText);
        clearButton = root.findViewById(R.id.filterAttendeeClearButton);
        applyButton = root.findViewById(R.id.filterAttendeeApplyButton);
    }

    private void setupListeners() {
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearFields());
        }
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> applyFilter());
        }
    }

    private void setupStatusDropdown() {
        if (statusLayout == null || statusAutoComplete == null) {
            return;
        }
        boolean showStatus = boundTab != AttendeeEventFilter.TAB_HISTORY;
        statusLayout.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        if (!showStatus) {
            dismissStatusPopup();
            return;
        }

        statusOptions.clear();
        List<String> labels = new ArrayList<>();
        if (boundTab == AttendeeEventFilter.TAB_DISCOVER) {
            addStatus(AttendeeEventFilter.Status.ALL, labels);
            addStatus(AttendeeEventFilter.Status.HAS_SPOTS, labels);
            addStatus(AttendeeEventFilter.Status.FULL, labels);
        } else {
            addStatus(AttendeeEventFilter.Status.ALL, labels);
            addStatus(AttendeeEventFilter.Status.JOINED, labels);
            addStatus(AttendeeEventFilter.Status.WAITLIST, labels);
        }

        if (statusAdapter == null) {
            statusAdapter = new ArrayAdapter<>(activity, R.layout.dropdown_item, new ArrayList<>(labels));
            bindStatusPopup();
        } else {
            statusAdapter.clear();
            statusAdapter.addAll(labels);
            statusAdapter.notifyDataSetChanged();
        }
    }

    private void bindStatusPopup() {
        statusAutoComplete.setKeyListener(null);
        statusAutoComplete.setThreshold(Integer.MAX_VALUE);
        statusPopup = new ListPopupWindow(statusAutoComplete.getContext());
        statusPopup.setAnchorView(statusAutoComplete);
        statusPopup.setAdapter(statusAdapter);
        statusPopup.setModal(true);
        statusPopup.setBackgroundDrawable(
                AppCompatResources.getDrawable(activity, R.drawable.bg_dropdown_popup));
        statusPopup.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= statusOptions.size()) {
                return;
            }
            AttendeeEventFilter.Status status = statusOptions.get(position);
            statusAutoComplete.setText(AttendeeEventFilter.labelForStatus(activity, status), false);
            statusPopup.dismiss();
        });
        statusPopup.setOnDismissListener(() -> rotateStatusEndIcon(false));

        View.OnClickListener toggle = v -> {
            if (statusPopup != null && statusPopup.isShowing()) {
                statusPopup.dismiss();
            } else {
                KeyboardUtils.runAfterHidingIme(statusAutoComplete, this::showStatusPopup);
            }
        };
        statusAutoComplete.setOnClickListener(toggle);
        statusAutoComplete.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });
        statusLayout.setEndIconOnClickListener(toggle);
    }

    private void showStatusPopup() {
        if (statusPopup == null || statusAutoComplete == null) {
            return;
        }
        View anchor = statusLayout != null ? statusLayout : statusAutoComplete;
        statusPopup.setWidth(Math.max(anchor.getWidth(), statusAutoComplete.getWidth()));
        statusPopup.setHeight(ListPopupWindow.WRAP_CONTENT);
        statusPopup.show();
        rotateStatusEndIcon(true);
    }

    private void dismissStatusPopup() {
        if (statusPopup != null && statusPopup.isShowing()) {
            statusPopup.dismiss();
        }
    }

    private void rotateStatusEndIcon(boolean expanded) {
        if (statusLayout == null) {
            return;
        }
        View endIcon = statusLayout.findViewById(com.google.android.material.R.id.text_input_end_icon);
        if (endIcon != null) {
            endIcon.animate().rotation(expanded ? 180f : 0f).setDuration(150).start();
        }
    }

    private static final String[] EVENT_TYPES = {
            "Boda", "Comunión", "Reunión", "Cumpleaños", "Festival",
            "Concierto", "Graduación", "Fiesta", "Despedida",
            "Aniversario", "Conferencia", "Seminario", "Taller",
            "Exposición", "Feria", "Congreso", "Ceremonia", "Otro"
    };

    private void setupEventTypeDropdown() {
        if (typeAutoComplete == null) {
            return;
        }
        typeAutoComplete.setKeyListener(null);
        typeAutoComplete.setThreshold(Integer.MAX_VALUE);
        typeAdapter = new TypeCheckAdapter();
        typePopup = new ListPopupWindow(typeAutoComplete.getContext());
        typePopup.setAnchorView(typeAutoComplete);
        typePopup.setAdapter(typeAdapter);
        typePopup.setModal(true);
        typePopup.setBackgroundDrawable(
                AppCompatResources.getDrawable(activity, R.drawable.bg_dropdown_popup));
        typePopup.setOnItemClickListener((parent, view, position, id) -> {
            String type = typeAdapter.getItem(position);
            if (type == null) {
                return;
            }
            if (!selectedEventTypes.add(type)) {
                selectedEventTypes.remove(type);
            }
            CheckBox checkBox = view != null ? view.findViewById(R.id.dropdownCheckbox) : null;
            if (checkBox != null) {
                checkBox.setChecked(selectedEventTypes.contains(type));
            }
            refreshTypeFieldText();
        });
        typePopup.setOnDismissListener(() -> rotateTypeEndIcon(false));

        View.OnClickListener toggle = v -> {
            if (typePopup != null && typePopup.isShowing()) {
                typePopup.dismiss();
            } else {
                KeyboardUtils.runAfterHidingIme(typeAutoComplete, this::showTypePopup);
            }
        };
        typeAutoComplete.setOnClickListener(toggle);
        typeAutoComplete.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });
        if (typeLayout != null) {
            typeLayout.setEndIconOnClickListener(toggle);
        }
    }

    private void showTypePopup() {
        if (typePopup == null || typeAutoComplete == null) {
            return;
        }
        View anchor = typeLayout != null ? typeLayout : typeAutoComplete;
        typePopup.setWidth(Math.max(anchor.getWidth(), typeAutoComplete.getWidth()));
        typePopup.setHeight((int) (280 * activity.getResources().getDisplayMetrics().density));
        typePopup.show();
        rotateTypeEndIcon(true);
    }

    private void dismissTypePopup() {
        if (typePopup != null && typePopup.isShowing()) {
            typePopup.dismiss();
        }
    }

    private void rotateTypeEndIcon(boolean expanded) {
        if (typeLayout == null) {
            return;
        }
        View endIcon = typeLayout.findViewById(com.google.android.material.R.id.text_input_end_icon);
        if (endIcon != null) {
            endIcon.animate().rotation(expanded ? 180f : 0f).setDuration(150).start();
        }
    }

    private void refreshTypeFieldText() {
        if (typeAutoComplete == null) {
            return;
        }
        String summary = selectedEventTypes.isEmpty()
                ? activity.getString(R.string.filter_attendee_status_all)
                : String.join(", ", selectedEventTypes);
        typeAutoComplete.setText(summary, false);
    }

    private final class TypeCheckAdapter extends ArrayAdapter<String> {
        TypeCheckAdapter() {
            super(activity, 0, Arrays.asList(EVENT_TYPES));
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_dropdown_checkbox, parent, false);
            }
            String type = getItem(position);
            TextView label = convertView.findViewById(R.id.dropdownCheckboxLabel);
            CheckBox checkBox = convertView.findViewById(R.id.dropdownCheckbox);
            if (label != null) {
                label.setText(type);
            }
            if (checkBox != null) {
                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(type != null && selectedEventTypes.contains(type));
            }
            return convertView;
        }
    }

    private void addStatus(AttendeeEventFilter.Status status, List<String> labels) {
        statusOptions.add(status);
        labels.add(AttendeeEventFilter.labelForStatus(activity, status));
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
        AttendeeEventFilter current = currentFilterSupplier.get();
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
        AttendeeEventFilter filter = currentFilterSupplier.get();
        selectedEventTypes.clear();
        if (filter.getEventTypes() != null) {
            selectedEventTypes.addAll(filter.getEventTypes());
        }
        if (typeAdapter != null) {
            typeAdapter.notifyDataSetChanged();
        }
        refreshTypeFieldText();
        dismissTypePopup();
        if (locationEditText != null) {
            locationEditText.setText(filter.getLocation() != null ? filter.getLocation() : "");
        }
        if (dateFromEditText != null) {
            dateFromEditText.setText(filter.getDateFrom() != null
                    ? dateFormat.format(filter.getDateFrom()) : "");
        }
        if (dateToEditText != null) {
            dateToEditText.setText(filter.getDateTo() != null
                    ? dateFormat.format(filter.getDateTo()) : "");
        }
        if (statusAutoComplete != null && statusLayout != null
                && statusLayout.getVisibility() == View.VISIBLE) {
            statusAutoComplete.setText(
                    AttendeeEventFilter.labelForStatus(activity, filter.statusForTab(boundTab)),
                    false);
        }
    }

    private void clearFields() {
        selectedEventTypes.clear();
        if (typeAdapter != null) {
            typeAdapter.notifyDataSetChanged();
        }
        refreshTypeFieldText();
        if (locationEditText != null) {
            locationEditText.setText("");
        }
        if (dateFromEditText != null) {
            dateFromEditText.setText("");
        }
        if (dateToEditText != null) {
            dateToEditText.setText("");
        }
        if (statusAutoComplete != null && !statusOptions.isEmpty()) {
            statusAutoComplete.setText(
                    AttendeeEventFilter.labelForStatus(activity, AttendeeEventFilter.Status.ALL),
                    false);
        }
    }

    private void applyFilter() {
        String location = textOf(locationEditText);
        String dateFromStr = textOf(dateFromEditText);
        String dateToStr = textOf(dateToEditText);

        AttendeeEventFilter filter = currentFilterSupplier.get();
        filter.setEventTypes(new ArrayList<>(selectedEventTypes));
        filter.setLocation(location.isEmpty() ? null : location);
        try {
            filter.setDateFrom(dateFromStr.isEmpty() ? null : dateFormat.parse(dateFromStr));
            filter.setDateTo(dateToStr.isEmpty() ? null : dateFormat.parse(dateToStr));
        } catch (ParseException e) {
            showSheetToast(activity.getString(R.string.age_birth_date_invalid), ToastUtils.ToastType.ERROR);
            return;
        }
        filter.setStatusForTab(boundTab, selectedStatus());
        host.onAttendeeFilterApplied(filter);
        hide();
    }

    @NonNull
    private AttendeeEventFilter.Status selectedStatus() {
        if (statusAutoComplete == null || statusOptions.isEmpty()) {
            return AttendeeEventFilter.Status.ALL;
        }
        String selected = String.valueOf(statusAutoComplete.getText()).trim();
        for (AttendeeEventFilter.Status status : statusOptions) {
            if (AttendeeEventFilter.labelForStatus(activity, status).equals(selected)) {
                return status;
            }
        }
        return AttendeeEventFilter.Status.ALL;
    }

    @NonNull
    private static String textOf(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void showSheetToast(@NonNull String message, @NonNull ToastUtils.ToastType type) {
        View anchor = EventumBottomSheetHelper.toastAnchor(
                sheet != null ? sheet.root : null, activity);
        ToastUtils.showPanelToast(anchor, message, type);
    }
}
