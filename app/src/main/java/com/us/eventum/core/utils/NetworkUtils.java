package com.us.eventum.core.utils;

import android.app.Activity;
import android.content.Context;

import com.us.eventum.data.network.NetworkStateManager;

/**
 * Utilidades para manejo de conexión a internet.
 */
public class NetworkUtils {

    private static final String DEFAULT_OFFLINE_MESSAGE =
            "Sin conexión a internet. Verifique su conexión de red.";

    public static boolean isOnline(Context context) {
        return NetworkStateManager.getInstance(context).isOnline();
    }

    public static boolean checkConnectionAndShowMessage(Context context) {
        return checkConnectionAndShowMessage(context, null);
    }

    public static boolean checkConnectionAndShowMessage(Context context, String customMessage) {
        if (isOnline(context)) {
            return true;
        }
        String message = customMessage != null ? customMessage : DEFAULT_OFFLINE_MESSAGE;
        if (context instanceof Activity) {
            ToastUtils.showCustomToast((Activity) context, message, ToastUtils.ToastType.ERROR);
        }
        return false;
    }
}
