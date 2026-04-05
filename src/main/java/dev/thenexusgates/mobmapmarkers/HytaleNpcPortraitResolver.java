package dev.thenexusgates.mobmapmarkers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class HytaleNpcPortraitResolver {

    private static final Logger LOGGER = Logger.getLogger(HytaleNpcPortraitResolver.class.getName());
    private static final String MEMORIES_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String PNG_SUFFIX = ".png";
    private static final String NOT_FOUND = "!";

    private static final Set<String> AVAILABLE_PORTRAITS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> RESOLVED_BY_ROLE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TOKENS_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PORTRAIT_BYTES = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_UNRESOLVED_ROLES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);
    private static final AtomicBoolean MISSING_ASSETS_ZIP_LOGGED = new AtomicBoolean(false);

    private static final Map<String, List<String>> ROLE_ALIASES = Map.ofEntries(
            Map.entry("Mosshorn_Plain", List.of("Mosshorn")),
            Map.entry("Bunny", List.of("Bunny", "Rabbit")),
            Map.entry("Rabbit", List.of("Rabbit", "Bunny"))
    );

    private static final Set<String> LEADING_PREFIXES = Set.of(
            "Temple", "Tamed", "Friendly", "Passive", "Companion", "Summoned");
    private static final Set<String> TRAILING_SUFFIXES = Set.of(
            "Wander", "Wandering", "Patrol", "Static", "Friendly", "Passive", "Alerted", "Sleeping");

    private static volatile Path assetsZipPath;

    private HytaleNpcPortraitResolver() {
    }

    static String resolvePortraitName(String roleName) {
        String entryName = resolveEntryName(roleName);
        if (entryName == null || NOT_FOUND.equals(entryName)) {
            return null;
        }

        return entryName.substring(MEMORIES_PREFIX.length(), entryName.length() - PNG_SUFFIX.length());
    }

    static Set<String> getAvailablePortraitNames() {
        ensureIndexed();
        return Set.copyOf(AVAILABLE_PORTRAITS);
    }

    static byte[] loadPortraitPngByPortraitName(String portraitName) {
        if (portraitName == null || portraitName.isBlank()) {
            return null;
        }

        String entryName = MEMORIES_PREFIX + portraitName + PNG_SUFFIX;
        Path zipPath = resolveAssetsZipPath();
        if (zipPath == null) {
            return null;
        }

        byte[] cached = PORTRAIT_BYTES.get(entryName);
        if (cached != null) {
            return cached.clone();
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] bytes = inputStream.readAllBytes();
                PORTRAIT_BYTES.put(entryName, bytes);
                return bytes.clone();
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to read NPC portrait from Assets.zip: " + e.getMessage());
            return null;
        }
    }

    private static String resolveEntryName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return NOT_FOUND;
        }

        ensureIndexed();
        if (AVAILABLE_PORTRAITS.isEmpty()) {
            return NOT_FOUND;
        }

        String cached = RESOLVED_BY_ROLE.get(roleName);
        if (cached != null) {
            return cached;
        }

        String resolved = findEntryName(roleName);
        if (resolved == null && LOGGED_UNRESOLVED_ROLES.add(roleName)) {
            LOGGER.info("[MobMapMarkers] No official NPC portrait match for role: " + roleName);
        }

        String result = resolved != null ? resolved : NOT_FOUND;
        RESOLVED_BY_ROLE.put(roleName, result);
        return result;
    }

    private static String findEntryName(String roleName) {
        for (String candidate : buildCandidates(roleName)) {
            if (AVAILABLE_PORTRAITS.contains(candidate)) {
                return MEMORIES_PREFIX + candidate + PNG_SUFFIX;
            }
        }

        String bestMatch = findBestFuzzyPortrait(roleName);
        if (bestMatch != null) {
            return MEMORIES_PREFIX + bestMatch + PNG_SUFFIX;
        }

        return null;
    }

    private static List<String> buildCandidates(String roleName) {
        String normalized = normalizeRole(roleName);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        List<String> aliases = ROLE_ALIASES.get(normalized);
        if (aliases != null) {
            candidates.addAll(aliases);
        }

        List<String> parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        while (!parts.isEmpty() && LEADING_PREFIXES.contains(parts.get(0))) {
            parts.remove(0);
            if (!parts.isEmpty()) {
                candidates.add(String.join("_", parts));
            }
        }

        parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        while (!parts.isEmpty() && TRAILING_SUFFIXES.contains(parts.get(parts.size() - 1))) {
            parts.remove(parts.size() - 1);
            if (!parts.isEmpty()) {
                candidates.add(String.join("_", parts));
            }
        }

        if (normalized.endsWith("_Plain")) {
            candidates.add(normalized.substring(0, normalized.length() - "_Plain".length()));
        }

        if (normalized.startsWith("Wolf_")) {
            candidates.add("Wolf_Black");
            candidates.add("Wolf_White");
        }

        if (normalized.startsWith("Pig_") && normalized.contains("Boar")) {
            candidates.add("Pig_Wild");
            candidates.add("Pig_Wild_Piglet");
        }

        String[] normalizedParts = normalized.split("_");
        if (normalizedParts.length > 1) {
            for (int start = 1; start < normalizedParts.length; start++) {
                candidates.add(String.join("_", Arrays.copyOfRange(normalizedParts, start, normalizedParts.length)));
            }
            for (int end = normalizedParts.length - 1; end > 0; end--) {
                candidates.add(String.join("_", Arrays.copyOfRange(normalizedParts, 0, end)));
            }
        }

        return List.copyOf(candidates);
    }

    private static String findBestFuzzyPortrait(String roleName) {
        List<String> roleTokens = normalizedTokens(normalizeRole(roleName));
        if (roleTokens.isEmpty()) {
            return null;
        }

        int bestScore = Integer.MIN_VALUE;
        String bestPortrait = null;
        for (String portraitName : AVAILABLE_PORTRAITS) {
            List<String> portraitTokens = TOKENS_BY_PORTRAIT.get(portraitName);
            if (portraitTokens == null || portraitTokens.isEmpty()) {
                continue;
            }

            int score = scorePortraitMatch(roleTokens, portraitTokens, portraitName);
            if (score > bestScore) {
                bestScore = score;
                bestPortrait = portraitName;
            }
        }

        return bestScore >= 8 ? bestPortrait : null;
    }

    private static int scorePortraitMatch(List<String> roleTokens, List<String> portraitTokens, String portraitName) {
        int overlap = 0;
        Map<String, Integer> portraitCounts = new HashMap<>();
        for (String token : portraitTokens) {
            portraitCounts.merge(token, 1, Integer::sum);
        }

        for (String token : roleTokens) {
            Integer count = portraitCounts.get(token);
            if (count != null && count > 0) {
                overlap++;
                portraitCounts.put(token, count - 1);
            }
        }

        if (overlap == 0) {
            return Integer.MIN_VALUE;
        }

        int score = overlap * 10;
        int roleRemainder = roleTokens.size() - overlap;
        int portraitRemainder = portraitTokens.size() - overlap;
        score -= roleRemainder * 3;
        score -= portraitRemainder * 2;

        String normalizedRole = normalizeRole(String.join("_", roleTokens));
        if (portraitName.equals(normalizedRole)) {
            score += 50;
        } else if (portraitName.endsWith(normalizedRole) || normalizedRole.endsWith(portraitName)) {
            score += 20;
        }

        return score;
    }

    private static List<String> normalizedTokens(String name) {
        List<String> tokens = new ArrayList<>();
        for (String token : name.split("_")) {
            if (token == null || token.isBlank()) {
                continue;
            }

            if (LEADING_PREFIXES.contains(token) || TRAILING_SUFFIXES.contains(token)) {
                continue;
            }

            tokens.add(token.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tokens);
    }

    private static String normalizeRole(String roleName) {
        String[] rawParts = roleName.replace('-', '_').split("_");
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }

            String part = rawPart.toLowerCase(Locale.ROOT);
            parts.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join("_", parts);
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        Path zipPath = resolveAssetsZipPath();
        if (zipPath == null) {
            return;
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.startsWith(MEMORIES_PREFIX) || !name.endsWith(PNG_SUFFIX)) {
                    continue;
                }

                String portraitName = name.substring(MEMORIES_PREFIX.length(), name.length() - PNG_SUFFIX.length());
                if (!portraitName.isBlank()) {
                    AVAILABLE_PORTRAITS.add(portraitName);
                    TOKENS_BY_PORTRAIT.put(portraitName, normalizedTokens(portraitName));
                }
            }

            LOGGER.info("[MobMapMarkers] Indexed official NPC portraits: " + AVAILABLE_PORTRAITS.size());
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to index official NPC portraits: " + e.getMessage());
        }
    }

    private static Path resolveAssetsZipPath() {
        Path cached = assetsZipPath;
        if (cached != null && Files.exists(cached)) {
            return cached;
        }

        for (Path candidate : buildAssetsZipCandidates()) {
            if (candidate != null && Files.exists(candidate)) {
                assetsZipPath = candidate;
                return candidate;
            }
        }

        if (MISSING_ASSETS_ZIP_LOGGED.compareAndSet(false, true)) {
            LOGGER.warning("[MobMapMarkers] Could not locate Hytale Assets.zip for official NPC portraits; generated fallback icons will be used.");
        }
        return null;
    }

    private static List<Path> buildAssetsZipCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("Assets.zip"));

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            Path userDirPath = Paths.get(userDir).toAbsolutePath().normalize();
            candidates.add(userDirPath.resolve("Assets.zip"));
            for (Path path = userDirPath; path != null; path = path.getParent()) {
                candidates.add(path.resolve("Assets.zip"));
                candidates.add(path.resolve("install/release/package/game/latest/Assets.zip"));
                candidates.add(path.resolve("release/package/game/latest/Assets.zip"));
                candidates.add(path.resolve("game/latest/Assets.zip"));
            }
        }

        return List.copyOf(candidates);
    }
}
