package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MobMapMarkersPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "mobMapMarkers";
    private static final String VERSION = "1.0.1";

    private static MobMapMarkersPlugin instance;
    private static MobMapMarkersConfig config;

    private MobMarkerManager mobMarkerManager;
    private MobMarkerTicker mobMarkerTicker;

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

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[MobMapMarkers] Starting v" + VERSION);

        MobMapAssetPack.init();
        LivePlayerTracker.register();
        config = MobMapMarkersConfig.load(
                MobMapAssetPack.getPackRoot().resolve("mobmapmarkers-config.json"));

        MobMapAssetPack.refreshFallbackIcons(config.mobMarkerSize, config.mobIconContentScalePercent);

        if (config.prewarmOfficialIcons) {
            MobMapAssetPack.prewarmMobIcons(config.mobMarkerSize, config.mobIconContentScalePercent);
        }

        mobMarkerManager = new MobMarkerManager();
        mobMarkerTicker = new MobMarkerTicker(mobMarkerManager, config.scanIntervalMs);
        mobMarkerTicker.start();

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

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef() != null ? event.getPlayerRef().getUuid() : null;
            if (uuid != null) {
                LivePlayerTracker.remove(uuid);
            }
        });

        getLogger().at(Level.INFO).log("[MobMapMarkers] Ready.");
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
