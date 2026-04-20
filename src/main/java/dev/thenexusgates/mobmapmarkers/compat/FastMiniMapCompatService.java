package dev.thenexusgates.mobmapmarkers.compat;

import dev.thenexusgates.fastminimap.FastMiniMapMobLayerApi;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.asset.MobMapImageProcessor;
import dev.thenexusgates.mobmapmarkers.catalog.HytaleMobIconResolver;
import dev.thenexusgates.mobmapmarkers.catalog.MobPortraitMatcher;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerManager;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerVisibilityService;
import dev.thenexusgates.mobmapmarkers.util.WeightedLruCache;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FastMiniMapCompatService {

    private static final int MINIMAP_ICON_SIZE = 20;
    private static final int MAX_CACHED_ICONS = 512;
    private static final long ICON_CACHE_BYTES = 4L * 1024 * 1024;

    private static final BufferedImage NO_ICON =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final MobMarkerManager manager;
    private final MobMarkerVisibilityService visibilityService;

    private final WeightedLruCache<String, BufferedImage> iconCache = new WeightedLruCache<>(
            MAX_CACHED_ICONS,
            ICON_CACHE_BYTES,
            FastMiniMapCompatService::estimateImageWeight);
    private final Set<String> loadingRoles = ConcurrentHashMap.newKeySet();

    public FastMiniMapCompatService(MobMarkerManager manager, MobMarkerVisibilityService visibilityService) {
        this.manager = manager;
        this.visibilityService = visibilityService;
    }

    public void register() {
        FastMiniMapMobLayerApi.setProvider(this::getDots);
    }

    public void unregister() {
        FastMiniMapMobLayerApi.setProvider(null);
        iconCache.clear();
        loadingRoles.clear();
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

        double radiusSq = resolveMaxDistanceSquared(config.mobMarkerRadius, radiusBlocks);

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
            BufferedImage icon = resolveIcon(snapshot.roleName(), snapshot.displayName());
            if (icon == null) {
                continue;
            }

            dots.add(new FastMiniMapMobLayerApi.MobDot(
                    snapshot.position().x,
                    snapshot.position().z,
                    icon));
        }
        return dots;
    }

    private BufferedImage resolveIcon(String roleName, String displayName) {
        if (MobPortraitMatcher.isExcludedRole(roleName)) {
            return null;
        }

        String key = roleName == null ? "" : roleName.toLowerCase(Locale.ROOT);
        BufferedImage cached = iconCache.get(key);
        if (cached != null) {
            return cached == NO_ICON ? null : cached;
        }

        queueIconLoad(key, roleName, displayName);
        return null;
    }

    private void queueIconLoad(String key, String roleName, String displayName) {
        if (!loadingRoles.add(key)) {
            return;
        }

        MobMapMarkersPlugin plugin = MobMapMarkersPlugin.getInstance();
        if (plugin == null) {
            loadingRoles.remove(key);
            return;
        }

        plugin.runUiTask(() -> loadAndCacheIcon(key, roleName, displayName));
    }

    private void loadAndCacheIcon(String key, String roleName, String displayName) {
        try {
            BufferedImage icon = loadIcon(roleName, displayName);
            iconCache.put(key, icon != null ? icon : NO_ICON);
        } finally {
            loadingRoles.remove(key);
        }
    }

    private static BufferedImage loadIcon(String roleName, String displayName) {
        String iconEntryName = HytaleMobIconResolver.resolveIconEntryName(roleName);
        if (iconEntryName != null) {
            byte[] rawPng = HytaleMobIconResolver.loadIconPngByEntryName(iconEntryName);
            if (rawPng != null && rawPng.length > 0) {
                try {
                    BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawPng));
                    if (source != null) {
                        return scaleSquare(source, MINIMAP_ICON_SIZE);
                    }
                } catch (IOException ignored) {
                }
            }
        }

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

    private static BufferedImage scaleSquare(BufferedImage source, int size) {
        if (source.getWidth() == size && source.getHeight() == size && source.getType() == BufferedImage.TYPE_INT_ARGB) {
            BufferedImage copy = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = copy.createGraphics();
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            return copy;
        }

        double scale = Math.min(
                (double) size / Math.max(1, source.getWidth()),
                (double) size / Math.max(1, source.getHeight()));
        int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int offsetX = (size - drawWidth) / 2;
        int offsetY = (size - drawHeight) / 2;

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, offsetX, offsetY, drawWidth, drawHeight, null);
        graphics.dispose();
        return scaled;
    }

    private static double resolveMaxDistanceSquared(int configRadius, int overlayRadius) {
        double configLimit = configRadius <= 0 ? Double.POSITIVE_INFINITY : (double) configRadius * configRadius;
        double overlayLimit = overlayRadius <= 0 ? Double.POSITIVE_INFINITY : (double) overlayRadius * overlayRadius;
        return Math.min(configLimit, overlayLimit);
    }

    private static long estimateImageWeight(BufferedImage image) {
        if (image == null) {
            return 0L;
        }
        return (long) image.getWidth() * image.getHeight() * 4L;
    }
}
