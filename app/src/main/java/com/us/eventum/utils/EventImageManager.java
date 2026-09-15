package com.us.eventum.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import android.graphics.drawable.Drawable;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import com.us.eventum.R;
import com.us.eventum.utils.FirebaseBackendErrorHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Imágenes de evento en Storage: {@code event_images/{eventId}.jpg}.
 */
public final class EventImageManager {

    private static final String TAG = "EventImageManager";

    private static final ConcurrentHashMap<String, Long> imageVersions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Uri> pendingLocalPreviews = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> resolvedUrls = new ConcurrentHashMap<>();

    public interface ImageAvailabilityCallback {
        void onResult(boolean available);
    }

    private EventImageManager() {
    }

    public static void markEventImageUpdated(@Nullable String eventId) {
        if (eventId != null && !eventId.trim().isEmpty()) {
            String normalizedId = eventId.trim();
            imageVersions.put(normalizedId, System.currentTimeMillis());
            resolvedUrls.keySet().removeIf(key -> key.startsWith(normalizedId + "#"));
        }
    }

    public static void setPendingLocalPreview(@Nullable String eventId, @Nullable Uri uri) {
        if (eventId == null || eventId.trim().isEmpty() || uri == null) {
            return;
        }
        pendingLocalPreviews.put(eventId.trim(), uri);
    }

    public static void clearPendingLocalPreview(@Nullable String eventId) {
        if (eventId != null && !eventId.trim().isEmpty()) {
            pendingLocalPreviews.remove(eventId.trim());
        }
    }

    @Nullable
    public static Uri getPendingLocalPreview(@Nullable String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return null;
        }
        return pendingLocalPreviews.get(eventId.trim());
    }

    public static void loadEventImage(Context context, ImageView target, @Nullable String eventId) {
        loadEventImage(context, target, eventId, null);
    }

    public static void deleteEventImage(@Nullable String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        FirebaseStorage.getInstance().getReference()
                .child("event_images/" + eventId.trim() + ".jpg")
                .delete()
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo borrar la imagen del evento", e));
    }

    public static void loadEventImage(Context context, ImageView target, @Nullable String eventId,
                                      @Nullable ImageAvailabilityCallback callback) {
        if (eventId == null || eventId.trim().isEmpty()) {
            target.setImageResource(R.mipmap.ic_launcher);
            notifyAvailability(callback, false);
            return;
        }
        String normalizedId = eventId.trim();
        Uri pendingUri = getPendingLocalPreview(normalizedId);
        if (pendingUri != null) {
            loadLocalPreview(context, target, pendingUri);
            notifyAvailability(callback, true);
            return;
        }
        long version = imageVersions.getOrDefault(normalizedId, 0L);
        String loadKey = normalizedId + "#" + version;
        if (loadKey.equals(target.getTag(R.id.tag_image_load_key)) && target.getDrawable() != null) {
            notifyAvailability(callback, true);
            return;
        }
        Object previousTag = target.getTag(R.id.tag_image_load_key);
        if (previousTag != null && !loadKey.equals(previousTag)) {
            target.setImageResource(R.mipmap.ic_launcher);
        }
        target.setTag(R.id.tag_image_load_key, loadKey);

        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null) {
            applyRemoteImage(context, target, cachedUrl, version, callback);
            return;
        }

        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("event_images/" + normalizedId + ".jpg");
        ref.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    if (!loadKey.equals(target.getTag(R.id.tag_image_load_key))) {
                        return;
                    }
                    resolvedUrls.put(loadKey, uri.toString());
                    applyRemoteImage(context, target, uri.toString(), version, callback);
                })
                .addOnFailureListener(e -> {
                    if (!loadKey.equals(target.getTag(R.id.tag_image_load_key))) {
                        return;
                    }
                    target.setImageResource(R.mipmap.ic_launcher);
                    notifyAvailability(callback, false);
                });
    }

    private static void applyRemoteImage(Context context, ImageView target, String url, long version,
                                         @Nullable ImageAvailabilityCallback callback) {
        Glide.with(context)
                .load(url)
                .signature(new ObjectKey(version))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.mipmap.ic_launcher)
                .dontAnimate()
                .centerCrop()
                .into(target);
        notifyAvailability(callback, true);
    }

    private static void notifyAvailability(@Nullable ImageAvailabilityCallback callback, boolean available) {
        if (callback != null) {
            callback.onResult(available);
        }
    }

    /**
     * Muestra la foto del evento ampliada a pantalla completa (tocar para cerrar).
     */
    public static void showFullScreenEventImage(@NonNull Context context, @Nullable String eventId) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || eventId == null || eventId.trim().isEmpty()) {
            return;
        }

        String normalizedId = eventId.trim();
        Uri pendingUri = getPendingLocalPreview(normalizedId);
        if (pendingUri != null) {
            openFullScreenEventDialog(activity, normalizedId, pendingUri.toString(), true);
            return;
        }

        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("event_images/" + normalizedId + ".jpg");
        ref.getDownloadUrl()
                .addOnSuccessListener(uri ->
                        openFullScreenEventDialog(activity, normalizedId, uri.toString(), false))
                .addOnFailureListener(e -> { /* sin foto: no abrir */ });
    }

    private static void openFullScreenEventDialog(@NonNull Activity activity, @NonNull String eventId,
                                                  @NonNull String imageUrl, boolean isLocalUri) {
        if (activity.isFinishing()) {
            return;
        }

        Dialog dialog = FullScreenZoomImageHelper.createWithScrim(activity);
        ImageView imageView = FullScreenZoomImageHelper.imageView(dialog);
        ProgressBar loading = FullScreenZoomImageHelper.loading(dialog);
        dialog.show();

        long version = imageVersions.getOrDefault(eventId, 0L);
        Object loadSource = isLocalUri ? Uri.parse(imageUrl) : imageUrl;
        // with(Activity) está deprecado en Glide 5
        Glide.with(imageView)
                .load(loadSource)
                .signature(new ObjectKey(version))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource) {
                        loading.setVisibility(View.GONE);
                        dialog.dismiss();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource dataSource,
                            boolean isFirstResource) {
                        loading.setVisibility(View.GONE);
                        return false;
                    }
                })
                .fitCenter()
                .into(imageView);
    }

    public static void loadLocalPreview(Context context, ImageView target, Uri uri) {
        Glide.with(context)
                .load(uri)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .centerCrop()
                .into(target);
    }

    public interface UploadCallback {
        void onSuccess();

        void onError(String message);
    }

    public static void uploadEventImage(Context context, Uri imageUri, String eventId,
                                        UploadCallback callback) {
        if (imageUri == null || eventId == null || eventId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(FirebaseBackendErrorHandler.getIncompleteImageDataMessage(context));
            }
            return;
        }
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("event_images/" + eventId.trim() + ".jpg");
        try {
            Bitmap bitmap = decodeBitmapFromUri(context, imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            UploadTask task = ref.putBytes(baos.toByteArray());
            task.addOnSuccessListener(snapshot -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error subiendo imagen del evento", e);
                if (callback != null) {
                    callback.onError(FirebaseBackendErrorHandler.getErrorMessage(
                            e, R.string.backend_op_upload_event_image));
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error procesando imagen", e);
            if (callback != null) {
                callback.onError(FirebaseBackendErrorHandler.getProcessImageFailedMessage(context));
            }
        }
    }

    private static Bitmap decodeBitmapFromUri(Context context, Uri imageUri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException("No se pudo abrir la imagen");
            }
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                throw new IOException("No se pudo decodificar la imagen");
            }
            return bitmap;
        }
    }
}
