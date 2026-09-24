package com.us.eventum.data.repositories;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;

    private FirebaseManager() {
        try {
            this.auth = FirebaseAuth.getInstance();
            this.firestore = FirebaseFirestore.getInstance();
            this.storage = FirebaseStorage.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar FirebaseManager", e);
            throw e;
        }
    }

    public static FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }
}
