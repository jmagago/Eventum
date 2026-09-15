package com.us.eventum.utils;

import android.app.Dialog;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.us.eventum.R;
import com.us.eventum.adapters.WaitlistAdapter;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.WaitlistToEvent;
import com.us.eventum.data.repositories.AttendeeRepository;
import com.us.eventum.data.repositories.firebase.FirebaseAttendeeRepository;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WaitlistDialogHelper {

    private WaitlistDialogHelper() {
    }

    public static void show(@NonNull AppCompatActivity activity,
                            @NonNull String eventId,
                            @NonNull AttendeeViewModel attendeeViewModel) {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_waitlist, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.waitlistRecyclerView);
        TextView emptyMessage = dialogView.findViewById(R.id.waitlistEmptyMessage);
        MaterialButton closeButton = dialogView.findViewById(R.id.closeButton);
        WaitlistAdapter adapter = new WaitlistAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);

        Dialog dialog = new Dialog(activity);
        View modalRoot = AttendeeProfileDialogHelper.wrapWithModalScrim(activity, dialogView);
        dialog.setContentView(modalRoot);
        dialog.setCancelable(true);
        AttendeeProfileDialogHelper.applyModalDialogWindow(dialog);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        attendeeViewModel.loadEventWaitlist(eventId);
        Observer<List<WaitlistToEvent>> waitlistObserver = entries -> {
            List<WaitlistToEvent> active = new ArrayList<>();
            if (entries != null) {
                for (WaitlistToEvent entry : entries) {
                    if (entry != null && entry.isActiveForUser()) {
                        active.add(entry);
                    }
                }
            }
            boolean empty = active.isEmpty();
            emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (empty) {
                adapter.submit(new ArrayList<>());
                return;
            }
            loadRows(activity, active, adapter);
        };
        attendeeViewModel.getEventWaitlist().observeForever(waitlistObserver);
        dialog.setOnDismissListener(d ->
                attendeeViewModel.getEventWaitlist().removeObserver(waitlistObserver));

        adapter.setListener((entry, displayName) ->
                attendeeViewModel.removeFromWaitlistByOrganizer(
                        entry.getId(), eventId, displayName));

        dialog.show();
    }

    private static void loadRows(AppCompatActivity activity,
                                 List<WaitlistToEvent> active,
                                 WaitlistAdapter adapter) {
        AttendeeRepository attendeeRepository = new FirebaseAttendeeRepository();
        Map<String, Attendee> profiles = new HashMap<>();
        int[] loaded = {0};
        int total = active.size();
        List<WaitlistAdapter.Row> rows = new ArrayList<>();
        int waitingPosition = 0;
        for (WaitlistToEvent entry : WaitlistUtils.sortByJoinedAt(active)) {
            if (WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())) {
                waitingPosition++;
            }
        }
        int positionCounter = 0;
        for (WaitlistToEvent entry : WaitlistUtils.sortByJoinedAt(active)) {
            final int position = WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus())
                    ? ++positionCounter : 0;
            attendeeRepository.getAttendee(entry.getUserId(),
                    new AttendeeRepository.RepositoryCallback<Attendee>() {
                        @Override
                        public void onSuccess(Attendee attendee) {
                            String name = attendee != null ? attendee.getSortedNameLabel() : entry.getUserId();
                            String status;
                            if (WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus())) {
                                status = activity.getString(R.string.waitlist_status_offered);
                            } else {
                                status = activity.getString(R.string.waitlist_status_waiting)
                                        + " · #" + position;
                            }
                            rows.add(new WaitlistAdapter.Row(entry, name, position, status));
                            loaded[0]++;
                            if (loaded[0] == total) {
                                adapter.submit(rows);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            rows.add(new WaitlistAdapter.Row(
                                    entry,
                                    entry.getUserId(),
                                    position,
                                    activity.getString(R.string.waitlist_status_waiting)));
                            loaded[0]++;
                            if (loaded[0] == total) {
                                adapter.submit(rows);
                            }
                        }
                    });
        }
    }
}
