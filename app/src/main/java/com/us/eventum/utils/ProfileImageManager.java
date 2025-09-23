package com.us.eventum.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.us.eventum.R;
import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileImageManager {
    private static final String TAG = "ProfileImageManager";
    private static final String PREFS_NAME = "profile_images";
    private static final String URI_KEY_PREFIX = "profile_uri_";
    
    // Cache para evitar recargas innecesarias
    private static String currentImageUri;
    private static CircleImageView currentImageView;
    
    /**
     * Carga la imagen de perfil desde URI local o Firebase Storage
     */
    public static void loadProfileImage(Context context, CircleImageView profileImageView) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d(TAG, "Usuario no autenticado");
            return;
        }
        
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String savedUri = getImageUri(context, userId);
        
        if (savedUri != null) {
            loadImageFromUri(context, profileImageView, savedUri);
            return;
        }
        
        // Si no hay URI guardada, intentar cargar desde Firebase Storage
        loadFromFirebaseStorage(context, profileImageView, userId);
    }
    
    /**
     * Carga imagen desde URI local
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
                            // Guardar la URI de Firebase para evitar recargas innecesarias
                            currentImageUri = uri.toString();
                            currentImageView = profileImageView;
                            // Guardar la URI en SharedPreferences para uso local
                            saveImageUri(context, userId, uri.toString());
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
     * Guarda la URI de la imagen en SharedPreferences
     */
    public static void saveImageUri(Context context, String userId, String imageUri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(URI_KEY_PREFIX + userId, imageUri)
            .apply();
    }
    
    /**
     * Obtiene la URI guardada de la imagen
     */
    public static String getImageUri(Context context, String userId) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(URI_KEY_PREFIX + userId, null);
    }
    
    /**
     * Limpia la URI guardada (útil para logout)
     */
    public static void clearImageUri(Context context, String userId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(URI_KEY_PREFIX + userId)
            .apply();
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
