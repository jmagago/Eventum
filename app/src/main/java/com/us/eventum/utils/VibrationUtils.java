package com.us.eventum.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Feedback háptico consistente en toda la app.
 */
public final class VibrationUtils {

    private static final long PULSE_SHORT_MS = 80L;
    private static final long PULSE_MEDIUM_MS = 120L;
    private static final long PULSE_LONG_MS = 180L;

    private VibrationUtils() {
    }

    /** Alta / check-in correcto */
    public static void vibrateSuccess(Context context) {
        vibrateOneShot(context, PULSE_MEDIUM_MS, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /** Baja / aviso */
    public static void vibrateWarning(Context context) {
        vibratePattern(context, new long[]{0, PULSE_SHORT_MS, 60, PULSE_MEDIUM_MS});
    }

    /** Error o QR inválido */
    public static void vibrateError(Context context) {
        vibratePattern(context, new long[]{0, PULSE_SHORT_MS, 50, PULSE_SHORT_MS, 50, PULSE_SHORT_MS});
    }

    /** Escaneo QR (cualquier resultado definitivo) */
    public static void vibrateScanResult(Context context) {
        vibrateOneShot(context, PULSE_LONG_MS, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /** Notificación del organizador (pulso breve) */
    public static void vibrateNotification(Context context) {
        vibrateOneShot(context, PULSE_SHORT_MS, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    private static void vibrateOneShot(Context context, long durationMs, int amplitude) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
    }

    private static void vibratePattern(Context context, long[] pattern) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    @SuppressWarnings("deprecation")
    private static Vibrator getVibrator(Context context) {
        if (context == null) {
            return null;
        }
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            return null;
        }
        return vibrator;
    }
}
