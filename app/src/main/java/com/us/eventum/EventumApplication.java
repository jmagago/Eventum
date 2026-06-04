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

        FirebaseApp.initializeApp(this);
        Log.d(TAG, "Firebase inicializado correctamente");
    }
}
