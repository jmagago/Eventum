package com.us.eventum.utils;

import android.app.Activity;
import android.view.WindowManager;

/**
 * Aumenta temporalmente el brillo de pantalla (útil al mostrar códigos QR).
 */
public final class ScreenBrightnessHelper {

    private float previousBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

    public void setMaxBrightness(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        previousBrightness = params.screenBrightness;
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        activity.getWindow().setAttributes(params);
    }

    public void restore(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = previousBrightness;
        activity.getWindow().setAttributes(params);
    }
}
