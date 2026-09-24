package com.us.eventum.core.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;
import com.us.eventum.R;

/**
 * Utilidad para mostrar mensajes temporales con diseño personalizado (Snackbar).
 */
public class ToastUtils {

    public enum ToastType {
        SUCCESS, ERROR, WARNING, INFO
    }

    private static View inflateWithoutAttach(LayoutInflater inflater, int layoutRes) {
        return inflater.inflate(layoutRes, new FrameLayout(inflater.getContext()), false);
    }

    public static void showCustomToastOnAnchor(@NonNull View anchor, String message, ToastType type) {
        showCustomToastOnAnchor(anchor, message, type, dpToPx(anchor.getContext(), 16));
    }

    public static void showCustomToastOnAnchor(@NonNull View anchor, String message, ToastType type,
                                               int bottomMarginPx) {
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
            default:
                layoutRes = R.layout.toast_info;
                break;
        }

        View toastView = inflateWithoutAttach(LayoutInflater.from(anchor.getContext()), layoutRes);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);

        int duration = type == ToastType.ERROR ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
        showSnackbar(anchor, toastView, duration, bottomMarginPx);
    }

    /**
     * Muestra un mensaje anclado a un panel overlay (por encima del scrim).
     */
    public static void showPanelToast(@NonNull View panelOverlay, String message, ToastType type) {
        showCustomToastOnAnchor(panelOverlay, message, type, dpToPx(panelOverlay.getContext(), 96));
    }

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
            default:
                layoutRes = R.layout.toast_info;
                break;
        }

        View toastView = inflateWithoutAttach(activity.getLayoutInflater(), layoutRes);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);

        int duration = type == ToastType.ERROR ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
        showSnackbar(activity, toastView, duration, dpToPx(activity, 80));
    }

    private static void showSnackbar(@NonNull View anchor, View customView, int duration, int bottomMarginPx) {
        Snackbar snackbar = Snackbar.make(anchor, "", duration);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.TRANSPARENT);
        snackbarView.setElevation(dpToPx(anchor.getContext(), 24));

        ViewGroup.LayoutParams layoutParams = snackbarView.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
            marginParams.bottomMargin = bottomMarginPx;
            snackbarView.setLayoutParams(marginParams);
        }

        if (snackbarView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) snackbarView;
            group.removeAllViews();
            group.setPadding(0, 0, 0, 0);

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(group.getContext());
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            android.widget.FrameLayout.LayoutParams childLp =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER_HORIZONTAL);
            wrapper.addView(customView, childLp);
            group.addView(wrapper);
        }

        snackbar.show();
    }

    private static void showSnackbar(Activity activity, View customView, int duration, int bottomMarginPx) {
        View anchor = findSnackbarAnchor(activity);
        if (anchor == null) {
            return;
        }
        showSnackbar(anchor, customView, duration, bottomMarginPx);
    }

    private static int dpToPx(android.content.Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static View findSnackbarAnchor(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup contentGroup = (ViewGroup) content;
            if (contentGroup.getChildCount() > 0) {
                View rootChild = contentGroup.getChildAt(0);
                if (rootChild instanceof CoordinatorLayout || rootChild instanceof FrameLayout) {
                    return rootChild;
                }
            }
        }
        return content;
    }

    private static int dpToPx(Activity activity, int dp) {
        return dpToPx((android.content.Context) activity, dp);
    }
}
