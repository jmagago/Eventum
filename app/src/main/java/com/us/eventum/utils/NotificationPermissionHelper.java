package com.us.eventum.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public final class NotificationPermissionHelper {

    private NotificationPermissionHelper() {
    }

    public static boolean hasPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestIfNeeded(@NonNull Activity activity,
                                       @NonNull ActivityResultLauncher<String> launcher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(activity)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
