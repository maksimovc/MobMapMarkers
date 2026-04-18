package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.thenexusgates.mobmapmarkers.asset.MobMapAssetPack;
import dev.thenexusgates.mobmapmarkers.catalog.HytaleNpcPortraitResolver;
import dev.thenexusgates.mobmapmarkers.catalog.MobArchiveIndex;
import dev.thenexusgates.mobmapmarkers.command.MobMapFiltersCommand;
import dev.thenexusgates.mobmapmarkers.compat.FastMiniMapCompat;
import dev.thenexusgates.mobmapmarkers.compat.FastMiniMapCompatService;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerFilterStore;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerManager;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerProvider;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerTicker;
import dev.thenexusgates.mobmapmarkers.marker.MobMarkerVisibilityService;
import dev.thenexusgates.mobmapmarkers.security.MobMapPermissions;
import dev.thenexusgates.mobmapmarkers.tracking.LivePlayerTracker;
import dev.thenexusgates.mobmapmarkers.ui.MobMapFiltersPage;
import dev.thenexusgates.mobmapmarkers.ui.MobMapUiSounds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

public final class MobMapMarkersPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "mobMapMarkers";
    private static final String VERSION = "1.6.0";

    private static MobMapMarkersPlugin instance;
    private static MobMapMarkersConfig config;
    private static final Map<UUID, Player> ACTIVE_PLAYERS = new ConcurrentHashMap<>();

    private MobMarkerManager mobMarkerManager;
    private MobMarkerTicker mobMarkerTicker;
    private FastMiniMapCompatService fastMiniMapCompatService;
    private MobMarkerFilterStore filterStore;
    private MobMarkerVisibilityService visibilityService;
    private MobMapUiSounds uiSounds;
    private ExecutorService uiWorker;

    public MobMapMarkersPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static MobMapMarkersPlugin getInstance() {
        return instance;
    }

    public static MobMapMarkersConfig getConfig() {
        return config;
    }

    public static boolean isMobMapCommandEnabled() {
        return config == null || config.enableMobMapCommand;
    }

    public MobMarkerManager getMobMarkerManager() {
        return mobMarkerManager;
    }

    public MobMarkerFilterStore getFilterStore() {
        return filterStore;
    }

    public MobMarkerVisibilityService getVisibilityService() {
        return visibilityService;
    }

    public MobMapUiSounds getUiSounds() {
        return uiSounds;
    }

    static Player getActivePlayer(UUID playerUuid) {
        return playerUuid == null ? null : ACTIVE_PLAYERS.get(playerUuid);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[MobMapMarkers] Starting v" + VERSION);

        Path dataDirectory = resolveDataDirectory();
        MobMapAssetPack.init(dataDirectory);
        LivePlayerTracker.register();
        config = MobMapMarkersConfig.load(
            MobMapAssetPack.getDataRoot().resolve("mobmapmarkers-config.json"));
        filterStore = new MobMarkerFilterStore(MobMapAssetPack.getDataRoot());
        visibilityService = new MobMarkerVisibilityService(filterStore, MobMapMarkersPlugin::getConfig);
        uiSounds = new MobMapUiSounds();
        uiWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MobMapMarkers-UI");
            thread.setDaemon(true);
            return thread;
        });

        MobMapAssetPack.refreshFallbackIcons(config.mobMarkerSize, config.mobIconContentScalePercent);

        mobMarkerManager = new MobMarkerManager();
        mobMarkerTicker = new MobMarkerTicker(mobMarkerManager, config.scanIntervalMs);
        mobMarkerTicker.start();
        if (FastMiniMapCompat.isAvailable()) {
            fastMiniMapCompatService = new FastMiniMapCompatService(mobMarkerManager, visibilityService);
            fastMiniMapCompatService.register();
        }
        runUiTask(() -> {
            HytaleNpcPortraitResolver.prewarm();
            MobArchiveIndex.prewarm();
        });
        getCommandRegistry().registerCommand(new MobMapFiltersCommand(this));

        Universe universe = Universe.get();
        if (universe != null) {
            for (World world : universe.getWorlds().values()) {
                registerProvider(world);
            }
        }

        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                registerProvider(world);
            }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }

            UUID playerUuid = ((CommandSender) player).getUuid();
            if (playerUuid != null) {
                ACTIVE_PLAYERS.put(playerUuid, player);
                filterStore.preload(playerUuid);
            }
        });

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef() != null ? event.getPlayerRef().getUuid() : null;
            if (uuid != null) {
                ACTIVE_PLAYERS.remove(uuid);
                filterStore.unload(uuid);
                LivePlayerTracker.remove(uuid);
                MobMapAssetPack.clearViewer(uuid);
            }
        });

        getLogger().at(Level.INFO).log("[MobMapMarkers] Ready.");
    }

    @Override
    protected void shutdown() {
        if (fastMiniMapCompatService != null) {
            fastMiniMapCompatService.unregister();
            fastMiniMapCompatService = null;
        }

        if (mobMarkerTicker != null) {
            mobMarkerTicker.shutdown();
            mobMarkerTicker = null;
        }

        ACTIVE_PLAYERS.clear();
        LivePlayerTracker.shutdown();
        MobMapAssetPack.shutdown();
        if (uiWorker != null) {
            uiWorker.shutdownNow();
            uiWorker = null;
        }
        mobMarkerManager = null;
        filterStore = null;
        visibilityService = null;
        uiSounds = null;
        config = null;
    }

    public void openFilters(com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef,
                            PlayerRef playerRef) {
        if (store == null || entityRef == null || playerRef == null) {
            return;
        }
        if (!isMobMapCommandEnabled()) {
            MobMapPermissions.sendUiDisabled(playerRef);
            return;
        }

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        uiSounds.play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
        player.getPageManager().openCustomPage(entityRef, store, new MobMapFiltersPage(playerRef, this));
    }

    public void runUiTask(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        ExecutorService worker = uiWorker;
        if (worker == null) {
            return;
        }

        try {
            worker.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void registerProvider(World world) {
        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) {
            return;
        }

        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers == null || !(providers.get(PROVIDER_KEY) instanceof MobMarkerProvider)) {
            installProvider(worldMapManager, PROVIDER_KEY, new MobMarkerProvider(mobMarkerManager, visibilityService));
        }
    }

    private void installProvider(WorldMapManager worldMapManager, String key, WorldMapManager.MarkerProvider provider) {
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers != null) {
            try {
                providers.put(key, provider);
                return;
            } catch (UnsupportedOperationException ignored) {
            }
        }

        worldMapManager.addMarkerProvider(key, provider);
    }

    private Path resolveDataDirectory() {
        Path dataDirectory = getDataDirectory();
        if (dataDirectory == null) {
            throw new IllegalStateException("MobMapMarkers data directory is unavailable");
        }

        Path legacyPluginDataDirectory = resolveLegacyPluginDataDirectory(dataDirectory, "MobMapMarkers");
        migrateLegacyDataDirectory(legacyPluginDataDirectory, dataDirectory);
        return dataDirectory;
    }

    private Path resolveLegacyPluginDataDirectory(Path dataDirectory, String directoryName) {
        Path modsDirectory = dataDirectory.getParent();
        if (modsDirectory == null || modsDirectory.getFileName() == null
                || !"mods".equalsIgnoreCase(modsDirectory.getFileName().toString())) {
            return dataDirectory;
        }

        Path worldRoot = modsDirectory.getParent();
        if (worldRoot == null) {
            return dataDirectory;
        }

        return worldRoot.resolve("plugins").resolve(directoryName);
    }

    private void migrateLegacyDataDirectory(Path legacyDataDirectory, Path quietDataDirectory) {
        if (legacyDataDirectory == null || quietDataDirectory == null || legacyDataDirectory.equals(quietDataDirectory)) {
            return;
        }

        try {
            if (!Files.exists(legacyDataDirectory)) {
                Files.createDirectories(quietDataDirectory);
                return;
            }

            Files.createDirectories(quietDataDirectory);
            try (var stream = Files.walk(legacyDataDirectory)) {
                for (Path source : (Iterable<Path>) stream::iterator) {
                    Path relative = legacyDataDirectory.relativize(source);
                    Path target = quietDataDirectory.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            try (var cleanup = Files.walk(legacyDataDirectory).sorted(Comparator.reverseOrder())) {
                cleanup.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException exception) {
            getLogger().at(Level.WARNING).log("[MobMapMarkers] Failed to migrate legacy data directory: " + exception.getMessage());
        }
    }
}
