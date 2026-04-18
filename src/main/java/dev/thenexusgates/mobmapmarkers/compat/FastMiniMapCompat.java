package dev.thenexusgates.mobmapmarkers.compat;

public final class FastMiniMapCompat {

    private static final String API_CLASS = "dev.thenexusgates.fastminimap.FastMiniMapMobLayerApi";
    private static final boolean AVAILABLE = detect();

    private FastMiniMapCompat() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean detect() {
        try {
            Class.forName(API_CLASS, false, FastMiniMapCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
