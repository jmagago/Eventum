package com.us.eventum.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.us.eventum.R;
import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileImageManager {
    private static final String TAG = "ProfileImageManager";
    
    // Cache simple para evitar recargas innecesarias
    private static String currentImageUri;
    private static CircleImageView currentImageView;
    
    /**
     * Carga la foto de perfil de un usuario concreto (misma convención que en Storage: {@code profile_images/{uid}.jpg}).
     * Pensado para listas (p. ej. asistentes a un evento); no usa la caché estática del propio perfil.
     */
    public static void loadProfileImageForUserId(Context context, ImageView target, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            target.setImageResource(R.drawable.default_profile);
            return;
        }
        StorageReference profileRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + userId.trim() + ".jpg");
        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    Glide.with(context).clear(target);
                    Glide.with(context)
                            .load(uri.toString())
                            .placeholder(R.drawable.default_profile)
                            .error(R.drawable.default_profile)
                            .circleCrop()
                            .into(target);
                })
                .addOnFailureListener(e -> target.setImageResource(R.drawable.default_profile));
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
        
        // Cargar directamente desde Firebase Storage
        loadFromFirebaseStorage(context, profileImageView, userId);
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
    private static void loadFromFirebaseStorage(Context context, CircleImageView profileImageView, String userId) {
        StorageReference profileRef = FirebaseStorage.getInstance().getReference().child("profile_images/" + userId + ".jpg");
        
        profileRef.getDownloadUrl()
            .addOnSuccessListener(uri -> {
                // Limpiar cache de Glide para esta URL específica
                Glide.with(context).clear(profileImageView);
                
                // Cargar la imagen usando Glide
                Glide.with(context)
                    .load(uri.toString())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            // Error interno de Glide - no mostrar al usuario
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // Actualizar cache para evitar recargas innecesarias
                            currentImageUri = uri.toString();
                            currentImageView = profileImageView;
                            return false;
                        }
                    })
                    .into(profileImageView);
            })
            .addOnFailureListener(e -> {
                // Establecer imagen por defecto
                profileImageView.setImageResource(R.drawable.default_profile);
            });
    }
    
    /**
     * Limpia el cache (útil para logout)
     */
    public static void clearCache() {
        currentImageUri = null;
        currentImageView = null;
    }
    
    /**
     * Actualiza la URI actual (útil cuando se cambia la imagen)
     */
    public static void updateCurrentUri(String uri, CircleImageView profileImageView) {
        currentImageUri = uri;
        currentImageView = profileImageView;
    }
}
