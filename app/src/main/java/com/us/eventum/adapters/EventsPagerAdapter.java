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
import com.us.eventum.models.Event;
import com.us.eventum.presentation.fragments.EventsFragment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventsPagerAdapter extends FragmentStateAdapter {
    private static final int NUM_TABS = 2;
    private final List<Event> futureEvents;
    private final List<Event> pastEvents;
    private final Map<Integer, EventsFragment> fragmentsMap = new HashMap<>();
    private Context context;

    public interface EventContextMenuListener {
        boolean onMenuItemClick(MenuItem item, Event event);
    }

    public EventsPagerAdapter(@NonNull FragmentActivity fragmentActivity, 
                           List<Event> futureEvents, 
                           List<Event> pastEvents) {
        super(fragmentActivity);
        this.context = fragmentActivity;
        this.futureEvents = futureEvents;
        this.pastEvents = pastEvents;
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
        fragmentsMap.put(position, fragment);
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

    public void showEventContextMenu(View anchor, Event event, EventContextMenuListener listener) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_event_details, popup.getMenu());
        
        // Forzar que se muestren los iconos
        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            java.lang.reflect.Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceShowIcon.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.setOnMenuItemClickListener(item -> {
            if (listener != null) {
                return listener.onMenuItemClick(item, event);
            }
            return false;
        });
        
        popup.show();
    }
}