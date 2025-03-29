package com.us.eventum;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;

public class EventumApplication extends Application {
    private static final String TAG = "EventumApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Inicializando aplicación");
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase inicializado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar Firebase", e);
        }
    }
} 