package dev.thenexusgates.mobmapmarkers;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MobMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final Logger LOGGER = Logger.getLogger(MobMarkerProvider.class.getName());

    private final MobMarkerManager manager;
    private final MobMarkerVisibilityService visibilityService;

    MobMarkerProvider(MobMarkerManager manager, MobMarkerVisibilityService visibilityService) {
        this.manager = manager;
        this.visibilityService = visibilityService;
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        if (world == null || viewer == null || collector == null) {
            return;
        }

        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        if (config == null || !config.enableMobMarkers) {
            return;
        }

        String worldName = world.getName();
        UUID viewerUuid = ((CommandSender) viewer).getUuid();
        boolean worldMapVisible = MobMapMarkerSupport.isWorldMapVisible(viewer);
        MobMarkerSurface surface = worldMapVisible ? MobMarkerSurface.MAP : MobMarkerSurface.COMPASS;
        Vector3d viewerPosition = manager.getPlayerPosition(worldName, viewerUuid);
        if (viewerPosition == null) {
            viewerPosition = findViewerPosition(world.getPlayerRefs(), viewerUuid);
        }

        double maxDistanceSquared = config.mobMarkerRadius <= 0
                ? Double.POSITIVE_INFINITY
                : (double) config.mobMarkerRadius * config.mobMarkerRadius;

        List<MarkerCandidate> candidates = new ArrayList<>();
        for (MobMarkerManager.MobMarkerSnapshot snapshot : manager.getMobData(worldName)) {
            if (snapshot == null || snapshot.position() == null) {
                continue;
            }
            if (!visibilityService.isVisible(viewerUuid, snapshot, surface)) {
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
        List<String> imagePathsToDeliver = new ArrayList<>();
        PlayerRef viewerRef = findViewerRef(world.getPlayerRefs(), viewerUuid);
        String viewerLanguage = viewerRef != null ? viewerRef.getLanguage() : null;

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
                    MobNameLocalization.buildAssetLocaleKey(snapshot.nameTranslationKey(), viewerLanguage, localizedDisplayName),
                        config.mobMarkerSize,
                        config.mobIconContentScalePercent,
                        snapshot.facingRight(),
                        config.renderUnknownMobFallbacks);
                if (markerImage == null) {
                    continue;
                }
                imagePathsToDeliver.add(markerImage);

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
                collector.add(marker);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "[MobMapMarkers] Mob marker build failed for snapshot "
                        + snapshot.id() + " in world " + worldName, e);
            }
        }

        if (viewerRef != null && !imagePathsToDeliver.isEmpty()) {
            MobMapAssetPack.deliverAssetsToViewer(viewerRef, imagePathsToDeliver);
        }
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

    private static double squaredDistance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record MarkerCandidate(MobMarkerManager.MobMarkerSnapshot snapshot, double distanceSquared) {
    }
}
