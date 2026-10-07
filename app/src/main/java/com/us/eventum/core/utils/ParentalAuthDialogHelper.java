package com.us.eventum.core.utils;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;

public final class ParentalAuthDialogHelper {

    public interface JoinCallback {
        void onAccepted();

        void onCancelled();
    }

    public interface UploadCallback {
        void onUploadSuccess(@NonNull String parentalAuthUrl);

        void onCancelled();
    }

    public static final class Handle {
        private final Dialog dialog;
        private final View dialogView;
        private final Uri[] selectedUri = {null};
        private final TextView fileNameView;
        private final MaterialButton selectButton;
        private final MaterialButton uploadButton;
        private final MaterialButton cancelButton;
        private final ProgressBar progressBar;

        private Handle(Dialog dialog, View dialogView, MaterialButton selectButton,
                       MaterialButton uploadButton, MaterialButton cancelButton,
                       TextView fileNameView, ProgressBar progressBar) {
            this.dialog = dialog;
            this.dialogView = dialogView;
            this.selectButton = selectButton;
            this.uploadButton = uploadButton;
            this.cancelButton = cancelButton;
            this.fileNameView = fileNameView;
            this.progressBar = progressBar;
        }

        public Dialog getDialog() {
            return dialog;
        }

        public void setOnSelectFileClickListener(@NonNull View.OnClickListener listener) {
            selectButton.setOnClickListener(listener);
        }

        public void onFileSelected(@Nullable Uri uri, @Nullable String displayName) {
            selectedUri[0] = uri;
            if (uri != null) {
                fileNameView.setVisibility(View.VISIBLE);
                fileNameView.setText(displayName != null && !displayName.isEmpty()
                        ? displayName
                        : dialogView.getContext().getString(R.string.parental_auth_file_selected));
            } else {
                fileNameView.setVisibility(View.GONE);
            }
        }

        private void startUpload(@NonNull Activity activity, @NonNull Event event,
                                 @NonNull String userId, @NonNull UploadCallback callback) {
            if (selectedUri[0] == null) {
                AttendeeProfileDialogHelper.showError(
                        dialogView,
                        activity.getString(R.string.parental_auth_file_required));
                return;
            }
            uploadButton.setEnabled(false);
            selectButton.setEnabled(false);
            cancelButton.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            ParentalAuthManager.uploadParentalAuthorization(
                    activity,
                    selectedUri[0],
                    event.getId(),
                    userId,
                    new ParentalAuthManager.UploadCallback() {
                        @Override
                        public void onSuccess(@NonNull String downloadUrl) {
                            dialog.dismiss();
                            callback.onUploadSuccess(downloadUrl);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            progressBar.setVisibility(View.GONE);
                            uploadButton.setEnabled(true);
                            selectButton.setEnabled(true);
                            cancelButton.setEnabled(true);
                            AttendeeProfileDialogHelper.showError(dialogView, message);
                        }
                    });
        }
    }

    private ParentalAuthDialogHelper() {
    }

    /** Aviso al apuntarse: sin selección de archivo. */
    public static void showJoinReminder(@NonNull Activity activity, @NonNull JoinCallback callback) {
        View dialogView = activity.getLayoutInflater()
                .inflate(R.layout.dialog_parental_auth_join_reminder, null);
        MaterialButton cancelButton = dialogView.findViewById(R.id.parentalAuthJoinCancelButton);
        MaterialButton acceptButton = dialogView.findViewById(R.id.parentalAuthJoinAcceptButton);

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.CustomTransparentDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        AttendeeProfileDialogHelper.applyTransparentWindow(dialog);

        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancelled();
        });
        acceptButton.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onAccepted();
        });
        dialog.setOnCancelListener(d -> callback.onCancelled());
        dialog.show();
    }

    /** Subida del documento desde el panel del evento. */
    @NonNull
    public static Handle showUpload(@NonNull Activity activity,
                                    @NonNull Event event,
                                    @NonNull String userId,
                                    @NonNull UploadCallback callback) {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_parental_auth_upload, null);
        MaterialButton selectButton = dialogView.findViewById(R.id.selectParentalAuthButton);
        MaterialButton uploadButton = dialogView.findViewById(R.id.uploadParentalAuthButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelParentalAuthButton);
        TextView fileNameView = dialogView.findViewById(R.id.parentalAuthFileName);
        ProgressBar progressBar = dialogView.findViewById(R.id.parentalAuthProgress);

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.CustomTransparentDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        AttendeeProfileDialogHelper.applyTransparentWindow(dialog);

        Handle handle = new Handle(dialog, dialogView, selectButton, uploadButton,
                cancelButton, fileNameView, progressBar);

        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancelled();
        });
        dialog.setOnCancelListener(d -> callback.onCancelled());
        uploadButton.setOnClickListener(v -> handle.startUpload(activity, event, userId, callback));

        dialog.show();
        return handle;
    }
}
