package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.AttendeesToEventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeRepository;
import com.us.eventum.utils.FirebaseAuthErrorHandler;
import com.us.eventum.utils.QrCheckInResult;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeesToEventRepository;
import com.us.eventum.data.repositories.firebase.FirebaseOrganizerNotificationRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeNotificationRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventRepository;
import com.us.eventum.data.repositories.OrganizerNotificationRepository;
import com.us.eventum.data.repositories.AttendeeNotificationRepository;
import com.us.eventum.data.repositories.EventRepository;
import com.us.eventum.data.models.AttendeesToEvent;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.models.OrganizerNotification;
import com.us.eventum.data.models.AttendeeNotification;

import android.util.Log;

import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.WaitlistRepository;
import com.us.eventum.data.repositories.firebase.FirebaseWaitlistRepository;
import com.us.eventum.utils.EventActivityLogHelper;
import com.us.eventum.utils.WaitlistService;
import com.us.eventum.utils.WaitlistUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttendeeViewModel extends ViewModel {

    public enum CheckInMethod {
        QR,
        MANUAL
    }

    private MutableLiveData<Attendee> currentAttendee = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeRegistered = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeUpdated = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeLoggedIn = new MutableLiveData<>();
    private MutableLiveData<Boolean> usernameAvailable = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private Context appContext;
    private AttendeeRepository attendeeRepository;
    private AttendeesToEventRepository attendeesToEventRepository;
    private OrganizerNotificationRepository organizerNotificationRepository;
    private AttendeeNotificationRepository attendeeNotificationRepository;
    private EventRepository eventRepository;
    private WaitlistRepository waitlistRepository;
    private static final String TAG = "AttendeeViewModel";

    // Getters para LiveData
    public LiveData<Attendee> getCurrentAttendee() { return currentAttendee; }
    public LiveData<Boolean> getAttendeeRegistered() { return attendeeRegistered; }
    public LiveData<Boolean> getAttendeeUpdated() { return attendeeUpdated; }
    public LiveData<Boolean> getAttendeeLoggedIn() { return attendeeLoggedIn; }
    public LiveData<Boolean> getUsernameAvailable() { return usernameAvailable; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        appContext = context.getApplicationContext();
        if (attendeeRepository == null) {
            attendeeRepository = new FirebaseAttendeeRepository();
        }
        if (attendeesToEventRepository == null) {
            attendeesToEventRepository = new FirebaseAttendeesToEventRepository();
        }
        if (organizerNotificationRepository == null) {
            organizerNotificationRepository = new FirebaseOrganizerNotificationRepository();
        }
        if (attendeeNotificationRepository == null) {
            attendeeNotificationRepository = new FirebaseAttendeeNotificationRepository();
        }
        if (eventRepository == null) {
            eventRepository = new FirebaseEventRepository();
        }
        if (waitlistRepository == null) {
            waitlistRepository = new FirebaseWaitlistRepository();
        }
    }

    /**
     * Crea la notificación en Firestore leyendo siempre el organizador desde el evento en Firebase.
     */
    private void notifyOrganizerAfterRegistrationChange(
            String eventId,
            String attendeeDisplayName,
            String type,
            String eventTitleHint) {
        if (organizerNotificationRepository == null || eventId == null || eventId.isEmpty()) {
            return;
        }
        if (eventRepository == null) {
            eventRepository = new FirebaseEventRepository();
        }
        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                if (event == null || event.getUserId() == null || event.getUserId().isEmpty()) {
                    Log.w(TAG, "No se pudo notificar: evento sin userId (organizador)");
                    return;
                }
                String title = event.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    title = eventTitleHint;
                }
                persistOrganizerNotification(
                        event.getUserId(),
                        eventId,
                        title,
                        attendeeDisplayName,
                        type);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "No se pudo cargar evento para notificar al organizador: " + error);
            }
        });
    }

    private void persistOrganizerNotification(String organizerId, String eventId, String eventTitle,
                                              String attendeeDisplayName, String type) {
        if (organizerId == null || organizerId.isEmpty()) {
            return;
        }
        String name = attendeeDisplayName != null && !attendeeDisplayName.trim().isEmpty()
                ? attendeeDisplayName.trim()
                : "Un asistente";
        String title = eventTitle != null ? eventTitle.trim() : "";
        OrganizerNotification notification = new OrganizerNotification(
                organizerId, eventId, title, name, type);
        organizerNotificationRepository.create(notification,
                new OrganizerNotificationRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "Notificación de organizador creada: " + type);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error al crear notificación de organizador: " + error);
                    }
                });
    }

    private void notifyAttendeeAttendanceVerified(String attendeeId, String eventId) {
        if (attendeeNotificationRepository == null || eventRepository == null
                || attendeeId == null || eventId == null) {
            return;
        }
        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                String title = event != null && event.getTitle() != null
                        ? event.getTitle().trim()
                        : "";
                persistAttendeeNotification(
                        attendeeId,
                        eventId,
                        title,
                        AttendeeNotification.TYPE_ATTENDANCE_VERIFIED);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "No se pudo cargar evento para notificar al asistente: " + error);
                persistAttendeeNotification(
                        attendeeId,
                        eventId,
                        "",
                        AttendeeNotification.TYPE_ATTENDANCE_VERIFIED);
            }
        });
    }

    private void persistAttendeeNotification(String attendeeId, String eventId,
                                             String eventTitle, String type) {
        if (attendeeId == null || attendeeId.isEmpty()) {
            return;
        }
        AttendeeNotification notification = new AttendeeNotification(
                attendeeId, eventId, eventTitle != null ? eventTitle : "", type);
        attendeeNotificationRepository.create(notification,
                new AttendeeNotificationRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "Notificación de asistente creada: " + type);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error al crear notificación de asistente: " + error);
                    }
                });
    }

    /**
     * Cargar datos del asistente actual
     */
    public void loadCurrentAttendee() {
        if (mAuth.getCurrentUser() == null) {
            errorMessage.postValue("Usuario no autenticado");
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        attendeeRepository.getAttendee(uid, new AttendeeRepository.RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee attendee) {
                currentAttendee.postValue(attendee);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
            }
        });
    }

    /**
     * Registrar nuevo asistente
     */
    public void registerAttendee(String email, String password, String username, String nombre, String dni, String phone, 
                                String primerApellido, String segundoApellido, String fechaNacimiento) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }

        isLoading.postValue(true);

        // Validaciones básicas
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
            errorMessage.postValue("El nombre de usuario es obligatorio");
            isLoading.postValue(false);
            return;
        }

        // Verificar disponibilidad del username
        attendeeRepository.checkUsernameAvailability(username, new AttendeeRepository.RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isAvailable) {
                if (isAvailable) {
                    performAttendeeRegistration(email, password, username, nombre, dni, phone, primerApellido, segundoApellido, fechaNacimiento);
                } else {
                    errorMessage.postValue("Este nombre de usuario ya está en uso");
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

    private void performAttendeeRegistration(String email, String password, String username, String nombre, String dni, String phone, 
                                           String primerApellido, String segundoApellido, String fechaNacimiento) {
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
                                        Attendee attendee = new Attendee(uid, username, email, nombre, dni, phone, primerApellido, segundoApellido, fechaNacimiento);
                                        
                                        attendeeRepository.createAttendee(attendee, new AttendeeRepository.RepositoryCallback<Attendee>() {
                                @Override
                                public void onSuccess(Attendee result) {
                                    attendeeRegistered.postValue(true);
                                    isLoading.postValue(false);
                                }

                                @Override
                                public void onError(String error) {
                                    // Si falla la creación del asistente, eliminar el usuario de Auth
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
                        errorMessage.postValue(FirebaseAuthErrorHandler.getErrorMessage(
                                appContext, task.getException()));
                        isLoading.postValue(false);
                    }
                });
    }

    public interface DniAvailabilityCallback {
        void onResult(boolean available, @Nullable String error);
    }

    public void checkDniAvailability(String dni, String excludeUid, DniAvailabilityCallback callback) {
        if (attendeeRepository == null) {
            if (callback != null) {
                callback.onResult(false, appContext != null
                        ? appContext.getString(com.us.eventum.R.string.backend_op_check_dni)
                        : "No se pudo verificar el DNI");
            }
            return;
        }
        attendeeRepository.checkDniAvailability(dni, excludeUid, new AttendeeRepository.RepositoryCallback<Boolean>() {
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

    /**
     * Actualizar asistente
     */
    public void updateAttendee(String username, String nombre, String dni, String phone, String primerApellido, String segundoApellido, String fechaNacimiento) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }

        isLoading.postValue(true);

        // Obtener asistente actual
        attendeeRepository.getAttendee(mAuth.getCurrentUser().getUid(), new AttendeeRepository.RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee currentAttendee) {
                // Actualizar campos
                currentAttendee.setUsername(username);
                currentAttendee.setNombre(nombre);
                currentAttendee.setDni(dni);
                currentAttendee.setPhone(phone);
                currentAttendee.setPrimerApellido(primerApellido);
                currentAttendee.setSegundoApellido(segundoApellido);
                
                // Actualizar fecha de nacimiento si se proporciona
                if (fechaNacimiento != null && !fechaNacimiento.isEmpty()) {
                    java.util.Date parsed = com.us.eventum.utils.AgeUtils.parseBirthDate(fechaNacimiento);
                    if (parsed == null) {
                        errorMessage.postValue(appContext != null
                                ? appContext.getString(com.us.eventum.R.string.age_birth_date_invalid)
                                : "Fecha de nacimiento inválida");
                        isLoading.postValue(false);
                        return;
                    }
                    if (!com.us.eventum.utils.AgeUtils.isAttendeeAgeValid(parsed, new java.util.Date())) {
                        errorMessage.postValue(appContext != null
                                ? appContext.getString(com.us.eventum.R.string.age_attendee_min_error)
                                : "Debes tener al menos 16 años");
                        isLoading.postValue(false);
                        return;
                    }
                    currentAttendee.setFechaNacimientoFromString(fechaNacimiento);
                }

                // Guardar cambios
                attendeeRepository.updateAttendee(currentAttendee, new AttendeeRepository.RepositoryCallback<Attendee>() {
                    @Override
                    public void onSuccess(Attendee result) {
                        AttendeeViewModel.this.currentAttendee.postValue(result);
                        attendeeUpdated.postValue(true);
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
        attendeeLoggedIn.postValue(false);
        currentAttendee.postValue(null);
    }

    /**
     * Eliminar cuenta
     */
    public void deleteAccount() {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
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
        attendeeRepository.deleteAttendee(uid, new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Eliminar cuenta de Firebase Auth
                mAuth.getCurrentUser().delete()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                attendeeLoggedIn.postValue(false);
                                currentAttendee.postValue(null);
                                isLoading.postValue(false);
                            } else {
                                errorMessage.postValue(FirebaseAuthErrorHandler.getErrorMessage(
                                        appContext, task.getException()));
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
     * Check-in por QR: válido, ya usado o no inscrito en el evento.
     */
    public void verifyAttendeeCheckIn(String attendeeId, String eventId) {
        verifyAttendeeCheckIn(attendeeId, eventId, CheckInMethod.QR, null);
    }

    public void verifyAttendeeCheckIn(String attendeeId, String eventId,
                                      CheckInMethod method, String attendeeDisplayName) {
        if (attendeesToEventRepository == null) {
            qrCheckInResult.postValue(QrCheckInResult.INVALID_TOKEN);
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);
        qrCheckInResult.postValue(null);

        attendeesToEventRepository.loadAttendeesToEvent(eventId,
                new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
                    @Override
                    public void onSuccess(List<AttendeesToEvent> result) {
                        for (AttendeesToEvent attendeeToEvent : result) {
                            if (!attendeeId.equals(attendeeToEvent.getUserId())) {
                                continue;
                            }
                            if (attendeeToEvent.isScannedQR()) {
                                qrCheckInResult.postValue(QrCheckInResult.ALREADY_USED);
                                isLoading.postValue(false);
                                return;
                            }
                            attendeeToEvent.setScannedQR(true);
                            attendeesToEventRepository.updateAttendeeToEvent(attendeeToEvent,
                                    new AttendeesToEventRepository.RepositoryCallback<AttendeesToEvent>() {
                                        @Override
                                        public void onSuccess(AttendeesToEvent updatedResult) {
                                            qrCheckInResult.postValue(QrCheckInResult.VALID);
                                            notifyAttendeeAttendanceVerified(attendeeId, eventId);
                                            logCheckIn(eventId, attendeeId, method, attendeeDisplayName);
                                            isLoading.postValue(false);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            errorMessage.postValue(error);
                                            isLoading.postValue(false);
                                        }
                                    });
                            return;
                        }
                        qrCheckInResult.postValue(QrCheckInResult.NOT_REGISTERED);
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
     * Elimina todos los asistentes de un evento específico
     * Útil para limpiar la lista cuando se cancela un evento
     */
    public void clearEventAttendees(String eventId) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        
        isLoading.postValue(true);
        errorMessage.postValue(null);
        
        // Obtenemos la lista de asistentes y los eliminamos uno por uno
        attendeesToEventRepository.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> result) {
                if (result.isEmpty()) {
                    attendeeUpdated.postValue(true);
                    isLoading.postValue(false);
                    return;
                }

                int total = result.size();
                int[] completed = {0};

                for (AttendeesToEvent attendeeToEvent : result) {
                    attendeesToEventRepository.deleteAttendeeToEvent(attendeeToEvent.getId(), new AttendeesToEventRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void deleteResult) {
                            completed[0]++;
                            if (completed[0] == total) {
                                attendeeUpdated.postValue(true);
                                isLoading.postValue(false);
                                EventActivityLogHelper.logListCleared(eventId, total);
                                tryPromoteFromWaitlist(eventId);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            errorMessage.postValue(error);
                            isLoading.postValue(false);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Limpiar estados de operación
     */
    public void clearOperationStates() {
        attendeeRegistered.postValue(false);
        attendeeUpdated.postValue(false);
        attendeeAdded.postValue(false);
        attendeeDeleted.postValue(false);
        waitlistJoined.postValue(false);
        waitlistLeft.postValue(false);
        usernameAvailable.postValue(false);
        qrCheckInResult.postValue(null);
        errorMessage.postValue(null);
    }

    public LiveData<QrCheckInResult> getQrCheckInResult() {
        return qrCheckInResult;
    }

    /**
     * Obtener LiveData para asistentes limpiados
     */
    public LiveData<Boolean> getAttendeesCleared() {
        return attendeeUpdated; // Reutilizamos attendeeUpdated para simplicidad
    }

    /**
     * Obtener LiveData para asistente verificado
     */
    public LiveData<Boolean> getAttendeeVerified() {
        return attendeeUpdated; // Reutilizamos attendeeUpdated para simplicidad
    }

    // Métodos adicionales para compatibilidad con Activities
    private MutableLiveData<List<Attendee>> attendees = new MutableLiveData<>();
    private MutableLiveData<Map<String, Boolean>> scannedAttendeesMapLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeAdded = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeDeleted = new MutableLiveData<>();
    private MutableLiveData<Boolean> waitlistJoined = new MutableLiveData<>();
    private MutableLiveData<Boolean> waitlistLeft = new MutableLiveData<>();
    private MutableLiveData<List<WaitlistToEvent>> eventWaitlist = new MutableLiveData<>();
    private MutableLiveData<WaitlistToEvent> currentUserWaitlistEntry = new MutableLiveData<>();
    private MutableLiveData<QrCheckInResult> qrCheckInResult = new MutableLiveData<>();
    private boolean listeningEventAttendees;
    private boolean listeningEventWaitlist;
    private String listeningAttendeesEventId;
    private String listeningWaitlistEventId;

    public LiveData<List<Attendee>> getAttendees() {
        return attendees;
    }

    public LiveData<Map<String, Boolean>> getScannedAttendeesMap() {
        return scannedAttendeesMapLiveData;
    }

    /** Evita que un observer reciba la lista de un evento anterior al abrir otro diálogo. */
    public void clearAttendeesList() {
        attendees.setValue(null);
    }

    public LiveData<Boolean> getAttendeeAdded() {
        return attendeeAdded;
    }

    public LiveData<Boolean> getAttendeeDeleted() {
        return attendeeDeleted;
    }

    public LiveData<Boolean> getWaitlistJoined() {
        return waitlistJoined;
    }

    public LiveData<Boolean> getWaitlistLeft() {
        return waitlistLeft;
    }

    public LiveData<List<WaitlistToEvent>> getEventWaitlist() {
        return eventWaitlist;
    }

    public LiveData<WaitlistToEvent> getCurrentUserWaitlistEntry() {
        return currentUserWaitlistEntry;
    }

    /**
     * Escucha en tiempo real las inscripciones de un evento y carga los perfiles.
     */
    public void startListeningEventAttendees(String eventId) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        if (listeningEventAttendees && eventId.equals(listeningAttendeesEventId)) {
            return;
        }
        stopListeningEventAttendees();
        listeningEventAttendees = true;
        listeningAttendeesEventId = eventId;
        attendees.setValue(null);
        isLoading.postValue(true);
        errorMessage.postValue(null);

        attendeesToEventRepository.startEventAttendeesListener(eventId,
                new AttendeesToEventRepository.EventAttendeesListener() {
                    @Override
                    public void onAttendeesUpdated(List<AttendeesToEvent> result) {
                        handleAttendeesToEventSnapshot(result);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Attendees listener: " + error);
                        attendees.postValue(new java.util.ArrayList<>());
                        isLoading.postValue(false);
                    }
                });
    }

    public void stopListeningEventAttendees() {
        if (attendeesToEventRepository == null || !listeningEventAttendees) {
            return;
        }
        attendeesToEventRepository.stopEventAttendeesListener();
        listeningEventAttendees = false;
        listeningAttendeesEventId = null;
    }

    public void restartListeningEventAttendees(String eventId) {
        stopListeningEventAttendees();
        startListeningEventAttendees(eventId);
    }

    private void handleAttendeesToEventSnapshot(List<AttendeesToEvent> result) {
        Map<String, Boolean> scanned = new HashMap<>();
        if (result != null) {
            for (AttendeesToEvent row : result) {
                if (row != null && row.getUserId() != null) {
                    scanned.put(row.getUserId(), row.isScannedQR());
                }
            }
        }
        scannedAttendeesMapLiveData.postValue(scanned);

        List<Attendee> attendeeList = new java.util.ArrayList<>();
        if (result == null || result.isEmpty()) {
            attendees.postValue(attendeeList);
            isLoading.postValue(false);
            return;
        }

        int[] loadedCount = {0};
        int totalCount = result.size();
        for (AttendeesToEvent attendeeToEvent : result) {
            if (attendeeToEvent == null || attendeeToEvent.getUserId() == null) {
                loadedCount[0]++;
                if (loadedCount[0] == totalCount) {
                    attendees.postValue(attendeeList);
                    isLoading.postValue(false);
                }
                continue;
            }
            attendeeRepository.getAttendee(attendeeToEvent.getUserId(),
                    new AttendeeRepository.RepositoryCallback<Attendee>() {
                        @Override
                        public void onSuccess(Attendee attendee) {
                            if (attendee != null) {
                                attendeeList.add(attendee);
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == totalCount) {
                                attendees.postValue(attendeeList);
                                isLoading.postValue(false);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == totalCount) {
                                attendees.postValue(attendeeList);
                                isLoading.postValue(false);
                            }
                        }
                    });
        }
    }

    /**
     * Carga la lista completa de asistentes para un evento específico
     * Incluye todos los datos del perfil de cada asistente
     */
    public void loadEventAttendees(String eventId) {
        startListeningEventAttendees(eventId);
    }

    /**
     * Inscribe a un asistente en un evento específico
     * Crea el registro de inscripción en la base de datos
     */
    public void addAttendee(String eventId, String uid, String name, String lastName, String dni,
                            String email, String phone, String birthDate, boolean requiresAuth) {
        addAttendee(eventId, uid, name, lastName, dni, email, phone, birthDate, requiresAuth,
                null, null, null, null);
    }

    /**
     * Inscribe a un asistente. Si se indican organizerId y eventTitle, notifica al organizador.
     */
    public void addAttendee(String eventId, String uid, String name, String lastName, String dni,
                            String email, String phone, String birthDate, boolean requiresAuth,
                            String organizerId, String eventTitle, String attendeeDisplayName) {
        addAttendee(eventId, uid, name, lastName, dni, email, phone, birthDate, requiresAuth,
                organizerId, eventTitle, attendeeDisplayName, null);
    }

    public void addAttendee(String eventId, String uid, String name, String lastName, String dni,
                            String email, String phone, String birthDate, boolean requiresAuth,
                            String organizerId, String eventTitle, String attendeeDisplayName,
                            String parentalAuthUrl) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        
        isLoading.postValue(true);
        errorMessage.postValue(null);
        attendeeAdded.postValue(false);
        
        // Creamos el registro de inscripción del asistente al evento
        AttendeesToEvent attendeeToEvent = new AttendeesToEvent();
        attendeeToEvent.setEventId(eventId);
        attendeeToEvent.setUserId(uid);
        attendeeToEvent.setScannedQR(false);
        if (parentalAuthUrl != null && !parentalAuthUrl.trim().isEmpty()) {
            attendeeToEvent.setParentalAuthUrl(parentalAuthUrl.trim());
        }
        
        attendeesToEventRepository.createAttendeeToEvent(attendeeToEvent, new AttendeesToEventRepository.RepositoryCallback<AttendeesToEvent>() {
            @Override
            public void onSuccess(AttendeesToEvent result) {
                attendeeAdded.postValue(true);
                isLoading.postValue(false);
                notifyOrganizerAfterRegistrationChange(
                        eventId,
                        attendeeDisplayName,
                        OrganizerNotification.TYPE_ATTENDEE_JOINED,
                        eventTitle);
                EventActivityLogHelper.logJoined(eventId, attendeeDisplayName);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Desinscribe al usuario actual de un evento específico
     * Elimina su registro de asistencia de la base de datos
     */
    public void unsubscribeFromEvent(String eventId, String email) {
        unsubscribeFromEvent(eventId, email, null, null, null);
    }

    public void unsubscribeFromEvent(String eventId, String email, String organizerId,
                                     String eventTitle, String attendeeDisplayName) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        
        isLoading.postValue(true);
        errorMessage.postValue(null);
        attendeeDeleted.postValue(false);
        
        // Obtenemos el ID del usuario autenticado
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            errorMessage.postValue("Usuario no autenticado");
            isLoading.postValue(false);
            return;
        }
        
        // Buscamos y eliminamos el registro de inscripción del usuario
        attendeesToEventRepository.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> result) {
                // Buscar el registro específico del usuario
                for (AttendeesToEvent attendeeToEvent : result) {
                    if (attendeeToEvent.getUserId().equals(uid)) {
                        // Eliminar el registro
                        attendeesToEventRepository.deleteAttendeeToEvent(attendeeToEvent.getId(), new AttendeesToEventRepository.RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void deleteResult) {
                                attendeeDeleted.postValue(true);
                                isLoading.postValue(false);
                                notifyOrganizerAfterRegistrationChange(
                                        eventId,
                                        attendeeDisplayName,
                                        OrganizerNotification.TYPE_ATTENDEE_LEFT,
                                        eventTitle);
                                EventActivityLogHelper.logLeft(eventId, attendeeDisplayName);
                                tryPromoteFromWaitlist(eventId);
                            }

                            @Override
                            public void onError(String error) {
                                errorMessage.postValue(error);
                                isLoading.postValue(false);
                            }
                        });
                        return;
                    }
                }
                // Si no se encuentra el usuario inscrito
                errorMessage.postValue("No estás inscrito en este evento");
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
     * Elimina un asistente de un evento específico
     * Solo elimina su registro en AttendeesToEvent, NO elimina el perfil del usuario
     */
    public void removeAttendeeFromEvent(String attendeeId, String eventId) {
        removeAttendeeFromEvent(attendeeId, eventId, null);
    }

    public void removeAttendeeFromEvent(String attendeeId, String eventId, String attendeeDisplayName) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        
        isLoading.postValue(true);
        errorMessage.postValue(null);
        
        // Buscar y eliminar el registro en AttendeesToEvent para este evento
        attendeesToEventRepository.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> result) {
                // Buscar el registro específico del asistente en este evento
                for (AttendeesToEvent attendeeToEvent : result) {
                    if (attendeeToEvent.getUserId().equals(attendeeId)) {
                        // Eliminar solo el registro de AttendeesToEvent (NO el perfil del usuario)
                        attendeesToEventRepository.deleteAttendeeToEvent(attendeeToEvent.getId(), new AttendeesToEventRepository.RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void deleteResult) {
                                attendeeDeleted.postValue(true);
                                isLoading.postValue(false);
                                EventActivityLogHelper.logRemoved(eventId, attendeeDisplayName);
                                notifyAttendeeRemovedByOrganizer(attendeeId, eventId);
                                tryPromoteFromWaitlist(eventId);
                            }

                            @Override
                            public void onError(String error) {
                                errorMessage.postValue(error);
                                isLoading.postValue(false);
                            }
                        });
                        return;
                    }
                }
                // Si no se encuentra el registro, el asistente ya no está en el evento
                errorMessage.postValue("El asistente no está inscrito en este evento");
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
     * Elimina completamente un asistente del sistema
     * Borra su perfil y todas sus inscripciones a eventos
     */
    public void deleteAttendee(String attendeeId) {
        if (attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            isLoading.postValue(false);
            return;
        }
        
        isLoading.postValue(true);
        errorMessage.postValue(null);
        
        // Eliminamos el perfil del asistente de la base de datos
        attendeeRepository.deleteAttendee(attendeeId, new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Ahora necesitamos eliminar todas sus inscripciones a eventos
                // Como no tenemos un método directo, usamos una consulta manual
                // Por ahora marcamos como eliminado - las inscripciones se limpiarán automáticamente
                // cuando se intente acceder a un asistente que ya no existe
                attendeeDeleted.postValue(true);
                isLoading.postValue(false);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    private void notifyAttendeeRemovedByOrganizer(String attendeeId, String eventId) {
        if (attendeeNotificationRepository == null || eventRepository == null
                || attendeeId == null || eventId == null) {
            return;
        }
        eventRepository.getEventById(eventId, new EventRepository.RepositoryCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                String title = event != null && event.getTitle() != null
                        ? event.getTitle().trim()
                        : "";
                persistAttendeeNotification(
                        attendeeId,
                        eventId,
                        title,
                        AttendeeNotification.TYPE_REMOVED_BY_ORGANIZER);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "No se pudo cargar evento para notificar baja forzada: " + error);
                persistAttendeeNotification(
                        attendeeId,
                        eventId,
                        "",
                        AttendeeNotification.TYPE_REMOVED_BY_ORGANIZER);
            }
        });
    }

    private void logCheckIn(String eventId, String attendeeId, CheckInMethod method,
                            String attendeeDisplayName) {
        if (method == CheckInMethod.MANUAL) {
            EventActivityLogHelper.logVerifiedManual(eventId, attendeeDisplayName);
            return;
        }
        if (attendeeDisplayName != null && !attendeeDisplayName.trim().isEmpty()) {
            EventActivityLogHelper.logVerifiedQr(eventId, attendeeDisplayName);
            return;
        }
        if (attendeeRepository == null || attendeeId == null) {
            EventActivityLogHelper.logVerifiedQr(eventId, null);
            return;
        }
        attendeeRepository.getAttendee(attendeeId, new AttendeeRepository.RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee attendee) {
                String name = attendee != null ? attendee.getFullNameLabel() : null;
                EventActivityLogHelper.logVerifiedQr(eventId, name);
            }

            @Override
            public void onError(String error) {
                EventActivityLogHelper.logVerifiedQr(eventId, null);
            }
        });
    }

    /**
     * Carga los registros de inscripción de un evento para contar asistentes escaneados
     * Solo cuenta los asistentes que están en la lista actual (filtra registros huérfanos)
     * @param eventId ID del evento
     * @param currentAttendeeIds Lista de UIDs de asistentes actuales en el evento
     * @param callback Callback con el número de asistentes escaneados
     */
    public void loadScannedAttendeesCount(String eventId, List<String> currentAttendeeIds, AttendeeRepository.RepositoryCallback<Integer> callback) {
        if (attendeesToEventRepository == null) {
            callback.onError("Repositorio no inicializado");
            return;
        }
        
        attendeesToEventRepository.loadAttendeesToEvent(eventId, new AttendeesToEventRepository.RepositoryCallback<List<AttendeesToEvent>>() {
            @Override
            public void onSuccess(List<AttendeesToEvent> result) {
                // Contar solo los asistentes escaneados que están en la lista actual
                int scannedCount = 0;
                for (AttendeesToEvent attendeeToEvent : result) {
                    // Solo contar si está escaneado Y está en la lista actual de asistentes
                    if (attendeeToEvent.isScannedQR() && currentAttendeeIds.contains(attendeeToEvent.getUserId())) {
                        scannedCount++;
                    }
                }
                callback.onSuccess(scannedCount);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    protected void onCleared() {
        stopListeningEventAttendees();
        stopListeningEventWaitlist();
    }

    private void tryPromoteFromWaitlist(String eventId) {
        WaitlistService.promoteIfNeeded(
                eventId,
                attendeesToEventRepository,
                waitlistRepository,
                eventRepository,
                attendeeNotificationRepository);
    }

    public void promoteWaitlistIfNeeded(String eventId) {
        tryPromoteFromWaitlist(eventId);
    }

    public void startListeningEventWaitlist(String eventId) {
        if (waitlistRepository == null) {
            return;
        }
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        if (listeningEventWaitlist && eventId.equals(listeningWaitlistEventId)) {
            return;
        }
        stopListeningEventWaitlist();
        listeningEventWaitlist = true;
        listeningWaitlistEventId = eventId;
        eventWaitlist.setValue(null);
        currentUserWaitlistEntry.setValue(null);
        waitlistRepository.startEventWaitlistListener(eventId, new WaitlistRepository.EventWaitlistListener() {
            @Override
            public void onWaitlistUpdated(List<WaitlistToEvent> entries) {
                List<WaitlistToEvent> list = entries != null ? entries : new java.util.ArrayList<>();
                eventWaitlist.postValue(list);
                String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
                currentUserWaitlistEntry.postValue(WaitlistUtils.findActiveEntryForUser(list, uid));
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Waitlist listener: " + error);
                eventWaitlist.postValue(new java.util.ArrayList<>());
                currentUserWaitlistEntry.postValue(null);
            }
        });
    }

    public void stopListeningEventWaitlist() {
        if (waitlistRepository == null || !listeningEventWaitlist) {
            return;
        }
        waitlistRepository.stopEventWaitlistListener();
        listeningEventWaitlist = false;
        listeningWaitlistEventId = null;
    }

    public void loadEventWaitlist(String eventId) {
        startListeningEventWaitlist(eventId);
    }

    public void joinWaitlist(String eventId, String uid, @Nullable String parentalAuthUrl,
                             @Nullable String attendeeDisplayName) {
        if (waitlistRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        isLoading.postValue(true);
        errorMessage.postValue(null);
        waitlistJoined.postValue(false);

        WaitlistToEvent entry = new WaitlistToEvent(eventId, uid, WaitlistToEvent.STATUS_WAITING);
        if (parentalAuthUrl != null && !parentalAuthUrl.trim().isEmpty()) {
            entry.setParentalAuthUrl(parentalAuthUrl.trim());
        }
        waitlistRepository.createWaitlistEntry(entry, new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
            @Override
            public void onSuccess(WaitlistToEvent result) {
                waitlistJoined.postValue(true);
                isLoading.postValue(false);
                EventActivityLogHelper.logWaitlistJoined(eventId, attendeeDisplayName);
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    public void leaveWaitlist(String eventId, String uid, @Nullable String attendeeDisplayName) {
        if (waitlistRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        isLoading.postValue(true);
        errorMessage.postValue(null);
        waitlistLeft.postValue(false);

        waitlistRepository.loadWaitlistForEvent(eventId, new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
            @Override
            public void onSuccess(List<WaitlistToEvent> entries) {
                WaitlistToEvent active = WaitlistUtils.findActiveEntryForUser(entries, uid);
                if (active == null || active.getId() == null) {
                    errorMessage.postValue("No estás en la lista de espera");
                    isLoading.postValue(false);
                    return;
                }
                boolean wasOffered = WaitlistToEvent.STATUS_OFFERED.equals(active.getStatus())
                        && WaitlistUtils.isValidOffer(active, System.currentTimeMillis());
                waitlistRepository.cancelEntry(eventId, active.getId(),
                        new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
                            @Override
                            public void onSuccess(WaitlistToEvent result) {
                                waitlistLeft.postValue(true);
                                isLoading.postValue(false);
                                EventActivityLogHelper.logWaitlistLeft(eventId, attendeeDisplayName);
                                if (wasOffered) {
                                    tryPromoteFromWaitlist(eventId);
                                }
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

    public void confirmWaitlistOffer(String eventId, String uid, String name, String lastName,
                                     String dni, String email, String phone, String birthDate,
                                     boolean requiresAuth, String organizerId, String eventTitle,
                                     String attendeeDisplayName) {
        if (waitlistRepository == null || attendeesToEventRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        isLoading.postValue(true);
        errorMessage.postValue(null);
        attendeeAdded.postValue(false);

        waitlistRepository.loadWaitlistForEvent(eventId, new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
            @Override
            public void onSuccess(List<WaitlistToEvent> entries) {
                WaitlistToEvent offer = WaitlistUtils.findActiveEntryForUser(entries, uid);
                if (offer == null || !WaitlistToEvent.STATUS_OFFERED.equals(offer.getStatus())) {
                    errorMessage.postValue("No tienes una plaza pendiente de confirmar");
                    isLoading.postValue(false);
                    return;
                }
                if (!WaitlistUtils.isValidOffer(offer, System.currentTimeMillis())) {
                    errorMessage.postValue("El plazo para confirmar la plaza ha expirado");
                    isLoading.postValue(false);
                    tryPromoteFromWaitlist(eventId);
                    return;
                }
                String parentalAuthUrl = offer.getParentalAuthUrl();
                AttendeesToEvent attendeeToEvent = new AttendeesToEvent();
                attendeeToEvent.setEventId(eventId);
                attendeeToEvent.setUserId(uid);
                attendeeToEvent.setScannedQR(false);
                if (parentalAuthUrl != null && !parentalAuthUrl.trim().isEmpty()) {
                    attendeeToEvent.setParentalAuthUrl(parentalAuthUrl.trim());
                }
                int knownOffered = WaitlistUtils.countValidOffers(entries);
                waitlistRepository.confirmOfferAndRegister(
                        offer,
                        attendeeToEvent,
                        0,
                        knownOffered,
                        new WaitlistRepository.RepositoryCallback<AttendeesToEvent>() {
                            @Override
                            public void onSuccess(AttendeesToEvent result) {
                                attendeeAdded.postValue(true);
                                isLoading.postValue(false);
                                notifyOrganizerAfterRegistrationChange(
                                        eventId,
                                        attendeeDisplayName,
                                        OrganizerNotification.TYPE_ATTENDEE_JOINED,
                                        eventTitle);
                                EventActivityLogHelper.logJoined(eventId, attendeeDisplayName);
                                EventActivityLogHelper.logWaitlistPromoted(
                                        eventId, attendeeDisplayName);
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

    public void removeFromWaitlistByOrganizer(String entryId, String eventId,
                                              @Nullable String attendeeDisplayName) {
        if (waitlistRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        isLoading.postValue(true);
        waitlistRepository.loadWaitlistForEvent(eventId, new WaitlistRepository.RepositoryCallback<List<WaitlistToEvent>>() {
            @Override
            public void onSuccess(List<WaitlistToEvent> entries) {
                WaitlistToEvent target = null;
                for (WaitlistToEvent entry : entries) {
                    if (entry != null && entryId.equals(entry.getId())) {
                        target = entry;
                        break;
                    }
                }
                if (target == null) {
                    errorMessage.postValue("Entrada de lista de espera no encontrada");
                    isLoading.postValue(false);
                    return;
                }
                boolean wasOffered = WaitlistToEvent.STATUS_OFFERED.equals(target.getStatus())
                        && WaitlistUtils.isValidOffer(target, System.currentTimeMillis());
                waitlistRepository.cancelEntry(eventId, target.getId(),
                        new WaitlistRepository.RepositoryCallback<WaitlistToEvent>() {
                            @Override
                            public void onSuccess(WaitlistToEvent result) {
                                isLoading.postValue(false);
                                EventActivityLogHelper.logWaitlistLeft(eventId, attendeeDisplayName);
                                if (wasOffered) {
                                    tryPromoteFromWaitlist(eventId);
                                }
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

}