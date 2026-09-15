package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.hdodenhof.circleimageview.CircleImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.us.eventum.R;
import com.us.eventum.adapters.AttendeeEventsPagerAdapter;
import com.us.eventum.data.models.Attendee;
import com.us.eventum.data.models.Event;
import com.us.eventum.presentation.fragments.AttendeeEventsFragment;
import com.us.eventum.presentation.viewmodels.EventViewModel;
import com.us.eventum.presentation.viewmodels.AttendeeViewModel;
import com.us.eventum.utils.AgeUtils;
import com.us.eventum.utils.AttendeeEventFilter;
import com.us.eventum.utils.AttendeeEventPanelController;
import com.us.eventum.utils.FilterAttendeeEventsPanelController;
import com.us.eventum.utils.EventUiMerger;
import com.us.eventum.utils.AttendeeQrPanelController;
import com.us.eventum.utils.EventPrivateAccessCode;
import com.us.eventum.utils.ParentalAuthDialogHelper;
import com.us.eventum.utils.ToastUtils;
import com.us.eventum.utils.VibrationUtils;
import com.us.eventum.utils.WindowInsetsHelper;
import com.us.eventum.utils.ProfileImageManager;
import com.us.eventum.utils.NetworkUtils;
import com.us.eventum.utils.NotificationPermissionHelper;
import com.us.eventum.utils.AttendeeNotificationWatcher;
import com.us.eventum.utils.AttendeeNotificationDispatcher;
import com.us.eventum.utils.AttendeeNotificationHelper;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.presentation.viewmodels.SharedViewModel;

import android.app.Dialog;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.Calendar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Home del asistente: pestañas Mis eventos, Descubrir e Historial (eventos pasados inscritos).
 */
public class AttendeeHomeActivity extends AppCompatActivity
        implements AttendeeEventsFragment.AttendeeEventListener {

    private static final int TAB_MY_EVENTS = 0;
    private static final int TAB_DISCOVER = 1;
    private static final int TAB_HISTORY = 2;

    private EventViewModel eventViewModel;
    private AttendeeViewModel attendeeViewModel;
    private CircleImageView profileImageView;
    private SharedViewModel sharedViewModel = SharedViewModel.getInstance();
    private EventDialogContext eventDialogContext;
    private AttendeeEventPanelController eventPanelController;
    private AttendeeQrPanelController qrPanelController;
    private boolean pendingSubscribeAction;
    private boolean pendingWaitlistAction;
    private boolean pendingConfirmWaitlistAction;
    private boolean decliningWaitlistOffer;
    private boolean waitingProfileForSubscribe;
    private boolean profileSaveSubmitted;
    private Event pendingSubscribeEvent;
    private String pendingSubscribeUserEmail;
    private Dialog activeCompleteProfileDialog;
    private ParentalAuthDialogHelper.Handle parentalAuthDialogHandle;

    private final ActivityResultLauncher<String[]> parentalAuthPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onParentalAuthFilePicked);
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private AttendeeEventsPagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private final List<Event> myEventsRaw = new ArrayList<>();
    private final List<Event> discoverEventsRaw = new ArrayList<>();
    private final List<Event> historyEventsRaw = new ArrayList<>();
    private final List<Event> myEventsList = new ArrayList<>();
    private final List<Event> discoverEventsList = new ArrayList<>();
    private final List<Event> historyEventsList = new ArrayList<>();
    private final AttendeeEventFilter attendeeEventFilter = new AttendeeEventFilter();
    private FilterAttendeeEventsPanelController filterEventsPanelController;
    private ImageButton sortAttendeeEventsButton;
    private ImageButton filterAttendeeEventsButton;
    private LinearLayout filterIndicatorLayout;
    private TextView filterIndicatorText;
    private AttendeeNotificationWatcher attendeeNotificationWatcher;

    private static final class EventDialogContext {
        final Event event;
        final String userEmail;

        EventDialogContext(Event event, String userEmail) {
            this.event = event;
            this.userEmail = userEmail;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_available_events);

        View statusBarStripe = findViewById(R.id.statusBarStripe);
        if (statusBarStripe != null) {
            WindowInsetsHelper.enableEdgeToEdgeWithStatusBarStripe(this, statusBarStripe);
        }

        // Header similar a Home: rellenar nombre/email/rol e imagen de perfil

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        eventViewModel.initializeRepository(this);
        attendeeViewModel = new ViewModelProvider(this).get(AttendeeViewModel.class);
        attendeeViewModel.initializeRepository(this);

        // Referencia a imagen de perfil y carga inicial
        profileImageView = findViewById(R.id.profileImageView);
        ProfileImageManager.loadProfileImage(this, profileImageView);

        // Observar actualización de imagen de perfil desde Settings
        sharedViewModel.getProfileImageUpdated().observe(this, updated -> {
            if (updated != null && updated) {
                ProfileImageManager.loadProfileImage(this, profileImageView);
                sharedViewModel.resetProfileImageUpdated();
            }
        });

        // Pintar datos de usuario en header
        // Usar attendeeViewModel para datos de usuario
        TextView nameTv = findViewById(R.id.userNameTextView);
        TextView emailTv = findViewById(R.id.userEmailTextView);
        TextView roleTv = findViewById(R.id.userRoleTextView);
        attendeeViewModel.getCurrentAttendee().observe(this, attendee -> {
            if (attendee != null) {
                nameTv.setText(attendee.getUsername());
                emailTv.setText(attendee.getEmail());
                roleTv.setText(R.string.role_attendee);
                roleTv.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                roleTv.setBackgroundResource(R.drawable.bg_role_badge_assistant);
            }
        });
        attendeeViewModel.loadCurrentAttendee();

        // Configurar botón de settings
        setupSettingsButton();
        setupViewPager();
        setupAttendeeFilters();
        eventPanelController = new AttendeeEventPanelController(this);
        eventPanelController.setOnHideListener(() -> {
            if (attendeeViewModel != null) {
                attendeeViewModel.stopListeningEventAttendees();
                attendeeViewModel.stopListeningEventWaitlist();
            }
        });
        eventPanelController.setOnOfferExpiredListener(() -> {
            Event current = eventPanelController.getCurrentEvent();
            if (current != null && current.getId() != null && attendeeViewModel != null) {
                attendeeViewModel.promoteWaitlistIfNeeded(current.getId());
            }
            eventPanelController.showPanelToast(
                    getString(R.string.waitlist_offer_no_longer_valid),
                    ToastUtils.ToastType.WARNING);
        });
        qrPanelController = new AttendeeQrPanelController(this);
        setupPanelBackHandler();
        observeViewModel();
        loadAvailableEvents();

        attendeeNotificationWatcher = new AttendeeNotificationWatcher(this);
        NotificationPermissionHelper.requestIfNeeded(this, notificationPermissionLauncher);
        AttendeeNotificationDispatcher.getLatestNotification().observe(this, notification -> {
            if (notification == null) {
                return;
            }
            String body = AttendeeNotificationHelper.buildBody(this, notification);
            ToastUtils.showCustomToast(this, body, ToastUtils.ToastType.SUCCESS);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (attendeeNotificationWatcher != null
                && FirebaseAuth.getInstance().getCurrentUser() != null) {
            attendeeNotificationWatcher.start(
                    FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
    }

    @Override
    protected void onStop() {
        if (attendeeNotificationWatcher != null) {
            attendeeNotificationWatcher.stop();
        }
        super.onStop();
    }

    private void setupViewPager() {
        pagerAdapter = new AttendeeEventsPagerAdapter(this, myEventsList, discoverEventsList, historyEventsList);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(true);

        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case TAB_MY_EVENTS:
                    tab.setText(getString(R.string.attendee_tab_my_events_count, myEventsList.size()));
                    break;
                case TAB_HISTORY:
                    tab.setText(getString(R.string.attendee_tab_history_count, historyEventsList.size()));
                    break;
                case TAB_DISCOVER:
                default:
                    tab.setText(getString(R.string.attendee_tab_discover_count, discoverEventsList.size()));
                    break;
            }
        });
        tabLayoutMediator.attach();
        applyUniformTabTextSize();
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateFilterIndicator();
                updateAttendeeActionButtons();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profileImageView != null) {
            ProfileImageManager.loadProfileImage(this, profileImageView);
        }
        if (qrPanelController != null) {
            qrPanelController.onHostResume();
        }
        loadAvailableEvents();
    }

    @Override
    protected void onPause() {
        if (qrPanelController != null) {
            qrPanelController.onHostPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (eventViewModel != null) {
            eventViewModel.stopListeningAllEvents();
        }
        if (attendeeViewModel != null) {
            attendeeViewModel.stopListeningEventAttendees();
            attendeeViewModel.stopListeningEventWaitlist();
        }
        super.onDestroy();
    }

    private void setupPanelBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (qrPanelController != null && qrPanelController.isVisible()) {
                    qrPanelController.hide();
                } else if (eventPanelController != null && eventPanelController.isVisible()) {
                    eventPanelController.hide();
                } else if (filterEventsPanelController != null && filterEventsPanelController.isVisible()) {
                    filterEventsPanelController.hide();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void observeViewModel() {
        eventViewModel.getIsLoading().observe(this, loading -> {
            if (loading == null || pagerAdapter == null) {
                return;
            }
            pagerAdapter.setRefreshing(loading);
        });

        eventViewModel.getAllEvents().observe(this, events -> {
            List<Event> list = events != null ? events : new ArrayList<>();
            partitionAndUpdateTabs(list);
            syncOpenEventPanel(list);
            if (pagerAdapter != null) {
                pagerAdapter.setRefreshing(false);
            }
        });

        setupRegistrationResultObservers();
    }

    /** Un solo observer para alta/baja; evita toasts duplicados al abrir diálogos varias veces. */
    private void setupRegistrationResultObservers() {
        attendeeViewModel.getAttendeeAdded().observe(this, added -> {
            if (!Boolean.TRUE.equals(added)) {
                return;
            }
            VibrationUtils.vibrateSuccess(this);
            ToastUtils.showCustomToast(this,
                    "Te has apuntado al evento correctamente", ToastUtils.ToastType.SUCCESS);
            dismissEventDialogIfOpen();
            switchAttendeeTab(TAB_MY_EVENTS);
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getAttendeeDeleted().observe(this, deleted -> {
            if (!Boolean.TRUE.equals(deleted)) {
                return;
            }
            VibrationUtils.vibrateWarning(this);
            ToastUtils.showCustomToast(this,
                    "Te has dado de baja del evento", ToastUtils.ToastType.WARNING);
            dismissEventDialogIfOpen();
            switchAttendeeTab(TAB_DISCOVER);
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getWaitlistJoined().observe(this, joined -> {
            if (!Boolean.TRUE.equals(joined)) {
                return;
            }
            VibrationUtils.vibrateSuccess(this);
            showEventPanelToast(getString(R.string.waitlist_joined_success), ToastUtils.ToastType.SUCCESS);
            switchAttendeeTab(TAB_MY_EVENTS);
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getWaitlistLeft().observe(this, left -> {
            if (!Boolean.TRUE.equals(left)) {
                return;
            }
            VibrationUtils.vibrateWarning(this);
            if (decliningWaitlistOffer) {
                decliningWaitlistOffer = false;
                showEventPanelToast(getString(R.string.waitlist_offer_declined), ToastUtils.ToastType.WARNING);
            } else {
                showEventPanelToast(getString(R.string.waitlist_left_success), ToastUtils.ToastType.WARNING);
                switchAttendeeTab(TAB_DISCOVER);
            }
            attendeeViewModel.clearOperationStates();
        });

        attendeeViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showEventPanelToast(error, ToastUtils.ToastType.ERROR);
                pendingSubscribeAction = false;
                pendingWaitlistAction = false;
                pendingConfirmWaitlistAction = false;
                decliningWaitlistOffer = false;
                waitingProfileForSubscribe = false;
                profileSaveSubmitted = false;
                pendingSubscribeEvent = null;
                pendingSubscribeUserEmail = null;
                attendeeViewModel.clearOperationStates();
            }
        });

        attendeeViewModel.getAttendeeUpdated().observe(this, updated -> {
            if (!Boolean.TRUE.equals(updated) || !waitingProfileForSubscribe) {
                return;
            }
            Attendee refreshed = attendeeViewModel.getCurrentAttendee().getValue();
            Event event = pendingSubscribeEvent;
            String userEmail = pendingSubscribeUserEmail;
            if (refreshed == null || event == null || userEmail == null || !refreshed.isProfileComplete()) {
                return;
            }
            waitingProfileForSubscribe = false;
            profileSaveSubmitted = false;
            pendingSubscribeEvent = null;
            pendingSubscribeUserEmail = null;
            attendeeViewModel.clearOperationStates();
            pendingSubscribeAction = true;
            executeSubscribeWithProfile(event, userEmail, refreshed);
        });
    }

    private void dismissEventDialogIfOpen() {
        if (qrPanelController != null && qrPanelController.isVisible()) {
            qrPanelController.hide();
        }
        if (eventPanelController != null && eventPanelController.isVisible()) {
            eventPanelController.hide();
        }
        eventDialogContext = null;
    }

    private void switchAttendeeTab(int tabIndex) {
        if (viewPager == null) {
            return;
        }
        viewPager.post(() -> viewPager.setCurrentItem(tabIndex, true));
    }

    private void loadAvailableEvents() {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        eventViewModel.loadAllEvents();
    }

    /**
     * Mis eventos: asistente inscrito (independiente de QR), excepto los que van a Historial.
     * Descubrir: no inscrito y fecha de hoy o futura.
     * Historial: inscrito y fecha pasada, o inscrito y evento cancelado.
     */
    private void partitionAndUpdateTabs(List<Event> allEvents) {
        myEventsRaw.clear();
        discoverEventsRaw.clear();
        historyEventsRaw.clear();

        Date now = new Date();
        for (Event event : allEvents) {
            if (event == null || event.getDate() == null) {
                continue;
            }
            if (event.isCancelled()) {
                if (event.isCurrentUserJoined()) {
                    historyEventsRaw.add(event);
                }
                continue;
            }
            if (event.isCurrentUserJoined()) {
                if (isBeforeToday(event.getDate())) {
                    historyEventsRaw.add(event);
                } else {
                    myEventsRaw.add(event);
                }
            } else if (event.isCurrentUserOnWaitlist()) {
                myEventsRaw.add(event);
            } else if (!event.getDate().before(now)) {
                discoverEventsRaw.add(event);
            }
        }

        publishFilteredEvents();
    }

    private void setupAttendeeFilters() {
        filterIndicatorLayout = findViewById(R.id.filterIndicatorLayout);
        filterIndicatorText = findViewById(R.id.filterIndicatorText);
        sortAttendeeEventsButton = findViewById(R.id.sortAttendeeEventsButton);
        filterAttendeeEventsButton = findViewById(R.id.filterAttendeeEventsButton);
        View clearFiltersButton = findViewById(R.id.clearFiltersButton);

        filterEventsPanelController = new FilterAttendeeEventsPanelController(
                this,
                filter -> publishFilteredEvents(),
                () -> attendeeEventFilter,
                this::currentAttendeeTab);

        if (sortAttendeeEventsButton != null) {
            sortAttendeeEventsButton.setOnClickListener(v -> showAttendeeSortMenu());
        }
        if (filterAttendeeEventsButton != null) {
            filterAttendeeEventsButton.setOnClickListener(v -> {
                if (filterEventsPanelController != null) {
                    filterEventsPanelController.show();
                }
            });
        }
        if (clearFiltersButton != null) {
            clearFiltersButton.setOnClickListener(v -> {
                attendeeEventFilter.clearSharedCriteria();
                attendeeEventFilter.setMyStatus(AttendeeEventFilter.Status.ALL);
                attendeeEventFilter.setDiscoverStatus(AttendeeEventFilter.Status.ALL);
                publishFilteredEvents();
            });
        }
        updateAttendeeActionButtons();
    }

    private int currentAttendeeTab() {
        return viewPager != null ? viewPager.getCurrentItem() : AttendeeEventFilter.TAB_MY_EVENTS;
    }

    private void publishFilteredEvents() {
        replaceList(myEventsList, attendeeEventFilter.apply(myEventsRaw, AttendeeEventFilter.TAB_MY_EVENTS));
        replaceList(discoverEventsList, attendeeEventFilter.apply(discoverEventsRaw, AttendeeEventFilter.TAB_DISCOVER));
        replaceList(historyEventsList, attendeeEventFilter.apply(historyEventsRaw, AttendeeEventFilter.TAB_HISTORY));

        if (pagerAdapter != null) {
            pagerAdapter.updateEvents(myEventsList, discoverEventsList, historyEventsList);
        }
        updateTabLabels();
        updateFilterIndicator();
        updateAttendeeActionButtons();
    }

    private static void replaceList(List<Event> target, List<Event> source) {
        target.clear();
        target.addAll(source);
    }

    private void updateFilterIndicator() {
        if (filterIndicatorLayout == null || filterIndicatorText == null) {
            return;
        }
        int tab = currentAttendeeTab();
        if (attendeeEventFilter.hasActiveFilters(tab)) {
            filterIndicatorLayout.setVisibility(View.VISIBLE);
            filterIndicatorText.setText(getString(
                    R.string.filters_summary,
                    attendeeEventFilter.getActiveFiltersSummary(this, tab)));
        } else {
            filterIndicatorLayout.setVisibility(View.GONE);
        }
    }

    private void updateAttendeeActionButtons() {
        int tab = currentAttendeeTab();
        int rawCount = rawCountForTab(tab);
        boolean sortEnabled = tab != AttendeeEventFilter.TAB_HISTORY && rawCount > 1;
        boolean filterEnabled = rawCount > 0 || attendeeEventFilter.hasActiveFilters(tab);

        if (sortAttendeeEventsButton != null) {
            sortAttendeeEventsButton.setEnabled(sortEnabled);
            sortAttendeeEventsButton.setAlpha(sortEnabled ? 1f : 0.38f);
        }
        if (filterAttendeeEventsButton != null) {
            filterAttendeeEventsButton.setEnabled(filterEnabled);
            filterAttendeeEventsButton.setAlpha(filterEnabled ? 1f : 0.38f);
        }
    }

    private int rawCountForTab(int tab) {
        if (tab == AttendeeEventFilter.TAB_DISCOVER) {
            return discoverEventsRaw.size();
        }
        if (tab == AttendeeEventFilter.TAB_HISTORY) {
            return historyEventsRaw.size();
        }
        return myEventsRaw.size();
    }

    private void showAttendeeSortMenu() {
        if (sortAttendeeEventsButton == null || !sortAttendeeEventsButton.isEnabled()) {
            return;
        }
        int tab = currentAttendeeTab();
        final AttendeeEventFilter.Sort[] options = tab == AttendeeEventFilter.TAB_DISCOVER
                ? new AttendeeEventFilter.Sort[] {
                    AttendeeEventFilter.Sort.DATE_ASC,
                    AttendeeEventFilter.Sort.DATE_DESC,
                    AttendeeEventFilter.Sort.FREE_SPOTS_DESC
                }
                : new AttendeeEventFilter.Sort[] {
                    AttendeeEventFilter.Sort.DATE_ASC,
                    AttendeeEventFilter.Sort.DATE_DESC
                };
        final AttendeeEventFilter.Sort activeSort = attendeeEventFilter.sortForTab(tab);
        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(sortAttendeeEventsButton);
        popup.setModal(true);
        popup.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_sort_popup));
        float density = getResources().getDisplayMetrics().density;
        popup.setWidth((int) (240 * density));
        popup.setVerticalOffset((int) (4 * density));
        popup.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return options.length;
            }

            @Override
            public AttendeeEventFilter.Sort getItem(int position) {
                return options[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View optionView = convertView;
                if (optionView == null) {
                    optionView = getLayoutInflater().inflate(R.layout.item_sort_option, parent, false);
                }
                AttendeeEventFilter.Sort sort = options[position];
                TextView titleView = optionView.findViewById(R.id.sortOptionTitle);
                ImageView checkView = optionView.findViewById(R.id.sortOptionCheck);
                titleView.setText(attendeeEventFilter.sortLabelRes(sort));
                boolean selected = sort == activeSort;
                checkView.setVisibility(selected ? View.VISIBLE : View.GONE);
                titleView.setTypeface(null, selected
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                return optionView;
            }
        });
        popup.setOnItemClickListener((parent, view, position, id) -> {
            attendeeEventFilter.setSortForTab(tab, options[position]);
            publishFilteredEvents();
            popup.dismiss();
        });
        popup.show();
    }

    private boolean isBeforeToday(Date date) {
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        return date.before(startOfToday.getTime());
    }

    private void updateTabLabels() {
        if (tabLayout == null) {
            return;
        }
        TabLayout.Tab myTab = tabLayout.getTabAt(TAB_MY_EVENTS);
        TabLayout.Tab discoverTab = tabLayout.getTabAt(TAB_DISCOVER);
        TabLayout.Tab historyTab = tabLayout.getTabAt(TAB_HISTORY);
        if (myTab != null) {
            myTab.setText(getString(R.string.attendee_tab_my_events_count, myEventsList.size()));
        }
        if (discoverTab != null) {
            discoverTab.setText(getString(R.string.attendee_tab_discover_count, discoverEventsList.size()));
        }
        if (historyTab != null) {
            historyTab.setText(getString(R.string.attendee_tab_history_count, historyEventsList.size()));
        }
        applyUniformTabTextSize();
    }

    private void applyUniformTabTextSize() {
        if (tabLayout == null) {
            return;
        }
        tabLayout.post(() -> {
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab == null) {
                    continue;
                }
                for (int c = 0; c < tab.view.getChildCount(); c++) {
                    View child = tab.view.getChildAt(c);
                    if (!(child instanceof TextView)) {
                        continue;
                    }
                    TextView label = (TextView) child;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        label.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                    }
                    label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                    label.setMaxLines(1);
                    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
            }
        });
    }

    private boolean isHistoryEvent(Event event) {
        return event != null && event.isCurrentUserJoined()
                && event.getDate() != null && isBeforeToday(event.getDate());
    }

    private boolean isWaitlistEvent(Event event) {
        return event != null && event.isCurrentUserOnWaitlist()
                && event.getDate() != null && !isBeforeToday(event.getDate());
    }

    @Override
    public void onEventClick(Event event) {
        showEventDetailsDialog(event, isHistoryEvent(event));
    }

    @Override
    public void onEventLongClick(Event event) {
        if (isHistoryEvent(event)) {
            return;
        }
        if (isWaitlistEvent(event)) {
            String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;
            if (userId != null) {
                attendeeViewModel.leaveWaitlist(event.getId(), userId, resolveAttendeeDisplayName());
            }
            return;
        }
        if (event.isCurrentUserScannedQR()) {
            ToastUtils.showCustomToast(this,
                    getString(R.string.attendee_history_no_unsubscribe),
                    ToastUtils.ToastType.INFO);
            return;
        }
        if (!event.isCurrentUserJoined()) {
            return;
        }
        String email = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;
        if (email != null && !email.trim().isEmpty()) {
            eventDialogContext = null;
            attendeeViewModel.unsubscribeFromEvent(
                    event.getId(),
                    email,
                    event.getUserId(),
                    event.getTitle(),
                    resolveAttendeeDisplayName());
        } else {
            showUnsubscribeDialog(event);
        }
    }

    @Override
    public void onLockIconLongClick(Event event) {
        ToastUtils.showCustomToast(this,
                "Solo el organizador puede cambiar la privacidad del evento",
                ToastUtils.ToastType.INFO);
    }

    @Override
    public boolean hasActiveAttendeeFilters(AttendeeEventsFragment.TabType tabType) {
        int tab = AttendeeEventFilter.TAB_MY_EVENTS;
        if (tabType == AttendeeEventsFragment.TabType.DISCOVER) {
            tab = AttendeeEventFilter.TAB_DISCOVER;
        } else if (tabType == AttendeeEventsFragment.TabType.HISTORY) {
            tab = AttendeeEventFilter.TAB_HISTORY;
        }
        return attendeeEventFilter.hasActiveFilters(tab);
    }

    @Override
    public void onRefreshRequested() {
        if (eventViewModel != null) {
            eventViewModel.restartListeningAllEvents();
        }
    }

    private void showJoinDialog(Event event) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Nombre");
        layout.addView(nameInput);

        EditText lastNameInput = new EditText(this);
        lastNameInput.setHint("Apellido");
        layout.addView(lastNameInput);

        EditText dniInput = new EditText(this);
        dniInput.setHint("DNI");
        layout.addView(dniInput);

        EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(emailInput);

        EditText phoneInput = new EditText(this);
        phoneInput.setHint("Teléfono");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(phoneInput);

        EditText birthDateInput = new EditText(this);
        birthDateInput.setHint("Fecha de nacimiento (dd/MM/yyyy)");
        birthDateInput.setFocusable(false);
        birthDateInput.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dd = dayOfMonth < 10 ? "0" + dayOfMonth : String.valueOf(dayOfMonth);
                String mm = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
                birthDateInput.setText(getString(R.string.date_format_dmy, dd, mm, year));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(birthDateInput);

        // Autocompletar con el usuario autenticado
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String authEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (authEmail != null) {
                emailInput.setText(authEmail);
                emailInput.setEnabled(false);
            }
            String displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                String[] parts = displayName.trim().split(" ", 2);
                nameInput.setText(parts[0]);
                if (parts.length > 1) {
                    lastNameInput.setText(parts[1]);
                }
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Apuntarse al evento")
                .setView(layout)
                .setPositiveButton("Apuntarme", (dialog, which) -> {
                    String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                            : null;
                    String displayName = formatDisplayName(
                            nameInput.getText().toString().trim(),
                            lastNameInput.getText().toString().trim());
                    attendeeViewModel.addAttendee(
                            event.getId(),
                            uid,
                            nameInput.getText().toString().trim(),
                            lastNameInput.getText().toString().trim(),
                            dniInput.getText().toString().trim(),
                            emailInput.getText().toString().trim(),
                            phoneInput.getText().toString().trim(),
                            birthDateInput.getText().toString().trim(),
                            false,
                            event.getUserId(),
                            event.getTitle(),
                            displayName
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showUnsubscribeDialog(Event event) {
        final EditText emailInput = new EditText(this);
        emailInput.setHint("Email con el que te apuntaste");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emailInput.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Desapuntarse del evento")
                .setView(emailInput)
                .setPositiveButton("Desapuntarme", (dialog, which) -> {
                    attendeeViewModel.unsubscribeFromEvent(
                            event.getId(),
                            emailInput.getText().toString().trim(),
                            event.getUserId(),
                            event.getTitle(),
                            resolveAttendeeDisplayName());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Configurar el botón de settings en el header
     */
    private void setupSettingsButton() {
        View settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_USER_TYPE, SettingsActivity.USER_TYPE_ATTENDEE);
                startActivity(intent);
            });
        }
    }

    /**
     * Panel deslizante con detalles del evento y opción de apuntarse.
     * Obtiene el evento desde Firestore para reflejar privacidad y código actualizados.
     */
    private void showEventDetailsDialog(Event event, boolean historyMode) {
        if (eventPanelController == null || event == null || event.getId() == null) {
            return;
        }
        eventViewModel.fetchEventById(
                event.getId(),
                fresh -> openEventPanel(EventUiMerger.mergeRemoteWithListState(fresh, event), historyMode),
                () -> openEventPanel(event, historyMode));
    }

    private void openEventPanel(Event event, boolean historyMode) {
        eventPanelController.show(event, historyMode);

        MaterialButton showQrButton = eventPanelController.getShowQrButton();
        if (showQrButton != null) {
            showQrButton.setOnClickListener(v -> showEventQrDialog(event));
        }

        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;
        if (userEmail != null) {
            loadEventAttendeesAndSetupButton(event, userEmail, historyMode);
        }
    }

    private void syncOpenEventPanel(List<Event> events) {
        if (eventPanelController == null || !eventPanelController.isVisible()) {
            return;
        }
        Event current = eventPanelController.getCurrentEvent();
        if (current == null || current.getId() == null) {
            return;
        }
        for (Event updated : events) {
            if (current.getId().equals(updated.getId())) {
                EventUiMerger.syncAllExceptImage(current, updated);
                eventPanelController.syncEventContent(current);
                String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                        : null;
                if (userEmail != null) {
                    updateEventPanelActions(current, userEmail, eventPanelController.isHistoryMode());
                }
                break;
            }
        }
    }

    private void loadEventAttendeesAndSetupButton(Event event, String userEmail, boolean historyMode) {
        attendeeViewModel.getAttendees().removeObservers(this);
        attendeeViewModel.getCurrentUserWaitlistEntry().removeObservers(this);
        attendeeViewModel.clearAttendeesList();

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        attendeeViewModel.startListeningEventAttendees(event.getId());
        attendeeViewModel.startListeningEventWaitlist(event.getId());

        Observer<List<Attendee>> attendeesObserver = attendees -> {
            if (attendees == null) {
                return;
            }
            boolean isAlreadyJoined = userId != null && attendees.stream()
                    .anyMatch(attendee -> userId.equals(attendee.getUid()));
            event.setCurrentParticipants(attendees.size());
            event.setCurrentUserJoined(isAlreadyJoined);
            updateEventPanelActions(event, userEmail, historyMode);
        };
        Observer<com.us.eventum.data.models.WaitlistToEvent> waitlistObserver = entry ->
                updateEventPanelActions(event, userEmail, historyMode);
        attendeeViewModel.getAttendees().observe(this, attendeesObserver);
        attendeeViewModel.getCurrentUserWaitlistEntry().observe(this, waitlistObserver);
        updateEventPanelActions(event, userEmail, historyMode);
    }

    private void updateEventPanelActions(Event event, String userEmail, boolean historyMode) {
        MaterialButton joinButton = eventPanelController.getJoinButton();
        TextView participantsText = eventPanelController.getParticipantsText();
        if (joinButton == null || participantsText == null || event == null) {
            return;
        }

        boolean isEventFull = event.getCurrentParticipants() >= event.getMaxParticipants();
        String participantsLabel = getResources().getQuantityString(
                R.plurals.event_participants_count,
                event.getMaxParticipants(),
                event.getCurrentParticipants(),
                event.getMaxParticipants());
        participantsText.setText(participantsLabel);
        participantsText.setTextColor(getResources().getColor(
                isEventFull ? android.R.color.holo_red_dark : R.color.colorSecondaryText,
                getTheme()));

        if (historyMode || event.isCancelled()) {
            joinButton.setEnabled(false);
            joinButton.setAlpha(0.5f);
            eventPanelController.setQrButtonVisible(false);
            eventPanelController.setDeclineOfferVisible(false);
            eventPanelController.updateWaitlistStatus(null, 0, null);
            eventPanelController.updatePrivateCodeVisibility(false);
            joinButton.setText(event.isCurrentUserJoined()
                    ? R.string.attendee_unsubscribe_short
                    : R.string.attendee_join_event_short);
            return;
        }

        if (event.isCurrentUserJoined()) {
            joinButton.setText(R.string.attendee_unsubscribe_short);
            joinButton.setEnabled(true);
            joinButton.setAlpha(1f);
            eventPanelController.setQrButtonVisible(true);
            eventPanelController.setDeclineOfferVisible(false);
            eventPanelController.updatePrivateCodeVisibility(false);
            eventPanelController.updateWaitlistStatus(null, 0, null);
            joinButton.setOnClickListener(v -> unsubscribeFromEvent(event, userEmail));
            return;
        }

        eventPanelController.setQrButtonVisible(false);
        eventPanelController.setDeclineOfferVisible(false);
        com.us.eventum.data.models.WaitlistToEvent entry =
                attendeeViewModel.getCurrentUserWaitlistEntry().getValue();
        boolean hasOffer = event.isCurrentUserWaitlistOffered()
                || (entry != null && com.us.eventum.data.models.WaitlistToEvent.STATUS_OFFERED.equals(entry.getStatus()));
        boolean isWaiting = event.isCurrentUserWaitlisted()
                || (entry != null && com.us.eventum.data.models.WaitlistToEvent.STATUS_WAITING.equals(entry.getStatus()));

        if (hasOffer) {
            Date expiresAt = event.getWaitlistOfferExpiresAt();
            if (entry != null && entry.getOfferExpiresAt() != null) {
                expiresAt = entry.getOfferExpiresAt().toDate();
            }
            eventPanelController.updateWaitlistStatus(
                    getString(R.string.waitlist_offer_status),
                    0,
                    expiresAt);
            eventPanelController.updatePrivateCodeVisibility(false);
            eventPanelController.setDeclineOfferVisible(true);
            MaterialButton declineButton = eventPanelController.getDeclineOfferButton();
            if (declineButton != null) {
                declineButton.setOnClickListener(v -> declineWaitlistOffer(event));
            }
            joinButton.setText(R.string.waitlist_confirm_spot);
            joinButton.setEnabled(true);
            joinButton.setAlpha(1f);
            joinButton.setOnClickListener(v -> confirmWaitlistSpot(userEmail));
            return;
        }

        if (isWaiting) {
            int position = event.getCurrentUserWaitlistPosition();
            eventPanelController.updateWaitlistStatus(
                    getString(R.string.waitlist_waiting_status, Math.max(position, 1)),
                    position,
                    null);
            eventPanelController.updatePrivateCodeVisibility(false);
            joinButton.setText(R.string.waitlist_leave);
            joinButton.setEnabled(true);
            joinButton.setAlpha(1f);
            joinButton.setOnClickListener(v -> leaveWaitlist(event));
            return;
        }

        eventPanelController.updateWaitlistStatus(null, 0, null);
        if (isEventFull) {
            joinButton.setText(R.string.waitlist_join);
            joinButton.setEnabled(true);
            joinButton.setAlpha(1f);
            eventPanelController.updatePrivateCodeVisibility(event.getPrivateEvent());
            joinButton.setOnClickListener(v -> joinWaitlist(userEmail));
            return;
        }

        joinButton.setText(R.string.attendee_join_event_short);
        joinButton.setEnabled(true);
        joinButton.setAlpha(1f);
        eventPanelController.updatePrivateCodeVisibility(event.getPrivateEvent());
        joinButton.setOnClickListener(v -> subscribeToEvent(userEmail));
    }

    private void declineWaitlistOffer(Event event) {
        decliningWaitlistOffer = true;
        leaveWaitlist(event);
    }

    private void leaveWaitlist(Event event) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (userId == null || event == null) {
            decliningWaitlistOffer = false;
            return;
        }
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            decliningWaitlistOffer = false;
            return;
        }
        attendeeViewModel.leaveWaitlist(event.getId(), userId, resolveAttendeeDisplayName());
    }

    private void joinWaitlist(String userEmail) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        Event currentEvent = eventPanelController != null ? eventPanelController.getCurrentEvent() : null;
        if (currentEvent == null) {
            return;
        }
        if (!validatePrivateAccessCodeForAction(currentEvent)) {
            return;
        }
        pendingWaitlistAction = true;
        Attendee cachedProfile = attendeeViewModel.getCurrentAttendee().getValue();
        if (cachedProfile != null) {
            executeWaitlistJoinWithProfile(currentEvent, cachedProfile);
            return;
        }
        attendeeViewModel.getCurrentAttendee().observe(this, new Observer<Attendee>() {
            @Override
            public void onChanged(Attendee attendee) {
                if (!pendingWaitlistAction || attendee == null) {
                    return;
                }
                attendeeViewModel.getCurrentAttendee().removeObserver(this);
                executeWaitlistJoinWithProfile(currentEvent, attendee);
            }
        });
        attendeeViewModel.loadCurrentAttendee();
    }

    private void confirmWaitlistSpot(String userEmail) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        Event currentEvent = eventPanelController != null ? eventPanelController.getCurrentEvent() : null;
        if (currentEvent == null) {
            return;
        }
        pendingConfirmWaitlistAction = true;
        Attendee cachedProfile = attendeeViewModel.getCurrentAttendee().getValue();
        if (cachedProfile != null) {
            executeConfirmWaitlistWithProfile(currentEvent, userEmail, cachedProfile);
            return;
        }
        attendeeViewModel.getCurrentAttendee().observe(this, new Observer<Attendee>() {
            @Override
            public void onChanged(Attendee attendee) {
                if (!pendingConfirmWaitlistAction || attendee == null) {
                    return;
                }
                attendeeViewModel.getCurrentAttendee().removeObserver(this);
                executeConfirmWaitlistWithProfile(currentEvent, userEmail, attendee);
            }
        });
        attendeeViewModel.loadCurrentAttendee();
    }

    private void executeWaitlistJoinWithProfile(Event event, Attendee attendee) {
        if (!pendingWaitlistAction) {
            return;
        }
        pendingWaitlistAction = false;
        if (!validateAttendeeForEvent(event, attendee)) {
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (userId == null) {
            showEventPanelToast("Error: No se pudo obtener el usuario", ToastUtils.ToastType.ERROR);
            return;
        }
        if (AgeUtils.requiresParentalAuthOnEventDay(
                attendee.getFechaNacimiento().toDate(), event.getDate())) {
            if (!event.getRequiresParentalAuth()) {
                showEventPanelToast(getString(R.string.event_minor_not_allowed), ToastUtils.ToastType.WARNING);
                return;
            }
            showParentalAuthDialogForWaitlist(event, attendee, userId);
            return;
        }
        attendeeViewModel.joinWaitlist(event.getId(), userId, null, attendee.getSortedNameLabel());
    }

    private void showParentalAuthDialogForWaitlist(Event event, Attendee attendee, String userId) {
        parentalAuthDialogHandle = ParentalAuthDialogHelper.show(
                this, event, userId, new ParentalAuthDialogHelper.Callback() {
                    @Override
                    public void onUploadSuccess(@NonNull String parentalAuthUrl) {
                        attendeeViewModel.joinWaitlist(
                                event.getId(), userId, parentalAuthUrl, attendee.getSortedNameLabel());
                    }

                    @Override
                    public void onCancelled() {
                        // Sin acción
                    }
                });
        parentalAuthDialogHandle.setOnSelectFileClickListener(
                v -> parentalAuthPickerLauncher.launch(new String[]{"image/*", "application/pdf"}));
    }

    private void executeConfirmWaitlistWithProfile(Event event, String userEmail, Attendee attendee) {
        if (!pendingConfirmWaitlistAction) {
            return;
        }
        pendingConfirmWaitlistAction = false;
        if (!validateAttendeeForEvent(event, attendee)) {
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (userId == null) {
            showEventPanelToast("Error: No se pudo obtener el usuario", ToastUtils.ToastType.ERROR);
            return;
        }
        attendeeViewModel.confirmWaitlistOffer(
                event.getId(),
                userId,
                attendee.getUsername(),
                attendee.getPrimerApellido(),
                attendee.getDni(),
                userEmail,
                attendee.getPhone() != null ? attendee.getPhone() : "",
                attendee.getFechaNacimiento() != null
                        ? attendee.getFechaNacimiento().toDate().toString() : "",
                event.getRequiresParentalAuth(),
                event.getUserId(),
                event.getTitle(),
                attendee.getSortedNameLabel());
    }

    private boolean validatePrivateAccessCodeForAction(Event currentEvent) {
        if (!currentEvent.getPrivateEvent()) {
            return true;
        }
        if (!eventPanelController.isPrivateCodeFieldVisible()) {
            eventPanelController.updatePrivateCodeVisibility(true);
        }
        String enteredCode = eventPanelController.getEnteredAccessCode();
        if (!EventPrivateAccessCode.isValidFormat(enteredCode)) {
            eventPanelController.showAccessCodeFormatError();
            return false;
        }
        if (!EventPrivateAccessCode.isConfigured(currentEvent)) {
            showEventPanelToast(getString(R.string.private_access_code_not_set), ToastUtils.ToastType.ERROR);
            return false;
        }
        if (!EventPrivateAccessCode.matchesEvent(enteredCode, currentEvent)) {
            eventPanelController.showWrongAccessCodeError();
            return false;
        }
        eventPanelController.clearAccessCodeError();
        return true;
    }

    private boolean validateAttendeeForEvent(Event event, Attendee attendee) {
        if (!attendee.isProfileComplete()) {
            pendingSubscribeAction = true;
            showCompleteProfileDialog(event, attendee,
                    FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                            : "");
            return false;
        }
        if (attendee.getFechaNacimiento() == null || event.getDate() == null) {
            showEventPanelToast(getString(R.string.age_birth_date_invalid), ToastUtils.ToastType.ERROR);
            return false;
        }
        if (!AgeUtils.isAttendeeAgeValid(attendee.getFechaNacimiento().toDate(), event.getDate())) {
            showEventPanelToast(getString(R.string.age_attendee_min_error), ToastUtils.ToastType.ERROR);
            return false;
        }
        return true;
    }

    private void unsubscribeFromEvent(Event event, String userEmail) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        
        eventDialogContext = new EventDialogContext(event, userEmail);
        attendeeViewModel.unsubscribeFromEvent(
                event.getId(),
                userEmail,
                event.getUserId(),
                event.getTitle(),
                resolveAttendeeDisplayName());
    }

    private void showEventPanelToast(String message, ToastUtils.ToastType type) {
        if (eventPanelController != null && eventPanelController.isVisible()) {
            eventPanelController.showPanelToast(message, type);
        } else {
            ToastUtils.showCustomToast(this, message, type);
        }
    }

    private void subscribeToEvent(String userEmail) {
        if (!NetworkUtils.checkConnectionAndShowMessage(this)) {
            return;
        }
        
        if (userEmail == null || userEmail.trim().isEmpty()) {
            showEventPanelToast("Error: No se pudo obtener el email del usuario", ToastUtils.ToastType.ERROR);
            return;
        }

        Event currentEvent = eventPanelController != null
                ? eventPanelController.getCurrentEvent()
                : null;
        if (currentEvent == null) {
            showEventPanelToast("Error: No se pudo cargar el evento", ToastUtils.ToastType.ERROR);
            return;
        }

        if (currentEvent.getPrivateEvent()) {
            if (!eventPanelController.isPrivateCodeFieldVisible()) {
                eventPanelController.updatePrivateCodeVisibility(true);
            }
            String enteredCode = eventPanelController.getEnteredAccessCode();
            if (!EventPrivateAccessCode.isValidFormat(enteredCode)) {
                eventPanelController.showAccessCodeFormatError();
                return;
            }
            if (!EventPrivateAccessCode.isConfigured(currentEvent)) {
                showEventPanelToast(
                        getString(R.string.private_access_code_not_set),
                        ToastUtils.ToastType.ERROR);
                return;
            }
            if (!EventPrivateAccessCode.matchesEvent(enteredCode, currentEvent)) {
                eventPanelController.showWrongAccessCodeError();
                return;
            }
            eventPanelController.clearAccessCodeError();
        }

        eventDialogContext = new EventDialogContext(currentEvent, userEmail);
        pendingSubscribeAction = true;

        Attendee cachedProfile = attendeeViewModel.getCurrentAttendee().getValue();
        if (cachedProfile != null) {
            executeSubscribeWithProfile(currentEvent, userEmail, cachedProfile);
            return;
        }

        Event eventForProfile = currentEvent;
        Observer<Attendee> profileObserver = new Observer<Attendee>() {
            @Override
            public void onChanged(Attendee attendee) {
                if (!pendingSubscribeAction || attendee == null) {
                    return;
                }
                attendeeViewModel.getCurrentAttendee().removeObserver(this);
                executeSubscribeWithProfile(eventForProfile, userEmail, attendee);
            }
        };
        attendeeViewModel.getCurrentAttendee().observe(this, profileObserver);
        attendeeViewModel.loadCurrentAttendee();
    }

    private void executeSubscribeWithProfile(Event event, String userEmail, Attendee attendee) {
        if (!pendingSubscribeAction) {
            return;
        }
        pendingSubscribeAction = false;

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (!attendee.isProfileComplete()) {
            pendingSubscribeAction = true;
            showCompleteProfileDialog(event, attendee, userEmail);
            return;
        }

        if (attendee.getFechaNacimiento() == null || event.getDate() == null) {
            showEventPanelToast(getString(R.string.age_birth_date_invalid), ToastUtils.ToastType.ERROR);
            return;
        }

        Date birthDate = attendee.getFechaNacimiento().toDate();
        Date eventDate = event.getDate();
        if (!AgeUtils.isAttendeeAgeValid(birthDate, eventDate)) {
            showEventPanelToast(getString(R.string.age_attendee_min_error), ToastUtils.ToastType.ERROR);
            return;
        }

        if (AgeUtils.requiresParentalAuthOnEventDay(birthDate, eventDate)) {
            if (!event.getRequiresParentalAuth()) {
                showEventPanelToast(
                        getString(R.string.event_minor_not_allowed), ToastUtils.ToastType.WARNING);
                return;
            }
            if (userId == null) {
                showEventPanelToast(
                        "Error: No se pudo obtener el usuario", ToastUtils.ToastType.ERROR);
                return;
            }
            showParentalAuthDialog(event, attendee, userEmail, userId);
            return;
        }

        completeEventSubscription(event, userEmail, userId, attendee, null);
    }

    private void showParentalAuthDialog(Event event, Attendee attendee, String userEmail, String userId) {
        parentalAuthDialogHandle = ParentalAuthDialogHelper.show(
                this, event, userId, new ParentalAuthDialogHelper.Callback() {
                    @Override
                    public void onUploadSuccess(@NonNull String parentalAuthUrl) {
                        completeEventSubscription(event, userEmail, userId, attendee, parentalAuthUrl);
                    }

                    @Override
                    public void onCancelled() {
                        // Sin acción
                    }
                });
        parentalAuthDialogHandle.setOnSelectFileClickListener(
                v -> parentalAuthPickerLauncher.launch(new String[]{"image/*", "application/pdf"}));
    }

    private void onParentalAuthFilePicked(@Nullable Uri uri) {
        if (parentalAuthDialogHandle == null || uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Algunos proveedores no permiten permiso persistente
        }
        parentalAuthDialogHandle.onFileSelected(uri, resolveDisplayName(uri));
    }

    private String resolveDisplayName(@NonNull Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Usar texto genérico
        }
        return getString(R.string.parental_auth_file_selected);
    }

    private void completeEventSubscription(Event event, String userEmail, String userId,
                                           Attendee attendee, @Nullable String parentalAuthUrl) {
        attendeeViewModel.addAttendee(
                event.getId(),
                userId,
                attendee.getUsername(),
                attendee.getPrimerApellido(),
                attendee.getDni(),
                userEmail,
                attendee.getPhone() != null ? attendee.getPhone() : "",
                attendee.getFechaNacimiento() != null
                        ? attendee.getFechaNacimiento().toDate().toString() : "",
                event.getRequiresParentalAuth(),
                event.getUserId(),
                event.getTitle(),
                attendee.getSortedNameLabel(),
                parentalAuthUrl
        );
    }

    private String resolveAttendeeDisplayName() {
        Attendee attendee = attendeeViewModel.getCurrentAttendee().getValue();
        if (attendee != null) {
            String label = attendee.getSortedNameLabel();
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }
        }
        return getString(R.string.notification_organizer_unknown_attendee);
    }

    private static String formatDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (!last.isEmpty() && !first.isEmpty()) {
            return last + ", " + first;
        }
        if (!first.isEmpty()) {
            return first;
        }
        return last;
    }

    private void showEventQrDialog(Event event) {
        if (event == null || event.getId() == null) {
            ToastUtils.showCustomToast(this, getString(R.string.attendee_qr_error), ToastUtils.ToastType.ERROR);
            return;
        }
        if (!event.isCurrentUserJoined() || event.isCancelled()) {
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            ToastUtils.showCustomToast(this, getString(R.string.attendee_qr_error), ToastUtils.ToastType.ERROR);
            return;
        }
        if (qrPanelController != null) {
            qrPanelController.show(event.getId(), event.getTitle());
        }
    }

    @Override
    public void onEventQrClick(Event event) {
        showEventQrDialog(event);
    }

    /**
     * Muestra dialog para completar perfil de asistente antes de inscribirse a evento
     */
    private void showCompleteProfileDialog(Event event, Attendee attendee, String userEmail) {
        if (activeCompleteProfileDialog != null && activeCompleteProfileDialog.isShowing()) {
            return;
        }

        waitingProfileForSubscribe = true;
        profileSaveSubmitted = false;
        pendingSubscribeEvent = event;
        pendingSubscribeUserEmail = userEmail;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_attendee_profile, null);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        title.setText(R.string.complete_profile_join_title);

        com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.nameInput);
        com.google.android.material.textfield.TextInputEditText firstSurnameInput = dialogView.findViewById(R.id.firstSurnameInput);
        com.google.android.material.textfield.TextInputEditText secondSurnameInput = dialogView.findViewById(R.id.secondSurnameInput);
        com.google.android.material.textfield.TextInputEditText dniInput = dialogView.findViewById(R.id.dniInput);
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);
        com.google.android.material.textfield.TextInputEditText birthDateInput = dialogView.findViewById(R.id.birthDateInput);
        com.google.android.material.textfield.TextInputLayout dniLayout = dialogView.findViewById(R.id.dniLayout);
        com.google.android.material.textfield.TextInputLayout birthDateLayout =
                dialogView.findViewById(R.id.birthDateLayout);

        com.us.eventum.utils.AttendeeProfileDialogHelper.populateFields(dialogView, attendee);
        com.us.eventum.utils.AttendeeProfileDialogHelper.bindDniValidation(dniInput, dniLayout);
        com.us.eventum.utils.AttendeeProfileDialogHelper.setupBirthDatePicker(
                this, birthDateInput, birthDateLayout);

        MaterialButton joinButton = eventPanelController != null
                ? eventPanelController.getJoinButton()
                : null;
        if (joinButton != null) {
            joinButton.setEnabled(false);
        }

        Dialog dialog = new Dialog(this);
        View modalRoot = com.us.eventum.utils.AttendeeProfileDialogHelper.wrapWithModalScrim(this, dialogView);
        dialog.setContentView(modalRoot);
        dialog.setCancelable(true);
        com.us.eventum.utils.AttendeeProfileDialogHelper.applyModalDialogWindow(dialog);

        Runnable restoreJoinButton = () -> {
            if (joinButton != null) {
                joinButton.setEnabled(true);
            }
        };

        dialog.setOnDismissListener(d -> {
            activeCompleteProfileDialog = null;
            restoreJoinButton.run();
            if (!profileSaveSubmitted) {
                waitingProfileForSubscribe = false;
                pendingSubscribeAction = false;
                pendingSubscribeEvent = null;
                pendingSubscribeUserEmail = null;
            }
        });

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.saveButton).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            String firstSurname = String.valueOf(firstSurnameInput.getText()).trim();
            String secondSurname = String.valueOf(secondSurnameInput.getText()).trim();
            String dni = String.valueOf(dniInput.getText()).trim().toUpperCase(java.util.Locale.ROOT);
            String phone = String.valueOf(phoneInput.getText()).trim();
            String birth = String.valueOf(birthDateInput.getText()).trim();

            if (name.isEmpty() || firstSurname.isEmpty() || secondSurname.isEmpty()
                    || dni.isEmpty() || phone.isEmpty() || birth.isEmpty()) {
                com.us.eventum.utils.AttendeeProfileDialogHelper.showError(
                        dialogView, getString(R.string.profile_complete_all_required));
                return;
            }

            if (!com.us.eventum.utils.AttendeeProfileDialogHelper.validateDniForSave(this, dniLayout, dni)) {
                return;
            }

            if (!com.us.eventum.utils.AttendeeProfileDialogHelper.validateAttendeeBirthDateForSave(
                    this, birthDateLayout, birth)) {
                return;
            }

            com.us.eventum.utils.AttendeeProfileDialogHelper.ensureDniUniqueThen(
                    this,
                    attendeeViewModel,
                    dniLayout,
                    dialogView.findViewById(R.id.saveButton),
                    dni,
                    attendee.getUid(),
                    () -> com.us.eventum.utils.AttendeeProfileDialogHelper.showSaveConfirm(this, () -> {
                        profileSaveSubmitted = true;
                        attendeeViewModel.updateAttendee(
                                attendee.getUsername(), name, dni, phone, firstSurname, secondSurname, birth);
                        dialog.dismiss();
                    }));
        });

        activeCompleteProfileDialog = dialog;
        dialog.show();
    }
}

