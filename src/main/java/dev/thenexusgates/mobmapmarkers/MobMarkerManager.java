package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MobMarkerManager {

    private final Map<String, List<MobMarkerSnapshot>> mobDataByWorld = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Vector3d>> playerPositionsByWorld = new ConcurrentHashMap<>();
    private final Map<String, Map<String, FacingState>> facingStateByWorld = new ConcurrentHashMap<>();

    void setMobData(String worldName, Collection<MobMarkerSnapshot> snapshots) {
        List<MobMarkerSnapshot> copied = new ArrayList<>();
        Map<String, FacingState> previousFacing = facingStateByWorld.getOrDefault(worldName, Map.of());
        Map<String, FacingState> nextFacing = new ConcurrentHashMap<>();
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
                nextFacing.put(snapshot.id(), new FacingState(new Vector3d(snapshot.position()), facingRight));
                copied.add(new MobMarkerSnapshot(
                        snapshot.id(),
                        snapshot.roleName(),
                        snapshot.displayName(),
                        new Vector3d(snapshot.position()),
                        snapshot.rotation() != null ? new Vector3f(snapshot.rotation()) : null,
                        facingRight));
            }
        }

        mobDataByWorld.put(worldName, List.copyOf(copied));
        facingStateByWorld.put(worldName, Map.copyOf(nextFacing));
    }

    List<MobMarkerSnapshot> getMobData(String worldName) {
        List<MobMarkerSnapshot> data = mobDataByWorld.get(worldName);
        return data != null ? data : List.of();
    }

    void setPlayerPositions(String worldName, Map<UUID, Vector3d> positions) {
        Map<UUID, Vector3d> copied = new ConcurrentHashMap<>();
        if (positions != null) {
            positions.forEach((uuid, position) -> {
                if (uuid != null && position != null) {
                    copied.put(uuid, new Vector3d(position));
                }
            });
        }
        playerPositionsByWorld.put(worldName, Map.copyOf(copied));
    }

    Vector3d getPlayerPosition(String worldName, UUID uuid) {
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

    record MobMarkerSnapshot(String id, String roleName, String displayName, Vector3d position, Vector3f rotation,
                             boolean facingRight) {
    }

    private record FacingState(Vector3d position, boolean facingRight) {
    }
}
