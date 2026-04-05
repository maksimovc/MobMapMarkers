package dev.thenexusgates.mobmapmarkers;

import com.Landscaper.plugin.MinimapPlugin;
import com.Landscaper.plugin.config.MinimapConfig;
import com.Landscaper.plugin.external.MinimapApi;
import com.Landscaper.plugin.external.MultipleHudHelper;
import com.Landscaper.plugin.ui.MinimapHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

final class SimpleMinimapOverlayService {

    private static final Logger LOGGER = Logger.getLogger(SimpleMinimapOverlayService.class.getName());
    private static final long RECONCILE_INTERVAL_MS = 1000L;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MobMapMarkers-SimpleMinimap");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final MobMarkerManager manager;
    private final Map<UUID, ScheduledFuture<?>> replacedTickTasks = new ConcurrentHashMap<>();

    private volatile MinimapPlugin plugin;
    private volatile Field pluginField;
    private volatile Field hudsField;
    private volatile Field tickTasksField;
    private volatile Field multipleHudHelperField;
    private volatile Method initializePlayerHudMethod;

    SimpleMinimapOverlayService(MobMarkerManager manager) {
        this.manager = manager;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(this::reconcile, 0L, RECONCILE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("[MobMapMarkers] SimpleMinimap direct HUD integration started.");
    }

    void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        replacedTickTasks.values().forEach(future -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        });
        replacedTickTasks.clear();
        scheduler.shutdownNow();
        plugin = null;
        LOGGER.info("[MobMapMarkers] SimpleMinimap direct HUD integration stopped.");
    }

    void removeViewer(UUID viewerUuid) {
        if (viewerUuid == null) {
            return;
        }

        ScheduledFuture<?> future = replacedTickTasks.remove(viewerUuid);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }

        MinimapPlugin minimapPlugin = plugin;
        if (minimapPlugin == null) {
            return;
        }

        try {
            getTickTasks(minimapPlugin).entrySet().removeIf(entry -> {
                PlayerRef playerRef = entry.getKey();
                ScheduledFuture<?> tickFuture = entry.getValue();
                boolean matches = playerRef != null && viewerUuid.equals(playerRef.getUuid());
                if (matches && tickFuture != null && !tickFuture.isCancelled()) {
                    tickFuture.cancel(false);
                }
                return matches;
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warning("[MobMapMarkers] Failed to cancel SimpleMinimap tick for disconnected player "
                    + viewerUuid + ": " + e.getMessage());
        }
    }

    private void reconcile() {
        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        if (!SimpleMinimapCompat.isEnabled(config)) {
            return;
        }

        try {
            MinimapPlugin minimapPlugin = resolvePlugin();
            if (minimapPlugin == null) {
                return;
            }

            Map<PlayerRef, MinimapHud> huds = getHuds(minimapPlugin);
            if (huds.isEmpty()) {
                return;
            }

            for (Map.Entry<PlayerRef, MinimapHud> entry : huds.entrySet()) {
                PlayerRef playerRef = entry.getKey();
                MinimapHud currentHud = entry.getValue();
                if (playerRef == null || currentHud == null || currentHud instanceof MobMapMarkersMinimapHud) {
                    continue;
                }

                MinimapApi api = MinimapApi.getOrNull();
                if (api == null || !api.isMinimapEnabled(playerRef)) {
                    continue;
                }

                installDirectHud(minimapPlugin, playerRef, currentHud, huds);
            }
        } catch (RejectedExecutionException e) {
            LOGGER.fine("[MobMapMarkers] SimpleMinimap reconcile skipped because the scheduler is shutting down.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warning("[MobMapMarkers] SimpleMinimap direct integration reconcile failed: " + e.getMessage());
        }
    }

    private void installDirectHud(MinimapPlugin minimapPlugin, PlayerRef playerRef, MinimapHud currentHud,
                                  Map<PlayerRef, MinimapHud> huds) throws ReflectiveOperationException {
        MinimapConfig minimapConfig = minimapPlugin.getPlayerConfig(playerRef);
        MobMapMarkersMinimapHud mobHud = new MobMapMarkersMinimapHud(playerRef, minimapPlugin, minimapConfig, manager);
        if (currentHud.isWailaEnabled()) {
            mobHud.setWailaEnabled(true);
        }

        MultipleHudHelper multipleHudHelper = getMultipleHudHelper(minimapPlugin);
        if (multipleHudHelper != null) {
            multipleHudHelper.removeHudWithRetries(playerRef);
        }

        huds.put(playerRef, mobHud);
        cancelExistingTickTask(minimapPlugin, playerRef);
        initializePlayerHud(minimapPlugin, playerRef, mobHud, minimapConfig);

        ScheduledFuture<?> replacementTick = getTickTasks(minimapPlugin).get(playerRef);
        if (replacementTick != null) {
            replacedTickTasks.put(playerRef.getUuid(), replacementTick);
        }

        LOGGER.info("[MobMapMarkers] SimpleMinimap HUD replaced for player " + playerRef.getUuid());
    }

    private synchronized void initializePlayerHud(MinimapPlugin minimapPlugin, PlayerRef playerRef, MinimapHud mobHud,
                                     MinimapConfig minimapConfig) throws ReflectiveOperationException {
        Method method = initializePlayerHudMethod;
        if (method == null) {
            method = MinimapPlugin.class.getDeclaredMethod(
                    "initializePlayerHud",
                    PlayerRef.class,
                    MinimapHud.class,
                    MinimapConfig.class);
            method.setAccessible(true);
            initializePlayerHudMethod = method;
        }
        method.invoke(minimapPlugin, playerRef, mobHud, minimapConfig);
    }

    private void cancelExistingTickTask(MinimapPlugin minimapPlugin, PlayerRef playerRef) throws ReflectiveOperationException {
        ScheduledFuture<?> existing = getTickTasks(minimapPlugin).remove(playerRef);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel(false);
        }

        UUID viewerUuid = playerRef.getUuid();
        if (viewerUuid == null) {
            return;
        }

        ScheduledFuture<?> replaced = replacedTickTasks.remove(viewerUuid);
        if (replaced != null && !replaced.isCancelled()) {
            replaced.cancel(false);
        }
    }

    private synchronized MinimapPlugin resolvePlugin() throws ReflectiveOperationException {
        if (plugin != null) {
            return plugin;
        }

        MinimapApi api = MinimapApi.getOrNull();
        if (api == null) {
            return null;
        }

        Field field = pluginField;
        if (field == null) {
            field = MinimapApi.class.getDeclaredField("plugin");
            field.setAccessible(true);
            pluginField = field;
        }

        plugin = (MinimapPlugin) field.get(api);
        return plugin;
    }

    @SuppressWarnings("unchecked")
    private synchronized Map<PlayerRef, MinimapHud> getHuds(MinimapPlugin minimapPlugin) throws ReflectiveOperationException {
        Field field = hudsField;
        if (field == null) {
            field = MinimapPlugin.class.getDeclaredField("huds");
            field.setAccessible(true);
            hudsField = field;
        }
        return (Map<PlayerRef, MinimapHud>) field.get(minimapPlugin);
    }

    @SuppressWarnings("unchecked")
    private synchronized Map<PlayerRef, ScheduledFuture<?>> getTickTasks(MinimapPlugin minimapPlugin) throws ReflectiveOperationException {
        Field field = tickTasksField;
        if (field == null) {
            field = MinimapPlugin.class.getDeclaredField("tickTasks");
            field.setAccessible(true);
            tickTasksField = field;
        }
        return (Map<PlayerRef, ScheduledFuture<?>>) field.get(minimapPlugin);
    }

    private synchronized MultipleHudHelper getMultipleHudHelper(MinimapPlugin minimapPlugin) throws ReflectiveOperationException {
        Field field = multipleHudHelperField;
        if (field == null) {
            field = MinimapPlugin.class.getDeclaredField("multipleHudHelper");
            field.setAccessible(true);
            multipleHudHelperField = field;
        }
        return (MultipleHudHelper) field.get(minimapPlugin);
    }
}