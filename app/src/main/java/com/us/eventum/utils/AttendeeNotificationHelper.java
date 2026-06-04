package com.us.eventum.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.us.eventum.R;
import com.us.eventum.data.models.AttendeeNotification;
import com.us.eventum.presentation.activities.AttendeeHomeActivity;

/**
 * Muestra notificaciones locales al asistente.
 */
public final class AttendeeNotificationHelper {

    private static final String TAG = "AttNotificationHelper";
    private static final String CHANNEL_ID = "eventum_attendee_events_v1";
    private static final int NOTIFICATION_ID_BASE = 5000;
    private static final long[] NOTIFICATION_VIBRATION_PATTERN = {0, 100};

    private AttendeeNotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_attendee_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_attendee_desc));
        channel.enableVibration(true);
        channel.setVibrationPattern(NOTIFICATION_VIBRATION_PATTERN);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static String buildBody(Context context, AttendeeNotification notification) {
        String eventTitle = notification.getEventTitle();
        if (eventTitle == null || eventTitle.trim().isEmpty()) {
            eventTitle = context.getString(R.string.notification_organizer_unknown_event);
        }
        if (AttendeeNotification.TYPE_ATTENDANCE_VERIFIED.equals(notification.getType())) {
            return context.getString(R.string.notification_attendee_verified, eventTitle);
        }
        return context.getString(R.string.notification_attendee_generic, eventTitle);
    }

    public static void show(Context context, AttendeeNotification notification) {
        if (context == null || notification == null) {
            return;
        }
        ensureChannel(context);
        VibrationUtils.vibrateNotification(context);

        String title = context.getString(R.string.notification_attendee_title);
        String body = buildBody(context, notification);

        Intent intent = new Intent(context, AttendeeHomeActivity.class);
        intent.putExtra("eventId", notification.getEventId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = (notification.getEventId() + notification.getType()).hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(NOTIFICATION_VIBRATION_PATTERN)
                .setDefaults(NotificationCompat.DEFAULT_SOUND);

        int notificationId = NOTIFICATION_ID_BASE + Math.abs(requestCode);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notificaciones desactivadas en el sistema");
            AttendeeNotificationDispatcher.dispatch(notification);
            return;
        }
        try {
            manager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Sin permiso POST_NOTIFICATIONS", e);
            AttendeeNotificationDispatcher.dispatch(notification);
        }
    }
}
