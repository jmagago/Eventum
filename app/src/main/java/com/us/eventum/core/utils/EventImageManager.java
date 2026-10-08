package com.us.eventum.core.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Imágenes de evento en Storage: {@code event_images/{eventId}.jpg}.
 * La versión de caché es {@link Event#getImageUpdatedAt()} (Firestore).
 */
public final class EventImageManager {

    private static final String TAG = "EventImageManager";

    /** Cache URL remota por {@code eventId#imageUpdatedAt}. */
    private static final ConcurrentHashMap<String, String> resolvedUrls = new ConcurrentHashMap<>();

    public interface ImageAvailabilityCallback {
        void onResult(boolean available);
    }

    public interface UploadCallback {
        void onSuccess();

        void onError(String message);
    }

    private EventImageManager() {
    }

    public static void loadEventImage(Context context, ImageView target, @Nullable Event event) {
        loadEventImage(context, target, event, null);
    }

    public static void loadEventImage(Context context, ImageView target, @Nullable Event event,
                                      @Nullable ImageAvailabilityCallback callback) {
        if (event == null) {
            loadInternal(context, target, null, 0L, callback);
            return;
        }
        loadInternal(context, target, event.getId(), event.getImageUpdatedAt(), callback);
    }

    private static void loadInternal(Context context, ImageView target, @Nullable String eventId,
                                     long imageUpdatedAt, @Nullable ImageAvailabilityCallback callback) {
        if (target == null) {
            notifyAvailability(callback, false);
            return;
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            target.setImageResource(R.mipmap.ic_launcher);
            notifyAvailability(callback, false);
            return;
        }

        String normalizedId = eventId.trim();
        long version = Math.max(0L, imageUpdatedAt);
        String loadKey = normalizedId + "#" + version;

        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null
                && loadKey.equals(target.getTag(R.id.tag_image_load_key))
                && target.getDrawable() != null) {
            notifyAvailability(callback, true);
            return;
        }

        Object previousTag = target.getTag(R.id.tag_image_load_key);
        if (previousTag != null && !loadKey.equals(previousTag)) {
            target.setImageResource(R.mipmap.ic_launcher);
        }
        target.setTag(R.id.tag_image_load_key, loadKey);

        if (cachedUrl != null) {
            applyRemoteImage(target, cachedUrl, version, callback);
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
                    applyRemoteImage(target, uri.toString(), version, callback);
                })
                .addOnFailureListener(e -> {
                    if (!loadKey.equals(target.getTag(R.id.tag_image_load_key))) {
                        return;
                    }
                    target.setImageResource(R.mipmap.ic_launcher);
                    notifyAvailability(callback, false);
                });
    }

    public static void deleteEventImage(@Nullable String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        String normalizedId = eventId.trim();
        resolvedUrls.keySet().removeIf(key -> key.startsWith(normalizedId + "#"));
        FirebaseStorage.getInstance().getReference()
                .child("event_images/" + normalizedId + ".jpg")
                .delete()
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo borrar la imagen del evento", e));
    }

    public static void showFullScreenEventImage(@NonNull Context context, @Nullable Event event) {
        if (event == null) {
            return;
        }
        showFullScreenInternal(context, event.getId(), event.getImageUpdatedAt());
    }

    private static void showFullScreenInternal(@NonNull Context context, @Nullable String eventId,
                                               long imageUpdatedAt) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || eventId == null || eventId.trim().isEmpty()) {
            return;
        }

        String normalizedId = eventId.trim();
        long version = Math.max(0L, imageUpdatedAt);
        String loadKey = normalizedId + "#" + version;
        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null) {
            openFullScreenEventDialog(activity, cachedUrl, version);
            return;
        }

        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("event_images/" + normalizedId + ".jpg");
        ref.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    resolvedUrls.put(loadKey, uri.toString());
                    openFullScreenEventDialog(activity, uri.toString(), version);
                })
                .addOnFailureListener(e -> { /* sin foto: no abrir */ });
    }

    private static void openFullScreenEventDialog(@NonNull Activity activity, @NonNull String imageUrl,
                                                  long version) {
        if (activity.isFinishing()) {
            return;
        }

        Dialog dialog = FullScreenZoomImageHelper.createWithScrim(activity);
        ImageView imageView = FullScreenZoomImageHelper.imageView(dialog);
        ProgressBar loading = FullScreenZoomImageHelper.loading(dialog);
        dialog.show();

        Glide.with(imageView)
                .load(imageUrl)
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
        if (target == null || uri == null) {
            return;
        }
        RequestBuilder<Drawable> request = Glide.with(target)
                .load(uri)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .dontAnimate();
        if (target instanceof CircleImageView) {
            request = request.circleCrop();
        } else {
            request = request.centerCrop();
        }
        request.into(target);
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

    private static void applyRemoteImage(ImageView target, String url, long version,
                                         @Nullable ImageAvailabilityCallback callback) {
        RequestBuilder<Drawable> request = Glide.with(target)
                .load(url)
                .signature(new ObjectKey(version))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.mipmap.ic_launcher)
                .dontAnimate();
        if (target instanceof CircleImageView) {
            request = request.circleCrop();
        } else {
            request = request.centerCrop();
        }
        request.into(target);
        notifyAvailability(callback, true);
    }

    private static void notifyAvailability(@Nullable ImageAvailabilityCallback callback, boolean available) {
        if (callback != null) {
            callback.onResult(available);
        }
    }

    private static Bitmap decodeBitmapFromUri(Context context, Uri imageUri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException(context.getString(R.string.error_open_image));
            }
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                throw new IOException(context.getString(R.string.error_decode_image));
            }
            return bitmap;
        }
    }
}
