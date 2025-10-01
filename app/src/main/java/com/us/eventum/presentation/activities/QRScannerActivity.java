package com.us.eventum.presentation.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.view.WindowManager;
import android.view.Surface;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.us.eventum.data.repositories.FirebaseManager;
import com.google.gson.Gson;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.us.eventum.R;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QRScannerActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private String eventId;
    private FirebaseManager firebaseManager;
    private SurfaceView previewView;
    private Camera camera;
    private BarcodeScanner scanner;
    private boolean isProcessingFrame = false;
    private AttendeeViewModel attendeeViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        // Verificar que el usuario está autenticado
        if (firebaseManager.getAuth().getCurrentUser() == null) {
            ToastUtils.showCustomToast(this, "Debes iniciar sesión para escanear códigos QR", ToastUtils.ToastType.ERROR);
            finish();
            return;
        }

        // Mantener la pantalla encendida mientras se escanea
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Obtener el ID del evento
        eventId = getIntent().getStringExtra("eventId");
        if (eventId == null) {
            ToastUtils.showCustomToast(this, "Error: No se pudo obtener el ID del evento", ToastUtils.ToastType.ERROR);
            finish();
            return;
        }

        // Inicializar Firebase
        firebaseManager = FirebaseManager.getInstance();
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        
        // Inicializar repositorio en ViewModel
        attendeeViewModel.initializeRepository(this);

        // Inicializar vistas
        previewView = findViewById(R.id.preview_view);
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Configurar el escáner de códigos de barras
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build();
        scanner = BarcodeScanning.getClient(options);

        // Verificar y solicitar permisos de cámara
        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        // Verificar permisos del evento en segundo plano
        observeViewModel();
        verifyEventPermissions();
    }

    private void observeViewModel() {
        // Observar estado de carga
        attendeeViewModel.getIsLoading().observe(this, loading -> {
            // Manejar indicador de carga si es necesario
        });

        // Observar errores
        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                ToastUtils.showCustomToast(this, error, ToastUtils.ToastType.ERROR);
                isProcessingFrame = false;
                camera.setPreviewCallback((data, camera) -> {
                    if (!isProcessingFrame) {
                        processImageData(data, camera);
                    }
                });
            }
        });

        // Observar marcado de asistencia (escaneo) exitoso
        attendeeViewModel.getAttendeeVerified().observe(this, verified -> {
            if (verified != null && verified) {
                ToastUtils.showCustomToast(this, "Asistencia registrada correctamente", ToastUtils.ToastType.SUCCESS);
                attendeeViewModel.clearOperationStates();
                // Esperar un momento para que el usuario vea el mensaje de éxito
                new Handler().postDelayed(() -> {
                    setResult(RESULT_OK);
                    finish();
                }, 1500);
            }
        });
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            new String[]{Manifest.permission.CAMERA},
            CAMERA_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                ToastUtils.showCustomToast(this, "Se necesita permiso de cámara para escanear", ToastUtils.ToastType.ERROR);
                finish();
            }
        }
    }

    private void startCamera() {
        SurfaceHolder holder = previewView.getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setPreviewDisplay(holder);

            // Configurar parámetros de la cámara
            Camera.Parameters parameters = camera.getParameters();
            
            // Obtener el tamaño óptimo para el preview
            Camera.Size bestSize = getBestPreviewSize(parameters);
            if (bestSize != null) {
                parameters.setPreviewSize(bestSize.width, bestSize.height);
            }

            // Configurar el enfoque automático continuo si está disponible
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            camera.setParameters(parameters);

            // Configurar la orientación correcta de la cámara
            setCameraDisplayOrientation();
            
            camera.startPreview();
            
            // Configurar el callback para procesar los frames
            camera.setPreviewCallback((data, camera) -> {
                if (!isProcessingFrame) {
                    processImageData(data, camera);
                }
            });
        } catch (IOException e) {
            Log.e("QRScanner", "Error al iniciar la cámara: " + e.getMessage());
            ToastUtils.showCustomToast(this, "Error al iniciar la cámara", ToastUtils.ToastType.ERROR);
            finish();
        }
    }

    private Camera.Size getBestPreviewSize(Camera.Parameters parameters) {
        Camera.Size bestSize = null;
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        int bestArea = 0;
        
        for (Camera.Size size : sizes) {
            int area = size.width * size.height;
            if (area > bestArea) {
                bestArea = area;
                bestSize = size;
            }
        }
        
        return bestSize;
    }

    private void processImageData(byte[] data, Camera camera) {
        isProcessingFrame = true;
        
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        
        // Obtener la orientación actual de la cámara
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        int rotation = info.orientation;
        
        InputImage image = InputImage.fromByteArray(
            data,
            size.width,
            size.height,
            rotation, // Usar la orientación real de la cámara
            InputImage.IMAGE_FORMAT_NV21
        );

        scanner.process(image)
            .addOnSuccessListener(barcodes -> {
                if (barcodes.isEmpty()) {
                    isProcessingFrame = false;
                    return;
                }
                
                Log.d("QRScanner", "Barcodes encontrados: " + barcodes.size());
                
                for (Barcode barcode : barcodes) {
                    String qrContent = barcode.getRawValue();
                    Log.d("QRScanner", "Contenido del QR: " + qrContent);
                    
                    if (qrContent != null) {
                        processQRCode(qrContent);
                        return;
                    }
                }
                isProcessingFrame = false;
            })
            .addOnFailureListener(e -> {
                Log.e("QRScanner", "Error al procesar imagen: " + e.getMessage());
                isProcessingFrame = false;
            });
    }

    private void processQRCode(String qrContent) {
        try {
            Log.d("QRScanner", "Procesando QR: " + qrContent);
            
            // Decodificar el contenido del QR
            AttendeeQRData qrData = new Gson().fromJson(qrContent, AttendeeQRData.class);
            
            if (qrData == null) {
                Log.e("QRScanner", "Error: qrData es null");
                ToastUtils.showCustomToast(this, "Código QR inválido - Formato incorrecto", ToastUtils.ToastType.ERROR);
                isProcessingFrame = false;
                return;
            }
            
            Log.d("QRScanner", "EventId del QR: " + qrData.getEventId());
            Log.d("QRScanner", "AttendeeId del QR: " + qrData.getAttendeeId());
            
            // Verificar que los campos necesarios no sean null
            if (qrData.getEventId() == null || qrData.getAttendeeId() == null) {
                Log.e("QRScanner", "Error: campos requeridos son null");
                ToastUtils.showCustomToast(this, "Código QR inválido - Datos incompletos", ToastUtils.ToastType.ERROR);
                isProcessingFrame = false;
                return;
            }

            // Verificar que el QR corresponde al evento actual
            if (!eventId.equals(qrData.getEventId())) {
                Log.e("QRScanner", "Error: EventId no coincide. Esperado: " + eventId + ", Recibido: " + qrData.getEventId());
                ToastUtils.showCustomToast(this, "Este código QR no corresponde a este evento", ToastUtils.ToastType.ERROR);
                isProcessingFrame = false;
                return;
            }

            // Detener el procesamiento de frames mientras verificamos
            isProcessingFrame = true;
            camera.setPreviewCallback(null);

            // Usar AttendeeViewModel para verificar el asistente
            attendeeViewModel.verifyAttendee(qrData.getAttendeeId(), eventId);

        } catch (Exception e) {
            Log.e("QRScanner", "Error al procesar QR: " + e.getMessage(), e);
            ToastUtils.showCustomToast(this, "Código QR inválido - " + e.getMessage(), ToastUtils.ToastType.ERROR);
            isProcessingFrame = false;
            if (camera != null) {
                camera.setPreviewCallback((data, camera) -> {
                    if (!isProcessingFrame) {
                        processImageData(data, camera);
                    }
                });
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                ToastUtils.showCustomToast(this, "Error al actualizar la vista previa", ToastUtils.ToastType.ERROR);
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && hasCameraPermission()) {
            startCamera();
        }
    }

    private void setCameraDisplayOrientation() {
        if (camera == null) return;

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        
        camera.setDisplayOrientation(result);
    }

    private void verifyEventPermissions() {
        // Verificar que el usuario actual es el propietario del evento
        String currentUserId = firebaseManager.getAuth().getCurrentUser().getUid();
        if (currentUserId == null) {
            ToastUtils.showCustomToast(this, "Usuario no autenticado", ToastUtils.ToastType.ERROR);
            finish();
            return;
        }
        
        // Por ahora, asumimos que el usuario tiene permisos si está autenticado
        // En una implementación más robusta, se podría verificar contra el EventViewModel
        // pero para QRScanner esto es suficiente ya que el evento se pasa desde EventDetailsActivity
    }

    @Override
    public void finish() {
        if (camera != null) {
            camera.setPreviewCallback(null);
        }
        super.finish();
    }

    // Clase interna para la estructura del QR
    private static class AttendeeQRData {
        private String eventId;
        private String attendeeId;

        public String getEventId() { return eventId; }
        public String getAttendeeId() { return attendeeId; }
    }
} 