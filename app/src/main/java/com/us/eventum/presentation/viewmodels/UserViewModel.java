package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.data.models.User;
import com.us.eventum.data.models.UserRole;
import com.us.eventum.data.repositories.UserRepository;
import com.us.eventum.data.repositories.hybrid.HybridUserRepository;

public class UserViewModel extends ViewModel {
    private MutableLiveData<User> currentUser = new MutableLiveData<>();
    private MutableLiveData<Boolean> userRegistered = new MutableLiveData<>();
    private MutableLiveData<Boolean> userUpdated = new MutableLiveData<>();
    private MutableLiveData<Boolean> userLoggedIn = new MutableLiveData<>();
    private MutableLiveData<Boolean> passwordChanged = new MutableLiveData<>();
    private MutableLiveData<Boolean> passwordResetSent = new MutableLiveData<>();
    private MutableLiveData<Boolean> usernameAvailable = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private UserRepository userRepository;

    // Getters para LiveData
    public LiveData<User> getCurrentUser() { return currentUser; }
    public LiveData<Boolean> getUserRegistered() { return userRegistered; }
    public LiveData<Boolean> getUserUpdated() { return userUpdated; }
    public LiveData<Boolean> getUserLoggedIn() { return userLoggedIn; }
    public LiveData<Boolean> getPasswordChanged() { return passwordChanged; }
    public LiveData<Boolean> getPasswordResetSent() { return passwordResetSent; }
    public LiveData<Boolean> getUsernameAvailable() { return usernameAvailable; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        if (userRepository == null) {
            userRepository = new HybridUserRepository(context);
        }
    }

    /**
     * Cargar datos del usuario actual
     */
    public void loadCurrentUser() {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        userRepository.loadUser(firebaseUser.getUid(), new UserRepository.RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                currentUser.postValue(result);
                isLoading.postValue(false);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Verificar disponibilidad de username
     */
    public void checkUsernameAvailability(String username) {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        if (username == null || username.trim().isEmpty()) {
            errorMessage.postValue("El username es obligatorio");
            isLoading.postValue(false);
            return;
        }

        if (username.length() < 3) {
            errorMessage.postValue("El username debe tener al menos 3 caracteres");
            isLoading.postValue(false);
            return;
        }

        userRepository.checkUsernameAvailability(username.trim(), new UserRepository.RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                usernameAvailable.postValue(result);
                isLoading.postValue(false);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Registrar un nuevo usuario
     */
    public void registerUser(String email, String password, String username, String name, 
                           String primerApellido, String segundoApellido, String role) {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        // Validaciones
        if (email == null || email.trim().isEmpty()) {
            errorMessage.postValue("El email es obligatorio");
            isLoading.postValue(false);
            return;
        }
        
        if (password == null || password.length() < 6) {
            errorMessage.postValue("La contraseña debe tener al menos 6 caracteres");
            isLoading.postValue(false);
            return;
        }
        
        if (username == null || username.trim().isEmpty()) {
            errorMessage.postValue("El username es obligatorio");
            isLoading.postValue(false);
            return;
        }
        
        if (name == null || name.trim().isEmpty()) {
            errorMessage.postValue("El nombre es obligatorio");
            isLoading.postValue(false);
            return;
        }
        
        if (primerApellido == null || primerApellido.trim().isEmpty()) {
            errorMessage.postValue("El primer apellido es obligatorio");
            isLoading.postValue(false);
            return;
        }

        // Primero verificar disponibilidad del username
        checkUsernameAvailability(username);
        
        // Observar el resultado de la verificación
        usernameAvailable.observeForever(isAvailable -> {
            if (isAvailable != null && isAvailable) {
                // Username disponible, proceder con el registro
                performUserRegistration(email, password, username, name, primerApellido, segundoApellido, role);
            } else if (isAvailable != null && !isAvailable) {
                // Username no disponible
                errorMessage.postValue("Este username ya está en uso");
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Realizar el registro del usuario en Firebase Auth y Firestore
     */
    private void performUserRegistration(String email, String password, String username, 
                                       String name, String primerApellido, String segundoApellido, String role) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Crear objeto User
                            String userId = firebaseUser.getUid();
                            User user = new User(userId, email, username, name, primerApellido, segundoApellido, "", "", role != null && !role.trim().isEmpty() ? role : UserRole.ORGANIZER);
                            
                            // Guardar en repositorio
                                userRepository.createUser(user, new UserRepository.RepositoryCallback<User>() {
                                    @Override
                                    public void onSuccess(User result) {
                                        userRegistered.postValue(true);
                                        isLoading.postValue(false);
                                        
                                        // Enviar email de verificación
                                        firebaseUser.sendEmailVerification();
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        errorMessage.postValue(error);
                                        isLoading.postValue(false);
                                    }
                                });
                        }
                    } else {
                        errorMessage.postValue("Error al crear cuenta: " + task.getException().getMessage());
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Actualizar datos del usuario
     */
    public void updateUser(String username, String name, String primerApellido, 
                          String segundoApellido, String fechaNacimiento, String lugarNacimiento) {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        String userId = firebaseUser.getUid();
        User user = new User(userId, firebaseUser.getEmail(), username, name, 
                           primerApellido, segundoApellido, fechaNacimiento, lugarNacimiento);

        userRepository.updateUser(userId, user, new UserRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                userUpdated.postValue(true);
                currentUser.postValue(user);
                isLoading.postValue(false);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Iniciar sesión de usuario
     */
    public void loginUser(String email, String password) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            if (!firebaseUser.isEmailVerified()) {
                                // Si el email no está verificado, mostrar mensaje y cerrar sesión
                                mAuth.signOut();
                                errorMessage.postValue("Por favor, verifica tu email antes de iniciar sesión");
                                isLoading.postValue(false);
                                return;
                            }

                            // Login exitoso - cargar datos del usuario
                            loadCurrentUser();
                            userLoggedIn.postValue(true);
                        } else {
                            errorMessage.postValue("Error: Usuario de Firebase nulo después del login");
                            isLoading.postValue(false);
                        }
                    } else {
                        Exception ex = task.getException();
                        if (ex != null) {
                            String errorMsg = ex.getMessage();
                            if (errorMsg != null) {
                                if (errorMsg.contains("network")) {
                                    errorMessage.postValue("Sin conexión. Revisa tu Internet e inténtalo de nuevo");
                                } else if (errorMsg.contains("too-many-requests")) {
                                    errorMessage.postValue("Demasiados intentos. Espera unos minutos e inténtalo de nuevo");
                                } else if (errorMsg.contains("user-not-found")) {
                                    errorMessage.postValue("El usuario no existe o ha sido deshabilitado");
                                } else if (errorMsg.contains("wrong-password")) {
                                    errorMessage.postValue("Contraseña incorrecta");
                                } else if (errorMsg.contains("invalid-email")) {
                                    errorMessage.postValue("Email inválido");
                                } else {
                                    errorMessage.postValue("Error de autenticación: " + errorMsg);
                                }
                            } else {
                                errorMessage.postValue("No se pudo iniciar sesión. Inténtalo de nuevo");
                            }
                        } else {
                            errorMessage.postValue("No se pudo iniciar sesión. Inténtalo de nuevo");
                        }
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Cambiar contraseña del usuario
     */
    public void changePassword(String currentPassword, String newPassword) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            // Reautenticar al usuario
            user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), currentPassword))
                    .addOnSuccessListener(aVoid -> {
                        // Cambiar la contraseña
                        user.updatePassword(newPassword)
                                .addOnSuccessListener(aVoid1 -> {
                                    passwordChanged.postValue(true);
                                    isLoading.postValue(false);
                                })
                                .addOnFailureListener(e -> {
                                    errorMessage.postValue("Error al actualizar la contraseña: " + e.getMessage());
                                    isLoading.postValue(false);
                                });
                    })
                    .addOnFailureListener(e -> {
                        errorMessage.postValue("Contraseña actual incorrecta");
                        isLoading.postValue(false);
                    });
        } else {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
        }
    }

    /**
     * Enviar email de restablecimiento de contraseña
     */
    public void sendPasswordResetEmail(String email) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        passwordResetSent.postValue(true);
                        isLoading.postValue(false);
                    } else {
                        Exception ex = task.getException();
                        if (ex != null) {
                            String errorMsg = ex.getMessage();
                            if (errorMsg != null) {
                                if (errorMsg.contains("user-not-found")) {
                                    errorMessage.postValue("No existe una cuenta con este email");
                                } else if (errorMsg.contains("invalid-email")) {
                                    errorMessage.postValue("Email inválido");
                                } else if (errorMsg.contains("too-many-requests")) {
                                    errorMessage.postValue("Demasiados intentos. Espera unos minutos");
                                } else {
                                    errorMessage.postValue("Error al enviar el email de restablecimiento: " + errorMsg);
                                }
                            } else {
                                errorMessage.postValue("Error al enviar el email de restablecimiento");
                            }
                        } else {
                            errorMessage.postValue("Error al enviar el email de restablecimiento");
                        }
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Cerrar sesión del usuario
     */
    public void logout() {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        userRepository.logout(new UserRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                userLoggedIn.postValue(false);
                isLoading.postValue(false);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Eliminar cuenta del usuario
     */
    public void deleteAccount() {
        if (userRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        userRepository.deleteAccount(new UserRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                userLoggedIn.postValue(false);
                isLoading.postValue(false);
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }


    /**
     * Limpiar estados de operaciones
     */
    public void clearOperationStates() {
        userRegistered.postValue(false);
        userUpdated.postValue(false);
        userLoggedIn.postValue(false);
        passwordChanged.postValue(false);
        passwordResetSent.postValue(false);
        usernameAvailable.postValue(null);
        errorMessage.postValue(null);
    }
}