package com.us.eventum.core.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.us.eventum.R;

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
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int topInset = Math.max(statusBars.top, cutout.top);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = topInset;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(stripe);
    }

    /**
     * Edge-to-edge con franja azul bajo la status bar (login, home organizador/asistente).
     */
    public static void enableEdgeToEdgeWithStatusBarStripe(@NonNull Activity activity,
                                                           @NonNull View stripe) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        applyStatusBarStripe(stripe);
    }

    /**
     * Padding inferior para listas detrás de la barra FAB flotante.
     * No suma la barra de navegación del sistema: el dock FAB ya la respeta.
     */
    public static void applyOverlayFabListPadding(@NonNull View listView) {
        android.content.res.Resources res = listView.getResources();
        int bottom = (int) (res.getDimension(R.dimen.list_fab_clearance)
                + res.getDimension(R.dimen.list_bottom_extra_gap));
        listView.setPadding(
                listView.getPaddingLeft(),
                listView.getPaddingTop(),
                listView.getPaddingRight(),
                bottom);
    }
}
