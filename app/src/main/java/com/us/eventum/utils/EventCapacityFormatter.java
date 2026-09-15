package com.us.eventum.utils;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.us.eventum.R;

/**
 * Formato de aforo en lista y detalle, con lista de espera solo si hay gente.
 */
public final class EventCapacityFormatter {

    private EventCapacityFormatter() {
    }

    @NonNull
    public static CharSequence appendWaitlist(@NonNull Context context,
                                              @NonNull CharSequence base,
                                              int waitlistCount) {
        if (waitlistCount <= 0) {
            return base;
        }
        SpannableStringBuilder text = new SpannableStringBuilder(base);
        text.append("  ");
        int start = text.length();
        text.append(context.getResources().getQuantityString(
                R.plurals.event_waitlist_inline, waitlistCount, waitlistCount));
        int end = text.length();
        text.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.waitlist_accent)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    @NonNull
    public static CharSequence participantsWithWaitlist(@NonNull Context context,
                                                        int current,
                                                        int max,
                                                        int waitlistCount) {
        String base = context.getResources().getQuantityString(
                R.plurals.event_participants_count, max, current, max);
        return appendWaitlist(context, base, waitlistCount);
    }
}
