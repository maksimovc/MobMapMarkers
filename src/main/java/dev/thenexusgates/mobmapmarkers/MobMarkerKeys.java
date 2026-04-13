package dev.thenexusgates.mobmapmarkers;

import java.util.Locale;

final class MobMarkerKeys {

    private MobMarkerKeys() {
    }

    static String normalize(String raw) {
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