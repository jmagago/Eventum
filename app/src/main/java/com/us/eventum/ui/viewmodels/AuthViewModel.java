package com.us.eventum.ui.viewmodels;

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
import com.us.eventum.core.utils.FirebaseAuthErrorHandler;

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
    private Context appContext;

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
        appContext = context.getApplicationContext();
        if (organizerRepository == null) {
            organizerRepository = new FirebaseOrganizerRepository();
        }
        if (attendeeRepository == null) {
            attendeeRepository = new FirebaseAttendeeRepository();
        }
    }

    private void postAuthError(Exception exception) {
        errorMessage.postValue(FirebaseAuthErrorHandler.getErrorMessage(appContext, exception));
    }

    /**
     * Iniciar sesión con email y contraseña.
     * No se llama a signOut() antes: Firebase sustituye la sesión al autenticar correctamente
     * y un cierre previo provocaba condiciones de carrera con signIn (errores intermitentes).
     */
    public void login(String email, String password) {
        errorMessage.postValue(null);
        emailNotVerified.postValue(false);
        userLoggedIn.postValue(false);
        userType.postValue(null);
        currentOrganizer.postValue(null);
        currentAttendee.postValue(null);
        isLoading.postValue(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null && !firebaseUser.isEmailVerified()) {
                            emailNotVerified.postValue(true);
                            isLoading.postValue(false);
                            return;
                        }
                        determineUserType();
                    } else {
                        postAuthError(task.getException());
                        isLoading.postValue(false);
                    }
                });
    }

    /**
     * Determina si el usuario autenticado es organizador o asistente.
     */
    public void determineUserType() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.postValue(FirebaseAuthErrorHandler.getNotAuthenticatedMessage(appContext));
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
                        errorMessage.postValue(
                                FirebaseAuthErrorHandler.getUserNotInDatabaseMessage(appContext));
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
            errorMessage.postValue(FirebaseAuthErrorHandler.getNoUserForVerificationMessage(appContext));
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                verificationEmailSent.postValue(true);
            } else {
                errorMessage.postValue(FirebaseAuthErrorHandler.getVerificationEmailError(
                        appContext, t.getException()));
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
            errorMessage.postValue(FirebaseAuthErrorHandler.getNotAuthenticatedMessage(appContext));
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
                                        postAuthError(updateTask.getException());
                                        isLoading.postValue(false);
                                    }
                                });
                    } else {
                        postAuthError(authTask.getException());
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
                        postAuthError(task.getException());
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
