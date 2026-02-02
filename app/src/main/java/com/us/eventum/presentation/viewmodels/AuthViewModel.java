package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.data.models.Organizer;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.OrganizerRepository;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.firebase.FirebaseOrganizerRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeRepository;
import com.us.eventum.utils.FirebaseAuthErrorHandler;

public class AuthViewModel extends ViewModel {
    private MutableLiveData<Boolean> userLoggedIn = new MutableLiveData<>();
    private MutableLiveData<String> userType = new MutableLiveData<>(); // "ORGANIZER" o "ATTENDEE"
    private MutableLiveData<Organizer> currentOrganizer = new MutableLiveData<>();
    private MutableLiveData<Attendee> currentAttendee = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<Boolean> passwordResetSent = new MutableLiveData<>();
    private MutableLiveData<Boolean> passwordChanged = new MutableLiveData<>();
    private MutableLiveData<Boolean> emailNotVerified = new MutableLiveData<>();
    private MutableLiveData<Boolean> verificationEmailSent = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private OrganizerRepository organizerRepository;
    private AttendeeRepository attendeeRepository;

    // Getters para LiveData
    public LiveData<Boolean> getUserLoggedIn() { return userLoggedIn; }
    public LiveData<String> getUserType() { return userType; }
    public LiveData<Organizer> getCurrentOrganizer() { return currentOrganizer; }
    public LiveData<Attendee> getCurrentAttendee() { return currentAttendee; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Boolean> getPasswordResetSent() { return passwordResetSent; }
    public LiveData<Boolean> getPasswordChanged() { return passwordChanged; }
    public LiveData<Boolean> getEmailNotVerified() { return emailNotVerified; }
    public LiveData<Boolean> getVerificationEmailSent() { return verificationEmailSent; }

    /**
     * Inicializar los repositorios
     */
    public void initializeRepositories(Context context) {
        if (organizerRepository == null) {
            organizerRepository = new FirebaseOrganizerRepository();
        }
        if (attendeeRepository == null) {
            attendeeRepository = new FirebaseAttendeeRepository();
        }
    }

    /**
     * Iniciar sesión
     */
    public void login(String email, String password) {
        // Resetear estados para este intento de login
        errorMessage.postValue(null);
        emailNotVerified.postValue(false);
        userLoggedIn.postValue(false);

        // Asegurar que el login use SIEMPRE las credenciales introducidas
        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut();
        }

        isLoading.postValue(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null && !firebaseUser.isEmailVerified()) {
                            // Bloquear acceso si el email no está verificado
                            emailNotVerified.postValue(true);
                            isLoading.postValue(false);
                            return;
                        }
                        determineUserType();
                    } else {
                        // Manejo profesional de errores de Firebase Auth usando utilidad centralizada
                        String errorMessage = FirebaseAuthErrorHandler.getErrorMessage(task.getException());
                        this.errorMessage.postValue(errorMessage);
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Determinar si el usuario es Organizer o Attendee (método público)
     */
    public void determineUserType() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        String uid = firebaseUser.getUid();
        
        // Primero intentar como Organizer
        organizerRepository.getOrganizer(uid, new OrganizerRepository.RepositoryCallback<Organizer>() {
            @Override
            public void onSuccess(Organizer organizer) {
                userType.postValue("ORGANIZER");
                currentOrganizer.postValue(organizer);
                userLoggedIn.postValue(true);
                isLoading.postValue(false);
            }

            @Override
            public void onError(String error) {
                // Si no es organizador, intentar como Attendee
                attendeeRepository.getAttendee(uid, new AttendeeRepository.RepositoryCallback<Attendee>() {
                    @Override
                    public void onSuccess(Attendee attendee) {
                        userType.postValue("ATTENDEE");
                        currentAttendee.postValue(attendee);
                        userLoggedIn.postValue(true);
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(String attendeeError) {
                        // No es ni organizador ni asistente
                        errorMessage.postValue("Usuario no encontrado en la base de datos");
                        userLoggedIn.postValue(false);
                        isLoading.postValue(false);
                    }
                });
            }
        });
    }

    /**
     * Cerrar sesión
     */
    public void logout() {
        mAuth.signOut();
        userLoggedIn.postValue(false);
        userType.postValue(null);
        currentOrganizer.postValue(null);
        currentAttendee.postValue(null);
    }

    /**
     * Reenviar email de verificación al usuario autenticado actual
     */
    public void resendVerificationEmail() {
        verificationEmailSent.postValue(false);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            errorMessage.postValue("No hay usuario autenticado para reenviar el correo de verificación");
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                verificationEmailSent.postValue(true);
            } else {
                String errorMsg = t.getException() != null ? t.getException().getMessage() : "Error desconocido";
                errorMessage.postValue("No se pudo enviar el correo de verificación: " + errorMsg);
            }
        });
    }

    /**
     * Cambiar contraseña del usuario actual
     */
    public void changePassword(String currentPassword, String newPassword) {
        isLoading.postValue(true);
        errorMessage.postValue(null);
        passwordChanged.postValue(false);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        // Reautenticar con la contraseña actual
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), currentPassword))
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()) {
                        // Cambiar la contraseña
                        user.updatePassword(newPassword)
                                .addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        passwordChanged.postValue(true);
                                        isLoading.postValue(false);
                                    } else {
                        String errorMessage = FirebaseAuthErrorHandler.getErrorMessage(updateTask.getException());
                        this.errorMessage.postValue("Error al cambiar contraseña: " + errorMessage);
                                        isLoading.postValue(false);
                                    }
                                });
                    } else {
                        String errorMessage = FirebaseAuthErrorHandler.getErrorMessage(authTask.getException());
                        this.errorMessage.postValue("Error de autenticación: " + errorMessage);
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Enviar email de restablecimiento de contraseña
     */
    public void sendPasswordResetEmail(String email) {
        isLoading.postValue(true);
        errorMessage.postValue(null);
        passwordResetSent.postValue(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        passwordResetSent.postValue(true);
                        isLoading.postValue(false);
                    } else {
                        String errorMessage = FirebaseAuthErrorHandler.getErrorMessage(task.getException());
                        this.errorMessage.postValue("Error al enviar correo: " + errorMessage);
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Verificar si hay usuario autenticado
     */
    public boolean isUserLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

}
