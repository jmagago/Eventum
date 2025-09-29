package com.us.eventum.data.repositories.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.us.eventum.data.models.User;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.data.repositories.UserRepository;

/**
 * Implementación de UserRepository usando Firebase Firestore
 * Maneja todas las operaciones de usuarios en la nube
 */
public class FirebaseUserRepository implements UserRepository {
    
    private final FirebaseFirestore db;
    private final FirebaseAuth mAuth;
    
    public FirebaseUserRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }
    
    @Override
    public void loadUser(String userId, RepositoryCallback<User> callback) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && (user.getRole() == null || user.getRole().trim().isEmpty())) {
                            user.setRole(UserRole.ORGANIZER);
                        }
                        callback.onSuccess(user);
                    } else {
                        callback.onError("Usuario no encontrado");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al cargar usuario: " + e.getMessage());
                });
    }
    
    @Override
    public void createUser(User user, RepositoryCallback<User> callback) {
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole(UserRole.ORGANIZER);
        }
        db.collection("users").document(user.getUid())
                .set(user)
                .addOnSuccessListener(aVoid -> callback.onSuccess(user))
                .addOnFailureListener(e -> {
                    callback.onError("Error al crear usuario: " + e.getMessage());
                });
    }
    
    @Override
    public void updateUser(String userId, User user, RepositoryCallback<Void> callback) {
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole(UserRole.ORGANIZER);
        }
        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    callback.onError("Error al actualizar usuario: " + e.getMessage());
                });
    }
    
    @Override
    public void checkUsernameAvailability(String username, RepositoryCallback<Boolean> callback) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean isAvailable = queryDocumentSnapshots.isEmpty();
                    callback.onSuccess(isAvailable);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al verificar username: " + e.getMessage());
                });
    }
    
    @Override
    public void syncPendingUserData(RepositoryCallback<Void> callback) {
        // En Firebase, no hay datos de usuario pendientes por sincronizar
        // Esta implementación es para compatibilidad con el patrón offline
        callback.onSuccess(null);
    }
    
    @Override
    public void logout(RepositoryCallback<Void> callback) {
        mAuth.signOut();
        callback.onSuccess(null);
    }
    
    @Override
    public void deleteAccount(RepositoryCallback<Void> callback) {
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(null);
                    } else {
                        callback.onError("Error al eliminar la cuenta: " + 
                            (task.getException() != null ? task.getException().getMessage() : "Error desconocido"));
                    }
                });
        } else {
            callback.onError("Usuario no autenticado");
        }
    }
}
