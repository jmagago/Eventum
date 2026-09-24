package com.us.eventum.core.utils;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

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
import com.us.eventum.data.repositories.FirebaseManager;
import com.us.eventum.ui.viewmodels.EventViewModel;
import com.us.eventum.ui.viewmodels.SharedViewModel;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public final class CreateEventPanelController {

    public interface Host {
        void launchImagePicker(@NonNull Consumer<Uri> handler);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final EventViewModel eventViewModel;
    private final SharedViewModel sharedViewModel;
    private final FirebaseManager firebaseManager;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;
    private CircularProgressIndicator progressBar;
    private MaterialButton createButton;

    private TextInputEditText nombreEventoEditText;
    private TextInputEditText fechaEventoEditText;
    private TextInputEditText horaEventoEditText;
    private TextInputEditText maxParticipantesEditText;
    private TextInputEditText lugarEventoEditText;
    private TextInputEditText descripcionEventoEditText;
    private TextInputLayout nombreEventoLayout;
    private TextInputLayout fechaEventoLayout;
    private TextInputLayout horaEventoLayout;
    private TextInputLayout maxParticipantesLayout;
    private TextInputLayout lugarEventoLayout;
    private TextInputLayout tipoEventoLayout;
    private AutoCompleteTextView tipoEventoAutoComplete;
    private MaterialCheckBox eventoPrivadoCheckBox;
    private MaterialCheckBox requiresParentalAuthCheckBox;
    private TextInputLayout privateAccessCodeLayout;
    private TextInputEditText privateAccessCodeInput;
    private CircleImageView eventFormImageView;

    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private String selectedEventType;
    private Uri selectedEventImageUri;

    public CreateEventPanelController(@NonNull AppCompatActivity activity,
                                        @NonNull Host host,
                                        @NonNull EventViewModel eventViewModel,
                                        @NonNull SharedViewModel sharedViewModel) {
        this.activity = activity;
        this.host = host;
        this.eventViewModel = eventViewModel;
        this.sharedViewModel = sharedViewModel;
        this.firebaseManager = FirebaseManager.getInstance();
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
                R.id.createEventScrollView,
                R.id.createEventFooter);
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

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_create_event);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_add,
                R.string.create_event_title,
                R.string.cd_close_create_event);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        bindFormViews(sheet.root);
        setupPrivateEventFields();
        setupEventTypeDropdown();
        setupDateAndTimePickers();

        if (createButton != null) {
            createButton.setOnClickListener(v -> {
                if (validateFields()) {
                    createEvent();
                }
            });
        }
        EventumBottomSheetHelper.bindDestroyOnDismiss(sheet.dialog, this::resetPanelState);
    }

    private void resetPanelState() {
        selectedEventImageUri = null;
        sheet = null;
    }

    private void bindFormViews(@NonNull View root) {
        progressBar = root.findViewById(R.id.createEventProgress);
        createButton = root.findViewById(R.id.crearEventoButton);
        nombreEventoEditText = root.findViewById(R.id.nombreEventoEditText);
        fechaEventoEditText = root.findViewById(R.id.fechaEventoEditText);
        horaEventoEditText = root.findViewById(R.id.horaEventoEditText);
        maxParticipantesEditText = root.findViewById(R.id.maxParticipantesEditText);
        lugarEventoEditText = root.findViewById(R.id.lugarEventoEditText);
        descripcionEventoEditText = root.findViewById(R.id.descripcionEventoEditText);
        tipoEventoAutoComplete = root.findViewById(R.id.tipoEventoAutoComplete);
        eventoPrivadoCheckBox = root.findViewById(R.id.eventoPrivadoCheckBox);
        requiresParentalAuthCheckBox = root.findViewById(R.id.createRequiresParentalAuthCheckBox);

        nombreEventoLayout = root.findViewById(R.id.nombreEventoLayout);
        fechaEventoLayout = root.findViewById(R.id.fechaEventoLayout);
        horaEventoLayout = root.findViewById(R.id.horaEventoLayout);
        maxParticipantesLayout = root.findViewById(R.id.maxParticipantesLayout);
        lugarEventoLayout = root.findViewById(R.id.lugarEventoLayout);
        tipoEventoLayout = root.findViewById(R.id.tipoEventoLayout);
        privateAccessCodeLayout = root.findViewById(R.id.privateAccessCodeLayout);
        privateAccessCodeInput = root.findViewById(R.id.privateAccessCodeInput);
        if (privateAccessCodeLayout != null && privateAccessCodeInput != null) {
            PrivateAccessCodeInputHelper.attachGenerateEndIcon(privateAccessCodeLayout, privateAccessCodeInput);
        }

        eventFormImageView = root.findViewById(R.id.eventFormImageView);
        if (eventFormImageView != null) {
            eventFormImageView.setOnClickListener(v -> {
                KeyboardUtils.hide(v);
                host.launchImagePicker(uri -> {
                        selectedEventImageUri = uri;
                        EventImageManager.loadLocalPreview(activity, eventFormImageView, uri);
                    });
            });
        }

        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        dateFormat.setCalendar(calendar);
    }

    private void resetForm() {
        selectedEventType = null;
        selectedEventImageUri = null;
        calendar = Calendar.getInstance();

        if (nombreEventoEditText != null) {
            nombreEventoEditText.setText("");
        }
        if (fechaEventoEditText != null) {
            fechaEventoEditText.setText("");
        }
        if (horaEventoEditText != null) {
            horaEventoEditText.setText("");
        }
        if (maxParticipantesEditText != null) {
            maxParticipantesEditText.setText("");
        }
        if (lugarEventoEditText != null) {
            lugarEventoEditText.setText("");
        }
        if (descripcionEventoEditText != null) {
            descripcionEventoEditText.setText("");
        }
        if (tipoEventoAutoComplete != null) {
            tipoEventoAutoComplete.setText("", false);
        }
        if (eventoPrivadoCheckBox != null) {
            eventoPrivadoCheckBox.setChecked(false);
        }
        if (requiresParentalAuthCheckBox != null) {
            requiresParentalAuthCheckBox.setChecked(false);
        }
        if (privateAccessCodeInput != null) {
            privateAccessCodeInput.setText("");
        }
        updatePrivateAccessCodeVisibility();
        if (eventFormImageView != null) {
            eventFormImageView.setImageResource(R.mipmap.ic_launcher);
        }

        clearErrors();
    }

    private void setupPrivateEventFields() {
        if (eventoPrivadoCheckBox == null) {
            return;
        }
        eventoPrivadoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            KeyboardUtils.hide(buttonView);
            updatePrivateAccessCodeVisibility();
        });
        if (requiresParentalAuthCheckBox != null) {
            requiresParentalAuthCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    KeyboardUtils.hide(buttonView));
        }
        updatePrivateAccessCodeVisibility();
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

    private void clearErrors() {
        if (nombreEventoLayout != null) {
            nombreEventoLayout.setError(null);
        }
        if (fechaEventoLayout != null) {
            fechaEventoLayout.setError(null);
        }
        if (horaEventoLayout != null) {
            horaEventoLayout.setError(null);
        }
        if (tipoEventoLayout != null) {
            tipoEventoLayout.setError(null);
        }
        if (maxParticipantesLayout != null) {
            maxParticipantesLayout.setError(null);
        }
        if (lugarEventoLayout != null) {
            lugarEventoLayout.setError(null);
        }
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }
    }

    private void setupEventTypeDropdown() {
        if (tipoEventoAutoComplete == null) {
            return;
        }
        List<String> eventTypes = Arrays.asList(
                "Boda", "Comunión", "Reunión", "Cumpleaños", "Festival",
                "Concierto", "Graduación", "Fiesta", "Despedida",
                "Aniversario", "Conferencia", "Seminario", "Taller",
                "Exposición", "Feria", "Congreso", "Ceremonia", "Otro"
        );
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                R.layout.dropdown_item,
                eventTypes
        );
        tipoEventoAutoComplete.setAdapter(adapter);
        KeyboardUtils.bindExposedDropdown(tipoEventoAutoComplete);
        tipoEventoAutoComplete.setOnItemClickListener((parent, view, position, id) ->
                selectedEventType = parent.getItemAtPosition(position).toString());
    }

    private void setupDateAndTimePickers() {
        if (fechaEventoEditText != null) {
            View.OnClickListener openDatePicker = v -> {
                DatePickerDialog datePickerDialog = new DatePickerDialog(
                        activity,
                        (view, year, month, dayOfMonth) -> {
                            calendar.set(Calendar.YEAR, year);
                            calendar.set(Calendar.MONTH, month);
                            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            fechaEventoEditText.setText(dateFormat.format(calendar.getTime()));
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
                KeyboardUtils.runAfterHidingIme(v, datePickerDialog::show);
            };
            fechaEventoEditText.setOnClickListener(openDatePicker);
            if (fechaEventoLayout != null) {
                fechaEventoLayout.setClickable(true);
                fechaEventoLayout.setOnClickListener(openDatePicker);
                fechaEventoLayout.setStartIconOnClickListener(openDatePicker);
            }
        }

        if (horaEventoEditText != null) {
            View.OnClickListener openTimePicker = v -> {
                TimePickerDialog timePickerDialog = new TimePickerDialog(
                        activity,
                        (view, selectedHour, selectedMinute) -> {
                            calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
                            calendar.set(Calendar.MINUTE, selectedMinute);
                            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            horaEventoEditText.setText(timeFormat.format(calendar.getTime()));
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                );
                timePickerDialog.setTitle("Selecciona la hora");
                KeyboardUtils.runAfterHidingIme(v, timePickerDialog::show);
            };
            horaEventoEditText.setFocusable(false);
            horaEventoEditText.setFocusableInTouchMode(false);
            horaEventoEditText.setClickable(true);
            horaEventoEditText.setOnClickListener(openTimePicker);
            if (horaEventoLayout != null) {
                horaEventoLayout.setClickable(true);
                horaEventoLayout.setFocusable(true);
                horaEventoLayout.setOnClickListener(openTimePicker);
                horaEventoLayout.setStartIconOnClickListener(openTimePicker);
            }
        }
    }

    private void setupObservers(@NonNull LifecycleOwner owner) {
        eventViewModel.getIsLoading().observe(owner, loading -> {
            if (loading == null || progressBar == null || createButton == null) {
                return;
            }
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            createButton.setEnabled(!loading);
        });

        eventViewModel.getErrorMessage().observe(owner, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(activity, error, ToastUtils.ToastType.ERROR);
            }
        });

        eventViewModel.getEventCreated().observe(owner, created -> {
            if (created != null && created) {
                String eventId = eventViewModel.getCreatedEventId().getValue();
                if (selectedEventImageUri != null && eventId != null && !eventId.isEmpty()) {
                    EventImageManager.uploadEventImage(activity, selectedEventImageUri, eventId,
                            new EventImageManager.UploadCallback() {
                                @Override
                                public void onSuccess() {
                                    finishAfterEventCreated();
                                }

                                @Override
                                public void onError(String message) {
                                    ToastUtils.showCustomToast(activity, message, ToastUtils.ToastType.WARNING);
                                    finishAfterEventCreated();
                                }
                            });
                } else {
                    finishAfterEventCreated();
                }
            }
        });
    }

    private void finishAfterEventCreated() {
        ToastUtils.showCustomToast(activity, activity.getString(R.string.toast_event_created), ToastUtils.ToastType.SUCCESS);
        eventViewModel.clearOperationStates();
        sharedViewModel.notifyEventsUpdated();
        hide();
    }

    private boolean validateFields() {
        boolean isValid = true;

        if (nombreEventoEditText == null || nombreEventoEditText.getText().toString().trim().isEmpty()) {
            if (nombreEventoLayout != null) {
                nombreEventoLayout.setError(activity.getString(R.string.error_event_name_required));
            }
            isValid = false;
        } else if (nombreEventoLayout != null) {
            nombreEventoLayout.setError(null);
        }

        if (fechaEventoEditText == null || fechaEventoEditText.getText().toString().trim().isEmpty()) {
            if (fechaEventoLayout != null) {
                fechaEventoLayout.setError(activity.getString(R.string.error_edit_event_date_required));
            }
            isValid = false;
        } else if (fechaEventoLayout != null) {
            fechaEventoLayout.setError(null);
        }

        if (horaEventoEditText == null || horaEventoEditText.getText().toString().trim().isEmpty()) {
            if (horaEventoLayout != null) {
                horaEventoLayout.setError(activity.getString(R.string.error_edit_event_time_required));
            }
            isValid = false;
        } else if (horaEventoLayout != null) {
            horaEventoLayout.setError(null);
        }

        if (selectedEventType == null || selectedEventType.isEmpty()) {
            if (tipoEventoLayout != null) {
                tipoEventoLayout.setError(activity.getString(R.string.error_event_type_required));
            }
            isValid = false;
        } else if (tipoEventoLayout != null) {
            tipoEventoLayout.setError(null);
        }

        if (maxParticipantesEditText == null || maxParticipantesEditText.getText().toString().trim().isEmpty()) {
            if (maxParticipantesLayout != null) {
                maxParticipantesLayout.setError(activity.getString(R.string.error_max_participants_required));
            }
            isValid = false;
        } else {
            try {
                int maxParticipantes = Integer.parseInt(maxParticipantesEditText.getText().toString());
                if (maxParticipantes <= 0) {
                    if (maxParticipantesLayout != null) {
                        maxParticipantesLayout.setError(activity.getString(R.string.error_number_gt_zero));
                    }
                    isValid = false;
                } else if (maxParticipantesLayout != null) {
                    maxParticipantesLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                if (maxParticipantesLayout != null) {
                    maxParticipantesLayout.setError(activity.getString(R.string.error_number_invalid));
                }
                isValid = false;
            }
        }

        if (lugarEventoEditText == null || lugarEventoEditText.getText().toString().trim().isEmpty()) {
            if (lugarEventoLayout != null) {
                lugarEventoLayout.setError(activity.getString(R.string.error_location_required));
            }
            isValid = false;
        } else if (lugarEventoLayout != null) {
            lugarEventoLayout.setError(null);
        }

        boolean isPrivate = eventoPrivadoCheckBox != null && eventoPrivadoCheckBox.isChecked();
        String accessCode = privateAccessCodeInput != null && privateAccessCodeInput.getText() != null
                ? privateAccessCodeInput.getText().toString().trim()
                : "";
        if (isPrivate) {
            if (!EventPrivateAccessCode.isValidFormat(accessCode)) {
                if (privateAccessCodeLayout != null) {
                    privateAccessCodeLayout.setError(activity.getString(R.string.error_private_access_code_format));
                }
                isValid = false;
            } else if (privateAccessCodeLayout != null) {
                privateAccessCodeLayout.setError(null);
            }
        } else if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }

        return isValid;
    }

    private void createEvent() {
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            return;
        }
        String userId = firebaseManager.getAuth().getCurrentUser().getUid();
        String title = nombreEventoEditText.getText().toString().trim();
        String description = descripcionEventoEditText.getText().toString().trim();
        String location = lugarEventoEditText.getText().toString().trim();
        int maxParticipants = Integer.parseInt(maxParticipantesEditText.getText().toString());
        boolean privateEvent = eventoPrivadoCheckBox.isChecked();
        boolean requiresParentalAuth = requiresParentalAuthCheckBox.isChecked();
        String accessCode = privateAccessCodeInput != null && privateAccessCodeInput.getText() != null
                ? privateAccessCodeInput.getText().toString().trim()
                : null;

        eventViewModel.createEvent(userId, title, description, calendar.getTime(),
                location, maxParticipants, selectedEventType, privateEvent, requiresParentalAuth, accessCode);
    }
}
