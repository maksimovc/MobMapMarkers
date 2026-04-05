package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class MobMapAssetPack {

    private static final Logger LOGGER = Logger.getLogger(MobMapAssetPack.class.getName());
    private static final String MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String UNKNOWN_IMAGE_KEY = "unknown";
    private static final int ASSET_PACKET_SIZE = 2_621_440;
    private static final int MIN_ICON_SIZE = 16;
    private static final int MIN_CONTENT_SCALE_PERCENT = 50;
    private static final int MAX_CONTENT_SCALE_PERCENT = 100;
    private static final long REBUILD_DEBOUNCE_MS = 40L;

    private static final ConcurrentHashMap<String, MobMarkerAsset> GENERATED_ASSETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Set<String>> DELIVERED_BY_VIEWER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ScheduledFuture<?>> PENDING_REBUILDS = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService rebuildScheduler;

    private static volatile boolean initialized;
    private static Path dataRoot;

    private MobMapAssetPack() {
    }

    static void init() {
        ensureInitialized();
    }

    static Path getDataRoot() {
        ensureInitialized();
        return dataRoot;
    }

    static void clearViewer(UUID viewerUuid) {
        if (viewerUuid != null) {
            DELIVERED_BY_VIEWER.remove(viewerUuid);
            ScheduledFuture<?> future = PENDING_REBUILDS.remove(viewerUuid);
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }
    }

    static void shutdown() {
        DELIVERED_BY_VIEWER.clear();
        GENERATED_ASSETS.clear();
        PENDING_REBUILDS.values().forEach(future -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        });
        PENDING_REBUILDS.clear();

        ScheduledExecutorService scheduler = rebuildScheduler;
        rebuildScheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        initialized = false;
        dataRoot = null;
    }

    static String ensureMobIcon(String roleName, String displayName, int size, int contentScalePercent,
                                boolean facingRight, boolean renderFallback) {
        ensureInitialized();

        int normalizedSize = normalizeIconSize(size);
        int normalizedScale = normalizeContentScalePercent(contentScalePercent);

        String portraitName = HytaleNpcPortraitResolver.resolvePortraitName(roleName);
        if (portraitName != null) {
            String imagePath = buildImagePath("mmm-official", portraitName, normalizedSize, normalizedScale, facingRight);
            String resolvedImagePath = ensureGeneratedImage(imagePath, () -> {
                byte[] portraitPng = HytaleNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
                if (portraitPng == null || portraitPng.length == 0) {
                    return null;
                }
                return MobMapImageProcessor.createMobPortraitMarkerPng(
                        portraitPng,
                        normalizedSize,
                        facingRight,
                        normalizedScale);
            });
            if (resolvedImagePath != null) {
                return resolvedImagePath;
            }
        }

        if (!renderFallback) {
            return null;
        }

        if (roleName == null || roleName.isBlank()) {
            ensureFallbackIcons(normalizedSize, normalizedScale);
            return buildFallbackImagePath(normalizedSize, normalizedScale, facingRight);
        }

        String imagePath = buildImagePath("mmm-generated", roleName, normalizedSize, normalizedScale, facingRight);
        return ensureGeneratedImage(imagePath,
                () -> MobMapImageProcessor.createMobMarkerPng(roleName, displayName, normalizedSize));
    }

    static void prewarmMobIcons(int size, int contentScalePercent) {
        ensureInitialized();

        int iconSize = normalizeIconSize(size);
        int normalizedScale = normalizeContentScalePercent(contentScalePercent);
        ensureFallbackIcons(iconSize, normalizedScale);
        for (String portraitName : HytaleNpcPortraitResolver.getAvailablePortraitNames()) {
            prewarmPortraitIcon(portraitName, iconSize, normalizedScale, true);
            prewarmPortraitIcon(portraitName, iconSize, normalizedScale, false);
        }
    }

    static String toUiAssetPath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        return MARKER_ASSET_PREFIX + imagePath;
    }

    static void deliverAssetToViewer(PlayerRef viewer, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        deliverAssetsToViewer(viewer, List.of(imagePath));
    }

    static void deliverAssetsToViewer(PlayerRef viewer, Collection<String> imagePaths) {
        if (viewer == null || imagePaths == null || imagePaths.isEmpty()) {
            return;
        }

        UUID viewerUuid = viewer.getUuid();
        PacketHandler packetHandler = viewer.getPacketHandler();
        if (viewerUuid == null || packetHandler == null) {
            return;
        }

        Set<String> deliveredAssets = DELIVERED_BY_VIEWER.computeIfAbsent(viewerUuid,
                ignored -> ConcurrentHashMap.newKeySet());
        LinkedHashMap<String, MobMarkerAsset> pendingAssets = new LinkedHashMap<>();
        for (String imagePath : imagePaths) {
            String assetPath = toUiAssetPath(imagePath);
            if (assetPath == null || assetPath.isBlank() || deliveredAssets.contains(assetPath)) {
                continue;
            }

            MobMarkerAsset asset = GENERATED_ASSETS.get(assetPath);
            if (asset != null) {
                pendingAssets.putIfAbsent(assetPath, asset);
            }
        }

        if (pendingAssets.isEmpty()) {
            return;
        }

        for (MobMarkerAsset asset : pendingAssets.values()) {
            MobMarkerAsset.sendToPlayer(packetHandler, asset);
        }
        deliveredAssets.addAll(pendingAssets.keySet());
        scheduleRebuild(viewerUuid, packetHandler);
    }

    static void refreshFallbackIcons(int size, int contentScalePercent) {
        ensureInitialized();
        ensureFallbackIcons(normalizeIconSize(size), normalizeContentScalePercent(contentScalePercent));
    }

    private static void ensureFallbackIcons(int size, int contentScalePercent) {
        ensureGeneratedImage(
                buildFallbackImagePath(size, contentScalePercent, false),
                () -> MobMapImageProcessor.createFallbackMarkerPng(size, contentScalePercent));
        ensureGeneratedImage(
                buildFallbackImagePath(size, contentScalePercent, true),
                () -> MobMapImageProcessor.createFallbackMarkerPng(size, contentScalePercent));
    }

    private static String buildFallbackImagePath(int size, int contentScalePercent, boolean facingRight) {
        return buildImagePath("mmm-fallback", UNKNOWN_IMAGE_KEY, size, contentScalePercent, facingRight);
    }

    private static void prewarmPortraitIcon(String portraitName, int size, int contentScalePercent, boolean facingRight) {
        String imagePath = buildImagePath("mmm-official", portraitName, size, contentScalePercent, facingRight);
        ensureGeneratedImage(imagePath, () -> {
            byte[] pngBytes = HytaleNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
            if (pngBytes == null || pngBytes.length == 0) {
                return null;
            }
            return MobMapImageProcessor.createMobPortraitMarkerPng(
                    pngBytes,
                    size,
                    facingRight,
                    contentScalePercent);
        });
    }

    private static String buildImagePath(String prefix, String key, int size, int contentScalePercent,
                                         boolean facingRight) {
        String normalized = sanitizeKey(key);
        return prefix
                + "-"
                + normalized
                + "-s"
                + size
                + "-p"
                + contentScalePercent
                + (facingRight ? "-right" : "-left")
                + ".png";
    }

    private static int normalizeIconSize(int size) {
        return Math.max(MIN_ICON_SIZE, size);
    }

    private static int normalizeContentScalePercent(int contentScalePercent) {
        return Math.max(MIN_CONTENT_SCALE_PERCENT, Math.min(MAX_CONTENT_SCALE_PERCENT, contentScalePercent));
    }

    private static String sanitizeKey(String key) {
        String normalized = key == null ? UNKNOWN_IMAGE_KEY : key.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("(^-+)|(-+$)", "");
        return normalized.isBlank() ? UNKNOWN_IMAGE_KEY : normalized;
    }

    private static String ensureGeneratedImage(String imagePath, Supplier<byte[]> pngFactory) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String assetPath = toUiAssetPath(imagePath);
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }

        MobMarkerAsset asset = GENERATED_ASSETS.computeIfAbsent(assetPath, ignored -> {
            byte[] pngBytes = pngFactory.get();
            if (pngBytes == null || pngBytes.length == 0) {
                return null;
            }
            return new MobMarkerAsset(assetPath, Arrays.copyOf(pngBytes, pngBytes.length));
        });
        return asset != null ? imagePath : null;
    }

    private static String computeAssetHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (MobMapAssetPack.class) {
            if (initialized) {
                return;
            }

            try {
                ensureRebuildScheduler();
                Path pluginLocation = Paths.get(MobMapMarkersPlugin.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
                Path modsDirectory = Files.isDirectory(pluginLocation)
                        ? pluginLocation
                        : pluginLocation.getParent();

                dataRoot = modsDirectory.resolve("MobMapMarkersData");
                Files.createDirectories(dataRoot);
                cleanupLegacyPack(modsDirectory.resolve("MobMapMarkersAssets"));
                initialized = true;
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Failed to initialize mob map asset cache", e);
            }
        }
    }

    private static void ensureRebuildScheduler() {
        if (rebuildScheduler != null) {
            return;
        }

        synchronized (MobMapAssetPack.class) {
            if (rebuildScheduler != null) {
                return;
            }

            rebuildScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "MobMapMarkers-AssetRebuild");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private static void scheduleRebuild(UUID viewerUuid, PacketHandler packetHandler) {
        if (viewerUuid == null || packetHandler == null) {
            return;
        }

        ensureRebuildScheduler();
        ScheduledFuture<?> existing = PENDING_REBUILDS.get(viewerUuid);
        if (existing != null && !existing.isDone()) {
            return;
        }

        ScheduledFuture<?> future = rebuildScheduler.schedule(() -> {
            try {
                packetHandler.writeNoCache(new RequestCommonAssetsRebuild());
            } finally {
                PENDING_REBUILDS.remove(viewerUuid);
            }
        }, REBUILD_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        PENDING_REBUILDS.put(viewerUuid, future);
    }

    private static void cleanupLegacyPack(Path legacyPackRoot) {
        if (legacyPackRoot == null || !Files.exists(legacyPackRoot)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(legacyPackRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warning("[MobMapMarkers] Failed to delete legacy asset path "
                            + path.getFileName() + ": " + e.getMessage());
                }
            });
            LOGGER.info("[MobMapMarkers] Removed legacy MobMapMarkersAssets pack directory.");
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to clean legacy asset pack directory: " + e.getMessage());
        }
    }

    private static final class MobMarkerAsset extends CommonAsset {

        private final byte[] pngBytes;

        private MobMarkerAsset(String assetPath, byte[] pngBytes) {
            super(assetPath, computeAssetHash(pngBytes), pngBytes);
            this.pngBytes = pngBytes;
        }

        @Override
        protected CompletableFuture<byte[]> getBlob0() {
            return CompletableFuture.completedFuture(pngBytes);
        }

        private static void sendToPlayer(PacketHandler packetHandler, CommonAsset asset) {
            byte[] blob = asset.getBlob().join();
            byte[][] parts = ArrayUtil.split(blob, ASSET_PACKET_SIZE);
            Packet[] packets = new Packet[parts.length + 2];
            packets[0] = new AssetInitialize(asset.toPacket(), blob.length);
            for (int index = 0; index < parts.length; index++) {
                packets[index + 1] = new AssetPart(parts[index]);
            }
            packets[packets.length - 1] = new AssetFinalize();
            for (Packet packet : packets) {
                packetHandler.write((com.hypixel.hytale.protocol.ToClientPacket) packet);
            }
        }
    }
}
