package com.us.eventum.data.repositories.firebase;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Borra un evento y sus documentos relacionados (inscripciones, waitlist, avisos, subcolecciones).
 */
final class EventDeleteCascade {

    private static final int MAX_OPS_PER_BATCH = 450;

    private EventDeleteCascade() {
    }

    static void deleteEventAndRelated(@NonNull FirebaseFirestore db,
                                      @NonNull String eventId,
                                      @NonNull Runnable onSuccess,
                                      @NonNull OnFailureListener onFailure) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        queries.add(db.collection("attendeesToEvent").whereEqualTo("eventId", eventId).get());
        queries.add(db.collection("waitlistToEvent").whereEqualTo("eventId", eventId).get());
        queries.add(db.collection("attendeeNotifications").whereEqualTo("eventId", eventId).get());
        queries.add(db.collection("organizerNotifications").whereEqualTo("eventId", eventId).get());
        queries.add(eventRef.collection("activityLog").get());
        queries.add(eventRef.collection("secrets").get());

        Tasks.whenAllSuccess(queries)
                .addOnSuccessListener(results -> {
                    List<DocumentReference> refs = new ArrayList<>();
                    for (Object result : results) {
                        if (!(result instanceof QuerySnapshot)) {
                            continue;
                        }
                        for (DocumentSnapshot document : ((QuerySnapshot) result).getDocuments()) {
                            refs.add(document.getReference());
                        }
                    }
                    refs.add(eventRef);
                    commitBatches(db, refs, 0, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private static void commitBatches(@NonNull FirebaseFirestore db,
                                      @NonNull List<DocumentReference> refs,
                                      int index,
                                      @NonNull Runnable onSuccess,
                                      @NonNull OnFailureListener onFailure) {
        if (index >= refs.size()) {
            onSuccess.run();
            return;
        }
        WriteBatch batch = db.batch();
        int end = Math.min(index + MAX_OPS_PER_BATCH, refs.size());
        for (int i = index; i < end; i++) {
            batch.delete(refs.get(i));
        }
        batch.commit()
                .addOnSuccessListener(unused -> commitBatches(db, refs, end, onSuccess, onFailure))
                .addOnFailureListener(onFailure);
    }
}
