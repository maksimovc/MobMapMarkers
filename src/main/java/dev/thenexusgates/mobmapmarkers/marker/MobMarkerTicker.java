package dev.thenexusgates.mobmapmarkers.marker;

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
import com.hypixel.hytale.server.npc.role.Role;
import dev.thenexusgates.mobmapmarkers.catalog.MobMarkerNames;
import dev.thenexusgates.mobmapmarkers.catalog.MobPortraitMatcher;
import dev.thenexusgates.mobmapmarkers.tracking.LivePlayerTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class MobMarkerTicker {

    private static final Archetype<EntityStore> NPC_QUERY = Archetype.of(
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MobMapMarkers-MobTicker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, String> displayNameByRoleName = new ConcurrentHashMap<>();
    private final MobMarkerManager manager;
    private final long intervalMs;

    public MobMarkerTicker(MobMarkerManager manager, int intervalMs) {
        this.manager = manager;
        this.intervalMs = Math.max(250L, intervalMs);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long initialDelayMs = Math.min(1000L, intervalMs);
        scheduler.scheduleAtFixedRate(this::tick, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        scheduler.shutdownNow();
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
            } catch (RejectedExecutionException e) {
            } catch (RuntimeException e) {
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

            Store<EntityStore> store = entityStore.getStore();
            if (store == null) {
                manager.setMobData(world.getName(), List.of());
                return;
            }

            List<MobMarkerManager.MobMarkerSnapshot> snapshots = new ArrayList<>();
                BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> snapshotCollector =
                    (chunk, commandBuffer) -> collectMobSnapshots(chunk, snapshots);
            store.forEachChunk(
                    NPC_QUERY,
                    snapshotCollector);
            manager.setMobData(world.getName(), snapshots);
        } catch (RuntimeException e) {
        }
    }

    private void collectMobSnapshots(
            ArchetypeChunk<EntityStore> chunk,
            List<MobMarkerManager.MobMarkerSnapshot> snapshots) {
        int size = chunk.size();
        for (int index = 0; index < size; index++) {
            try {
                MobMarkerManager.MobMarkerSnapshot snapshot = extractSnapshot(chunk, index);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } catch (RuntimeException e) {
            }
        }
    }

    private MobMarkerManager.MobMarkerSnapshot extractSnapshot(ArchetypeChunk<EntityStore> chunk, int index) {
        NPCEntity npcEntity = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return null;
        }

        String roleName = npcEntity.getRoleName();
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        if (MobPortraitMatcher.isExcludedRole(roleName)) {
            return null;
        }

        Role role = npcEntity.getRole();
        String nameTranslationKey = role != null ? role.getNameTranslationKey() : null;
        if (nameTranslationKey == null || nameTranslationKey.isBlank()) {
            nameTranslationKey = "server.npcRoles." + roleName + ".name";
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return null;
        }

        TransformComponent transformComponent = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transformComponent == null || transformComponent.getPosition() == null) {
            return null;
        }

        Vector3d position = new Vector3d(transformComponent.getPosition());
        Vector3f rotation = transformComponent.getRotation() != null
                ? new Vector3f(transformComponent.getRotation())
                : null;
        return new MobMarkerManager.MobMarkerSnapshot(
                ref.getIndex(),
                roleName,
                nameTranslationKey,
                resolveDisplayName(roleName),
                position,
                rotation,
                true);
    }

    private String resolveDisplayName(String roleName) {
        return displayNameByRoleName.computeIfAbsent(roleName, MobMarkerNames::formatRoleName);
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
        } catch (RuntimeException e) {
        }
    }
}
