package com.us.eventum.ui.viewmodels;

import com.us.eventum.R;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.repositories.OrganizerRepository;
import com.us.eventum.data.repositories.firebase.FirebaseOrganizerRepository;
import com.us.eventum.core.utils.AgeUtils;
import com.us.eventum.core.utils.FirebaseAuthErrorHandler;

import java.util.Date;

public class OrganizerViewModel extends ViewModel {
    private MutableLiveData<Organizer> currentOrganizer = new MutableLiveData<>();
    private MutableLiveData<Boolean> organizerRegistered = new MutableLiveData<>();
    private MutableLiveData<Boolean> organizerUpdated = new MutableLiveData<>();
    private MutableLiveData<Boolean> organizerLoggedIn = new MutableLiveData<>();
    private MutableLiveData<Boolean> usernameAvailable = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private OrganizerRepository organizerRepository;
    private Context appContext;

    // Getters para LiveData
    public LiveData<Organizer> getCurrentOrganizer() { return currentOrganizer; }
    public LiveData<Boolean> getOrganizerRegistered() { return organizerRegistered; }
    public LiveData<Boolean> getOrganizerUpdated() { return organizerUpdated; }
    public LiveData<Boolean> getOrganizerLoggedIn() { return organizerLoggedIn; }
    public LiveData<Boolean> getUsernameAvailable() { return usernameAvailable; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        appContext = context.getApplicationContext();
        if (organizerRepository == null) {
            organizerRepository = new FirebaseOrganizerRepository();
        }
    }

    private void postAuthError(Exception exception) {
        errorMessage.postValue(FirebaseAuthErrorHandler.getErrorMessage(appContext, exception));
    }

    /**
     * Cargar datos del organizador actual
     */
    public void loadCurrentOrganizer() {
        if (mAuth.getCurrentUser() == null) {
            errorMessage.postValue("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        organizerRepository.getOrganizer(uid, new OrganizerRepository.RepositoryCallback<Organizer>() {
            @Override
            public void onSuccess(Organizer organizer) {
                currentOrganizer.postValue(organizer);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
            }
        });
    }

    /**
     * Registrar nuevo organizador
     */
    public void registerOrganizer(String email, String password, String username, String cif, String phone,
                                  String fechaNacimiento) {
        if (organizerRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            isLoading.postValue(false);
            return;
        }

        isLoading.postValue(true);

        // Validaciones básicas
        if (email == null || email.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_email_required_alt) : "El email es obligatorio"));
            isLoading.postValue(false);
            return;
        }
        if (password == null || password.length() < 6) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_password_min_length) : "La contraseña debe tener al menos 6 caracteres"));
            isLoading.postValue(false);
            return;
        }
        if (username == null || username.trim().isEmpty()) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.error_username_required) : "El nombre de usuario es obligatorio"));
            isLoading.postValue(false);
            return;
        }

        if (fechaNacimiento == null || fechaNacimiento.trim().isEmpty()) {
            errorMessage.postValue(appContext != null
                    ? appContext.getString(com.us.eventum.R.string.age_birth_date_required_organizer)
                    : "La fecha de nacimiento es obligatoria");
            isLoading.postValue(false);
            return;
        }
        Date birthDate = AgeUtils.parseBirthDate(fechaNacimiento);
        if (birthDate == null) {
            errorMessage.postValue(appContext != null
                    ? appContext.getString(com.us.eventum.R.string.age_birth_date_invalid)
                    : (appContext != null ? appContext.getString(R.string.error_birth_date_invalid_short) : "Fecha de nacimiento inválida"));
            isLoading.postValue(false);
            return;
        }
        if (!AgeUtils.isOrganizerAgeValid(birthDate)) {
            errorMessage.postValue(appContext != null
                    ? appContext.getString(com.us.eventum.R.string.age_organizer_min_error)
                    : (appContext != null ? appContext.getString(R.string.error_organizer_min_age) : "Debes tener al menos 18 años"));
            isLoading.postValue(false);
            return;
        }

        // Verificar disponibilidad del username
        organizerRepository.checkUsernameAvailability(username, new OrganizerRepository.RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isAvailable) {
                if (isAvailable) {
                    performOrganizerRegistration(email, password, username, cif, phone, fechaNacimiento);
                } else {
                    errorMessage.postValue((appContext != null ? appContext.getString(R.string.backend_error_username_in_use) : "Este nombre de usuario ya está en uso"));
                    isLoading.postValue(false);
                }
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    private void performOrganizerRegistration(String email, String password, String username, String cif,
                                            String phone, String fechaNacimiento) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Enviar email de verificación
                            firebaseUser.sendEmailVerification()
                                .addOnCompleteListener(verificationTask -> {
                                    if (verificationTask.isSuccessful()) {
                                        // Email de verificación enviado, continuar con la creación del perfil
                                        String uid = firebaseUser.getUid();
                                        Organizer organizer = new Organizer(
                                                uid, username, email, cif, phone, fechaNacimiento);
                                        
                                        organizerRepository.createOrganizer(organizer, new OrganizerRepository.RepositoryCallback<Organizer>() {
                                @Override
                                public void onSuccess(Organizer result) {
                                    organizerRegistered.postValue(true);
                                    isLoading.postValue(false);
                                }

                                @Override
                                public void onError(String error) {
                                    // Si falla la creación del organizador, eliminar el usuario de Auth
                                    firebaseUser.delete();
                                    errorMessage.postValue(error);
                                    isLoading.postValue(false);
                                }
                            });
                                    } else {
                                        // Error enviando email de verificación
                                        errorMessage.postValue(FirebaseAuthErrorHandler.getVerificationEmailError(
                                                appContext, verificationTask.getException()));
                                        isLoading.postValue(false);
                                    }
                                });
                        } else {
                            errorMessage.postValue(
                                    FirebaseAuthErrorHandler.getNullFirebaseUserMessage(appContext));
                            isLoading.postValue(false);
                        }
                    } else {
                        // Manejo profesional de errores de Firebase Auth usando utilidad centralizada
                        postAuthError(task.getException());
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Comprobar si un nombre de usuario está disponible (colección global usernames).
     */
    public void checkUsernameAvailability(String username, UsernameAvailabilityCallback callback) {
        if (organizerRepository == null) {
            if (callback != null) {
                callback.onResult(false, (appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            }
            return;
        }
        if (username == null || username.trim().length() < 3) {
            if (callback != null) {
                callback.onResult(false, null);
            }
            return;
        }

        organizerRepository.checkUsernameAvailability(username.trim(),
                new OrganizerRepository.RepositoryCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isAvailable) {
                        if (callback != null) {
                            callback.onResult(Boolean.TRUE.equals(isAvailable), null);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) {
                            callback.onResult(false, error);
                        }
                    }
                });
    }

    public interface UsernameAvailabilityCallback {
        void onResult(boolean available, String error);
    }

    /**
     * Actualizar organizador
     */
    public void updateOrganizer(String username, String cif, String phone) {
        if (organizerRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            isLoading.postValue(false);
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        isLoading.postValue(true);

        // Obtener organizador actual
        organizerRepository.getOrganizer(mAuth.getCurrentUser().getUid(), new OrganizerRepository.RepositoryCallback<Organizer>() {
            @Override
            public void onSuccess(Organizer currentOrganizer) {
                // Actualizar campos
                currentOrganizer.setUsername(username);
                currentOrganizer.setCif(cif);
                currentOrganizer.setPhone(phone);

                // Guardar cambios
                organizerRepository.updateOrganizer(currentOrganizer, new OrganizerRepository.RepositoryCallback<Organizer>() {
                    @Override
                    public void onSuccess(Organizer result) {
                        organizerUpdated.postValue(true);
                        // currentOrganizer ya se actualiza automáticamente
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(String error) {
                        errorMessage.postValue(error);
                        isLoading.postValue(false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Cerrar sesión
     */
    public void logout() {
        mAuth.signOut();
        organizerLoggedIn.postValue(false);
        currentOrganizer.postValue(null);
    }

    /**
     * Eliminar cuenta
     */
    public void deleteAccount() {
        if (organizerRepository == null) {
            errorMessage.postValue((appContext != null ? appContext.getString(R.string.auth_error_repo_not_initialized) : "Repositorio no inicializado"));
            isLoading.postValue(false);
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        isLoading.postValue(true);

        String uid = mAuth.getCurrentUser().getUid();
        organizerRepository.deleteOrganizer(uid, new OrganizerRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Eliminar cuenta de Firebase Auth
                mAuth.getCurrentUser().delete()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                organizerLoggedIn.postValue(false);
                                currentOrganizer.postValue(null);
                                isLoading.postValue(false);
                            } else {
                                postAuthError(task.getException());
                                isLoading.postValue(false);
                            }
                        });
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

}
