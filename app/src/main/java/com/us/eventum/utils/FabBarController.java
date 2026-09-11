package com.us.eventum.utils;

import android.app.Activity;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Barra de acciones FAB en pastilla: botón para plegar hacia un lado y tirador para desplegar.
 */
public final class FabBarController {

    private static final long ANIM_MS = 260L;
    private static final int MIN_FLING_PX = 56;

    private final View fabDock;
    private final View fabContainer;
    private final View fabBarHandle;
    private final GestureDetector gestureDetector;
    private boolean expanded = true;

    public FabBarController(@NonNull Context context,
                            @NonNull View fabDock,
                            @NonNull View fabContainer,
                            @NonNull View fabBarHandle,
                            @Nullable View fabBarCollapse) {
        this.fabDock = fabDock;
        this.fabContainer = fabContainer;
        this.fabBarHandle = fabBarHandle;

        fabBarHandle.setVisibility(View.GONE);
        fabBarHandle.setAlpha(0f);
        fabBarHandle.setOnClickListener(v -> show());

        if (fabBarCollapse != null) {
            fabBarCollapse.setOnClickListener(v -> hide());
        }

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();
                if (Math.abs(deltaX) <= Math.abs(deltaY) || Math.abs(deltaX) < MIN_FLING_PX) {
                    return false;
                }
                if (deltaX < 0) {
                    hide();
                } else {
                    show();
                }
                return true;
            }
        });

        View.OnTouchListener flingListener = (v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        };
        fabDock.setOnTouchListener(flingListener);
        fabContainer.setOnTouchListener(flingListener);

        fabContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private boolean initialCollapseDone;

            @Override
            public boolean onPreDraw() {
                if (!initialCollapseDone && fabContainer.getWidth() > 0) {
                    initialCollapseDone = true;
                    collapseImmediate();
                }
                return true;
            }
        });
    }

    public void attachToActivity(@NonNull Activity activity) {
        WindowInsetsHelper.applyBottomNavigationBarMargin(activity, fabDock);
        fabContainer.post(this::collapseImmediate);
    }

    /** Plegar sin animación (estado inicial al entrar en pantalla). */
    public void collapseImmediate() {
        expanded = false;
        fabContainer.animate().cancel();
        float distance = fabContainer.getWidth();
        if (distance <= 0f) {
            return;
        }
        distance += fabContainer.getResources().getDisplayMetrics().density * 8f;
        fabContainer.setTranslationX(distance);
        fabBarHandle.animate().cancel();
        fabBarHandle.setVisibility(View.VISIBLE);
        fabBarHandle.setAlpha(1f);
        fabBarHandle.bringToFront();
    }

    public void hide() {
        if (!expanded) {
            return;
        }
        expanded = false;
        fabContainer.post(() -> {
            float distance = fabContainer.getWidth() + fabContainer.getResources()
                    .getDisplayMetrics().density * 8f;
            fabContainer.animate()
                    .translationX(distance)
                    .setDuration(ANIM_MS)
                    .withEndAction(this::showHandle)
                    .start();
        });
    }

    public void show() {
        if (expanded) {
            return;
        }
        expanded = true;
        fabBarHandle.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            fabBarHandle.setVisibility(View.GONE);
            fabContainer.animate()
                    .translationX(0f)
                    .setDuration(ANIM_MS)
                    .start();
        }).start();
    }

    private void showHandle() {
        fabBarHandle.setVisibility(View.VISIBLE);
        fabBarHandle.bringToFront();
        fabBarHandle.animate().alpha(1f).setDuration(180).start();
    }

    public boolean isExpanded() {
        return expanded;
    }
}
