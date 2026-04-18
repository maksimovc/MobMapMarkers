package dev.thenexusgates.mobmapmarkers.marker;

import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerFilterRule;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerFilterStore;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;

import java.util.UUID;
import java.util.function.Supplier;

public final class MobMarkerVisibilityService {

    private final MobMarkerFilterStore filterStore;
    private final Supplier<MobMapMarkersConfig> configSupplier;

    public MobMarkerVisibilityService(MobMarkerFilterStore filterStore, Supplier<MobMapMarkersConfig> configSupplier) {
        this.filterStore = filterStore;
        this.configSupplier = configSupplier;
    }

    public boolean isVisible(UUID viewerUuid, MobMarkerManager.MobMarkerSnapshot snapshot, MobMarkerSurface surface) {
        if (snapshot == null || surface == null) {
            return false;
        }

        MobMapMarkersConfig config = configSupplier.get();
        if (config == null || !config.enableMobMarkers) {
            return false;
        }

        MobMarkerFilterRule rule = filterStore.resolveRule(viewerUuid, snapshot.roleName(), config);
        return rule.isEnabled(surface);
    }

    public MobMarkerFilterRule resolveRule(UUID viewerUuid, String mobKey) {
        return filterStore.resolveRule(viewerUuid, mobKey, configSupplier.get());
    }
}