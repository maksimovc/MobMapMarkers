package dev.thenexusgates.mobmapmarkers.asset;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.catalog.HytaleNpcPortraitResolver;
import dev.thenexusgates.mobmapmarkers.catalog.MobArchiveIndex;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class MobMapAssetPack {

    private static final Logger LOGGER = Logger.getLogger(MobMapAssetPack.class.getName());
    private static final String MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String UNKNOWN_IMAGE_KEY = "unknown";
    private static final int ASSET_PACKET_SIZE = 2_621_440;
    private static final int MIN_ICON_SIZE = 16;
    private static final int MIN_CONTENT_SCALE_PERCENT = 50;
    private static final int MAX_CONTENT_SCALE_PERCENT = 100;

    private static final ConcurrentHashMap<String, MobMarkerAsset> GENERATED_ASSETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ViewerDeliveryState> DELIVERY_BY_VIEWER = new ConcurrentHashMap<>();

    private static volatile boolean initialized;
    private static Path dataRoot;

    private MobMapAssetPack() {
    }

    public static void init(Path pluginDataRoot) {
        ensureInitialized(pluginDataRoot, null);
    }

    public static void init(Path pluginDataRoot, Path legacyModsDirectory) {
        ensureInitialized(pluginDataRoot, legacyModsDirectory);
    }

    public static Path getDataRoot() {
        ensureInitialized();
        return dataRoot;
    }

    public static boolean hasPendingAssets(UUID viewerUuid) {
        if (viewerUuid == null) {
            return false;
        }

        ViewerDeliveryState state = DELIVERY_BY_VIEWER.get(viewerUuid);
        return state != null && state.hasPendingAssets();
    }

    public static void clearViewer(UUID viewerUuid) {
        if (viewerUuid != null) {
            DELIVERY_BY_VIEWER.remove(viewerUuid);
        }
    }

    public static void shutdown() {
        DELIVERY_BY_VIEWER.clear();
        GENERATED_ASSETS.clear();

        initialized = false;
        dataRoot = null;
    }

    public static String ensureMobIcon(String roleName, String displayName, String localizedAssetKey,
                                       int size, int contentScalePercent,
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

        byte[] modPortraitPng = MobArchiveIndex.loadModPortraitPngByRoleName(roleName);
        if (modPortraitPng != null && modPortraitPng.length > 0) {
            String imagePath = buildImagePath("mmm-mod", roleName, normalizedSize, normalizedScale, facingRight);
            String resolvedImagePath = ensureGeneratedImage(imagePath, () -> MobMapImageProcessor.createMobPortraitMarkerPng(
                    modPortraitPng,
                    normalizedSize,
                    facingRight,
                    normalizedScale));
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

        String imagePath = buildGeneratedImagePath(roleName, localizedAssetKey, normalizedSize, normalizedScale, facingRight);
        return ensureGeneratedImage(imagePath,
                () -> MobMapImageProcessor.createMobMarkerPng(roleName, displayName, normalizedSize));
    }

    public static void prewarmMobIcons(int size, int contentScalePercent) {
        ensureInitialized();

        int iconSize = normalizeIconSize(size);
        int normalizedScale = normalizeContentScalePercent(contentScalePercent);
        ensureFallbackIcons(iconSize, normalizedScale);
        for (String portraitName : HytaleNpcPortraitResolver.getAvailablePortraitNames()) {
            prewarmPortraitIcon(portraitName, iconSize, normalizedScale, true);
            prewarmPortraitIcon(portraitName, iconSize, normalizedScale, false);
        }
    }

    public static String toUiAssetPath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        return MARKER_ASSET_PREFIX + imagePath;
    }

    public static boolean deliverAssetToViewer(PlayerRef viewer, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return false;
        }
        return deliverAssetsToViewer(viewer, List.of(imagePath));
    }

    public static boolean hasDeliveredAsset(UUID viewerUuid, String imagePath) {
        if (viewerUuid == null || imagePath == null || imagePath.isBlank()) {
            return false;
        }

        ViewerDeliveryState state = DELIVERY_BY_VIEWER.get(viewerUuid);
        if (state == null) {
            return false;
        }

        String assetPath = toUiAssetPath(imagePath);
        return assetPath != null && state.isDelivered(assetPath);
    }

    public static boolean deliverAssetsToViewer(PlayerRef viewer, Collection<String> imagePaths) {
        if (viewer == null || imagePaths == null || imagePaths.isEmpty()) {
            return false;
        }

        UUID viewerUuid = viewer.getUuid();
        PacketHandler packetHandler = viewer.getPacketHandler();
        if (viewerUuid == null || packetHandler == null) {
            return false;
        }

        ViewerDeliveryState deliveryState = DELIVERY_BY_VIEWER.computeIfAbsent(
                viewerUuid,
                ignored -> new ViewerDeliveryState());
        LinkedHashMap<String, MobMarkerAsset> pendingAssets = new LinkedHashMap<>();
        for (String imagePath : imagePaths) {
            String assetPath = toUiAssetPath(imagePath);
            if (assetPath == null || assetPath.isBlank()) {
                continue;
            }

            MobMarkerAsset asset = GENERATED_ASSETS.get(assetPath);
            if (asset != null) {
                pendingAssets.putIfAbsent(assetPath, asset);
            }
        }

        DeliveryReservation reservation = deliveryState.reserve(pendingAssets.keySet());
        if (reservation.assetPaths().isEmpty()) {
            return false;
        }

        for (String assetPath : reservation.assetPaths()) {
            MobMarkerAsset asset = pendingAssets.get(assetPath);
            if (asset != null) {
                MobMarkerAsset.sendToPlayer(packetHandler, asset);
            }
        }

        if (reservation.rebuildRequired() && !requestRebuild(packetHandler, viewerUuid)) {
            deliveryState.discardPendingBatches();
            return false;
        }

        return true;
    }

    public static void advanceViewerDeliveryPhase(PlayerRef viewer) {
        if (viewer == null) {
            return;
        }

        UUID viewerUuid = viewer.getUuid();
        if (viewerUuid == null) {
            return;
        }

        ViewerDeliveryState deliveryState = DELIVERY_BY_VIEWER.get(viewerUuid);
        if (deliveryState == null) {
            return;
        }

        boolean nextBatchReady = deliveryState.advancePhase();
        if (!nextBatchReady) {
            return;
        }

        PacketHandler packetHandler = viewer.getPacketHandler();
        if (packetHandler == null || !requestRebuild(packetHandler, viewerUuid)) {
            deliveryState.discardPendingBatches();
        }
    }

    public static void refreshFallbackIcons(int size, int contentScalePercent) {
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

    private static String buildGeneratedImagePath(String roleName, String localizedAssetKey,
                                                  int size, int contentScalePercent, boolean facingRight) {
        String roleSegment = sanitizeKey(roleName);
        String localeSegment = stableKey(localizedAssetKey);
        return "mmm-generated"
                + "-"
                + roleSegment
                + "-"
                + localeSegment
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

    private static String stableKey(String key) {
        if (key == null || key.isBlank()) {
            return UNKNOWN_IMAGE_KEY;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(key.hashCode());
        }
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
        if (!initialized || dataRoot == null) {
            throw new IllegalStateException("MobMapAssetPack.init(Path) must be called before use");
        }
    }

    private static void ensureInitialized(Path pluginDataRoot, Path legacyModsDirectory) {
        if (initialized) {
            return;
        }

        synchronized (MobMapAssetPack.class) {
            if (initialized) {
                return;
            }

            try {
                Path resolvedLegacyModsDirectory = legacyModsDirectory != null
                        ? legacyModsDirectory
                        : resolveLegacyModsDirectory();

                dataRoot = pluginDataRoot;
                Files.createDirectories(dataRoot);
                migrateLegacyDataDirectory(resolvedLegacyModsDirectory.resolve("MobMapMarkersData"), dataRoot);
                cleanupLegacyPack(resolvedLegacyModsDirectory.resolve("MobMapMarkersAssets"));
                initialized = true;
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Failed to initialize mob map asset cache", e);
            }
        }
    }

    private static Path resolveLegacyModsDirectory() throws URISyntaxException {
        Path pluginLocation = Paths.get(MobMapMarkersPlugin.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return Files.isDirectory(pluginLocation)
                ? pluginLocation
                : pluginLocation.getParent();
    }

    private static void migrateLegacyDataDirectory(Path legacyDataDirectory, Path newDataDirectory) throws IOException {
        if (legacyDataDirectory == null || newDataDirectory == null || legacyDataDirectory.equals(newDataDirectory)) {
            return;
        }

        Path legacyConfigPath = legacyDataDirectory.resolve("mobmapmarkers-config.json");
        Path newConfigPath = newDataDirectory.resolve("mobmapmarkers-config.json");
        if (Files.exists(legacyConfigPath) && Files.notExists(newConfigPath)) {
            Files.move(legacyConfigPath, newConfigPath);
        }

        deleteIfEmpty(legacyDataDirectory);
    }

    private static void deleteIfEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        }

        Files.deleteIfExists(directory);
    }

    private static boolean requestRebuild(PacketHandler packetHandler, UUID viewerUuid) {
        if (packetHandler == null) {
            return false;
        }

        try {
            packetHandler.writeNoCache(new RequestCommonAssetsRebuild());
            return true;
        } catch (RuntimeException e) {
            LOGGER.warning("[MobMapMarkers] Failed to request common assets rebuild for viewer "
                    + viewerUuid + ": " + e.getMessage());
            return false;
        }
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
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to clean legacy asset pack directory: " + e.getMessage());
        }
    }

    private record DeliveryReservation(Set<String> assetPaths, boolean rebuildRequired) {
    }

    private static final class ViewerDeliveryState {

        private final Set<String> deliveredAssets = ConcurrentHashMap.newKeySet();
        private final LinkedHashMap<String, Boolean> currentBatch = new LinkedHashMap<>();
        private final LinkedHashMap<String, Boolean> queuedBatch = new LinkedHashMap<>();

        private synchronized DeliveryReservation reserve(Collection<String> assetPaths) {
            LinkedHashMap<String, Boolean> targetBatch = currentBatch.isEmpty() ? currentBatch : queuedBatch;
            boolean rebuildRequired = targetBatch == currentBatch;
            LinkedHashMap<String, Boolean> reserved = new LinkedHashMap<>();
            for (String assetPath : assetPaths) {
                if (assetPath == null
                        || deliveredAssets.contains(assetPath)
                        || currentBatch.containsKey(assetPath)
                        || queuedBatch.containsKey(assetPath)) {
                    continue;
                }

                targetBatch.put(assetPath, Boolean.TRUE);
                reserved.put(assetPath, Boolean.TRUE);
            }

            return new DeliveryReservation(Set.copyOf(reserved.keySet()), rebuildRequired && !reserved.isEmpty());
        }

        private synchronized boolean isDelivered(String assetPath) {
            return deliveredAssets.contains(assetPath);
        }

        private synchronized boolean hasPendingAssets() {
            return !currentBatch.isEmpty() || !queuedBatch.isEmpty();
        }

        private synchronized boolean advancePhase() {
            if (currentBatch.isEmpty()) {
                return false;
            }

            deliveredAssets.addAll(currentBatch.keySet());
            currentBatch.clear();
            if (queuedBatch.isEmpty()) {
                return false;
            }

            currentBatch.putAll(queuedBatch);
            queuedBatch.clear();
            return true;
        }

        private synchronized void discardPendingBatches() {
            currentBatch.clear();
            queuedBatch.clear();
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
