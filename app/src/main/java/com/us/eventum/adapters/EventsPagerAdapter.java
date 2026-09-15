package com.us.eventum.adapters;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.us.eventum.R;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.fragments.EventsFragment;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventsPagerAdapter extends FragmentStateAdapter {
    private static final int NUM_TABS = 2;
    private final List<Event> futureEvents;
    private final List<Event> pastEvents;
    private final FragmentActivity hostActivity;
    private final Map<Integer, EventsFragment> fragmentsMap = new HashMap<>();
    private Context context;
    private EventsFragment.OnRefreshListener refreshListener;

    public interface EventContextMenuListener {
        boolean onMenuItemClick(MenuItem item, Event event);
    }

    public EventsPagerAdapter(@NonNull FragmentActivity fragmentActivity, 
                           List<Event> futureEvents, 
                           List<Event> pastEvents) {
        super(fragmentActivity);
        this.hostActivity = fragmentActivity;
        this.context = fragmentActivity;
        this.futureEvents = futureEvents;
        this.pastEvents = pastEvents;
    }
    
    public void setOnRefreshListener(EventsFragment.OnRefreshListener listener) {
        this.refreshListener = listener;
        // Establecer el callback en todos los fragments existentes
        for (EventsFragment fragment : fragmentsMap.values()) {
            if (fragment != null) {
                fragment.setOnRefreshListener(listener);
            }
        }
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        EventsFragment fragment;
        if (position == 0) {
            fragment = EventsFragment.newInstance(futureEvents, true);
        } else {
            fragment = EventsFragment.newInstance(pastEvents, false);
        }
        
        // Establecer el callback si está disponible
        if (refreshListener != null) {
            fragment.setOnRefreshListener(refreshListener);
        }
        
        fragmentsMap.put(position, fragment);

        // Sincronizar datos actuales en cuanto el fragment tenga vista (p. ej. Archivados creado tarde)
        fragment.getViewLifecycleOwnerLiveData().observe(
                hostActivity,
                owner -> {
                    if (owner != null) {
                        if (position == 0) {
                            fragment.setEvents(futureEvents);
                        } else {
                            fragment.setEvents(pastEvents);
                        }
                    }
                });

        return fragment;
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
    
    public void updateEvents() {
        for (Map.Entry<Integer, EventsFragment> entry : fragmentsMap.entrySet()) {
            if (entry.getValue() != null) {
                if (entry.getKey() == 0) {
                    entry.getValue().setEvents(futureEvents);
                } else {
                    entry.getValue().setEvents(pastEvents);
                }
            }
        }
    }
    
    public void updateEvents(List<Event> newFutureEvents, List<Event> newPastEvents) {
        for (Map.Entry<Integer, EventsFragment> entry : fragmentsMap.entrySet()) {
            if (entry.getValue() != null) {
                if (entry.getKey() == 0) {
                    entry.getValue().setEvents(newFutureEvents);
                } else {
                    entry.getValue().setEvents(newPastEvents);
                }
            }
        }
    }
    
    public void updateEvents(List<Event> newFutureEvents, List<Event> newPastEvents, boolean isSearchResult) {
        for (Map.Entry<Integer, EventsFragment> entry : fragmentsMap.entrySet()) {
            if (entry.getValue() != null) {
                if (entry.getKey() == 0) {
                    entry.getValue().setEvents(newFutureEvents, isSearchResult);
                } else {
                    entry.getValue().setEvents(newPastEvents, isSearchResult);
                }
            }
        }
    }

    public void showEventContextMenu(View anchor, Event event, EventContextMenuListener listener) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_event_details, popup.getMenu());
        configureEventMenu(popup.getMenu(), event, context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
        }

        popup.setOnMenuItemClickListener(item -> {
            if (listener != null) {
                return listener.onMenuItemClick(item, event);
            }
            return false;
        });
        
        popup.show();
    }

    public static void configureEventMenu(android.view.Menu menu, Event event, Context context) {
        configureEventMenu(menu, event, context, event != null ? event.getWaitlistCount() : 0);
    }

    public static void configureEventMenu(android.view.Menu menu, Event event, Context context,
                                          int waitlistCount) {
        if (menu == null || context == null) {
            return;
        }
        configurePrivateAccessCodeMenuItem(menu, event);
        boolean cancelled = event != null && event.isCancelled();
        boolean past = event != null && event.getDate() != null && event.getDate().before(new Date());
        boolean inactive = cancelled || past;

        android.view.MenuItem waitlistItem = menu.findItem(R.id.action_view_waitlist);
        if (waitlistItem != null) {
            waitlistItem.setTitle(context.getString(R.string.menu_view_waitlist, Math.max(0, waitlistCount)));
            waitlistItem.setVisible(!inactive);
        }
        setMenuVisible(menu, R.id.action_verify_attendees, !inactive);
        setMenuVisible(menu, R.id.action_edit_event, !cancelled);
        setMenuVisible(menu, R.id.action_clear_list, !inactive);
        setMenuVisible(menu, R.id.action_cancel_event, !cancelled && !past);
    }

    private static void setMenuVisible(android.view.Menu menu, int itemId, boolean visible) {
        android.view.MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    public static void configurePrivateAccessCodeMenuItem(android.view.Menu menu, Event event) {
        if (menu == null) {
            return;
        }
        android.view.MenuItem item = menu.findItem(R.id.action_view_private_access_code);
        if (item != null) {
            item.setVisible(event != null && event.getPrivateEvent());
        }
    }
}