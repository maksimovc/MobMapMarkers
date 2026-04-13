package dev.thenexusgates.mobmapmarkers;

final class MobMarkerFilterRule {

    boolean map = true;
    boolean minimap = true;
    boolean compass;

    MobMarkerFilterRule() {
    }

    MobMarkerFilterRule(boolean map, boolean minimap, boolean compass) {
        this.map = map;
        this.minimap = minimap;
        this.compass = compass;
    }

    static MobMarkerFilterRule defaults(MobMapMarkersConfig config) {
        boolean minimap = config == null || config.showMobMarkersOnFastMiniMap;
        boolean compass = config != null && config.showMobMarkersOnCompass;
        return new MobMarkerFilterRule(true, minimap, compass);
    }

    boolean isEnabled(MobMarkerSurface surface) {
        return switch (surface) {
            case MAP -> map;
            case MINIMAP -> minimap;
            case COMPASS -> compass;
        };
    }

    MobMarkerFilterRule with(MobMarkerSurface surface, boolean enabled) {
        return switch (surface) {
            case MAP -> new MobMarkerFilterRule(enabled, minimap, compass);
            case MINIMAP -> new MobMarkerFilterRule(map, enabled, compass);
            case COMPASS -> new MobMarkerFilterRule(map, minimap, enabled);
        };
    }

    MobMarkerFilterRule all(boolean enabled) {
        return new MobMarkerFilterRule(enabled, enabled, enabled);
    }
}