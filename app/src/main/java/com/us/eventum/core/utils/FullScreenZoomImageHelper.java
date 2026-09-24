package com.us.eventum.core.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.us.eventum.R;

public final class FullScreenZoomImageHelper {

    private FullScreenZoomImageHelper() {
    }

    /** Fondo con scrim al 60%. */
    @NonNull
    public static Dialog createWithScrim(@NonNull Activity activity) {
        Dialog dialog = new Dialog(activity, R.style.ThemeOverlay_Eventum_FullScreenZoom);
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_fullscreen_zoom_image, null);
        content.setBackgroundColor(ContextCompat.getColor(activity, R.color.fullscreen_image_scrim));
        content.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(content);
        applyWindow(dialog);

        ImageView imageView = imageView(dialog);
        int marginPx = (int) (64 * activity.getResources().getDisplayMetrics().density);
        imageView.setMaxWidth(activity.getResources().getDisplayMetrics().widthPixels - marginPx);
        return dialog;
    }

    @NonNull
    public static ImageView imageView(@NonNull Dialog dialog) {
        View content = dialog.findViewById(R.id.fullscreenZoomRoot);
        if (content == null) {
            throw new IllegalStateException("Contenido del visor de imagen no encontrado");
        }
        ImageView imageView = content.findViewById(R.id.fullscreenZoomImage);
        if (imageView == null) {
            throw new IllegalStateException("ImageView del visor de imagen no encontrado");
        }
        return imageView;
    }

    @NonNull
    public static ProgressBar loading(@NonNull Dialog dialog) {
        View content = dialog.findViewById(R.id.fullscreenZoomRoot);
        if (content == null) {
            throw new IllegalStateException("Contenido del visor de imagen no encontrado");
        }
        ProgressBar loading = content.findViewById(R.id.fullscreenZoomLoading);
        if (loading == null) {
            throw new IllegalStateException("ProgressBar del visor de imagen no encontrado");
        }
        return loading;
    }

    private static void applyWindow(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
    }
}
