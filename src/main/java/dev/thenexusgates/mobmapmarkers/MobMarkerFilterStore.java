package dev.thenexusgates.mobmapmarkers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class MobMarkerFilterStore {

    private static final Logger LOGGER = Logger.getLogger(MobMarkerFilterStore.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path playersRoot;
    private final Map<UUID, MobMarkerFilterProfile> profiles = new ConcurrentHashMap<>();

    MobMarkerFilterStore(Path dataRoot) {
        this.playersRoot = dataRoot.resolve("player-filters");
    }

    void preload(UUID playerUuid) {
        if (playerUuid != null) {
            getProfile(playerUuid);
        }
    }

    void unload(UUID playerUuid) {
        if (playerUuid != null) {
            profiles.remove(playerUuid);
        }
    }

    MobMarkerFilterRule resolveRule(UUID playerUuid, String mobKey, MobMapMarkersConfig config) {
        String normalizedKey = MobMarkerKeys.normalize(mobKey);
        if (normalizedKey == null) {
            return MobMarkerFilterRule.defaults(config);
        }

        if (playerUuid == null) {
            return MobMarkerFilterRule.defaults(config);
        }

        MobMarkerFilterRule stored = getProfile(playerUuid).mobRules.get(normalizedKey);
        return stored != null ? stored : MobMarkerFilterRule.defaults(config);
    }

    void setSurface(UUID playerUuid, String mobKey, MobMarkerSurface surface, boolean enabled,
                    MobMapMarkersConfig config) {
        String normalizedKey = MobMarkerKeys.normalize(mobKey);
        if (playerUuid == null || normalizedKey == null || surface == null) {
            return;
        }

        MobMarkerFilterProfile profile = getProfile(playerUuid);
        MobMarkerFilterRule current = profile.mobRules.getOrDefault(normalizedKey, MobMarkerFilterRule.defaults(config));
        profile.mobRules.put(normalizedKey, current.with(surface, enabled));
        saveProfile(playerUuid, profile);
    }

    void setAllSurfaces(UUID playerUuid, Collection<String> mobKeys, boolean enabled, MobMapMarkersConfig config) {
        if (playerUuid == null || mobKeys == null || mobKeys.isEmpty()) {
            return;
        }

        MobMarkerFilterProfile profile = getProfile(playerUuid);
        boolean changed = false;
        for (String mobKey : mobKeys) {
            String normalizedKey = MobMarkerKeys.normalize(mobKey);
            if (normalizedKey == null) {
                continue;
            }

            MobMarkerFilterRule current = profile.mobRules.getOrDefault(normalizedKey, MobMarkerFilterRule.defaults(config));
            profile.mobRules.put(normalizedKey, current.all(enabled));
            changed = true;
        }

        if (changed) {
            saveProfile(playerUuid, profile);
        }
    }

    void setSurfaceForMany(UUID playerUuid, Collection<String> mobKeys, MobMarkerSurface surface,
                           boolean enabled, MobMapMarkersConfig config) {
        if (playerUuid == null || mobKeys == null || mobKeys.isEmpty() || surface == null) {
            return;
        }

        MobMarkerFilterProfile profile = getProfile(playerUuid);
        boolean changed = false;
        for (String mobKey : mobKeys) {
            String normalizedKey = MobMarkerKeys.normalize(mobKey);
            if (normalizedKey == null) {
                continue;
            }

            MobMarkerFilterRule current = profile.mobRules.getOrDefault(normalizedKey, MobMarkerFilterRule.defaults(config));
            profile.mobRules.put(normalizedKey, current.with(surface, enabled));
            changed = true;
        }

        if (changed) {
            saveProfile(playerUuid, profile);
        }
    }

    Set<String> getExplicitMobKeys(UUID playerUuid) {
        if (playerUuid == null) {
            return Set.of();
        }

        return Set.copyOf(getProfile(playerUuid).mobRules.keySet());
    }

    private MobMarkerFilterProfile getProfile(UUID playerUuid) {
        return profiles.computeIfAbsent(playerUuid, this::loadProfile);
    }

    private MobMarkerFilterProfile loadProfile(UUID playerUuid) {
        Path profilePath = profilePath(playerUuid);
        if (Files.notExists(profilePath)) {
            return new MobMarkerFilterProfile().normalize();
        }

        try (Reader reader = Files.newBufferedReader(profilePath)) {
            MobMarkerFilterProfile profile = GSON.fromJson(reader, MobMarkerFilterProfile.class);
            return profile == null ? new MobMarkerFilterProfile().normalize() : profile.normalize();
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("[MobMapMarkers] Failed to read player filter profile " + profilePath + ": " + e.getMessage());
            return new MobMarkerFilterProfile().normalize();
        }
    }

    private void saveProfile(UUID playerUuid, MobMarkerFilterProfile profile) {
        Path profilePath = profilePath(playerUuid);
        try {
            Files.createDirectories(playersRoot);
            try (Writer writer = Files.newBufferedWriter(profilePath)) {
                GSON.toJson(profile.normalize(), writer);
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to save player filter profile " + profilePath + ": " + e.getMessage());
        }
    }

    private Path profilePath(UUID playerUuid) {
        return playersRoot.resolve(playerUuid + ".json");
    }
}