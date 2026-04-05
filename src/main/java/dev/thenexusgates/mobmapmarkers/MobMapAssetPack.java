package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

final class MobMapAssetPack {

    private static final Logger LOGGER = Logger.getLogger(MobMapAssetPack.class.getName());
    private static final String PACK_ID = "thenexusgates:MobMapMarkersAssets";
    private static final String PACK_GROUP = "thenexusgates";
    private static final String PACK_NAME = "MobMapMarkersAssets";
    private static final String PACK_VERSION = "1.0.1";
    private static final String TARGET_SERVER_VERSION = "2026.03.26-89796e57b";
    private static final String MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String UNKNOWN_LEFT_IMAGE = "mmm-unknown-left.png";
    private static final String UNKNOWN_RIGHT_IMAGE = "mmm-unknown-right.png";
    private static final long ASSET_REBUILD_DEBOUNCE_MS = 300L;

    private static final String MANIFEST_JSON = """
            {
              "Group": "thenexusgates",
              "Name": "MobMapMarkersAssets",
              "Version": "1.0.1",
              "Description": "Generated mob marker icons for the Hytale world map",
              "Authors": [
                {
                  "Name": "maksimovc"
                }
              ],
              "ServerVersion": "2026.03.26-89796e57b",
              "Dependencies": {},
              "OptionalDependencies": {},
              "DisabledByDefault": false,
              "IncludesAssetPack": true
            }
            """;

    private static final ConcurrentHashMap<String, FileCommonAsset> PUSHED_ASSETS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> GENERATED_MOB_ICONS = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService ASSET_REBUILD_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean REBUILD_QUEUED = new AtomicBoolean(false);

    private static volatile boolean initialized;
    private static volatile boolean registered;
    private static Path packRoot;

    private MobMapAssetPack() {
    }

    static void init() {
        ensureInitialized();
        registerPackIfNeeded();
    }

    static Path getPackRoot() {
        ensureInitialized();
        return packRoot;
    }

    static String ensureMobIcon(String roleName, String displayName, int size, int contentScalePercent,
                                boolean facingRight, boolean renderFallback) {
        ensureInitialized();
        registerPackIfNeeded();

        String portraitName = HytaleNpcPortraitResolver.resolvePortraitName(roleName);
        if (portraitName != null) {
            String imagePath = buildImagePath("mmm-official", portraitName, facingRight);
            if (!GENERATED_MOB_ICONS.add(imagePath)) {
                return imagePath;
            }

            byte[] portraitPng = HytaleNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
            if (portraitPng != null && portraitPng.length > 0) {
                writeMarkerImage(imagePath, MobMapImageProcessor.createMobPortraitMarkerPng(
                        portraitPng,
                        size,
                        facingRight,
                        contentScalePercent));
                return imagePath;
            }

            GENERATED_MOB_ICONS.remove(imagePath);
        }

        if (!renderFallback) {
            return null;
        }

        if (roleName == null || roleName.isBlank()) {
            refreshFallbackIcons(size, contentScalePercent);
            return facingRight ? UNKNOWN_RIGHT_IMAGE : UNKNOWN_LEFT_IMAGE;
        }

        String imagePath = buildImagePath("mmm-generated", roleName, facingRight);
        if (!GENERATED_MOB_ICONS.add(imagePath)) {
            return imagePath;
        }

        writeMarkerImage(imagePath, MobMapImageProcessor.createMobMarkerPng(roleName, displayName, size));
        return imagePath;
    }

    static void prewarmMobIcons(int size, int contentScalePercent) {
        ensureInitialized();
        registerPackIfNeeded();

        int iconSize = Math.max(16, size);
        refreshFallbackIcons(iconSize, contentScalePercent);
        for (String portraitName : HytaleNpcPortraitResolver.getAvailablePortraitNames()) {
            prewarmPortraitIcon(portraitName, iconSize, contentScalePercent, true);
            prewarmPortraitIcon(portraitName, iconSize, contentScalePercent, false);
        }
    }

    static void refreshFallbackIcons(int size, int contentScalePercent) {
        int iconSize = Math.max(16, size);
        int normalizedScalePercent = Math.max(50, Math.min(100, contentScalePercent));
        writeStaticMarkerAsset(
                UNKNOWN_LEFT_IMAGE,
                MobMapImageProcessor.createFallbackMarkerPng(iconSize, normalizedScalePercent));
        writeStaticMarkerAsset(
                UNKNOWN_RIGHT_IMAGE,
                MobMapImageProcessor.createFallbackMarkerPng(iconSize, normalizedScalePercent));
    }

    private static void prewarmPortraitIcon(String portraitName, int size, int contentScalePercent, boolean facingRight) {
        String imagePath = buildImagePath("mmm-official", portraitName, facingRight);
        if (!GENERATED_MOB_ICONS.add(imagePath)) {
            return;
        }

        byte[] pngBytes = HytaleNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
        if (pngBytes == null || pngBytes.length == 0) {
            GENERATED_MOB_ICONS.remove(imagePath);
            return;
        }

        writeMarkerImage(imagePath, MobMapImageProcessor.createMobPortraitMarkerPng(
                pngBytes,
                size,
                facingRight,
                contentScalePercent));
    }

    private static String buildImagePath(String prefix, String key, boolean facingRight) {
        String normalized = sanitizeKey(key);
        return prefix + "-" + normalized + (facingRight ? "-right" : "-left") + ".png";
    }

    private static String sanitizeKey(String key) {
        String normalized = key == null ? "unknown" : key.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("(^-+)|(-+$)", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static void writeMarkerImage(String imagePath, byte[] pngBytes) {
        if (imagePath == null || imagePath.isBlank() || pngBytes == null || pngBytes.length == 0) {
            return;
        }

        try {
            Path output = packRoot.resolve("Common/UI/WorldMap/MapMarkers").resolve(imagePath);
            Files.createDirectories(output.getParent());
            Files.write(output, pngBytes);
            pushAssetToClients(MARKER_ASSET_PREFIX + imagePath, pngBytes, output);
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to write marker asset " + imagePath + ": " + e.getMessage());
        }
    }

    private static void pushAssetToClients(String assetName, byte[] pngBytes, Path filePath) {
        try {
            CommonAssetModule commonAssetModule = CommonAssetModule.get();
            if (commonAssetModule == null) {
                LOGGER.warning("[MobMapMarkers] CommonAssetModule not available, cannot push asset");
                return;
            }

            FileCommonAsset asset = new FileCommonAsset(filePath, assetName, pngBytes);
            commonAssetModule.addCommonAsset(assetName, asset, true);
            PUSHED_ASSETS.put(assetName, asset);
            requestAssetRebuild();
        } catch (Exception e) {
            LOGGER.warning("[MobMapMarkers] Failed to push marker asset to clients: " + e.getMessage());
        }
    }

    private static void requestAssetRebuild() {
        Universe universe = Universe.get();
        if (universe == null || universe.getPlayerCount() <= 0) {
            return;
        }

        if (!REBUILD_QUEUED.compareAndSet(false, true)) {
            return;
        }

        ASSET_REBUILD_SCHEDULER.schedule(() -> {
            try {
                Universe activeUniverse = Universe.get();
                if (activeUniverse != null && activeUniverse.getPlayerCount() > 0) {
                    activeUniverse.broadcastPacketNoCache(new RequestCommonAssetsRebuild());
                }
            } finally {
                REBUILD_QUEUED.set(false);
            }
        }, ASSET_REBUILD_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
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
                Path pluginLocation = Paths.get(MobMapMarkersPlugin.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
                Path modsDirectory = Files.isDirectory(pluginLocation)
                        ? pluginLocation
                        : pluginLocation.getParent();

                packRoot = modsDirectory.resolve("MobMapMarkersAssets");
                Files.createDirectories(packRoot);
                Files.writeString(packRoot.resolve("manifest.json"), MANIFEST_JSON, StandardCharsets.UTF_8);
                ensureStaticAssets();
                ensurePackEnabled(modsDirectory.getParent().resolve("config.json"));
                registerPackIfNeeded();
                initialized = true;
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Failed to initialize mob map asset pack", e);
            }
        }
    }

    private static void ensureStaticAssets() {
        refreshFallbackIcons(44, 96);
    }

    private static void writeStaticMarkerAsset(String imagePath, byte[] pngBytes) {
        GENERATED_MOB_ICONS.add(imagePath);
        writeMarkerImage(imagePath, pngBytes);
    }

    private static void registerPackIfNeeded() {
        if (registered) {
            return;
        }

        AssetModule assetModule = AssetModule.get();
        if (assetModule == null) {
            return;
        }

        if (assetModule.getAssetPack(PACK_ID) != null) {
            registered = true;
            return;
        }

        assetModule.registerPack(PACK_ID, packRoot, buildRuntimeManifest(), true);
        registered = true;
        LOGGER.info("[MobMapMarkers] Registered runtime asset pack: " + PACK_ID);
    }

    private static PluginManifest buildRuntimeManifest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setGroup(PACK_GROUP);
        manifest.setName(PACK_NAME);
        manifest.setVersion(Semver.fromString(PACK_VERSION));
        manifest.setDescription("Generated mob marker icons for the Hytale world map");
        manifest.setWebsite("https://github.com/maksimovc/MobMapMarkers");
        manifest.setServerVersion(TARGET_SERVER_VERSION);

        AuthorInfo author = new AuthorInfo();
        author.setName("maksimovc");
        manifest.setAuthors(List.of(author));
        return manifest;
    }

    private static void ensurePackEnabled(Path configPath) {
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            String updated = json;
            if (json.contains('"' + PACK_ID + '"')) {
                updated = json.replaceAll(
                        "(\\\"" + java.util.regex.Pattern.quote(PACK_ID) + "\\\"\\s*:\\s*\\{\\s*\\\"Enabled\\\"\\s*:\\s*)false",
                        "$1true");
            } else if (json.contains("\"Mods\": {")) {
                updated = json.replace(
                        "\"Mods\": {",
                        "\"Mods\": {\n    \"" + PACK_ID + "\": {\n      \"Enabled\": true\n    },");
            }

            if (!updated.equals(json)) {
                Files.writeString(configPath, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to auto-enable asset pack in config.json: " + e.getMessage());
        }
    }
}
