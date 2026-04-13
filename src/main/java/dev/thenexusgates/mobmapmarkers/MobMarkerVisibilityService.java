package dev.thenexusgates.mobmapmarkers;

import java.util.UUID;
import java.util.function.Supplier;

final class MobMarkerVisibilityService {

    private final MobMarkerFilterStore filterStore;
    private final Supplier<MobMapMarkersConfig> configSupplier;

    MobMarkerVisibilityService(MobMarkerFilterStore filterStore, Supplier<MobMapMarkersConfig> configSupplier) {
        this.filterStore = filterStore;
        this.configSupplier = configSupplier;
    }

    boolean isVisible(UUID viewerUuid, MobMarkerManager.MobMarkerSnapshot snapshot, MobMarkerSurface surface) {
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

    MobMarkerFilterRule resolveRule(UUID viewerUuid, String mobKey) {
        return filterStore.resolveRule(viewerUuid, mobKey, configSupplier.get());
    }
}