package dev.thenexusgates.mobmapmarkers.filter;

import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;

public final class MobMarkerFilterRule {

    public boolean map = true;
    public boolean minimap = true;
    public boolean compass;

    public MobMarkerFilterRule() {
    }

    public MobMarkerFilterRule(boolean map, boolean minimap, boolean compass) {
        this.map = map;
        this.minimap = minimap;
        this.compass = compass;
    }

    public static MobMarkerFilterRule defaults(MobMapMarkersConfig config) {
        boolean minimap = config == null || config.showMobMarkersOnFastMiniMap;
        boolean compass = config != null && config.showMobMarkersOnCompass;
        return new MobMarkerFilterRule(true, minimap, compass);
    }

    public boolean isEnabled(MobMarkerSurface surface) {
        return switch (surface) {
            case MAP -> map;
            case MINIMAP -> minimap;
            case COMPASS -> compass;
        };
    }

    public MobMarkerFilterRule with(MobMarkerSurface surface, boolean enabled) {
        return switch (surface) {
            case MAP -> new MobMarkerFilterRule(enabled, minimap, compass);
            case MINIMAP -> new MobMarkerFilterRule(map, enabled, compass);
            case COMPASS -> new MobMarkerFilterRule(map, minimap, enabled);
        };
    }

    public MobMarkerFilterRule all(boolean enabled) {
        return new MobMarkerFilterRule(enabled, enabled, enabled);
    }
}