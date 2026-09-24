package com.us.eventum.core.utils;

import com.us.eventum.R;

import android.app.Activity;
import android.content.Context;

import com.us.eventum.data.network.NetworkStateManager;

/**
 * Utilidades para manejo de conexión a internet.
 */
public class NetworkUtils {

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
        String message = customMessage != null
                ? customMessage
                : context.getString(R.string.error_no_internet);
        if (context instanceof Activity) {
            ToastUtils.showCustomToast((Activity) context, message, ToastUtils.ToastType.ERROR);
        }
        return false;
    }
}
