package dev.thenexusgates.mobmapmarkers.filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;

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

public final class MobMarkerFilterStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path playersRoot;
    private final Map<UUID, MobMarkerFilterProfile> profiles = new ConcurrentHashMap<>();

    public MobMarkerFilterStore(Path dataRoot) {
        this.playersRoot = dataRoot.resolve("player-filters");
    }

    public void preload(UUID playerUuid) {
        if (playerUuid != null) {
            getProfile(playerUuid);
        }
    }

    public void unload(UUID playerUuid) {
        if (playerUuid != null) {
            profiles.remove(playerUuid);
        }
    }

    public MobMarkerFilterRule resolveRule(UUID playerUuid, String mobKey, MobMapMarkersConfig config) {
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

    public void setSurface(UUID playerUuid, String mobKey, MobMarkerSurface surface, boolean enabled,
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

    public void setAllSurfaces(UUID playerUuid, Collection<String> mobKeys, boolean enabled, MobMapMarkersConfig config) {
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

    public void setSurfaceForMany(UUID playerUuid, Collection<String> mobKeys, MobMarkerSurface surface,
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

    public Set<String> getExplicitMobKeys(UUID playerUuid) {
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
        }
    }

    private Path profilePath(UUID playerUuid) {
        return playersRoot.resolve(playerUuid + ".json");
    }
}