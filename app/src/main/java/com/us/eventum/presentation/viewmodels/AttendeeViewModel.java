package com.us.eventum.presentation.viewmodels;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.Event;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.hybrid.HybridAttendeeRepository;

import java.util.List;

public class AttendeeViewModel extends ViewModel {
    private MutableLiveData<List<Attendee>> attendees = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeAdded = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeUpdated = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeDeleted = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeeVerified = new MutableLiveData<>();
    private MutableLiveData<Boolean> attendeesCleared = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    private AttendeeRepository attendeeRepository;

    // Getters para LiveData
    public LiveData<List<Attendee>> getAttendees() { return attendees; }
    public LiveData<Boolean> getAttendeeAdded() { return attendeeAdded; }
    public LiveData<Boolean> getAttendeeUpdated() { return attendeeUpdated; }
    public LiveData<Boolean> getAttendeeDeleted() { return attendeeDeleted; }
    public LiveData<Boolean> getAttendeeVerified() { return attendeeVerified; }
    public LiveData<Boolean> getAttendeesCleared() { return attendeesCleared; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    /**
     * Inicializar el repositorio
     */
    public void initializeRepository(Context context) {
        if (attendeeRepository == null) {
            attendeeRepository = new HybridAttendeeRepository(context);
        }
    }

    /**
     * Cargar asistentes de un evento específico
     */
    public void loadEventAttendees(String eventId) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        if (eventId == null || eventId.isEmpty()) {
            errorMessage.postValue("ID de evento no válido");
            return;
        }
        
        isLoading.postValue(true);

        attendeeRepository.loadEventAttendees(eventId, new AttendeeRepository.RepositoryCallback<List<Attendee>>() {
            @Override
            public void onSuccess(List<Attendee> result) {
                attendees.postValue(result);
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
     * Añadir un nuevo asistente
     */
    public void addAttendee(String eventId, String name, String lastName, String dni,
                           String email, String phone, String birthDate, boolean requiresAuth) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);

        // Validaciones
        if (name == null || name.trim().isEmpty()) {
            errorMessage.postValue("El nombre es obligatorio");
            isLoading.postValue(false);
            return;
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            errorMessage.postValue("El apellido es obligatorio");
            isLoading.postValue(false);
            return;
        }
        if (dni == null || dni.trim().isEmpty()) {
            errorMessage.postValue("El DNI es obligatorio");
            isLoading.postValue(false);
            return;
        }
        if (email == null || email.trim().isEmpty()) {
            errorMessage.postValue("El email es obligatorio");
            isLoading.postValue(false);
            return;
        }
        if (phone == null || phone.trim().isEmpty()) {
            errorMessage.postValue("El teléfono es obligatorio");
            isLoading.postValue(false);
            return;
        }
        if (birthDate == null || birthDate.trim().isEmpty()) {
            errorMessage.postValue("La fecha de nacimiento es obligatoria");
            isLoading.postValue(false);
            return;
        }

        // Crear el asistente
        Attendee attendee = new Attendee(name, lastName, dni, email, phone, birthDate);
        attendee.setEventId(eventId);
        attendee.setRequiresParentalAuthorization(requiresAuth);

        attendeeRepository.createAttendee(attendee, new AttendeeRepository.RepositoryCallback<Attendee>() {
            @Override
            public void onSuccess(Attendee result) {
                attendeeAdded.postValue(true);
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
     * Eliminar un asistente
     */
    public void deleteAttendee(String attendeeId) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);
        
        attendeeRepository.deleteAttendee(attendeeId, new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                attendeeDeleted.postValue(true);
                isLoading.postValue(false);
                // No recargar aquí, la Activity observará y actualizará
            }
            
            @Override
            public void onError(String error) {
                errorMessage.postValue(error);
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Verificar un asistente (marcar como verificado)
     */
    public void verifyAttendee(String attendeeId, String eventId) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);

        attendeeRepository.verifyAttendee(attendeeId, eventId, new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                attendeeVerified.postValue(true);
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
     * Limpiar todos los asistentes de un evento
     */
    public void clearEventAttendees(String eventId) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        
        isLoading.postValue(true);

        attendeeRepository.clearEventAttendees(eventId, new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                attendeesCleared.postValue(true);
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
     * Desapuntarse de un evento por email
     */
    public void unsubscribeFromEvent(String eventId, String email) {
        if (attendeeRepository == null) {
            errorMessage.postValue("Repositorio no inicializado");
            return;
        }
        if (eventId == null || eventId.isEmpty()) {
            errorMessage.postValue("ID de evento no válido");
            return;
        }
        if (email == null || email.trim().isEmpty()) {
            errorMessage.postValue("Email obligatorio");
            return;
        }
        isLoading.postValue(true);
        attendeeRepository.unsubscribeByEventAndEmail(eventId, email.trim(), new AttendeeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
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



    /**
     * Limpiar estados de operaciones
     */
    public void clearOperationStates() {
        attendeeAdded.postValue(false);
        attendeeUpdated.postValue(false);
        attendeeDeleted.postValue(false);
        attendeeVerified.postValue(false);
        attendeesCleared.postValue(false);
        errorMessage.postValue(null);
    }
}