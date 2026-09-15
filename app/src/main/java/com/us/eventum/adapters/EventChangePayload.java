package com.us.eventum.adapters;

import com.us.eventum.data.models.Event;
import com.us.eventum.utils.EventUiMerger;

/**
 * Actualización parcial de una tarjeta de evento sin recargar la imagen.
 */
public final class EventChangePayload {

    public static final int FLAG_METADATA = 1;
    public static final int FLAG_STATS = 2;

    private final int flags;

    public EventChangePayload(int flags) {
        this.flags = flags;
    }

    public boolean includesMetadata() {
        return (flags & FLAG_METADATA) != 0;
    }

    public boolean includesStats() {
        return (flags & FLAG_STATS) != 0;
    }

    public static int computeFlags(Event before, Event after) {
        if (before == null || after == null) {
            return FLAG_METADATA | FLAG_STATS;
        }
        if (before.getId() == null || after.getId() == null
                || !before.getId().equals(after.getId())) {
            return 0;
        }
        int flags = 0;
        if (EventUiMerger.hasDocumentFieldsChanged(before, after)) {
            flags |= FLAG_METADATA;
        }
        if (before.isCancelled() != after.isCancelled()) {
            flags |= FLAG_STATS;
        }
        if (EventUiMerger.hasRegistrationUiChanged(before, after)
                || before.getMaxParticipants() != after.getMaxParticipants()) {
            flags |= FLAG_STATS;
        }
        return flags;
    }
}
