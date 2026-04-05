package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

final class MobMarkerTicker {

    private static final Logger LOGGER = Logger.getLogger(MobMarkerTicker.class.getName());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final MobMarkerManager manager;
    private final long intervalMs;

    MobMarkerTicker(MobMarkerManager manager, int intervalMs) {
        this.manager = manager;
        this.intervalMs = Math.max(250L, intervalMs);
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long initialDelayMs = Math.min(1000L, intervalMs);
        scheduler.scheduleAtFixedRate(this::tick, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("[MobMapMarkers] Mob marker ticker started: intervalMs=" + intervalMs);
    }

    private void tick() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        Collection<World> worlds = universe.getWorlds().values();
        for (World world : worlds) {
            if (world == null || !world.isAlive()) {
                continue;
            }

            Collection<PlayerRef> playerRefs = world.getPlayerRefs();
            if (playerRefs == null || playerRefs.isEmpty()) {
                manager.setMobData(world.getName(), List.of());
                manager.setPlayerPositions(world.getName(), Map.of());
                continue;
            }

            try {
                world.execute(() -> {
                    if (!world.isAlive()) {
                        return;
                    }

                    scanWorld(world);
                    cachePlayerPositions(world);
                });
            } catch (Exception e) {
                LOGGER.warning("[MobMapMarkers] Failed to schedule mob scan for world "
                        + world.getName() + ": " + e.getMessage());
            }
        }
    }

    private void scanWorld(World world) {
        try {
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) {
                manager.setMobData(world.getName(), List.of());
                return;
            }

            Store store = entityStore.getStore();
            if (store == null) {
                manager.setMobData(world.getName(), List.of());
                return;
            }

            List<MobMarkerManager.MobMarkerSnapshot> snapshots = new ArrayList<>();
            Archetype npcArchetype = Archetype.of(NPCEntity.getComponentType());
            store.forEachChunk(
                    npcArchetype,
                    (BiConsumer<ArchetypeChunk, CommandBuffer>) (chunk, commandBuffer) ->
                            collectMobSnapshots(store, chunk, snapshots));
            manager.setMobData(world.getName(), snapshots);
        } catch (Exception e) {
            LOGGER.warning("[MobMapMarkers] Mob scan failed for world "
                    + world.getName() + ": " + e.getMessage());
        }
    }

    private void collectMobSnapshots(Store store, ArchetypeChunk chunk, List<MobMarkerManager.MobMarkerSnapshot> snapshots) {
        int size = chunk.size();
        for (int index = 0; index < size; index++) {
            try {
                MobMarkerManager.MobMarkerSnapshot snapshot = extractSnapshot(store, chunk, index);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } catch (Exception e) {
                LOGGER.fine("[MobMapMarkers] Skipped NPC during mob scan: " + e.getMessage());
            }
        }
    }

    private MobMarkerManager.MobMarkerSnapshot extractSnapshot(Store store, ArchetypeChunk chunk, int index) {
        NPCEntity npcEntity = (NPCEntity) chunk.getComponent(index, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return null;
        }

        String roleName = npcEntity.getRoleName();
        if (roleName == null || roleName.isBlank()) {
            return null;
        }

        Ref ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return null;
        }

        TransformComponent transformComponent = (TransformComponent) store.getComponent(
                ref,
                TransformComponent.getComponentType());
        if (transformComponent == null || transformComponent.getPosition() == null) {
            return null;
        }

        Vector3d position = new Vector3d(transformComponent.getPosition());
        Vector3f rotation = transformComponent.getRotation() != null
                ? new Vector3f(transformComponent.getRotation())
                : null;
        return new MobMarkerManager.MobMarkerSnapshot(
                String.valueOf(ref.getIndex()),
                roleName,
                formatRoleName(roleName),
                position,
                rotation,
                true);
    }

    private void cachePlayerPositions(World world) {
        try {
            Map<UUID, Vector3d> positions = new HashMap<>();
            Collection<PlayerRef> playerRefs = world.getPlayerRefs();
            if (playerRefs != null) {
                for (PlayerRef ref : playerRefs) {
                    if (ref == null || ref.getUuid() == null) {
                        continue;
                    }

                    Vector3d position = LivePlayerTracker.resolvePosition(ref);
                    if (position != null) {
                        positions.put(ref.getUuid(), position);
                    }
                }
            }

            manager.setPlayerPositions(world.getName(), positions);
        } catch (Exception e) {
            LOGGER.warning("[MobMapMarkers] Failed to cache player positions for world "
                    + world.getName() + ": " + e.getMessage());
        }
    }

    private static String formatRoleName(String roleName) {
        String[] parts = roleName.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }

        return builder.isEmpty() ? "Mob" : builder.toString();
    }
}
