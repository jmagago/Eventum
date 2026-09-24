package com.us.eventum.core.utils;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;
import com.us.eventum.R;
import com.us.eventum.ui.adapters.EventActivityLogAdapter;
import com.us.eventum.data.models.EventActivityLog;
import com.us.eventum.data.repositories.EventActivityLogRepository;
import com.us.eventum.data.repositories.firebase.FirebaseEventActivityLogRepository;

import java.util.List;

public final class ActivityLogPanelController {

    private final AppCompatActivity activity;
    private final EventActivityLogRepository repository;

    @Nullable
    private EventumBottomSheetHelper.Sheet sheet;
    @Nullable
    private RecyclerView recyclerView;
    @Nullable
    private View emptyLayout;
    @Nullable
    private ProgressBar progressBar;
    @Nullable
    private EventActivityLogAdapter adapter;
    @Nullable
    private ListenerRegistration listener;
    @Nullable
    private String currentEventId;

    public ActivityLogPanelController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
        this.repository = new FirebaseEventActivityLogRepository();
    }

    public boolean isVisible() {
        return sheet != null && sheet.dialog.isShowing();
    }

    public void show(@NonNull String eventId, @NonNull String eventTitle) {
        ensureDialog();
        if (sheet == null) {
            return;
        }
        currentEventId = eventId;
        EventumBottomSheetHelper.setSubtitle(sheet.root, eventTitle);
        sheet.dialog.setOnDismissListener(d -> unsubscribe());
        sheet.dialog.show();
        subscribe(eventId);
    }

    public void hide() {
        if (sheet != null && sheet.dialog.isShowing()) {
            sheet.dialog.dismiss();
        }
        unsubscribe();
    }

    public void destroy() {
        hide();
        sheet = null;
    }

    private void ensureDialog() {
        if (sheet != null) {
            return;
        }

        sheet = EventumBottomSheetHelper.create(activity, R.layout.bottom_sheet_activity_log);
        EventumBottomSheetHelper.initHeader(
                sheet.root,
                R.drawable.ic_history,
                R.string.event_activity_log_title,
                R.string.cd_close_activity_log);
        EventumBottomSheetHelper.bindCloseButton(sheet.root, this::hide);

        recyclerView = sheet.root.findViewById(R.id.activityLogRecyclerView);
        emptyLayout = sheet.root.findViewById(R.id.activityLogEmptyLayout);
        progressBar = sheet.root.findViewById(R.id.activityLogProgress);

        adapter = new EventActivityLogAdapter(activity);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(activity));
            recyclerView.setAdapter(adapter);
        }
    }

    private void subscribe(@NonNull String eventId) {
        unsubscribe();
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (emptyLayout != null) {
            emptyLayout.setVisibility(View.GONE);
        }
        listener = repository.subscribe(eventId,
                new EventActivityLogRepository.RepositoryCallback<List<EventActivityLog>>() {
                    @Override
                    public void onSuccess(List<EventActivityLog> logs) {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        if (adapter != null) {
                            adapter.setEntries(logs);
                        }
                        boolean empty = logs == null || logs.isEmpty();
                        if (emptyLayout != null) {
                            emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
                        }
                        if (recyclerView != null) {
                            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                            if (!empty && logs != null) {
                                recyclerView.post(() ->
                                        recyclerView.smoothScrollToPosition(logs.size() - 1));
                            }
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        View anchor = EventumBottomSheetHelper.toastAnchor(
                                sheet != null ? sheet.root : null, activity);
                        ToastUtils.showCustomToastOnAnchor(anchor,
                                activity.getString(R.string.event_activity_log_load_error),
                                ToastUtils.ToastType.ERROR);
                    }
                });
    }

    private void unsubscribe() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        currentEventId = null;
    }
}
