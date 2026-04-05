package dev.thenexusgates.mobmapmarkers;

final class SimpleMinimapCompat {

    private static final String SIMPLE_MINIMAP_API_CLASS = "com.Landscaper.plugin.external.MinimapApi";
    private static final boolean AVAILABLE = detectAvailability();

    private SimpleMinimapCompat() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    static boolean isEnabled(MobMapMarkersConfig config) {
        return config != null && config.showMobMarkersOnSimpleMinimap && AVAILABLE;
    }

    private static boolean detectAvailability() {
        try {
            Class.forName(SIMPLE_MINIMAP_API_CLASS, false, MobMapMarkersPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}