package com.us.eventum.core.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.us.eventum.R;
import com.us.eventum.data.models.Attendee;

import java.util.concurrent.ConcurrentHashMap;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Fotos de perfil en Storage: {@code profile_images/{uid}.jpg}.
 * La versión de caché es {@link Attendee#getImageUpdatedAt()} (Firestore).
 */
public final class ProfileImageManager {

    /** Cache URL remota por {@code uid#imageUpdatedAt}. */
    private static final ConcurrentHashMap<String, String> resolvedUrls = new ConcurrentHashMap<>();

    public interface ImageAvailabilityCallback {
        void onResult(boolean available);
    }

    private ProfileImageManager() {
    }

    public static void loadProfileImage(Context context, CircleImageView profileImageView) {
        loadProfileImage(context, profileImageView, 0L);
    }

    public static void loadProfileImage(Context context, CircleImageView profileImageView,
                                        long imageUpdatedAt) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (profileImageView != null) {
                profileImageView.setImageResource(R.drawable.default_profile);
            }
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        loadInternal(context, profileImageView, userId, imageUpdatedAt, null);
    }

    public static void loadProfileImage(Context context, ImageView target, @Nullable Attendee attendee) {
        if (attendee == null) {
            loadInternal(context, target, null, 0L, null);
            return;
        }
        loadInternal(context, target, attendee.getUid(), attendee.getImageUpdatedAt(), null);
    }

    public static void loadProfileImageForUserId(Context context, ImageView target,
                                                 @Nullable Attendee attendee,
                                                 @Nullable ImageAvailabilityCallback callback) {
        if (attendee == null) {
            loadInternal(context, target, null, 0L, callback);
            return;
        }
        loadInternal(context, target, attendee.getUid(), attendee.getImageUpdatedAt(), callback);
    }

    public static void showFullScreenProfileImage(@NonNull Context context, @Nullable Attendee attendee) {
        if (attendee == null) {
            return;
        }
        showFullScreenInternal(context, attendee.getUid(), attendee.getImageUpdatedAt());
    }

    public static void clearCache() {
        resolvedUrls.clear();
    }

    private static void loadInternal(Context context, ImageView target, @Nullable String userId,
                                     long imageUpdatedAt,
                                     @Nullable ImageAvailabilityCallback callback) {
        if (target == null) {
            notifyAvailability(callback, false);
            return;
        }
        if (userId == null || userId.trim().isEmpty()) {
            target.setImageResource(R.drawable.default_profile);
            notifyAvailability(callback, false);
            return;
        }

        String normalizedId = userId.trim();
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
            target.setImageResource(R.drawable.default_profile);
        }
        target.setTag(R.id.tag_image_load_key, loadKey);

        if (cachedUrl != null) {
            applyRemoteImage(context, target, cachedUrl, version, callback);
            return;
        }

        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + normalizedId + ".jpg");
        profileRef.getDownloadUrl()
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
                    target.setImageResource(R.drawable.default_profile);
                    notifyAvailability(callback, false);
                });
    }

    private static void showFullScreenInternal(@NonNull Context context, @Nullable String userId,
                                               long imageUpdatedAt) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || userId == null || userId.trim().isEmpty()) {
            return;
        }

        String normalizedId = userId.trim();
        long version = Math.max(0L, imageUpdatedAt);
        String loadKey = normalizedId + "#" + version;
        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null) {
            openFullScreenProfileDialog(activity, cachedUrl, version);
            return;
        }

        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + normalizedId + ".jpg");
        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    resolvedUrls.put(loadKey, uri.toString());
                    openFullScreenProfileDialog(activity, uri.toString(), version);
                })
                .addOnFailureListener(e -> { /* sin foto: no abrir */ });
    }

    private static void openFullScreenProfileDialog(@NonNull Activity activity, @NonNull String imageUrl,
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

    private static void applyRemoteImage(Context context, ImageView target, String url, long version,
                                         @Nullable ImageAvailabilityCallback callback) {
        Glide.with(context).clear(target);
        Glide.with(context)
                .load(url)
                .signature(new ObjectKey(version))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .error(R.drawable.default_profile)
                .dontAnimate()
                .circleCrop()
                .into(target);
        notifyAvailability(callback, true);
    }

    private static void notifyAvailability(@Nullable ImageAvailabilityCallback callback, boolean available) {
        if (callback != null) {
            callback.onResult(available);
        }
    }
}
