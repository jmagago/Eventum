package com.us.eventum.core.utils;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.us.eventum.R;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.ui.viewmodels.AttendeeViewModel;

import java.nio.charset.StandardCharsets;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public final class AttendeeCsvExportHelper {

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<Intent> saveLauncher;
    private String pendingCsvContent;
    @Nullable
    private Observer<List<Attendee>> pendingExportObserver;
    @Nullable
    private AttendeeViewModel pendingExportViewModel;

    public AttendeeCsvExportHelper(@NonNull AppCompatActivity activity) {
        this.activity = activity;
        saveLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != AppCompatActivity.RESULT_OK
                            || result.getData() == null
                            || pendingCsvContent == null) {
                        pendingCsvContent = null;
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri == null) {
                        pendingCsvContent = null;
                        return;
                    }
                    try (java.io.OutputStream outputStream = activity.getContentResolver().openOutputStream(uri)) {
                        if (outputStream != null) {
                            // BOM UTF-8 para que Excel muestre bien tildes y eñes
                            outputStream.write(0xEF);
                            outputStream.write(0xBB);
                            outputStream.write(0xBF);
                            outputStream.write(pendingCsvContent.getBytes(StandardCharsets.UTF_8));
                            ToastUtils.showCustomToast(activity,
                                    activity.getString(R.string.export_attendees_csv_success),
                                    ToastUtils.ToastType.SUCCESS);
                        }
                    } catch (Exception e) {
                        ToastUtils.showCustomToast(activity,
                                activity.getString(R.string.export_attendees_csv_error),
                                ToastUtils.ToastType.ERROR);
                    } finally {
                        pendingCsvContent = null;
                    }
                });
    }

    public void export(@NonNull List<Attendee> attendees, @Nullable String eventTitle) {
        if (attendees.isEmpty()) {
            ToastUtils.showCustomToast(activity,
                    activity.getString(R.string.export_attendees_csv_empty),
                    ToastUtils.ToastType.WARNING);
            return;
        }
        pendingCsvContent = AttendeeCsvExporter.buildCsvContent(attendees);
        launchSavePicker(eventTitle);
    }

    public void exportForEvent(@NonNull AttendeeViewModel attendeeViewModel,
                               @NonNull String eventId,
                               @Nullable String eventTitle) {
        clearPendingExportObserver();
        pendingExportViewModel = attendeeViewModel;
        pendingExportObserver = attendees -> {
            if (attendees == null) {
                return;
            }
            clearPendingExportObserver();
            export(attendees, eventTitle);
        };
        attendeeViewModel.getAttendees().observe(activity, pendingExportObserver);
        ToastUtils.showCustomToast(activity,
                activity.getString(R.string.export_attendees_csv_loading),
                ToastUtils.ToastType.INFO);
        attendeeViewModel.loadEventAttendees(eventId);
    }

    private void launchSavePicker(@Nullable String eventTitle) {
        String filename = AttendeeCsvExporter.suggestedFilename(eventTitle);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        saveLauncher.launch(intent);
    }

    private void clearPendingExportObserver() {
        if (pendingExportObserver != null && pendingExportViewModel != null) {
            pendingExportViewModel.getAttendees().removeObserver(pendingExportObserver);
        }
        pendingExportObserver = null;
        pendingExportViewModel = null;
    }
}
