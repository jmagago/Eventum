package com.us.eventum.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.us.eventum.R;
import de.hdodenhof.circleimageview.CircleImageView;

import java.util.concurrent.ConcurrentHashMap;

public class ProfileImageManager {
    private static final String TAG = "ProfileImageManager";

    private static final ConcurrentHashMap<String, Long> imageVersions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> resolvedUrls = new ConcurrentHashMap<>();

    public interface ImageAvailabilityCallback {
        void onResult(boolean available);
    }

    // Cache simple para evitar recargas innecesarias
    private static String currentImageUri;
    private static CircleImageView currentImageView;
    private static String currentUserId;

    public static void markProfileImageUpdated(@Nullable String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            String normalizedId = userId.trim();
            imageVersions.put(normalizedId, System.currentTimeMillis());
            resolvedUrls.keySet().removeIf(key -> key.startsWith(normalizedId + "#"));
            if (normalizedId.equals(currentUserId)) {
                currentImageUri = null;
            }
        }
    }

    /**
     * Carga la foto de perfil de un usuario concreto (misma convención que en Storage: {@code profile_images/{uid}.jpg}).
     * Pensado para listas (p. ej. asistentes a un evento); no usa la caché estática del propio perfil.
     */
    public static void loadProfileImageForUserId(Context context, ImageView target, String userId) {
        loadProfileImageForUserId(context, target, userId, null);
    }

    public static void loadProfileImageForUserId(Context context, ImageView target, String userId,
                                                 @Nullable ImageAvailabilityCallback callback) {
        if (userId == null || userId.trim().isEmpty()) {
            target.setImageResource(R.drawable.default_profile);
            notifyAvailability(callback, false);
            return;
        }
        String normalizedUserId = userId.trim();
        long version = imageVersions.getOrDefault(normalizedUserId, 0L);
        String loadKey = normalizedUserId + "#" + version;
        if (loadKey.equals(target.getTag(R.id.tag_image_load_key)) && target.getDrawable() != null) {
            notifyAvailability(callback, true);
            return;
        }
        Object previousTag = target.getTag(R.id.tag_image_load_key);
        if (previousTag != null && !loadKey.equals(previousTag)) {
            target.setImageResource(R.drawable.default_profile);
        }
        target.setTag(R.id.tag_image_load_key, loadKey);

        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null) {
            applyRemoteProfileImage(context, target, cachedUrl, version, callback);
            return;
        }

        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + normalizedUserId + ".jpg");
        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    if (!loadKey.equals(target.getTag(R.id.tag_image_load_key))) {
                        return;
                    }
                    resolvedUrls.put(loadKey, uri.toString());
                    applyRemoteProfileImage(context, target, uri.toString(), version, callback);
                })
                .addOnFailureListener(e -> {
                    if (!loadKey.equals(target.getTag(R.id.tag_image_load_key))) {
                        return;
                    }
                    target.setImageResource(R.drawable.default_profile);
                    notifyAvailability(callback, false);
                });
    }

    private static void applyRemoteProfileImage(Context context, ImageView target, String url, long version,
                                                @Nullable ImageAvailabilityCallback callback) {
        Glide.with(context)
                .load(url)
                .signature(new com.bumptech.glide.signature.ObjectKey(version))
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

    /**
     * Muestra la foto de perfil ampliada a pantalla completa (tocar para cerrar).
     */
    public static void showFullScreenProfileImage(@NonNull Context context, @Nullable String userId) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || userId == null || userId.trim().isEmpty()) {
            return;
        }

        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + userId.trim() + ".jpg");
        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> openFullScreenProfileDialog(activity, uri.toString()))
                .addOnFailureListener(e -> { /* sin foto: no abrir */ });
    }

    private static void openFullScreenProfileDialog(@NonNull Activity activity, @NonNull String imageUrl) {
        if (activity.isFinishing()) {
            return;
        }

        Dialog dialog = FullScreenZoomImageHelper.createWithScrim(activity);
        ImageView imageView = FullScreenZoomImageHelper.imageView(dialog);
        ProgressBar loading = FullScreenZoomImageHelper.loading(dialog);
        dialog.show();

        // with(Activity) está deprecado en Glide 5
        Glide.with(imageView)
                .load(imageUrl)
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

    /**
     * Carga la imagen de perfil desde Firebase Storage (online-only)
     */
    public static void loadProfileImage(Context context, CircleImageView profileImageView) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d(TAG, "Usuario no autenticado");
            profileImageView.setImageResource(R.drawable.default_profile);
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        long version = imageVersions.getOrDefault(userId, 0L);
        String loadKey = userId + "#" + version;
        if (loadKey.equals(profileImageView.getTag(R.id.tag_image_load_key))
                && profileImageView.getDrawable() != null
                && profileImageView == currentImageView) {
            return;
        }

        String cachedUrl = resolvedUrls.get(loadKey);
        if (cachedUrl != null) {
            profileImageView.setTag(R.id.tag_image_load_key, loadKey);
            currentUserId = userId;
            Glide.with(context)
                    .load(cachedUrl)
                    .signature(new com.bumptech.glide.signature.ObjectKey(version))
                    .error(R.drawable.default_profile)
                    .dontAnimate()
                    .circleCrop()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            currentImageUri = cachedUrl;
                            currentImageView = profileImageView;
                            return false;
                        }
                    })
                    .into(profileImageView);
            return;
        }

        loadFromFirebaseStorage(context, profileImageView, userId, loadKey, version);
    }
    
    /**
     * Carga imagen desde URI (para uso interno)
     */
    public static void loadImageFromUri(Context context, CircleImageView profileImageView, String uri) {
        // Verificar si ya tenemos la misma URI cargada
        if (uri.equals(currentImageUri) && profileImageView == currentImageView && profileImageView.getDrawable() != null) {
            return;
        }
        
        // Limpiar cache para forzar recarga y detectar cambios
        Glide.with(context).clear(profileImageView);
        Glide.with(context)
            .load(Uri.parse(uri))
            .placeholder(R.drawable.default_profile)
            .error(R.drawable.default_profile)
            .circleCrop()
            .into(profileImageView);
        currentImageUri = uri; // Actualizar URI actual
        currentImageView = profileImageView; // Actualizar ImageView actual
    }
    
    /**
     * Carga imagen desde Firebase Storage
     */
    private static void loadFromFirebaseStorage(Context context, CircleImageView profileImageView,
                                                String userId, String loadKey, long version) {
        profileImageView.setTag(R.id.tag_image_load_key, loadKey);
        currentUserId = userId;
        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + userId + ".jpg");

        profileRef.getDownloadUrl()
            .addOnSuccessListener(uri -> {
                if (!loadKey.equals(profileImageView.getTag(R.id.tag_image_load_key))) {
                    return;
                }
                resolvedUrls.put(loadKey, uri.toString());
                Glide.with(context)
                    .load(uri.toString())
                    .signature(new com.bumptech.glide.signature.ObjectKey(version))
                    .error(R.drawable.default_profile)
                    .dontAnimate()
                    .circleCrop()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            currentImageUri = uri.toString();
                            currentImageView = profileImageView;
                            return false;
                        }
                    })
                    .into(profileImageView);
            })
            .addOnFailureListener(e -> {
                if (!loadKey.equals(profileImageView.getTag(R.id.tag_image_load_key))) {
                    return;
                }
                profileImageView.setImageResource(R.drawable.default_profile);
            });
    }
    
    /**
     * Limpia el cache (útil para logout)
     */
    public static void clearCache() {
        currentImageUri = null;
        currentImageView = null;
        currentUserId = null;
        imageVersions.clear();
        resolvedUrls.clear();
    }
    
    /**
     * Actualiza la URI actual (útil cuando se cambia la imagen)
     */
    public static void updateCurrentUri(String uri, CircleImageView profileImageView) {
        currentImageUri = uri;
        currentImageView = profileImageView;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentUserId = userId;
            markProfileImageUpdated(userId);
            long version = imageVersions.getOrDefault(userId, 0L);
            profileImageView.setTag(R.id.tag_image_load_key, userId + "#" + version);
        }
    }
}
