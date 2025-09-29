package com.us.eventum.data.repositories;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseException;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;
    
    // Servicios de Firebase
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    
    // Realtime Database (mantenido para compatibilidad)
    private final FirebaseDatabase database;
    private final DatabaseReference rootRef;

    private FirebaseManager() {
        Log.d(TAG, "Inicializando FirebaseManager");
        try {
            // Inicializar servicios principales
            this.auth = FirebaseAuth.getInstance();
            this.firestore = FirebaseFirestore.getInstance();
            this.storage = FirebaseStorage.getInstance();
            
            // Inicializar Realtime Database (opcional)
            this.database = FirebaseDatabase.getInstance("https://eventum-qr-default-rtdb.europe-west1.firebasedatabase.app");
            this.database.setPersistenceEnabled(true);
            this.rootRef = this.database.getReference();
            
            Log.d(TAG, "FirebaseManager inicializado correctamente");
            Log.d(TAG, "Servicios disponibles: Auth, Firestore, Storage, RealtimeDB");
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

    // Getters para servicios principales
    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }

    // Getters para Realtime Database (compatibilidad)
    public FirebaseDatabase getDatabase() {
        return database;
    }

    public DatabaseReference getRootRef() {
        return rootRef;
    }

    // Métodos de Realtime Database (mantenidos para compatibilidad)
    public void writeMessage(String path, String message) {
        Log.d(TAG, "Escribiendo mensaje en path: " + path);
        try {
            DatabaseReference ref = rootRef.child(path);
            Log.d(TAG, "Ruta completa: " + ref.toString());
            ref.setValue(message)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mensaje escrito con éxito");
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
            Log.d(TAG, "Listener configurado con éxito");
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
            Log.d(TAG, "Listener removido con éxito");
        } catch (Exception e) {
            Log.e(TAG, "Error al remover listener", e);
        }
    }
} 