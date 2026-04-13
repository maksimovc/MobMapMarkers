package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Locale;

final class MobMapUiText {

    private MobMapUiText() {
    }

    static String choose(PlayerRef playerRef, String english, String ukrainian) {
        return isUkrainian(playerRef) ? ukrainian : english;
    }

    static String format(PlayerRef playerRef, String english, String ukrainian, Object... args) {
        return String.format(Locale.ROOT, choose(playerRef, english, ukrainian), args);
    }

    private static boolean isUkrainian(PlayerRef playerRef) {
        String language = playerRef == null ? null : playerRef.getLanguage();
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("uk");
    }
}