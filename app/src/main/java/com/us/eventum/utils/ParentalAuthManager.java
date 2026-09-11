package com.us.eventum.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.us.eventum.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ParentalAuthManager {

    public interface UploadCallback {
        void onSuccess(@NonNull String downloadUrl);

        void onError(@NonNull String message);
    }

    private ParentalAuthManager() {
    }

    public static void uploadParentalAuthorization(@NonNull Context context,
                                                   @NonNull Uri fileUri,
                                                   @NonNull String eventId,
                                                   @NonNull String userId,
                                                   @NonNull UploadCallback callback) {
        String extension = resolveExtension(context, fileUri);
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("parental_auth/" + eventId + "/" + userId + extension);

        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
            if (inputStream == null) {
                callback.onError(context.getString(R.string.parental_auth_upload_error));
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            ref.putBytes(buffer.toByteArray())
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            throw task.getException() != null
                                    ? task.getException()
                                    : new IllegalStateException("Upload failed");
                        }
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                    .addOnFailureListener(e -> callback.onError(
                            context.getString(R.string.parental_auth_upload_error)));
        } catch (Exception e) {
            callback.onError(context.getString(R.string.parental_auth_upload_error));
        }
    }

    @NonNull
    private static String resolveExtension(@NonNull Context context, @NonNull Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (mime != null && mime.contains("pdf")) {
            return ".pdf";
        }
        return ".jpg";
    }
}
