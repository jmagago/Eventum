package com.us.eventum.data.repositories;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.auth.FirebaseAuth;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;
    private final FirebaseDatabase database;
    private final DatabaseReference rootRef;

    private FirebaseManager() {
        Log.d(TAG, "Inicializando FirebaseManager");
        try {
            database = FirebaseDatabase.getInstance("https://eventum-qr-default-rtdb.europe-west1.firebasedatabase.app");
            database.setPersistenceEnabled(true); // Habilitar persistencia offline
            rootRef = database.getReference();
            Log.d(TAG, "FirebaseManager inicializado correctamente");
            Log.d(TAG, "URL de la base de datos: " + database.getReference().toString());
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar FirebaseManager", e);
            throw e;
        }
    }

    public static FirebaseManager getInstance() {
        if (instance == null) {
            Log.d(TAG, "Creando nueva instancia de FirebaseManager");
            instance = new FirebaseManager();
        }
        return instance;
    }

    public void writeMessage(String path, String message) {
        Log.d(TAG, "Escribiendo mensaje en path: " + path);
        try {
            DatabaseReference ref = rootRef.child(path);
            Log.d(TAG, "Ruta completa: " + ref.toString());
            ref.setValue(message)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mensaje escrito exitosamente");
                    Log.d(TAG, "Valor actual: " + message);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al escribir mensaje", e);
                    Log.e(TAG, "Detalles del error: " + e.getMessage());
                });
        } catch (DatabaseException e) {
            Log.e(TAG, "Error de base de datos al escribir mensaje", e);
        } catch (Exception e) {
            Log.e(TAG, "Error inesperado al escribir mensaje", e);
        }
    }

    public void readMessage(String path, ValueEventListener listener) {
        Log.d(TAG, "Configurando lectura en path: " + path);
        try {
            DatabaseReference ref = rootRef.child(path);
            Log.d(TAG, "Ruta completa para lectura: " + ref.toString());
            ref.addValueEventListener(listener);
            Log.d(TAG, "Listener configurado exitosamente");
        } catch (DatabaseException e) {
            Log.e(TAG, "Error de base de datos al configurar lectura", e);
        } catch (Exception e) {
            Log.e(TAG, "Error inesperado al configurar lectura", e);
        }
    }

    public void removeListener(String path, ValueEventListener listener) {
        Log.d(TAG, "Removiendo listener en path: " + path);
        try {
            DatabaseReference ref = rootRef.child(path);
            ref.removeEventListener(listener);
            Log.d(TAG, "Listener removido exitosamente");
        } catch (Exception e) {
            Log.e(TAG, "Error al remover listener", e);
        }
    }
} 