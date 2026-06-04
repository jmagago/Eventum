package com.us.eventum.presentation.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.us.eventum.R;
import com.us.eventum.data.repositories.FirebaseManager;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.AttendeeQrToken;
import com.us.eventum.utils.VibrationUtils;

import java.util.concurrent.ExecutionException;

public class QRScannerActivity extends AppCompatActivity {
    private static final String TAG = "QRScanner";
    private static final long RESULT_DISPLAY_MS = 2200L;
    /** No repetir el mismo QR hasta que pase este tiempo o se lea otro código. */
    private static final long SAME_QR_COOLDOWN_MS = 3500L;

    private String eventId;
    private String lastHandledQrPayload;
    private long lastHandledQrAtMs;
    private int resumeScanGeneration;
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private BarcodeScanner scanner;
    private boolean isProcessingFrame = false;
    private boolean scanCompleted = false;
    private boolean cameraBound = false;
    private boolean finishingEarly = false;
    private AttendeeViewModel attendeeViewModel;
    private MaterialCardView scanStatusCard;
    private TextView scanStatusText;
    private TextView scanHintText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    bindCameraIfReady();
                } else {
                    showMessageAndFinish(getString(R.string.qr_scanner_camera_denied));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eventId = getIntent().getStringExtra("eventId");
        String launchError = getLaunchErrorMessage();
        if (launchError != null) {
            finishingEarly = true;
            setContentView(R.layout.activity_qr_scanner);
            setupScannerSystemBars();
            showMessageAndFinish(launchError);
            return;
        }

        setContentView(R.layout.activity_qr_scanner);
        setupScannerSystemBars();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        attendeeViewModel.initializeRepository(this);
        attendeeViewModel.clearOperationStates();

        previewView = findViewById(R.id.preview_view);
        scanStatusCard = findViewById(R.id.scanStatusCard);
        scanStatusText = findViewById(R.id.scanStatusText);
        scanHintText = findViewById(R.id.scanHintText);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);

        applyStatusCardBottomInset();
        observeViewModel();
    }

    private void setupScannerSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
    }

    private void applyStatusCardBottomInset() {
        View root = findViewById(R.id.qr_scanner_root);
        if (root == null || scanStatusCard == null) {
            return;
        }
        final int baseMargin = getResources().getDimensionPixelSize(R.dimen.qr_scanner_status_margin_bottom);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) scanStatusCard.getLayoutParams();
            if (lp != null) {
                lp.bottomMargin = baseMargin + systemBars.bottom;
                scanStatusCard.setLayoutParams(lp);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (finishingEarly || isFinishing()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            bindCameraIfReady();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onPause() {
        unbindCamera();
        super.onPause();
    }

    private String getLaunchErrorMessage() {
        FirebaseManager firebaseManager = FirebaseManager.getInstance();
        if (firebaseManager.getAuth() == null
                || firebaseManager.getAuth().getCurrentUser() == null) {
            return getString(R.string.qr_scanner_auth_required);
        }
        if (eventId == null || eventId.isEmpty()) {
            return getString(R.string.qr_scanner_missing_event);
        }
        return null;
    }

    /** Evita crash TopResumedActivityChangeItem al cerrar en onCreate. */
    private void finishSafely() {
        if (isFinishing()) {
            return;
        }
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor != null) {
            decor.post(() -> {
                if (!isFinishing()) {
                    finish();
                }
            });
        } else {
            mainHandler.post(() -> {
                if (!isFinishing()) {
                    finish();
                }
            });
        }
    }

    private void showMessageAndFinish(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        finishSafely();
    }

    private void observeViewModel() {
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty() && !isFinishing()) {
                scanCompleted = true;
                showScanFeedback(R.string.qr_checkin_error_generic, R.color.colorError,
                        FeedbackKind.ERROR);
                scheduleResumeScanning();
            }
        });

        attendeeViewModel.getQrCheckInResult().observe(this, result -> {
            if (result == null || isFinishing()) {
                return;
            }
            scanCompleted = true;
            switch (result) {
                case VALID:
                    showScanFeedback(R.string.qr_checkin_valid, R.color.colorSuccess,
                            FeedbackKind.SUCCESS);
                    mainHandler.postDelayed(() -> {
                        if (!isFinishing()) {
                            setResult(RESULT_OK);
                            finishSafely();
                        }
                    }, RESULT_DISPLAY_MS);
                    break;
                case ALREADY_USED:
                    showScanFeedback(R.string.qr_checkin_already_used, R.color.colorWarning,
                            FeedbackKind.WARNING);
                    scheduleResumeScanning();
                    break;
                case NOT_REGISTERED:
                    showScanFeedback(R.string.qr_checkin_not_registered, R.color.colorError,
                            FeedbackKind.ERROR);
                    scheduleResumeScanning();
                    break;
                case WRONG_EVENT:
                    showScanFeedback(R.string.qr_checkin_wrong_event, R.color.colorWarning,
                            FeedbackKind.WARNING);
                    scheduleResumeScanning();
                    break;
                case EXPIRED_TOKEN:
                    showScanFeedback(R.string.qr_checkin_expired, R.color.colorWarning,
                            FeedbackKind.WARNING);
                    scheduleResumeScanning();
                    break;
                case INVALID_TOKEN:
                default:
                    showScanFeedback(R.string.qr_checkin_invalid, R.color.colorError,
                            FeedbackKind.ERROR);
                    scheduleResumeScanning();
                    break;
            }
            attendeeViewModel.clearOperationStates();
        });
    }

    private void scheduleResumeScanning() {
        final int generation = ++resumeScanGeneration;
        mainHandler.postDelayed(() -> {
            if (isFinishing() || generation != resumeScanGeneration) {
                return;
            }
            scanCompleted = false;
            isProcessingFrame = false;
            if (scanStatusCard != null) {
                scanStatusCard.setVisibility(View.GONE);
            }
            if (scanHintText != null) {
                scanHintText.setVisibility(View.VISIBLE);
            }
        }, RESULT_DISPLAY_MS);
    }

    private enum FeedbackKind {
        SUCCESS, WARNING, ERROR
    }

    private void showScanFeedback(@StringRes int messageRes, int backgroundColorRes,
                                  FeedbackKind kind) {
        if (isFinishing()) {
            return;
        }
        String message = getString(messageRes);
        if (scanHintText != null) {
            scanHintText.setVisibility(View.GONE);
        }
        if (scanStatusText != null) {
            scanStatusText.setText(message);
        }
        if (scanStatusCard != null) {
            scanStatusCard.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColorRes));
            scanStatusCard.setVisibility(View.VISIBLE);
        }
        switch (kind) {
            case SUCCESS:
                VibrationUtils.vibrateSuccess(this);
                break;
            case WARNING:
                VibrationUtils.vibrateWarning(this);
                break;
            case ERROR:
            default:
                VibrationUtils.vibrateError(this);
                break;
        }
    }

    private boolean shouldIgnoreDuplicateQr(String qrContent) {
        if (lastHandledQrPayload == null) {
            return false;
        }
        return qrContent.equals(lastHandledQrPayload)
                && System.currentTimeMillis() - lastHandledQrAtMs < SAME_QR_COOLDOWN_MS;
    }

    private void markQrHandled(String qrContent) {
        lastHandledQrPayload = qrContent;
        lastHandledQrAtMs = System.currentTimeMillis();
    }

    private void bindCameraIfReady() {
        if (cameraBound || previewView == null || isFinishing() || finishingEarly) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            if (isFinishing() || finishingEarly) {
                return;
            }
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error al obtener ProcessCameraProvider", e);
                runOnUiThread(() -> showMessageAndFinish(
                        getString(R.string.qr_scanner_camera_error)));
            } catch (Exception e) {
                Log.e(TAG, "Error al iniciar cámara", e);
                runOnUiThread(() -> showMessageAndFinish(
                        getString(R.string.qr_scanner_camera_error)));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || previewView == null || isFinishing()) {
            return;
        }

        try {
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeFrame);

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            cameraBound = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al enlazar cámara", e);
            showMessageAndFinish(getString(R.string.qr_scanner_camera_error));
        }
    }

    private void unbindCamera() {
        cameraBound = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (isProcessingFrame || scanCompleted || isFinishing()) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessingFrame = true;
        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        scanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty() || scanCompleted) {
                        isProcessingFrame = false;
                        return;
                    }

                    for (Barcode barcode : barcodes) {
                        String qrContent = barcode.getRawValue();
                        if (qrContent != null) {
                            processQRCode(qrContent);
                            return;
                        }
                    }
                    isProcessingFrame = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al procesar imagen: " + e.getMessage());
                    isProcessingFrame = false;
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processQRCode(String qrContent) {
        if (scanCompleted || shouldIgnoreDuplicateQr(qrContent)) {
            isProcessingFrame = false;
            return;
        }

        AttendeeQrToken.ParseResult parseResult = AttendeeQrToken.parse(qrContent, eventId);

        switch (parseResult.getResult()) {
            case VALID:
                scanCompleted = true;
                markQrHandled(qrContent);
                attendeeViewModel.verifyAttendeeCheckIn(parseResult.getUid(), eventId);
                return;
            case WRONG_EVENT:
                scanCompleted = true;
                markQrHandled(qrContent);
                showScanFeedback(R.string.qr_checkin_wrong_event, R.color.colorWarning,
                        FeedbackKind.WARNING);
                break;
            case EXPIRED_TOKEN:
                scanCompleted = true;
                markQrHandled(qrContent);
                showScanFeedback(R.string.qr_checkin_expired, R.color.colorWarning,
                        FeedbackKind.WARNING);
                break;
            case ALREADY_USED:
            case NOT_REGISTERED:
            case INVALID_TOKEN:
            default:
                scanCompleted = true;
                markQrHandled(qrContent);
                showScanFeedback(R.string.qr_checkin_invalid, R.color.colorError,
                        FeedbackKind.ERROR);
                break;
        }
        scheduleResumeScanning();
        isProcessingFrame = false;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (scanner != null) {
            scanner.close();
        }
        unbindCamera();
        super.onDestroy();
    }
}
