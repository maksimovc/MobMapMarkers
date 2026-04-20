package dev.thenexusgates.mobmapmarkers.marker;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import dev.thenexusgates.mobmapmarkers.catalog.MobCatalogEntry;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerKeys;
import dev.thenexusgates.mobmapmarkers.util.MobFacingResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobMarkerManager {

    private final Map<String, List<MobMarkerSnapshot>> mobDataByWorld = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Vector3d>> playerPositionsByWorld = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, FacingState>> facingStateByWorld = new ConcurrentHashMap<>();
    private final Map<String, MobCatalogEntry> catalogByMobKey = new ConcurrentHashMap<>();

    public MobMarkerManager() {
    }

    public void setMobData(String worldName, Collection<MobMarkerSnapshot> snapshots) {
        List<MobMarkerSnapshot> copied = new ArrayList<>(snapshots != null ? snapshots.size() : 0);
        Map<Long, FacingState> previousFacing = facingStateByWorld.getOrDefault(worldName, Map.of());
        Map<Long, FacingState> nextFacing = new HashMap<>(copied.size());
        if (snapshots != null) {
            for (MobMarkerSnapshot snapshot : snapshots) {
                if (snapshot == null || snapshot.position() == null) {
                    continue;
                }

                FacingState previousState = previousFacing.get(snapshot.id());
                boolean facingRight = MobFacingResolver.resolveFacingRight(
                        snapshot.position(),
                        snapshot.rotation(),
                        previousState != null ? previousState.position() : null,
                        previousState == null || previousState.facingRight());
                nextFacing.put(snapshot.id(), new FacingState(snapshot.position(), facingRight));

                MobMarkerSnapshot preparedSnapshot = snapshot.facingRight() == facingRight
                        ? snapshot
                        : new MobMarkerSnapshot(
                                snapshot.id(),
                                snapshot.roleName(),
                                snapshot.nameTranslationKey(),
                                snapshot.displayName(),
                                snapshot.position(),
                                snapshot.rotation(),
                                facingRight);
                copied.add(preparedSnapshot);

                String mobKey = MobMarkerKeys.normalize(snapshot.roleName());
                if (mobKey != null) {
                    catalogByMobKey.put(mobKey, new MobCatalogEntry(
                            mobKey,
                            snapshot.roleName(),
                            snapshot.nameTranslationKey(),
                            snapshot.displayName(),
                            facingRight));
                }
            }
        }

        mobDataByWorld.put(worldName, copied.isEmpty() ? List.of() : List.copyOf(copied));
        facingStateByWorld.put(worldName, nextFacing.isEmpty() ? Map.of() : Map.copyOf(nextFacing));
    }

    public List<MobMarkerSnapshot> getMobData(String worldName) {
        List<MobMarkerSnapshot> data = mobDataByWorld.get(worldName);
        return data != null ? data : List.of();
    }

    public void setPlayerPositions(String worldName, Map<UUID, Vector3d> positions) {
        if (positions == null || positions.isEmpty()) {
            playerPositionsByWorld.put(worldName, Map.of());
            return;
        }

        Map<UUID, Vector3d> copied = new HashMap<>(positions.size());
        positions.forEach((uuid, position) -> {
            if (uuid != null && position != null) {
                copied.put(uuid, position);
            }
        });
        playerPositionsByWorld.put(worldName, copied.isEmpty() ? Map.of() : Map.copyOf(copied));
    }

    public Vector3d getPlayerPosition(String worldName, UUID uuid) {
        if (worldName == null || uuid == null) {
            return null;
        }

        Map<UUID, Vector3d> positions = playerPositionsByWorld.get(worldName);
        if (positions == null) {
            return null;
        }

        Vector3d position = positions.get(uuid);
        return position != null ? new Vector3d(position) : null;
    }

    public List<MobCatalogEntry> getCatalogEntries() {
        return List.copyOf(catalogByMobKey.values());
    }

    public record MobMarkerSnapshot(long id, String roleName, String nameTranslationKey, String displayName,
                                    Vector3d position, Vector3f rotation, boolean facingRight) {
    }

    private record FacingState(Vector3d position, boolean facingRight) {
    }
}
