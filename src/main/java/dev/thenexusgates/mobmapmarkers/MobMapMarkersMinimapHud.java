package dev.thenexusgates.mobmapmarkers;

import com.Landscaper.plugin.MinimapPlugin;
import com.Landscaper.plugin.config.MinimapConfig;
import com.Landscaper.plugin.ui.MinimapHud;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class MobMapMarkersMinimapHud extends MinimapHud {

    private static final long OVERLAY_ARM_DELAY_MS = 5000L;
    private static final int MAX_MINIMAP_MARKERS = 24;
    private static final String MINIMAP_CONTAINER_SELECTOR = "#MinimapContainer";
    private static final String DEFAULT_MAP_MARKER_OVERLAY_SELECTOR = "#MapMarkerOverlay";
    private static final String OVERLAY_ID = "#MobMapMarkersOverlay";
    private static final String OVERLAY_SELECTOR = "#MobMapMarkersOverlay";

    private final PlayerRef playerRef;
    private final MobMarkerManager manager;
    private MinimapConfig minimapConfig;
    private int lastOverlayHash = Integer.MIN_VALUE;
    private long overlayReadyAtMs;
    private boolean hudBuilt;
    private boolean overlayAttached;

    MobMapMarkersMinimapHud(PlayerRef playerRef, MinimapPlugin plugin, MinimapConfig minimapConfig,
                            MobMarkerManager manager) {
        super(playerRef, plugin, minimapConfig);
        this.playerRef = playerRef;
        this.manager = manager;
        this.minimapConfig = minimapConfig;
    }

    @Override
    protected void build(UICommandBuilder commands) {
        super.build(commands);
        hudBuilt = true;
        overlayReadyAtMs = System.currentTimeMillis() + OVERLAY_ARM_DELAY_MS;
        overlayAttached = false;
        lastOverlayHash = Integer.MIN_VALUE;
    }

    @Override
    public void refreshWithConfig(MinimapConfig minimapConfig) {
        this.minimapConfig = minimapConfig;
        this.lastOverlayHash = Integer.MIN_VALUE;
        this.overlayAttached = false;
        this.overlayReadyAtMs = hudBuilt ? System.currentTimeMillis() + OVERLAY_ARM_DELAY_MS : 0L;
        super.refreshWithConfig(minimapConfig);
    }

    @Override
    public void forceUpdate(PlayerRef playerRef, World world) {
        this.lastOverlayHash = Integer.MIN_VALUE;
        super.forceUpdate(playerRef, world);
        suppressDefaultWaypointOverlayIfNeeded();
        if (!hudBuilt) {
            return;
        }
        renderMobOverlay(world);
    }

    @Override
    public void tick(PlayerRef playerRef, World world) {
        super.tick(playerRef, world);
        suppressDefaultWaypointOverlayIfNeeded();
        if (!hudBuilt) {
            return;
        }
        renderMobOverlay(world);
    }

    private void suppressDefaultWaypointOverlayIfNeeded() {
        UUID viewerUuid = playerRef != null ? playerRef.getUuid() : null;
        if (viewerUuid == null) {
            return;
        }

        if (!MobMapMarkerSupport.isWorldMapVisible(MobMapMarkersPlugin.getActivePlayer(viewerUuid))) {
            return;
        }

        UICommandBuilder commands = new UICommandBuilder();
        commands.clear(DEFAULT_MAP_MARKER_OVERLAY_SELECTOR);
        update(false, commands);
    }

    private void renderMobOverlay(World world) {
        if (!isOverlayReady()) {
            return;
        }

        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        if (world == null || config == null || !config.showMobMarkersOnSimpleMinimap) {
            clearOverlayIfNeeded();
            return;
        }

        UUID viewerUuid = playerRef != null ? playerRef.getUuid() : null;
        if (viewerUuid == null) {
            clearOverlayIfNeeded();
            return;
        }

        Vector3d viewerPosition = manager.getPlayerPosition(world.getName(), viewerUuid);
        if (viewerPosition == null) {
            viewerPosition = LivePlayerTracker.resolvePosition(playerRef);
        }
        if (viewerPosition == null) {
            clearOverlayIfNeeded();
            return;
        }

        OverlayRenderModel overlayModel = buildOverlayModel(world.getName(), viewerPosition, config);
        if (overlayModel.isEmpty()) {
            clearOverlayIfNeeded();
            return;
        }

        int overlayHash = overlayModel.hashCode();
        if (overlayHash == lastOverlayHash) {
            return;
        }

        String overlayMarkup = overlayModel.toMarkup();
        UICommandBuilder commands = new UICommandBuilder();
        if (overlayAttached) {
            commands.clear(OVERLAY_SELECTOR);
            commands.appendInline(OVERLAY_SELECTOR, overlayMarkup);
        } else {
            commands.appendInline(MINIMAP_CONTAINER_SELECTOR, wrapOverlayMarkup(overlayMarkup));
            overlayAttached = true;
        }
        update(false, commands);
        lastOverlayHash = overlayHash;
    }

    private OverlayRenderModel buildOverlayModel(String worldName, Vector3d viewerPosition,
                                                 MobMapMarkersConfig config) {
        List<OverlayCandidate> candidates = buildOverlayCandidates(worldName, viewerPosition, config);
        if (candidates.isEmpty()) {
            return OverlayRenderModel.EMPTY;
        }

        double halfDisplay = minimapConfig.getDisplaySize() / 2.0D;
        double blocksToPixels = minimapConfig.getBlockSize() / (double) minimapConfig.getWorldBlocksPerDisplay();
        int mobIconSize = clamp((int) Math.round(25.0D * Math.max(0.8D, config.mobIconContentScalePercent / 100.0D)), 22, 30);

        List<OverlaySprite> sprites = new ArrayList<>(candidates.size());
        int index = 0;
        for (OverlayCandidate candidate : candidates) {
            OverlaySprite sprite = projectCandidate("#Mob" + index, candidate.position().x, candidate.position().z,
                    viewerPosition, blocksToPixels, halfDisplay, mobIconSize, candidate.assetPath());
            if (sprite != null) {
                sprites.add(sprite);
            }
            index++;
        }

        return sprites.isEmpty() ? OverlayRenderModel.EMPTY : new OverlayRenderModel(List.copyOf(sprites));
    }

    private List<OverlayCandidate> buildOverlayCandidates(String worldName, Vector3d viewerPosition,
                                                          MobMapMarkersConfig config) {
        double halfDisplay = minimapConfig.getDisplaySize() / 2.0D;
        double blocksToPixels = minimapConfig.getBlockSize() / (double) minimapConfig.getWorldBlocksPerDisplay();
        double minimapClampRadius = Math.max(0.0D, halfDisplay - 8.0D);
        double minimapWorldRadius = blocksToPixels > 0.0D ? minimapClampRadius / blocksToPixels : 0.0D;
        double maxDistanceSquared = config.mobMarkerRadius <= 0
                ? Double.POSITIVE_INFINITY
                : (double) config.mobMarkerRadius * config.mobMarkerRadius;
        double minimapDistanceSquared = minimapWorldRadius > 0.0D
            ? minimapWorldRadius * minimapWorldRadius
            : 0.0D;

        List<OverlayCandidate> candidates = new ArrayList<>();
        for (MobMarkerManager.MobMarkerSnapshot snapshot : manager.getMobData(worldName)) {
            if (snapshot == null || snapshot.position() == null) {
                continue;
            }

            double distanceSquared = squaredDistance(viewerPosition, snapshot.position());
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }
            if (distanceSquared > minimapDistanceSquared) {
                continue;
            }

            String imagePath = MobMapAssetPack.ensureMobIcon(
                    snapshot.roleName(),
                    snapshot.displayName(),
                    config.mobMarkerSize,
                    config.mobIconContentScalePercent,
                    snapshot.facingRight(),
                    config.renderUnknownMobFallbacks);
            if (imagePath == null) {
                continue;
            }

            String assetPath = MobMapAssetPack.toUiAssetPath(imagePath);
            if (assetPath == null || assetPath.isBlank()) {
                continue;
            }

            candidates.add(new OverlayCandidate(
                    snapshot.id(),
                    new Vector3d(snapshot.position()),
                    imagePath,
                    assetPath,
                    distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(OverlayCandidate::distanceSquared));
        int configuredLimit = config.maxVisibleMobMarkers > 0 ? config.maxVisibleMobMarkers : candidates.size();
        int minimapLimit = Math.min(configuredLimit, MAX_MINIMAP_MARKERS);
        List<OverlayCandidate> visibleCandidates = candidates.size() > minimapLimit
                ? List.copyOf(candidates.subList(0, minimapLimit))
                : List.copyOf(candidates);
        if (!visibleCandidates.isEmpty()) {
            List<String> imagePathsToDeliver = new ArrayList<>(visibleCandidates.size());
            for (OverlayCandidate candidate : visibleCandidates) {
                imagePathsToDeliver.add(candidate.imagePath());
            }
            MobMapAssetPack.deliverAssetsToViewer(playerRef, imagePathsToDeliver);
        }

        return visibleCandidates;
    }

    private OverlaySprite projectCandidate(String elementId, double worldX, double worldZ,
                                           Vector3d viewerPosition, double blocksToPixels, double halfDisplay,
                                           int iconSize, String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }

        double dx = worldX - viewerPosition.x;
        double dz = worldZ - viewerPosition.z;
        double projectedX = dx * blocksToPixels;
        double projectedZ = dz * blocksToPixels;
        double distance = Math.sqrt(projectedX * projectedX + projectedZ * projectedZ);
        double clampRadius = Math.max(0.0D, halfDisplay - 8.0D);
        if (distance > clampRadius && distance > 0.0D) {
            projectedX = projectedX / distance * clampRadius;
            projectedZ = projectedZ / distance * clampRadius;
        }

        int left = (int) Math.round(halfDisplay + projectedX - iconSize / 2.0D);
        int top = (int) Math.round(halfDisplay + projectedZ - iconSize / 2.0D);
        return new OverlaySprite(elementId, left, top, iconSize, assetPath);
    }

    private void clearOverlayIfNeeded() {
        if (lastOverlayHash == Integer.MIN_VALUE || !hudBuilt || !overlayAttached || !isOverlayReady()) {
            return;
        }

        UICommandBuilder commands = new UICommandBuilder();
        commands.clear(OVERLAY_SELECTOR);
        update(false, commands);
        lastOverlayHash = Integer.MIN_VALUE;
    }

    private boolean isOverlayReady() {
        return hudBuilt && System.currentTimeMillis() >= overlayReadyAtMs;
    }

    private static String wrapOverlayMarkup(String childrenMarkup) {
        return "Panel " + OVERLAY_ID + " { Anchor: (Full: 0); Visible: true; Background: #00000000; "
                + childrenMarkup + " }";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double squaredDistance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record OverlayCandidate(String id, Vector3d position, String imagePath, String assetPath,
                                    double distanceSquared) {
        @Override
        public int hashCode() {
            return Objects.hash(id, position.x, position.y, position.z, assetPath, distanceSquared);
        }
    }

    private record OverlayRenderModel(List<OverlaySprite> sprites) {

        private static final OverlayRenderModel EMPTY = new OverlayRenderModel(List.of());

        private boolean isEmpty() {
            return sprites.isEmpty();
        }

        private String toMarkup() {
            StringBuilder markup = new StringBuilder(sprites.size() * 160);
            for (OverlaySprite sprite : sprites) {
                sprite.appendTo(markup);
            }
            return markup.toString();
        }
    }

    private record OverlaySprite(String id, int left, int top, int size, String assetPath) {

        private void appendTo(StringBuilder markup) {
            markup.append("AssetImage ")
                    .append(id)
                    .append(" { Anchor: (Left: ")
                    .append(left)
                    .append(", Top: ")
                    .append(top)
                    .append(", Width: ")
                    .append(size)
                    .append(", Height: ")
                    .append(size)
                    .append("); Visible: true; AssetPath: \"")
                    .append(assetPath)
                    .append("\"; }");
        }
    }
}