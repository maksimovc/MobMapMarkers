package dev.thenexusgates.mobmapmarkers.compat;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.asset.MobMapAssetPack;
import dev.thenexusgates.mobmapmarkers.catalog.MobNameLocalization;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;
import dev.thenexusgates.mobmapmarkers.marker.MobMapMarkerSupport;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerManager;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerVisibilityService;
import dev.thenexusgates.mobmapmarkers.tracking.LivePlayerTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BetterMapCompatProvider implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_KEY = "BetterMapMobRadar";

    private final MobMarkerManager manager;
    private final MobMarkerVisibilityService visibilityService;

    public BetterMapCompatProvider(MobMarkerManager manager, MobMarkerVisibilityService visibilityService) {
        this.manager = manager;
        this.visibilityService = visibilityService;
    }

    public static boolean isAvailable() {
        return BetterMapBridge.isAvailable();
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        if (world == null || viewer == null || collector == null) {
            return;
        }

        BetterMapBridge.ViewerSettings viewerSettings = BetterMapBridge.resolveViewerSettings(viewer);
        if (!viewerSettings.enabled()) {
            return;
        }

        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        if (config == null || !config.enableMobMarkers) {
            return;
        }

        UUID viewerUuid = ((CommandSender) viewer).getUuid();
        String worldName = world.getName();
        PlayerRef viewerRef = findViewerRef(world.getPlayerRefs(), viewerUuid);
        if (viewerRef == null || viewerRef.getUuid() == null) {
            return;
        }

        MobMapAssetPack.advanceViewerDeliveryPhase(viewerRef);
        Vector3d viewerPosition = manager.getPlayerPosition(worldName, viewerUuid);
        if (viewerPosition == null) {
            viewerPosition = findViewerPosition(world.getPlayerRefs(), viewerUuid);
        }

        double maxDistanceSquared = resolveMaxDistanceSquared(config.mobMarkerRadius, viewerSettings.radarRange());
        List<MarkerCandidate> candidates = new ArrayList<>();
        for (MobMarkerManager.MobMarkerSnapshot snapshot : manager.getMobData(worldName)) {
            if (snapshot == null || snapshot.position() == null) {
                continue;
            }
            if (!visibilityService.isVisible(viewerUuid, snapshot, MobMarkerSurface.COMPASS)) {
                continue;
            }

            double distanceSquared = viewerPosition != null
                    ? squaredDistance(viewerPosition, snapshot.position())
                    : 0D;
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            candidates.add(new MarkerCandidate(snapshot, distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(MarkerCandidate::distanceSquared));
        int limit = config.maxVisibleMobMarkers > 0
                ? Math.min(config.maxVisibleMobMarkers, candidates.size())
                : candidates.size();
        String viewerLanguage = viewerRef.getLanguage();
        Set<String> missingImagePaths = new LinkedHashSet<>();
        List<BuiltMarker> builtMarkers = new ArrayList<>();

        for (int index = 0; index < limit; index++) {
            MarkerCandidate candidate = candidates.get(index);
            MobMarkerManager.MobMarkerSnapshot snapshot = candidate.snapshot();
            try {
                String localizedDisplayName = MobNameLocalization.resolveDisplayName(
                        snapshot.nameTranslationKey(),
                        snapshot.displayName(),
                        viewerLanguage);
                String markerImage = MobMapAssetPack.ensureMobIcon(
                        snapshot.roleName(),
                        localizedDisplayName,
                        MobNameLocalization.buildAssetLocaleKey(
                                snapshot.nameTranslationKey(),
                                viewerLanguage,
                                localizedDisplayName),
                        config.mobMarkerSize,
                        config.mobIconContentScalePercent,
                        snapshot.facingRight(),
                        config.renderUnknownMobFallbacks);
                if (markerImage == null) {
                    continue;
                }

                boolean assetReady = MobMapAssetPack.hasDeliveredAsset(viewerRef.getUuid(), markerImage);
                if (!assetReady) {
                    missingImagePaths.add(markerImage);
                }

                FormattedMessage label = MobMapMarkerSupport.createMarkerLabel(
                        localizedDisplayName,
                        viewerLanguage,
                        config.showMobNames,
                        config.showDistance,
                        candidate.distanceSquared(),
                        viewerPosition != null);
                Transform transform = new Transform(new Vector3d(snapshot.position()), Vector3f.ZERO);
                MapMarker marker = MobMapMarkerSupport.createPlainMarker(
                        MobMapMarkerSupport.MARKER_PREFIX + snapshot.id(),
                        label,
                        markerImage,
                        transform);
                builtMarkers.add(new BuiltMarker(marker, assetReady));
            } catch (RuntimeException e) {
            }
        }

        if (!missingImagePaths.isEmpty()) {
            MobMapAssetPack.deliverAssetsToViewer(viewerRef, missingImagePaths);
        }

        for (BuiltMarker builtMarker : builtMarkers) {
            if (builtMarker.assetReady()) {
                collector.add(builtMarker.marker());
            }
        }
    }

    private static PlayerRef findViewerRef(Collection<PlayerRef> playerRefs, UUID viewerUuid) {
        if (playerRefs == null || viewerUuid == null) {
            return null;
        }

        for (PlayerRef ref : playerRefs) {
            if (ref != null && viewerUuid.equals(ref.getUuid())) {
                return ref;
            }
        }

        return null;
    }

    private static Vector3d findViewerPosition(Collection<PlayerRef> playerRefs, UUID viewerUuid) {
        if (playerRefs == null || viewerUuid == null) {
            return null;
        }

        for (PlayerRef ref : playerRefs) {
            if (ref == null || !viewerUuid.equals(ref.getUuid())) {
                continue;
            }

            Vector3d position = LivePlayerTracker.resolvePosition(ref);
            if (position != null) {
                return position;
            }
        }

        return null;
    }

    private static double resolveMaxDistanceSquared(int configRadius, int radarRange) {
        double configLimit = configRadius <= 0 ? Double.POSITIVE_INFINITY : (double) configRadius * configRadius;
        double radarLimit = radarRange <= 0 ? Double.POSITIVE_INFINITY : (double) radarRange * radarRange;
        return Math.min(configLimit, radarLimit);
    }

    private static double squaredDistance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record MarkerCandidate(MobMarkerManager.MobMarkerSnapshot snapshot, double distanceSquared) {
    }

    private record BuiltMarker(MapMarker marker, boolean assetReady) {
    }
}