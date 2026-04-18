package dev.thenexusgates.mobmapmarkers.compat;

import dev.thenexusgates.fastminimap.FastMiniMapMobLayerApi;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.asset.MobMapImageProcessor;
import dev.thenexusgates.mobmapmarkers.catalog.HytaleNpcPortraitResolver;
import dev.thenexusgates.mobmapmarkers.catalog.MobArchiveIndex;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerManager;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerVisibilityService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public final class FastMiniMapCompatService {

    /** Rendered size of mob icons (portraits and badges) on the minimap in pixels. */
    private static final int MINIMAP_ICON_SIZE = 20;

    /**
     * Sentinel placed in {@link #iconCache} for roles whose portrait could not be
     * resolved, so we never attempt resolution more than once per role.
     */
    private static final BufferedImage NO_ICON =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final MobMarkerManager manager;
    private final MobMarkerVisibilityService visibilityService;

    /** Role-name → scaled portrait icon (or {@link #NO_ICON} sentinel when unavailable). */
    private final ConcurrentHashMap<String, BufferedImage> iconCache = new ConcurrentHashMap<>();

    public FastMiniMapCompatService(MobMarkerManager manager, MobMarkerVisibilityService visibilityService) {
        this.manager = manager;
        this.visibilityService = visibilityService;
    }

    public void register() {
        FastMiniMapMobLayerApi.setProvider(this::getDots);
    }

    public void unregister() {
        FastMiniMapMobLayerApi.setProvider(null);
    }

    private List<FastMiniMapMobLayerApi.MobDot> getDots(
            String worldName, UUID viewerUuid,
            double viewerX, double viewerZ, int radiusBlocks) {

        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        if (config == null || !config.enableMobMarkers) {
            return List.of();
        }

        List<MobMarkerManager.MobMarkerSnapshot> snapshots = manager.getMobData(worldName);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        double radiusSq = radiusBlocks <= 0
                ? Double.POSITIVE_INFINITY
                : (double) config.mobMarkerRadius * config.mobMarkerRadius;

        List<FastMiniMapMobLayerApi.MobDot> dots = new ArrayList<>();
        for (MobMarkerManager.MobMarkerSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.position() == null) {
                continue;
            }
            if (!visibilityService.isVisible(viewerUuid, snapshot, MobMarkerSurface.MINIMAP)) {
                continue;
            }
            double dx = snapshot.position().x - viewerX;
            double dz = snapshot.position().z - viewerZ;
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }
            dots.add(new FastMiniMapMobLayerApi.MobDot(
                    snapshot.position().x,
                    snapshot.position().z,
                    resolveIcon(snapshot.roleName(), snapshot.displayName())));
        }
        return dots;
    }

    // -------------------------------------------------------------------------
    // Icon resolution
    // -------------------------------------------------------------------------
    // Icon resolution — all rendering delegated to MobMapImageProcessor
    // -------------------------------------------------------------------------

    /**
     * Returns a cached {@link BufferedImage} icon for the given role.
     * All rendering is delegated to {@link MobMapImageProcessor}, the single
     * source of truth for icon generation in MobMapMarkers.
     */
    private BufferedImage resolveIcon(String roleName, String displayName) {
        String key = roleName == null ? "" : roleName.toLowerCase(Locale.ROOT);
        BufferedImage cached = iconCache.get(key);
        if (cached != null) {
            return cached == NO_ICON ? null : cached;
        }
        BufferedImage icon = loadIcon(roleName, displayName);
        iconCache.put(key, icon != null ? icon : NO_ICON);
        return icon;
    }

    /**
     * Generates the icon via the same pipeline used by the big map:
     * portrait ({@link MobMapImageProcessor#createMobPortraitMarkerPng}) when
     * available, otherwise a text-badge
     * ({@link MobMapImageProcessor#createMobMarkerPng}).
     */
    private static BufferedImage loadIcon(String roleName, String displayName) {
        // -- Try portrait first --
        String portraitName = HytaleNpcPortraitResolver.resolvePortraitName(roleName);
        if (portraitName != null) {
            byte[] rawPng = HytaleNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
            if (rawPng != null && rawPng.length > 0) {
                byte[] iconBytes = MobMapImageProcessor.createMobPortraitMarkerPng(
                        rawPng, MINIMAP_ICON_SIZE, false, 96);
                if (iconBytes != null && iconBytes.length > 0) {
                    try {
                        return ImageIO.read(new ByteArrayInputStream(iconBytes));
                    } catch (IOException ignored) {
                        // fall through to badge
                    }
                }
            }
        }

        byte[] modPortraitPng = MobArchiveIndex.loadModPortraitPngByRoleName(roleName);
        if (modPortraitPng != null && modPortraitPng.length > 0) {
            byte[] iconBytes = MobMapImageProcessor.createMobPortraitMarkerPng(
                    modPortraitPng, MINIMAP_ICON_SIZE, false, 96);
            if (iconBytes != null && iconBytes.length > 0) {
                try {
                    return ImageIO.read(new ByteArrayInputStream(iconBytes));
                } catch (IOException ignored) {
                    // fall through to badge
                }
            }
        }

        // -- No portrait: render a plain circle badge (no map-pin tail) --
        byte[] badgeBytes = MobMapImageProcessor.createMinimapBadgePng(roleName, displayName, MINIMAP_ICON_SIZE);
        if (badgeBytes == null || badgeBytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(badgeBytes));
        } catch (IOException ignored) {
            return null;
        }
    }
}
