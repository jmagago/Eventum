package com.us.eventum.utils;

import android.content.Context;
import android.widget.Toast;
import com.us.eventum.data.network.NetworkStateManager;

/**
 * Utilidades para manejo de conexión a internet
 */
public class NetworkUtils {
    
    /**
     * Verificar si hay conexión a internet
     */
    public static boolean isOnline(Context context) {
        NetworkStateManager networkManager = NetworkStateManager.getInstance(context);
        return networkManager.isOnline();
    }
    
    /**
     * Mostrar mensaje de "Sin conexión a internet" si no hay conexión
     * @return true si hay conexión, false si no hay conexión
     */
    public static boolean checkConnectionAndShowMessage(Context context) {
        if (!isOnline(context)) {
            Toast.makeText(context, "Sin conexión a internet. Verifique su conexión de red.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    
    /**
     * Verificar conexión y mostrar mensaje personalizado
     */
    public static boolean checkConnectionAndShowMessage(Context context, String customMessage) {
        if (!isOnline(context)) {
            String message = customMessage != null ? customMessage : "Sin conexión a internet. Verifique su conexión de red.";
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}
