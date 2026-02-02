package com.us.eventum.utils;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.us.eventum.R;

/**
 * Utilidad para mostrar toasts personalizados en la aplicación.
 */
public class ToastUtils {

    public enum ToastType {
        SUCCESS, ERROR, WARNING, INFO
    }

    /**
     * Muestra un toast de bienvenida personalizado.
     * Este toast tiene un diseño especial diferente a los demás toasts de la aplicación.
     *
     * @param activity La actividad actual
     * @param message  El mensaje a mostrar
     */
    public static void showWelcomeToast(Activity activity, String message) {
        View layout = LayoutInflater.from(activity).inflate(R.layout.custom_toast, null);
        
        TextView text = layout.findViewById(R.id.toast_text);
        text.setText(message);
        
        ImageView icon = layout.findViewById(R.id.toast_icon);
        icon.setImageResource(android.R.drawable.ic_menu_myplaces);
        
        Toast toast = new Toast(activity.getApplicationContext());
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    /**
     * Muestra un toast personalizado con diferentes estilos según el tipo.
     *
     * @param activity La actividad actual
     * @param message El mensaje a mostrar
     * @param type El tipo de toast (SUCCESS, ERROR, WARNING, INFO)
     */
    public static void showCustomToast(Activity activity, String message, ToastType type) {
        int layoutRes;
        switch (type) {
            case SUCCESS:
                layoutRes = R.layout.toast_success;
                break;
            case ERROR:
                layoutRes = R.layout.toast_error;
                break;
            case WARNING:
                layoutRes = R.layout.toast_warning;
                break;
            case INFO:
                layoutRes = R.layout.toast_info;
                break;
            default:
                layoutRes = R.layout.toast_info;
        }

        View toastView = activity.getLayoutInflater().inflate(layoutRes, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        
        Toast toast = new Toast(activity);
        // Usar duración más corta para mensajes de información
        if (type == ToastType.INFO) {
            toast.setDuration(Toast.LENGTH_SHORT);
        } else {
            toast.setDuration(Toast.LENGTH_LONG);
        }
        toast.setView(toastView);
        toast.show();
    }
} 