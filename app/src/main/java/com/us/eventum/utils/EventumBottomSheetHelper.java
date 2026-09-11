package com.us.eventum.utils;

import android.graphics.Rect;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;

public final class EventumBottomSheetHelper {

    public static final class Sheet {
        public final BottomSheetDialog dialog;
        public final View root;

        Sheet(@NonNull BottomSheetDialog dialog, @NonNull View root) {
            this.dialog = dialog;
            this.root = root;
        }
    }

    private EventumBottomSheetHelper() {
    }

    @NonNull
    public static Sheet create(@NonNull AppCompatActivity activity, int layoutResId) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_Eventum_BottomSheetDialog);
        View root = activity.getLayoutInflater().inflate(layoutResId, null, false);
        dialog.setContentView(root);
        dialog.setDismissWithAnimation(true);
        dialog.setCanceledOnTouchOutside(true);
        configureStandardBehavior(dialog);
        return new Sheet(dialog, root);
    }

    /**
     * Expande el panel tras el primer layout. Para hojas simples sin scroll/footer fijos.
     */
    public static void configureStandardBehavior(@NonNull BottomSheetDialog dialog) {
        dialog.setOnShowListener(d -> expandAfterLayout((BottomSheetDialog) d, null, null, 0, 0, false));
    }

    /**
     * Formulario editable: scroll ajustado al contenido (con tope), footer fijo y soporte de teclado.
     */
    public static void configureExpandedPanel(@NonNull AppCompatActivity activity,
                                              @NonNull BottomSheetDialog dialog,
                                              @NonNull View contentRoot,
                                              @IdRes int scrollViewId,
                                              @IdRes int footerId) {
        dialog.setOnShowListener(d ->
                expandAfterLayout((BottomSheetDialog) d, activity, contentRoot, scrollViewId, footerId, true));
    }

    /**
     * Panel de solo lectura (detalle, QR): sin teclado; el scroll ocupa solo lo que necesita el contenido.
     */
    public static void configureDisplayPanel(@NonNull AppCompatActivity activity,
                                             @NonNull BottomSheetDialog dialog,
                                             @NonNull View contentRoot,
                                             @IdRes int scrollViewId,
                                             @IdRes int footerId) {
        dialog.setOnShowListener(d ->
                expandAfterLayout((BottomSheetDialog) d, activity, contentRoot, scrollViewId, footerId, false));
    }

    private static void expandAfterLayout(@NonNull BottomSheetDialog dialog,
                                          @Nullable AppCompatActivity activity,
                                          @Nullable View contentRoot,
                                          @IdRes int scrollViewId,
                                          @IdRes int footerId,
                                          boolean keyboardAware) {
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        bottomSheet.setClipToOutline(true);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(true);

        if (activity != null && contentRoot != null && scrollViewId != 0 && footerId != 0) {
            int topOffset = activity.getResources().getDimensionPixelSize(R.dimen.panel_bottom_sheet_top_offset);
            int maxSheetHeight = activity.getResources().getDisplayMetrics().heightPixels - topOffset;
            behavior.setMaxHeight(maxSheetHeight);

            NestedScrollView scrollView = contentRoot.findViewById(scrollViewId);
            View footer = contentRoot.findViewById(footerId);
            bottomSheet.post(() -> applyScrollAndExpand(
                    activity, bottomSheet, behavior, scrollView, footer, maxSheetHeight, keyboardAware));
            return;
        }

        bottomSheet.post(() -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
    }

    private static void applyScrollAndExpand(@NonNull AppCompatActivity activity,
                                             @NonNull FrameLayout bottomSheet,
                                             @NonNull BottomSheetBehavior<FrameLayout> behavior,
                                             @Nullable NestedScrollView scrollView,
                                             @Nullable View footer,
                                             int maxSheetHeight,
                                             boolean keyboardAware) {
        if (scrollView != null && footer != null) {
            applyScrollHeight(activity, bottomSheet, scrollView, footer, maxSheetHeight);
            if (keyboardAware) {
                enableKeyboardAwareScroll(scrollView, behavior);
            }
            scrollView.scrollTo(0, 0);
        }

        bottomSheet.requestLayout();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private static void applyScrollHeight(@NonNull AppCompatActivity activity,
                                          @NonNull FrameLayout bottomSheet,
                                          @NonNull NestedScrollView scrollView,
                                          @NonNull View footer,
                                          int maxSheetHeight) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(bottomSheet);
        int navInset = insets != null
                ? insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                : 0;
        int headerHeight = activity.getResources().getDimensionPixelSize(
                R.dimen.panel_bottom_sheet_header_height);
        int sheetWidth = bottomSheet.getWidth();
        if (sheetWidth <= 0) {
            sheetWidth = activity.getResources().getDisplayMetrics().widthPixels;
        }
        int footerHeight = measureFooterHeight(footer, sheetWidth, activity);
        int scrollMax = maxSheetHeight - headerHeight - footerHeight - navInset;
        int contentHeight = measureContentHeight(scrollView, sheetWidth);
        int scrollHeight = scrollMax;
        if (contentHeight > 0) {
            scrollHeight = Math.min(scrollMax, contentHeight);
        }
        if (scrollHeight > 0) {
            ViewGroup.LayoutParams scrollParams = scrollView.getLayoutParams();
            scrollParams.height = scrollHeight;
            scrollView.setLayoutParams(scrollParams);
        }
    }

    private static int measureContentHeight(@NonNull NestedScrollView scrollView, int width) {
        if (scrollView.getChildCount() == 0) {
            return 0;
        }
        View child = scrollView.getChildAt(0);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        child.measure(widthSpec, heightSpec);
        return child.getMeasuredHeight();
    }

    private static void enableKeyboardAwareScroll(@NonNull NestedScrollView scrollView,
                                                  @NonNull BottomSheetBehavior<FrameLayout> behavior) {
        final int[] baseScrollHeight = {-1};

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
            int imeInset = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            if (imeInset == 0) {
                restoreScrollHeight(scrollView, baseScrollHeight);
            } else {
                int overlap = computeKeyboardOverlap(scrollView, imeInset);
                if (overlap > 0) {
                    shrinkScrollViewForKeyboard(scrollView, overlap, baseScrollHeight);
                }
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                v.postDelayed(() -> scrollFocusedFieldIntoView(scrollView), 150);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(scrollView);

        scrollView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null || !isDescendantOf(scrollView, newFocus)) {
                return;
            }
            if (!requiresKeyboardScroll(newFocus)) {
                return;
            }
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            scrollView.postDelayed(() -> scrollFocusedFieldIntoView(scrollView), 200);
        });
    }

    private static boolean requiresKeyboardScroll(@NonNull View focus) {
        if (focus instanceof AutoCompleteTextView) {
            return false;
        }
        if (!(focus instanceof EditText)) {
            return false;
        }
        if (!focus.isFocusable() || !focus.isFocusableInTouchMode()) {
            return false;
        }
        int inputClass = ((EditText) focus).getInputType() & InputType.TYPE_MASK_CLASS;
        return inputClass != InputType.TYPE_NULL;
    }

    private static void shrinkScrollViewForKeyboard(@NonNull NestedScrollView scrollView,
                                                    int overlap,
                                                    @NonNull int[] baseScrollHeight) {
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        if (baseScrollHeight[0] < 0 && params.height > 0) {
            baseScrollHeight[0] = params.height;
        }
        if (baseScrollHeight[0] <= 0) {
            return;
        }
        int minHeight = (int) (120 * scrollView.getResources().getDisplayMetrics().density);
        int newHeight = Math.max(minHeight, baseScrollHeight[0] - overlap);
        if (params.height != newHeight) {
            params.height = newHeight;
            scrollView.setLayoutParams(params);
        }
    }

    private static void restoreScrollHeight(@NonNull NestedScrollView scrollView,
                                            @NonNull int[] baseScrollHeight) {
        if (baseScrollHeight[0] <= 0) {
            return;
        }
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        params.height = baseScrollHeight[0];
        scrollView.setLayoutParams(params);
        baseScrollHeight[0] = -1;
    }

    private static int computeKeyboardOverlap(@NonNull View scrollView, int imeInset) {
        if (imeInset <= 0) {
            return 0;
        }
        int[] location = new int[2];
        scrollView.getLocationOnScreen(location);
        int scrollBottom = location[1] + scrollView.getHeight();
        int keyboardTop = scrollView.getResources().getDisplayMetrics().heightPixels - imeInset;
        return Math.max(0, scrollBottom - keyboardTop);
    }

    private static void scrollFocusedFieldIntoView(@NonNull NestedScrollView scrollView) {
        View focused = scrollView.findFocus();
        if (focused == null || !requiresKeyboardScroll(focused)) {
            return;
        }

        View target = resolveScrollTarget(focused);
        Rect rect = new Rect();
        target.getDrawingRect(rect);
        scrollView.offsetDescendantRectToMyCoords(target, rect);

        int margin = (int) (16 * scrollView.getResources().getDisplayMetrics().density);
        int scrollY = scrollView.getScrollY();
        int viewportHeight = scrollView.getHeight();

        if (rect.bottom > scrollY + viewportHeight - margin) {
            int newScrollY = rect.bottom - viewportHeight + margin;
            scrollView.smoothScrollTo(0, Math.max(0, newScrollY));
        } else if (rect.top < scrollY + margin) {
            scrollView.smoothScrollTo(0, Math.max(0, rect.top - margin));
        }
    }

    @NonNull
    private static View resolveScrollTarget(@NonNull View focused) {
        View target = focused;
        ViewParent parent = focused.getParent();
        while (parent instanceof View && !(parent instanceof NestedScrollView)) {
            if (parent instanceof TextInputLayout) {
                return (View) parent;
            }
            target = (View) parent;
            parent = parent.getParent();
        }
        return target;
    }

    private static boolean isDescendantOf(@NonNull ViewGroup parent, @NonNull View child) {
        ViewParent current = child.getParent();
        while (current instanceof View) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static int measureFooterHeight(@NonNull View footer, int sheetWidth,
                                           @NonNull AppCompatActivity activity) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                Math.max(sheetWidth, footer.getWidth()),
                View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        footer.measure(widthSpec, heightSpec);
        int measured = footer.getMeasuredHeight();
        if (measured > 0) {
            return measured;
        }
        return activity.getResources().getDimensionPixelSize(R.dimen.panel_bottom_sheet_create_event_footer);
    }

    public static void bindDestroyOnDismiss(@NonNull BottomSheetDialog dialog,
                                            @NonNull Runnable onDismissCleanup) {
        dialog.setOnDismissListener(d -> onDismissCleanup.run());
    }

    public static void bindCloseButton(@NonNull View root, @NonNull Runnable onClose) {
        View closeButton = root.findViewById(R.id.bottomSheetCloseButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> onClose.run());
        }
    }

    public static void initHeader(@NonNull View root,
                                  @DrawableRes int iconRes,
                                  @StringRes int titleRes,
                                  @StringRes int closeContentDescriptionRes) {
        ImageView icon = root.findViewById(R.id.bottomSheetHeaderIcon);
        TextView title = root.findViewById(R.id.bottomSheetTitle);
        View closeButton = root.findViewById(R.id.bottomSheetCloseButton);
        if (icon != null) {
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(iconRes);
        }
        if (title != null) {
            title.setText(titleRes);
        }
        if (closeButton != null) {
            closeButton.setContentDescription(root.getContext().getString(closeContentDescriptionRes));
        }
        setSubtitle(root, null);
    }

    public static void hideHeaderIcon(@NonNull View root) {
        ImageView icon = root.findViewById(R.id.bottomSheetHeaderIcon);
        if (icon != null) {
            icon.setVisibility(View.GONE);
        }
    }

    public static void setSubtitle(@NonNull View root, @Nullable CharSequence subtitle) {
        TextView subtitleView = root.findViewById(R.id.bottomSheetSubtitle);
        if (subtitleView == null) {
            return;
        }
        if (subtitle == null || subtitle.toString().trim().isEmpty()) {
            subtitleView.setVisibility(View.GONE);
            subtitleView.setText("");
        } else {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
        }
    }

    @NonNull
    public static View toastAnchor(@Nullable View sheetRoot, @NonNull AppCompatActivity activity) {
        return sheetRoot != null ? sheetRoot : activity.getWindow().getDecorView();
    }
}
