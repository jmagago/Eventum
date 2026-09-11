package com.us.eventum.utils;

import android.app.Dialog;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.us.eventum.R;

public final class PrivateAccessCodeDialogHelper {

    public interface Callback {
        void onCodeConfirmed(@NonNull String accessCode);

        void onCancelled();
    }

    private PrivateAccessCodeDialogHelper() {
    }

    public static void show(@NonNull AppCompatActivity activity, @NonNull Callback callback) {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_set_private_access_code, null);
        TextInputLayout codeLayout = dialogView.findViewById(R.id.privateAccessCodeLayout);
        TextInputEditText codeInput = dialogView.findViewById(R.id.privateAccessCodeInput);
        if (codeLayout != null && codeInput != null) {
            PrivateAccessCodeInputHelper.attachGenerateEndIcon(codeLayout, codeInput);
        }
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);
        MaterialButton confirmButton = dialogView.findViewById(R.id.confirmButton);

        Dialog dialog = new Dialog(activity);
        View modalRoot = AttendeeProfileDialogHelper.wrapWithModalScrim(activity, dialogView);
        dialog.setContentView(modalRoot);
        dialog.setCancelable(true);
        AttendeeProfileDialogHelper.applyModalDialogWindow(dialog);

        dialog.setOnCancelListener(d -> callback.onCancelled());
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancelled();
        });

        confirmButton.setOnClickListener(v -> {
            String code = codeInput.getText() != null ? codeInput.getText().toString().trim() : "";
            if (!EventPrivateAccessCode.isValidFormat(code)) {
                AnimationUtils.showErrorWithAnimation(
                        codeLayout, activity.getString(R.string.error_private_access_code_format));
                return;
            }
            AnimationUtils.clearErrorWithAnimation(codeLayout);
            dialog.dismiss();
            callback.onCodeConfirmed(EventPrivateAccessCode.normalize(code));
        });

        dialog.show();
        if (codeInput != null) {
            codeInput.requestFocus();
        }
    }

    public static void showViewCode(@NonNull AppCompatActivity activity, @Nullable String accessCode) {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_view_private_access_code, null);
        MaterialCardView codeCard = dialogView.findViewById(R.id.privateAccessCodeCard);
        TextView codeValue = dialogView.findViewById(R.id.privateAccessCodeValue);
        TextView emptyMessage = dialogView.findViewById(R.id.privateAccessCodeEmptyMessage);
        MaterialButton closeButton = dialogView.findViewById(R.id.closeButton);

        boolean hasCode = accessCode != null && !accessCode.isEmpty();
        if (codeCard != null) {
            codeCard.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        }
        if (codeValue != null && hasCode) {
            codeValue.setText(accessCode);
        }
        if (emptyMessage != null) {
            emptyMessage.setVisibility(hasCode ? View.GONE : View.VISIBLE);
        }

        Dialog dialog = new Dialog(activity);
        View modalRoot = AttendeeProfileDialogHelper.wrapWithModalScrim(activity, dialogView);
        dialog.setContentView(modalRoot);
        dialog.setCancelable(true);
        AttendeeProfileDialogHelper.applyModalDialogWindow(dialog);

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }
}
