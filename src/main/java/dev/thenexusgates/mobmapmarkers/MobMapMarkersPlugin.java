package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class MobMapMarkersPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "mobMapMarkers";
    private static final String VERSION = "1.5.0";

    private static MobMapMarkersPlugin instance;
    private static MobMapMarkersConfig config;
    private static final Map<UUID, Player> ACTIVE_PLAYERS = new ConcurrentHashMap<>();

    private MobMarkerManager mobMarkerManager;
    private MobMarkerTicker mobMarkerTicker;
    private SimpleMinimapOverlayService simpleMinimapOverlayService;

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

    static Player getActivePlayer(UUID playerUuid) {
        return playerUuid == null ? null : ACTIVE_PLAYERS.get(playerUuid);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[MobMapMarkers] Starting v" + VERSION);

        MobMapAssetPack.init();
        LivePlayerTracker.register();
        config = MobMapMarkersConfig.load(
            MobMapAssetPack.getDataRoot().resolve("mobmapmarkers-config.json"));

        boolean simpleMinimapCompat = SimpleMinimapCompat.isEnabled(config);
        if (config.showMobMarkersOnSimpleMinimap) {
            getLogger().at(Level.INFO).log(simpleMinimapCompat
                    ? "[MobMapMarkers] SimpleMinimap direct HUD integration enabled."
                    : "[MobMapMarkers] SimpleMinimap compatibility requested, but SimpleMinimap was not detected.");
        }

        MobMapAssetPack.refreshFallbackIcons(config.mobMarkerSize, config.mobIconContentScalePercent);

        mobMarkerManager = new MobMarkerManager();
        mobMarkerTicker = new MobMarkerTicker(mobMarkerManager, config.scanIntervalMs);
        mobMarkerTicker.start();
        if (simpleMinimapCompat) {
            simpleMinimapOverlayService = new SimpleMinimapOverlayService(mobMarkerManager);
            simpleMinimapOverlayService.start();
        }

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
            }
        });

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef() != null ? event.getPlayerRef().getUuid() : null;
            if (uuid != null) {
                ACTIVE_PLAYERS.remove(uuid);
                LivePlayerTracker.remove(uuid);
                MobMapAssetPack.clearViewer(uuid);
                if (simpleMinimapOverlayService != null) {
                    simpleMinimapOverlayService.removeViewer(uuid);
                }
            }
        });

        getLogger().at(Level.INFO).log("[MobMapMarkers] Ready.");
    }

    @Override
    protected void shutdown() {
        if (simpleMinimapOverlayService != null) {
            simpleMinimapOverlayService.shutdown();
            simpleMinimapOverlayService = null;
        }

        if (mobMarkerTicker != null) {
            mobMarkerTicker.shutdown();
            mobMarkerTicker = null;
        }

        ACTIVE_PLAYERS.clear();
        LivePlayerTracker.shutdown();
        MobMapAssetPack.shutdown();
        mobMarkerManager = null;
        config = null;
        getLogger().at(Level.INFO).log("[MobMapMarkers] Stopped.");
    }

    private void registerProvider(World world) {
        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) {
            return;
        }

        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers == null || !(providers.get(PROVIDER_KEY) instanceof MobMarkerProvider)) {
            installProvider(worldMapManager, PROVIDER_KEY, new MobMarkerProvider(mobMarkerManager));
            getLogger().at(Level.INFO).log("[MobMapMarkers] Provider registered: " + world.getName());
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
}
