package com.us.eventum.core.utils;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;

public final class KeyboardUtils {

    private static final int STABLE_LAYOUT_PASSES = 3;
    private static final long PANEL_SETTLE_TIMEOUT_MS = 600L;

    private KeyboardUtils() {
    }

    /** Cierra el teclado en la ventana del panel (BottomSheet), no en la Activity. */
    public static void hide(@NonNull View anchor) {
        InputMethodManager imm = inputMethodManager(anchor.getContext());
        if (imm == null) {
            return;
        }

        View root = anchor.getRootView();
        View focused = root.findFocus();

        if (focused instanceof EditText && focused != anchor) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        } else if (focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }

        if (root.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    public static void hide(@NonNull AppCompatActivity activity) {
        InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
        if (imm == null) {
            return;
        }
        View focused = activity.getCurrentFocus();
        if (focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
    }

    public static void bindExposedDropdown(@NonNull AutoCompleteTextView dropdown) {
        dropdown.setKeyListener(null);
        dropdown.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        final boolean[] opening = {false};
        TextInputLayout layout = findTextInputLayout(dropdown);

        Runnable showOnce = () -> {
            showDropdownOnce(dropdown);
            opening[0] = false;
        };

        Runnable toggleDropdown = () -> {
            if (opening[0]) {
                return;
            }
            if (dropdown.isPopupShowing()) {
                dismissDropdown(dropdown);
                return;
            }
            opening[0] = true;
            if (isImeVisible(dropdown)) {
                hide(dropdown);
                runAfterPanelSettles(dropdown, showOnce);
            } else {
                showOnce.run();
            }
        };

        dropdown.setOnClickListener(v -> toggleDropdown.run());
        dropdown.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });
        if (layout != null) {
            layout.setEndIconOnClickListener(v -> toggleDropdown.run());
        }
    }

    public static void runAfterHidingIme(@NonNull View anchor, @NonNull Runnable action) {
        if (isImeVisible(anchor)) {
            hide(anchor);
            runAfterPanelSettles(anchor, action);
        } else {
            anchor.post(action);
        }
    }

    private static void showDropdownOnce(@NonNull AutoCompleteTextView dropdown) {
        dropdown.requestFocus();
        dropdown.showDropDown();
    }

    private static void dismissDropdown(@NonNull AutoCompleteTextView dropdown) {
        if (dropdown.isPopupShowing()) {
            dropdown.dismissDropDown();
        }
    }

    @Nullable
    private static TextInputLayout findTextInputLayout(@NonNull View view) {
        ViewParent parent = view.getParent();
        return parent instanceof TextInputLayout ? (TextInputLayout) parent : null;
    }

    private static void runAfterPanelSettles(@NonNull View anchor, @NonNull Runnable action) {
        View watchView = findPanelScroll(anchor);
        if (watchView == null) {
            watchView = anchor.getRootView();
        }

        final boolean[] executed = {false};
        final View target = watchView;
        final ViewTreeObserver.OnGlobalLayoutListener[] listenerHolder = new ViewTreeObserver.OnGlobalLayoutListener[1];

        Runnable runOnce = () -> {
            if (executed[0]) {
                return;
            }
            executed[0] = true;
            if (listenerHolder[0] != null) {
                target.getViewTreeObserver().removeOnGlobalLayoutListener(listenerHolder[0]);
                listenerHolder[0] = null;
            }
            anchor.post(action);
        };

        final int[] lastAnchorY = {Integer.MIN_VALUE};
        final int[] stablePasses = {0};

        listenerHolder[0] = () -> {
            if (isImeVisible(anchor)) {
                stablePasses[0] = 0;
                if (anchor instanceof AutoCompleteTextView) {
                    dismissDropdown((AutoCompleteTextView) anchor);
                }
                return;
            }

            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int anchorY = location[1];

            if (anchorY == lastAnchorY[0]) {
                stablePasses[0]++;
                if (stablePasses[0] >= STABLE_LAYOUT_PASSES) {
                    runOnce.run();
                }
            } else {
                lastAnchorY[0] = anchorY;
                stablePasses[0] = 0;
            }
        };

        target.getViewTreeObserver().addOnGlobalLayoutListener(listenerHolder[0]);
        anchor.postDelayed(runOnce, PANEL_SETTLE_TIMEOUT_MS);
    }

    @Nullable
    private static View findPanelScroll(@NonNull View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof NestedScrollView) {
                return (View) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static boolean isImeVisible(@NonNull View anchor) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(anchor);
        return insets != null && insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0;
    }

    private static InputMethodManager inputMethodManager(@NonNull Context context) {
        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}
