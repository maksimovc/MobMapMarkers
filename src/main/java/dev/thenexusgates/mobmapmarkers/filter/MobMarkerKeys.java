package dev.thenexusgates.mobmapmarkers.filter;

import java.util.Locale;

public final class MobMarkerKeys {

    private MobMarkerKeys() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }
}