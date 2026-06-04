package com.us.eventum.presentation.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.us.eventum.R;
import com.us.eventum.utils.QRCodeGenerator;
import com.us.eventum.utils.ScreenBrightnessHelper;
import com.us.eventum.utils.WindowInsetsHelper;

/**
 * Pantalla a pantalla completa para mostrar el QR de check-in con brillo máximo y renovación automática.
 */
public class AttendeeQrDisplayActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "eventId";
    private static final String EXTRA_EVENT_TITLE = "eventTitle";

    private final ScreenBrightnessHelper brightnessHelper = new ScreenBrightnessHelper();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::renderQrCode;

    private String eventId;
    private ImageView qrImageView;
    private CircularProgressIndicator loadingIndicator;
    private TextView refreshInfoText;

    public static void start(Context context, String eventId, String eventTitle) {
        Intent intent = new Intent(context, AttendeeQrDisplayActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putExtra(EXTRA_EVENT_TITLE, eventTitle);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendee_qr);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        String eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);

        if (eventId == null || eventId.isEmpty()) {
            finish();
            return;
        }

        View statusBarStripe = findViewById(R.id.statusBarStripe);
        WindowInsetsHelper.applyStatusBarStripe(statusBarStripe);

        MaterialToolbar toolbar = findViewById(R.id.qrToolbar);
        toolbar.setTitle(R.string.attendee_qr_title);

        TextView titleView = findViewById(R.id.qrEventTitle);
        titleView.setText(eventTitle != null ? eventTitle : "");

        qrImageView = findViewById(R.id.qrImageView);
        loadingIndicator = findViewById(R.id.qrLoadingIndicator);
        refreshInfoText = findViewById(R.id.qrRefreshInfo);
        WindowInsetsHelper.applyBottomNavigationBarMargin(this, refreshInfoText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        brightnessHelper.setMaxBrightness(this);
        renderQrCode();
        scheduleRefresh();
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        brightnessHelper.restore(this);
        super.onPause();
    }

    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, com.us.eventum.utils.AttendeeQrToken.REFRESH_INTERVAL_MS);
    }

    private void renderQrCode() {
        String attendeeId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (attendeeId == null) {
            finish();
            return;
        }

        loadingIndicator.setVisibility(View.VISIBLE);
        qrImageView.setVisibility(View.INVISIBLE);

        int sizePx = Math.min(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels
        ) - (int) (48 * getResources().getDisplayMetrics().density);
        if (sizePx < 512) {
            sizePx = 512;
        }

        Bitmap bitmap = QRCodeGenerator.generateCheckInQr(attendeeId, eventId, sizePx);
        loadingIndicator.setVisibility(View.GONE);
        if (bitmap == null) {
            finish();
            return;
        }

        qrImageView.setImageBitmap(bitmap);
        qrImageView.setVisibility(View.VISIBLE);
        refreshInfoText.setText(getString(R.string.attendee_qr_refresh_info));
        scheduleRefresh();
    }
}
