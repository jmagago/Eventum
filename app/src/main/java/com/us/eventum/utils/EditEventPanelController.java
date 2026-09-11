package com.us.eventum.utils;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Consumer;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.viewmodels.EventViewModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public final class EditEventPanelController {

    public interface Host {
        void launchImagePicker(@NonNull Consumer<Uri> handler);
    }

    public interface SaveListener {
        void onEventSaved(@NonNull Event event, @Nullable Uri newImageUri);

        default void onEventImageUploadComplete(@NonNull String eventId) {
        }

        int getMinParticipantsAllowed();
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final EventViewModel eventViewModel;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;
    private CircularProgressIndicator progressBar;
    private MaterialButton saveButton;

    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText locationInput;
    private TextInputEditText dateInput;
    private TextInputEditText timeInput;
    private TextInputEditText maxParticipantsInput;
    private TextInputLayout titleLayout;
    private TextInputLayout locationLayout;
    private TextInputLayout maxParticipantsLayout;
    private TextInputLayout dateLayout;
    private TextInputLayout timeLayout;
    private MaterialCheckBox eventoPrivadoCheckBox;
    private MaterialCheckBox requiresParentalAuthCheckBox;
    private TextInputLayout privateAccessCodeLayout;
    private TextInputEditText privateAccessCodeInput;
    private CircleImageView eventFormImageView;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private Event currentEvent;
    private SaveListener saveListener;
    private Calendar calendar;
    private Uri pendingImageUri;

    private String initialTitle;
    private String initialDescription;
    private String initialLocation;
    private String initialDate;
    private String initialTime;
    private int initialMaxParticipants;
    private boolean initialPrivateEvent;
    private boolean initialRequiresParentalAuth;
    private String initialPrivateAccessCode;

    public EditEventPanelController(@NonNull AppCompatActivity activity,
                                    @NonNull Host host,
                                    @NonNull EventViewModel eventViewModel) {
        this.activity = activity;
        this.host = host;
        this.eventViewModel = eventViewModel;
        setupObservers(activity);
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show(@NonNull Event event, @NonNull SaveListener listener) {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        currentEvent = event;
        saveListener = listener;
        pendingImageUri = null;
        populateForm(event);
        EventumBottomSheetHelper.configureExpandedPanel(
                activity,
                sheet.dialog,
                sheet.root,
                R.id.editEventScrollView,
                R.id.editEventFooter);
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

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_edit_event);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_edit,
                R.string.menu_edit_event,
                R.string.cd_close_edit_event);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindFormViews(sheet.root);
        setupPickers();
        setupChangeListeners();
        setupPrivateEventFields();

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveChanges());
        }
        if (eventFormImageView != null) {
            eventFormImageView.setOnClickListener(v -> {
                KeyboardUtils.hide(v);
                host.launchImagePicker(uri -> {
                    pendingImageUri = uri;
                    EventImageManager.loadLocalPreview(activity, eventFormImageView, uri);
                    checkChanges();
                });
            });
        }

        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void bindFormViews(@NonNull View root) {
        progressBar = root.findViewById(R.id.editEventProgress);
        saveButton = root.findViewById(R.id.saveEventButton);
        titleInput = root.findViewById(R.id.titleInput);
        descriptionInput = root.findViewById(R.id.descriptionInput);
        locationInput = root.findViewById(R.id.locationInput);
        dateInput = root.findViewById(R.id.dateInput);
        timeInput = root.findViewById(R.id.timeInput);
        maxParticipantsInput = root.findViewById(R.id.maxParticipantsInput);
        titleLayout = root.findViewById(R.id.titleLayout);
        locationLayout = root.findViewById(R.id.locationLayout);
        maxParticipantsLayout = root.findViewById(R.id.maxParticipantsLayout);
        dateLayout = root.findViewById(R.id.dateLayout);
        timeLayout = root.findViewById(R.id.timeLayout);
        eventoPrivadoCheckBox = root.findViewById(R.id.eventoPrivadoCheckBox);
        requiresParentalAuthCheckBox = root.findViewById(R.id.editRequiresParentalAuthCheckBox);
        privateAccessCodeLayout = root.findViewById(R.id.privateAccessCodeLayout);
        privateAccessCodeInput = root.findViewById(R.id.privateAccessCodeInput);
        if (privateAccessCodeLayout != null && privateAccessCodeInput != null) {
            PrivateAccessCodeInputHelper.attachGenerateEndIcon(privateAccessCodeLayout, privateAccessCodeInput);
        }
        eventFormImageView = root.findViewById(R.id.eventFormImageView);
    }

    private void populateForm(@NonNull Event event) {
        calendar = Calendar.getInstance();
        calendar.setTime(event.getDate());

        initialTitle = event.getTitle();
        initialDescription = event.getDescription() != null ? event.getDescription() : "";
        initialLocation = event.getLocation();
        initialDate = dateFormat.format(event.getDate());
        initialTime = timeFormat.format(event.getDate());
        initialMaxParticipants = event.getMaxParticipants();
        initialPrivateEvent = event.getPrivateEvent();
        initialRequiresParentalAuth = event.getRequiresParentalAuth();
        initialPrivateAccessCode = EventPrivateAccessCode.normalize(event.getPrivateAccessCode());

        if (sheet != null) {
            EventumBottomSheetHelper.setSubtitle(sheet.root, event.getTitle());
        }
        if (titleInput != null) {
            titleInput.setText(initialTitle);
        }
        if (descriptionInput != null) {
            descriptionInput.setText(initialDescription);
        }
        if (locationInput != null) {
            locationInput.setText(initialLocation);
        }
        if (dateInput != null) {
            dateInput.setText(initialDate);
        }
        if (timeInput != null) {
            timeInput.setText(initialTime);
        }
        if (maxParticipantsInput != null) {
            maxParticipantsInput.setText(String.valueOf(initialMaxParticipants));
        }
        if (eventoPrivadoCheckBox != null) {
            eventoPrivadoCheckBox.setChecked(initialPrivateEvent);
        }
        if (requiresParentalAuthCheckBox != null) {
            requiresParentalAuthCheckBox.setChecked(initialRequiresParentalAuth);
        }
        if (privateAccessCodeInput != null) {
            privateAccessCodeInput.setText(initialPrivateAccessCode != null ? initialPrivateAccessCode : "");
        }
        updatePrivateAccessCodeVisibility();
        if (eventFormImageView != null) {
            EventImageManager.loadEventImage(activity, eventFormImageView, event.getId());
        }

        updateSaveButtonState(false);
    }

    private void setupPrivateEventFields() {
        if (eventoPrivadoCheckBox == null) {
            return;
        }
        eventoPrivadoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            KeyboardUtils.hide(buttonView);
            updatePrivateAccessCodeVisibility();
            checkChanges();
        });
    }

    private void updatePrivateAccessCodeVisibility() {
        boolean isPrivate = eventoPrivadoCheckBox != null && eventoPrivadoCheckBox.isChecked();
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setVisibility(isPrivate ? View.VISIBLE : View.INVISIBLE);
        }
        if (!isPrivate && privateAccessCodeInput != null) {
            privateAccessCodeInput.setText("");
        }
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }
    }

    private void setupPickers() {
        if (dateInput != null) {
            View.OnClickListener openDatePicker = v -> {
                DatePickerDialog datePicker = new DatePickerDialog(
                        activity,
                        (view, year, month, dayOfMonth) -> {
                            calendar.set(Calendar.YEAR, year);
                            calendar.set(Calendar.MONTH, month);
                            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            dateInput.setText(dateFormat.format(calendar.getTime()));
                            if (dateLayout != null) {
                                dateLayout.setError(null);
                            }
                            checkChanges();
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePicker.getDatePicker().setMinDate(startOfTodayMillis());
                KeyboardUtils.runAfterHidingIme(v, datePicker::show);
            };
            dateInput.setFocusable(false);
            dateInput.setFocusableInTouchMode(false);
            dateInput.setClickable(true);
            dateInput.setOnClickListener(openDatePicker);
            if (dateLayout != null) {
                dateLayout.setClickable(true);
                dateLayout.setOnClickListener(openDatePicker);
                dateLayout.setStartIconOnClickListener(openDatePicker);
            }
        }

        if (timeInput != null) {
            View.OnClickListener openTimePicker = v -> {
                TimePickerDialog timePicker = new TimePickerDialog(
                        activity,
                        (view, hourOfDay, minute) -> {
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                            calendar.set(Calendar.MINUTE, minute);
                            timeInput.setText(timeFormat.format(calendar.getTime()));
                            checkChanges();
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                );
                KeyboardUtils.runAfterHidingIme(v, timePicker::show);
            };
            timeInput.setFocusable(false);
            timeInput.setFocusableInTouchMode(false);
            timeInput.setClickable(true);
            timeInput.setCursorVisible(false);
            timeInput.setKeyListener(null);
            timeInput.setOnClickListener(openTimePicker);
            if (timeLayout != null) {
                timeLayout.setClickable(true);
                timeLayout.setFocusable(true);
                timeLayout.setOnClickListener(openTimePicker);
                timeLayout.setStartIconOnClickListener(openTimePicker);
            }
        }
    }

    private void setupChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkChanges();
            }
        };

        if (titleInput != null) {
            titleInput.addTextChangedListener(watcher);
        }
        if (descriptionInput != null) {
            descriptionInput.addTextChangedListener(watcher);
        }
        if (locationInput != null) {
            locationInput.addTextChangedListener(watcher);
        }
        if (dateInput != null) {
            dateInput.addTextChangedListener(watcher);
        }
        if (timeInput != null) {
            timeInput.addTextChangedListener(watcher);
        }
        if (maxParticipantsInput != null) {
            maxParticipantsInput.addTextChangedListener(watcher);
        }
        if (eventoPrivadoCheckBox != null) {
            eventoPrivadoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> checkChanges());
        }
        if (requiresParentalAuthCheckBox != null) {
            requiresParentalAuthCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                KeyboardUtils.hide(buttonView);
                checkChanges();
            });
        }
        if (privateAccessCodeInput != null) {
            privateAccessCodeInput.addTextChangedListener(watcher);
        }
    }

    private void checkChanges() {
        updateSaveButtonState(hasChanges());
    }

    private boolean hasChanges() {
        return pendingImageUri != null || hasDataChanges();
    }

    private boolean hasDataChanges() {
        String currentTitle = textOf(titleInput);
        String currentDescription = textOf(descriptionInput);
        String currentLocation = textOf(locationInput);
        String currentDate = textOf(dateInput);
        String currentTime = textOf(timeInput);
        String currentMaxParticipantsStr = textOf(maxParticipantsInput);
        boolean currentPrivateEvent = eventoPrivadoCheckBox != null && eventoPrivadoCheckBox.isChecked();
        boolean currentRequiresParentalAuth = requiresParentalAuthCheckBox != null
                && requiresParentalAuthCheckBox.isChecked();
        String currentAccessCode = EventPrivateAccessCode.normalize(
                privateAccessCodeInput != null ? textOf(privateAccessCodeInput) : null);

        if (!currentTitle.equals(initialTitle)
                || !currentDescription.equals(initialDescription)
                || !currentLocation.equals(initialLocation)
                || !currentDate.equals(initialDate)
                || !currentTime.equals(initialTime)
                || currentPrivateEvent != initialPrivateEvent
                || currentRequiresParentalAuth != initialRequiresParentalAuth
                || !java.util.Objects.equals(currentAccessCode, initialPrivateAccessCode)) {
            return true;
        }

        try {
            int currentMaxParticipants = Integer.parseInt(currentMaxParticipantsStr);
            return currentMaxParticipants != initialMaxParticipants;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void updateSaveButtonState(boolean enabled) {
        if (saveButton == null) {
            return;
        }
        saveButton.setEnabled(enabled);
        saveButton.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private boolean validateFields() {
        boolean isValid = true;

        String title = textOf(titleInput);
        if (title.isEmpty()) {
            if (titleLayout != null) {
                titleLayout.setError(activity.getString(R.string.error_edit_event_title_required));
            }
            isValid = false;
        } else if (titleLayout != null) {
            titleLayout.setError(null);
        }

        String location = textOf(locationInput);
        if (location.isEmpty()) {
            if (locationLayout != null) {
                locationLayout.setError(activity.getString(R.string.error_edit_event_location_required));
            }
            isValid = false;
        } else if (locationLayout != null) {
            locationLayout.setError(null);
        }

        String dateStr = textOf(dateInput);
        if (dateStr.isEmpty()) {
            if (dateLayout != null) {
                dateLayout.setError(activity.getString(R.string.error_edit_event_date_required));
            }
            isValid = false;
        } else if (dateLayout != null) {
            dateLayout.setError(null);
        }

        String timeStr = textOf(timeInput);
        if (timeStr.isEmpty()) {
            if (timeLayout != null) {
                timeLayout.setError(activity.getString(R.string.error_edit_event_time_required));
            }
            isValid = false;
        } else if (timeLayout != null) {
            timeLayout.setError(null);
        }

        String maxParticipantsStr = textOf(maxParticipantsInput);
        if (maxParticipantsStr.isEmpty()) {
            if (maxParticipantsLayout != null) {
                maxParticipantsLayout.setError(activity.getString(R.string.error_edit_event_max_participants_required));
            }
            isValid = false;
        } else {
            try {
                int maxParticipants = Integer.parseInt(maxParticipantsStr);
                if (maxParticipants <= 0) {
                    if (maxParticipantsLayout != null) {
                        maxParticipantsLayout.setError(
                                activity.getString(R.string.error_edit_event_max_participants_invalid));
                    }
                    isValid = false;
                } else {
                    int minAllowed = saveListener != null ? saveListener.getMinParticipantsAllowed() : 0;
                    if (maxParticipants < minAllowed) {
                        if (maxParticipantsLayout != null) {
                            maxParticipantsLayout.setError(activity.getString(
                                    R.string.error_edit_event_max_below_current, minAllowed));
                        }
                        isValid = false;
                    } else if (maxParticipantsLayout != null) {
                        maxParticipantsLayout.setError(null);
                    }
                }
            } catch (NumberFormatException e) {
                if (maxParticipantsLayout != null) {
                    maxParticipantsLayout.setError(
                            activity.getString(R.string.error_edit_event_max_participants_invalid));
                }
                isValid = false;
            }
        }

        if (!dateStr.isEmpty()) {
            try {
                Date dateOnly = dateFormat.parse(dateStr);
                Calendar selectedDay = Calendar.getInstance();
                selectedDay.setTime(dateOnly);
                selectedDay.set(Calendar.HOUR_OF_DAY, 0);
                selectedDay.set(Calendar.MINUTE, 0);
                selectedDay.set(Calendar.SECOND, 0);
                selectedDay.set(Calendar.MILLISECOND, 0);
                if (selectedDay.getTimeInMillis() < startOfTodayMillis()) {
                    if (dateLayout != null) {
                        dateLayout.setError(activity.getString(R.string.error_edit_event_date_past));
                    }
                    isValid = false;
                }
            } catch (ParseException e) {
                if (dateLayout != null) {
                    dateLayout.setError(activity.getString(R.string.error_edit_event_date_required));
                }
                isValid = false;
            }
        }

        boolean newPrivateState = eventoPrivadoCheckBox != null && eventoPrivadoCheckBox.isChecked();
        String accessCode = privateAccessCodeInput != null ? textOf(privateAccessCodeInput) : null;
        if (newPrivateState && !EventPrivateAccessCode.isValidFormat(accessCode)) {
            if (privateAccessCodeLayout != null) {
                privateAccessCodeLayout.setError(activity.getString(R.string.error_private_access_code_format));
            }
            isValid = false;
        } else if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }

        return isValid;
    }

    private void saveChanges() {
        if (currentEvent == null || saveListener == null || saveButton == null || !saveButton.isEnabled()) {
            return;
        }

        if (!validateFields()) {
            return;
        }

        String title = textOf(titleInput);
        String description = textOf(descriptionInput);
        String location = textOf(locationInput);
        String dateStr = textOf(dateInput);
        String timeStr = textOf(timeInput);
        String maxParticipantsStr = textOf(maxParticipantsInput);

        try {
            Date dateOnly = dateFormat.parse(dateStr);
            Calendar dateCalendar = Calendar.getInstance();
            dateCalendar.setTime(dateOnly);

            String[] timeParts = timeStr.split(":");
            if (timeParts.length != 2) {
                if (timeLayout != null) {
                    timeLayout.setError(activity.getString(R.string.error_edit_event_time_required));
                }
                return;
            }
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            dateCalendar.set(Calendar.HOUR_OF_DAY, hour);
            dateCalendar.set(Calendar.MINUTE, minute);
            dateCalendar.set(Calendar.SECOND, 0);
            dateCalendar.set(Calendar.MILLISECOND, 0);

            Date newDate = dateCalendar.getTime();
            int maxParticipants = Integer.parseInt(maxParticipantsStr);

            boolean newPrivateState = eventoPrivadoCheckBox != null && eventoPrivadoCheckBox.isChecked();
            boolean newRequiresParentalAuth = requiresParentalAuthCheckBox != null
                    && requiresParentalAuthCheckBox.isChecked();
            String accessCode = privateAccessCodeInput != null ? textOf(privateAccessCodeInput) : null;

            boolean dataChanged = hasDataChanges();
            boolean imageChanged = pendingImageUri != null;
            String eventId = currentEvent.getId();

            currentEvent.setTitle(title);
            currentEvent.setDescription(description);
            currentEvent.setLocation(location);
            currentEvent.setDate(newDate);
            currentEvent.setMaxParticipants(maxParticipants);
            currentEvent.setPrivateEvent(newPrivateState);
            currentEvent.setRequiresParentalAuth(newRequiresParentalAuth);
            currentEvent.setPrivateAccessCode(EventPrivateAccessCode.normalize(
                    newPrivateState ? accessCode : null));

            if (dataChanged) {
                eventViewModel.updateEvent(eventId, title, description, newDate,
                        location, maxParticipants, currentEvent.getEventType(), newPrivateState,
                        newRequiresParentalAuth, accessCode);
            }

            Uri imageToUpload = pendingImageUri;
            if (imageChanged && eventId != null) {
                EventImageManager.setPendingLocalPreview(eventId, imageToUpload);
                EventImageManager.markEventImageUpdated(eventId);
                EventImageManager.uploadEventImage(activity, imageToUpload, eventId,
                        new EventImageManager.UploadCallback() {
                            @Override
                            public void onSuccess() {
                                EventImageManager.markEventImageUpdated(eventId);
                                EventImageManager.clearPendingLocalPreview(eventId);
                                activity.runOnUiThread(() -> {
                                    if (saveListener != null) {
                                        saveListener.onEventImageUploadComplete(eventId);
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                EventImageManager.clearPendingLocalPreview(eventId);
                                activity.runOnUiThread(() ->
                                        ToastUtils.showCustomToast(activity, message, ToastUtils.ToastType.WARNING));
                            }
                        });
            }

            saveListener.onEventSaved(currentEvent, imageToUpload);
            hide();
        } catch (ParseException e) {
            if (dateLayout != null) {
                dateLayout.setError(activity.getString(R.string.error_edit_event_date_required));
            }
        } catch (NumberFormatException e) {
            if (maxParticipantsLayout != null) {
                maxParticipantsLayout.setError(
                        activity.getString(R.string.error_edit_event_max_participants_invalid));
            }
        }
    }

    private static long startOfTodayMillis() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return today.getTimeInMillis();
    }

    private void clearFormErrors() {
        if (titleLayout != null) {
            titleLayout.setError(null);
        }
        if (locationLayout != null) {
            locationLayout.setError(null);
        }
        if (maxParticipantsLayout != null) {
            maxParticipantsLayout.setError(null);
        }
        if (dateLayout != null) {
            dateLayout.setError(null);
        }
        if (timeLayout != null) {
            timeLayout.setError(null);
        }
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }
    }

    private void resetPanelState() {
        pendingImageUri = null;
        currentEvent = null;
        saveListener = null;
        sheet = null;
    }

    private void setupObservers(@NonNull LifecycleOwner owner) {
        eventViewModel.getIsLoading().observe(owner, loading -> {
            if (loading == null || progressBar == null || saveButton == null) {
                return;
            }
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (isVisible()) {
                saveButton.setEnabled(!loading && hasChanges());
            }
        });
    }

    @NonNull
    private static String textOf(@Nullable TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }
}
