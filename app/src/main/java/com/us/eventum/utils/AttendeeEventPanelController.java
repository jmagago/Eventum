package com.us.eventum.utils;

import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;

import java.text.SimpleDateFormat;
import java.util.Locale;

public final class AttendeeEventPanelController {

    private final AppCompatActivity activity;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;

    private TextView panelTitle;
    private ImageView eventImageView;
    private TextView descriptionText;
    private View descriptionSection;
    private TextView dateText;
    private TextView timeText;
    private TextView locationText;
    private TextView participantsText;
    private TextView eventTypeText;
    private View privateBadge;
    private TextInputLayout privateAccessCodeLayout;
    private TextInputEditText privateAccessCodeInput;
    private MaterialButton joinButton;
    private MaterialButton cancelButton;
    private MaterialButton showQrButton;
    private TextView waitlistStatusText;

    private Event currentEvent;
    private boolean historyMode;
    private Runnable onHideListener;
    @Nullable
    private String boundImageEventId;

    public void setOnHideListener(@Nullable Runnable listener) {
        onHideListener = listener;
    }

    public AttendeeEventPanelController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    @Nullable
    public Event getCurrentEvent() {
        return currentEvent;
    }

    public boolean isHistoryMode() {
        return historyMode;
    }

    @Nullable
    public MaterialButton getJoinButton() {
        return joinButton;
    }

    @Nullable
    public MaterialButton getShowQrButton() {
        return showQrButton;
    }

    @Nullable
    public TextView getParticipantsText() {
        return participantsText;
    }

    @Nullable
    public String getEnteredAccessCode() {
        if (privateAccessCodeInput == null || privateAccessCodeInput.getText() == null) {
            return "";
        }
        return privateAccessCodeInput.getText().toString().trim();
    }

    public void show(@NonNull Event event, boolean historyMode) {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        currentEvent = event;
        this.historyMode = historyMode;
        bindEvent(event);

        sheet.dialog.setOnDismissListener(d -> {
            currentEvent = null;
            boundImageEventId = null;
            if (onHideListener != null) {
                onHideListener.run();
            }
        });
        EventumBottomSheetHelper.configureExpandedPanel(
                activity,
                sheet.dialog,
                sheet.root,
                R.id.attendeeEventScrollView,
                R.id.attendeeEventFooter);
        sheet.dialog.show();
    }

    public void refreshEvent(@NonNull Event event) {
        syncEventContent(event);
    }

    /**
     * Sincroniza metadatos y aforo en tiempo real sin recargar imagen ni reiniciar botones.
     */
    public void syncEventContent(@NonNull Event event) {
        if (!isVisible()) {
            return;
        }
        currentEvent = event;
        bindEventMetadata(event);
        refreshEventStats(event);
    }

    /** Actualiza solo el contador de participantes. */
    public void refreshEventStats(@NonNull Event event) {
        if (!isVisible()) {
            return;
        }
        currentEvent = event;
        if (participantsText != null) {
            participantsText.setText(activity.getResources().getQuantityString(
                    R.plurals.event_participants_count,
                    event.getMaxParticipants(),
                    event.getCurrentParticipants(), event.getMaxParticipants()));
        }
    }

    public void hide() {
        if (sheet != null && sheet.dialog.isShowing()) {
            sheet.dialog.dismiss();
        }
    }

    private void ensureDialog() {
        if (sheet != null) {
            return;
        }

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_attendee_event);
        EventumBottomSheetHelper.hideHeaderIcon(sheet.root);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        View root = sheet.root;
        panelTitle = root.findViewById(R.id.bottomSheetTitle);
        if (root.findViewById(R.id.bottomSheetCloseButton) != null) {
            root.findViewById(R.id.bottomSheetCloseButton)
                    .setContentDescription(activity.getString(R.string.cd_close_attendee_event_panel));
        }
        eventImageView = root.findViewById(R.id.attendeeEventPanelImageView);
        descriptionText = root.findViewById(R.id.eventDescription);
        descriptionSection = root.findViewById(R.id.eventDescriptionSection);
        dateText = root.findViewById(R.id.eventDate);
        timeText = root.findViewById(R.id.eventTime);
        locationText = root.findViewById(R.id.eventLocation);
        participantsText = root.findViewById(R.id.eventParticipants);
        eventTypeText = root.findViewById(R.id.eventType);
        privateBadge = root.findViewById(R.id.eventPrivateBadge);
        privateAccessCodeLayout = root.findViewById(R.id.attendeePrivateAccessCodeLayout);
        privateAccessCodeInput = root.findViewById(R.id.attendeePrivateAccessCodeInput);
        joinButton = root.findViewById(R.id.joinEventButton);
        cancelButton = root.findViewById(R.id.cancelEventPanelButton);
        showQrButton = root.findViewById(R.id.showQrButton);
        waitlistStatusText = root.findViewById(R.id.waitlistStatusText);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> hide());
        }
    }

    public void updatePrivateCodeVisibility(boolean showCodeField) {
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setVisibility(showCodeField ? View.VISIBLE : View.GONE);
        }
        if (!showCodeField && privateAccessCodeInput != null) {
            privateAccessCodeInput.setText("");
        }
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }
    }

    public void showAccessCodeFormatError() {
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(activity.getString(R.string.error_private_access_code_format));
        }
    }

    public void showWrongAccessCodeError() {
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(activity.getString(R.string.error_private_access_code_wrong));
        }
        showPanelToast(activity.getString(R.string.error_private_access_code_wrong), ToastUtils.ToastType.ERROR);
    }

    public boolean isPrivateCodeFieldVisible() {
        return privateAccessCodeLayout != null
                && privateAccessCodeLayout.getVisibility() == View.VISIBLE;
    }

    public void showPanelToast(@NonNull String message, @NonNull ToastUtils.ToastType type) {
        View anchor = EventumBottomSheetHelper.toastAnchor(
                sheet != null ? sheet.root : null, activity);
        ToastUtils.showPanelToast(anchor, message, type);
    }

    public void clearAccessCodeError() {
        if (privateAccessCodeLayout != null) {
            privateAccessCodeLayout.setError(null);
        }
    }

    public void setQrButtonVisible(boolean visible) {
        if (showQrButton != null) {
            showQrButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                showQrButton.setEnabled(true);
                showQrButton.setAlpha(1f);
            }
        }
    }

    public void updateWaitlistStatus(@Nullable String message, int position, @Nullable java.util.Date expiresAt) {
        if (waitlistStatusText == null) {
            return;
        }
        if (message == null || message.trim().isEmpty()) {
            waitlistStatusText.setVisibility(View.GONE);
            waitlistStatusText.setText("");
            return;
        }
        waitlistStatusText.setVisibility(View.VISIBLE);
        if (expiresAt != null) {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            String deadline = format.format(expiresAt);
            waitlistStatusText.setText(activity.getString(
                    R.string.waitlist_offer_deadline, message, deadline));
        } else {
            waitlistStatusText.setText(message);
        }
    }

    private void bindEvent(@NonNull Event event) {
        bindEventMetadata(event);
        bindEventImage(event);
        updateWaitlistStatus(null, 0, null);
        setQrButtonVisible(false);
        if (joinButton != null) {
            joinButton.setEnabled(false);
            joinButton.setAlpha(0.5f);
        }
    }

    private void bindEventMetadata(@NonNull Event event) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        if (panelTitle != null) {
            panelTitle.setText(event.getTitle());
        }
        if (descriptionText != null) {
            String description = event.getDescription();
            boolean hasDescription = description != null && !description.trim().isEmpty();
            if (hasDescription) {
                descriptionText.setText(description);
                descriptionText.setVisibility(View.VISIBLE);
            } else {
                descriptionText.setText("");
            }
            if (descriptionSection != null) {
                descriptionSection.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            } else {
                descriptionText.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            }
        }
        if (event.getDate() != null) {
            if (dateText != null) {
                dateText.setText(dateFormat.format(event.getDate()));
            }
            if (timeText != null) {
                timeText.setText(timeFormat.format(event.getDate()));
            }
        } else {
            if (dateText != null) {
                dateText.setText("");
            }
            if (timeText != null) {
                timeText.setText("");
            }
        }
        if (locationText != null) {
            locationText.setText(event.getLocation());
        }
        refreshEventStats(event);
        if (eventTypeText != null) {
            eventTypeText.setText(event.getEventType());
        }
        if (privateBadge != null) {
            privateBadge.setVisibility(event.getPrivateEvent() ? View.VISIBLE : View.GONE);
        }
        updatePrivateCodeVisibility(!historyMode && event.getPrivateEvent());
    }

    private void bindEventImage(@NonNull Event event) {
        if (eventImageView == null) {
            return;
        }
        String eventId = event.getId();
        if (eventId != null && !eventId.equals(boundImageEventId)) {
            eventImageView.setImageResource(R.mipmap.ic_launcher);
            eventImageView.setTag(R.id.tag_image_load_key, null);
        }
        EventImageManager.loadEventImage(activity, eventImageView, eventId, available -> {
            if (eventId != null) {
                boundImageEventId = eventId;
            }
            eventImageView.setClickable(available);
            eventImageView.setFocusable(available);
            if (available) {
                TypedValue ripple = new TypedValue();
                activity.getTheme().resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, ripple, true);
                eventImageView.setBackgroundResource(ripple.resourceId);
                eventImageView.setOnClickListener(v ->
                        EventImageManager.showFullScreenEventImage(activity, eventId));
            } else {
                eventImageView.setBackground(null);
                eventImageView.setOnClickListener(null);
            }
        });
    }
}
