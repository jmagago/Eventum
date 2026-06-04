package com.us.eventum.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Ajusta márgenes inferiores según la barra de navegación del sistema.
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    public static void applyBottomNavigationBarMargin(@NonNull Activity activity,
                                                      @NonNull View... views) {
        activity.getWindow().getDecorView().post(() -> {
            if (activity.isFinishing()) {
                return;
            }
            for (View view : views) {
                if (view != null) {
                    applyBottomNavigationBarMargin(view);
                }
            }
        });
    }

    public static void applyBottomNavigationBarMargin(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        final int baseBottomMargin = marginParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            marginParams.bottomMargin = baseBottomMargin + navBars.bottom;
            v.setLayoutParams(marginParams);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void applyStatusBarStripe(@NonNull View stripe) {
        ViewCompat.setOnApplyWindowInsetsListener(stripe, (v, windowInsets) -> {
            int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(stripe);
    }
}
