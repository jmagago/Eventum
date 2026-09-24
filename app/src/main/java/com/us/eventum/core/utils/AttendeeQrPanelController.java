package com.us.eventum.core.utils;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.R;

public final class AttendeeQrPanelController {

    private final AppCompatActivity activity;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;

    private ImageView qrImageView;
    private CircularProgressIndicator loadingIndicator;
    private TextView eventTitleView;
    private TextView refreshInfoView;

    private final ScreenBrightnessHelper brightnessHelper = new ScreenBrightnessHelper();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::renderQrCode;

    private String eventId;

    public AttendeeQrPanelController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show(@NonNull String eventId, @Nullable String eventTitle) {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        this.eventId = eventId;
        if (eventTitleView != null) {
            eventTitleView.setText(eventTitle != null ? eventTitle : "");
        }

        sheet.dialog.setOnDismissListener(d -> {
            AttendeeQrPanelController.this.eventId = null;
            onHostPause();
        });
        EventumBottomSheetHelper.configureDisplayPanel(
                activity,
                sheet.dialog,
                sheet.root,
                R.id.attendeeQrScrollView,
                R.id.attendeeQrFooter);
        sheet.dialog.show();
        onHostResume();
    }

    public void hide() {
        if (sheet != null && sheet.dialog.isShowing()) {
            sheet.dialog.dismiss();
        }
    }

    public void onHostResume() {
        if (!isVisible() || eventId == null) {
            return;
        }
        brightnessHelper.setMaxBrightness(activity);
        if (sheet != null && sheet.dialog.getWindow() != null) {
            sheet.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        renderQrCode();
        scheduleRefresh();
    }

    public void onHostPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        brightnessHelper.restore(activity);
        if (sheet != null && sheet.dialog.getWindow() != null) {
            sheet.dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void ensureDialog() {
        if (sheet != null) {
            return;
        }

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_attendee_qr);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_qr_code,
                R.string.attendee_qr_title,
                R.string.cd_close_attendee_qr_panel);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        View root = sheet.root;
        qrImageView = root.findViewById(R.id.attendeeQrImageView);
        loadingIndicator = root.findViewById(R.id.attendeeQrLoadingIndicator);
        eventTitleView = root.findViewById(R.id.attendeeQrEventTitle);
        refreshInfoView = root.findViewById(R.id.attendeeQrRefreshInfo);
        MaterialButton closeBottom = root.findViewById(R.id.attendeeQrCloseButtonBottom);
        if (closeBottom != null) {
            closeBottom.setOnClickListener(v -> hide());
        }
    }

    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, AttendeeQrToken.REFRESH_INTERVAL_MS);
    }

    private void renderQrCode() {
        if (!isVisible() || eventId == null) {
            return;
        }

        String attendeeId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (attendeeId == null) {
            hide();
            ToastUtils.showCustomToast(activity, activity.getString(R.string.attendee_qr_error),
                    ToastUtils.ToastType.ERROR);
            return;
        }

        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }
        if (qrImageView != null) {
            qrImageView.setVisibility(View.INVISIBLE);
        }

        int sizePx = (int) (280 * activity.getResources().getDisplayMetrics().density);
        Bitmap bitmap = QRCodeGenerator.generateCheckInQr(attendeeId, eventId, sizePx);

        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.GONE);
        }
        if (bitmap == null || qrImageView == null) {
            hide();
            ToastUtils.showCustomToast(activity, activity.getString(R.string.attendee_qr_error),
                    ToastUtils.ToastType.ERROR);
            return;
        }

        qrImageView.setImageBitmap(bitmap);
        qrImageView.setVisibility(View.VISIBLE);
        if (refreshInfoView != null) {
            refreshInfoView.setText(activity.getString(R.string.attendee_qr_refresh_info));
        }
        scheduleRefresh();
    }
}
