package dev.thenexusgates.mobmapmarkers.catalog;

import dev.thenexusgates.mobmapmarkers.util.HytaleInstallLocator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class HytaleNpcPortraitResolver {

    private static final Logger LOGGER = Logger.getLogger(HytaleNpcPortraitResolver.class.getName());
    private static final String MEMORIES_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String PNG_SUFFIX = ".png";
    private static final String NOT_FOUND = "!";

    private static final Set<String> AVAILABLE_PORTRAITS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> RESOLVED_BY_ROLE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TOKENS_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PORTRAIT_BYTES = new ConcurrentHashMap<>();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);
    private static final AtomicBoolean MISSING_ASSETS_ZIP_LOGGED = new AtomicBoolean(false);

    private HytaleNpcPortraitResolver() {
    }

    public static void prewarm() {
        ensureIndexed();
    }

    public static String resolvePortraitName(String roleName) {
        String entryName = resolveEntryName(roleName);
        if (entryName == null || NOT_FOUND.equals(entryName)) {
            return null;
        }

        return entryName.substring(MEMORIES_PREFIX.length(), entryName.length() - PNG_SUFFIX.length());
    }

    public static Set<String> getAvailablePortraitNames() {
        ensureIndexed();
        return Set.copyOf(AVAILABLE_PORTRAITS);
    }

    public static byte[] loadPortraitPngByPortraitName(String portraitName) {
        if (portraitName == null || portraitName.isBlank()) {
            return null;
        }

        String entryName = MEMORIES_PREFIX + portraitName + PNG_SUFFIX;
        Path zipPath = HytaleInstallLocator.resolveAssetsZipPath();
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

        String bestMatch = MobPortraitMatcher.findBestFuzzyPortrait(roleName, AVAILABLE_PORTRAITS, TOKENS_BY_PORTRAIT);
        if (bestMatch != null) {
            return MEMORIES_PREFIX + bestMatch + PNG_SUFFIX;
        }

        return null;
    }

    private static List<String> buildCandidates(String roleName) {
        return MobPortraitMatcher.buildCandidates(roleName);
    }

    private static String findBestFuzzyPortrait(String roleName) {
        return MobPortraitMatcher.findBestFuzzyPortrait(roleName, AVAILABLE_PORTRAITS, TOKENS_BY_PORTRAIT);
    }

    private static int scorePortraitMatch(List<String> roleTokens, List<String> portraitTokens, String portraitName) {
        return MobPortraitMatcher.scorePortraitMatch(roleTokens, portraitTokens, portraitName);
    }

    private static List<String> normalizedTokens(String name) {
        return MobPortraitMatcher.normalizedTokens(name);
    }

    private static String normalizeRole(String roleName) {
        return MobPortraitMatcher.normalizeRole(roleName);
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        Path zipPath = HytaleInstallLocator.resolveAssetsZipPath();
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
                    TOKENS_BY_PORTRAIT.put(portraitName, MobPortraitMatcher.normalizedTokens(portraitName));
                }
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to index official NPC portraits: " + e.getMessage());
        }
    }

    private static Path resolveAssetsZipPath() {
        Path zipPath = HytaleInstallLocator.resolveAssetsZipPath();
        if (zipPath != null) {
            return zipPath;
        }

        if (MISSING_ASSETS_ZIP_LOGGED.compareAndSet(false, true)) {
            LOGGER.warning("[MobMapMarkers] Could not locate Hytale Assets.zip for official NPC portraits; generated fallback icons will be used.");
        }
        return null;
    }
}
