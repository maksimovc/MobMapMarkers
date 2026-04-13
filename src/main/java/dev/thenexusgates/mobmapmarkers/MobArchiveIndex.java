package dev.thenexusgates.mobmapmarkers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class MobArchiveIndex {

    private static final Logger LOGGER = Logger.getLogger(MobArchiveIndex.class.getName());
    private static final String ROLE_PREFIX = "Server/NPC/Roles/";
    private static final String PORTRAIT_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String JSON_SUFFIX = ".json";
    private static final String PNG_SUFFIX = ".png";
    private static final String NOT_FOUND = "!";
    private static final Pattern NAME_TRANSLATION_KEY_PATTERN = Pattern.compile(
            "\\\"NameTranslationKey\\\"\\s*:\\s*\\{.*?\\\"Value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.DOTALL);

    private static final Map<String, MobCatalogEntry> CATALOG_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, PortraitSource> MOD_PORTRAITS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TOKENS_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PORTRAIT_BYTES = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_PORTRAIT_BY_ROLE = new ConcurrentHashMap<>();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);

    private MobArchiveIndex() {
    }

        static void prewarm() {
        ensureIndexed();
        }

    static List<MobCatalogEntry> getCatalogEntries() {
        ensureIndexed();
        return List.copyOf(CATALOG_BY_KEY.values());
    }

    static byte[] loadModPortraitPngByRoleName(String roleName) {
        ensureIndexed();
        String portraitName = resolvePortraitName(roleName);
        if (portraitName == null) {
            return null;
        }

        PortraitSource source = MOD_PORTRAITS.get(portraitName);
        if (source == null) {
            return null;
        }

        byte[] cached = PORTRAIT_BYTES.get(source.cacheKey());
        if (cached != null) {
            return cached.clone();
        }

        try (ZipFile zipFile = new ZipFile(source.archivePath().toFile())) {
            ZipEntry entry = zipFile.getEntry(source.entryName());
            if (entry == null) {
                return null;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] bytes = inputStream.readAllBytes();
                PORTRAIT_BYTES.put(source.cacheKey(), bytes);
                return bytes.clone();
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to load mod portrait " + portraitName + ": " + e.getMessage());
            return null;
        }
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        Path officialAssetsZip = resolveAssetsZipPath();
        if (officialAssetsZip != null) {
            indexRolesFromArchive(officialAssetsZip, false);
        }

        List<Path> modArchives = findSaveModArchives();
        for (Path archivePath : modArchives) {
            indexRolesFromArchive(archivePath, true);
            indexPortraitsFromArchive(archivePath);
        }

        LOGGER.info("[MobMapMarkers] Indexed archive role catalog entries: " + CATALOG_BY_KEY.size()
                + ", mod portraits: " + MOD_PORTRAITS.size());
    }

    private static void indexRolesFromArchive(Path archivePath, boolean fromMods) {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(ROLE_PREFIX) && entry.getName().endsWith(JSON_SUFFIX))
                    .forEach(entry -> addRoleEntry(zipFile, entry, fromMods));
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to scan role archive " + archivePath.getFileName() + ": " + e.getMessage());
        }
    }

    private static void addRoleEntry(ZipFile zipFile, ZipEntry entry, boolean fromMods) {
        String roleName = fileStem(entry.getName());
        String mobKey = MobMarkerKeys.normalize(roleName);
        if (mobKey == null) {
            return;
        }

        String nameTranslationKey = null;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = NAME_TRANSLATION_KEY_PATTERN.matcher(json);
            if (matcher.find()) {
                nameTranslationKey = matcher.group(1);
            }
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed reading role json " + entry.getName() + ": " + e.getMessage());
        }

        String fallbackDisplayName = MobMarkerNames.formatRoleName(roleName);
        MobCatalogEntry next = new MobCatalogEntry(
                mobKey,
                roleName,
                nameTranslationKey == null || nameTranslationKey.isBlank()
                        ? "server.npcRoles." + roleName + ".name"
                        : nameTranslationKey,
                fallbackDisplayName,
                false);

        CATALOG_BY_KEY.merge(mobKey, next, (existing, candidate) -> fromMods ? candidate : existing);
    }

    private static void indexPortraitsFromArchive(Path archivePath) {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(PORTRAIT_PREFIX) && entry.getName().endsWith(PNG_SUFFIX))
                    .forEach(entry -> {
                        String portraitName = fileStem(entry.getName());
                        if (portraitName == null || portraitName.isBlank()) {
                            return;
                        }
                        MOD_PORTRAITS.putIfAbsent(portraitName, new PortraitSource(
                                archivePath,
                                entry.getName(),
                                archivePath.toAbsolutePath().normalize() + "::" + entry.getName()));
                        TOKENS_BY_PORTRAIT.putIfAbsent(portraitName, MobPortraitMatcher.normalizedTokens(portraitName));
                    });
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to scan portrait archive " + archivePath.getFileName() + ": " + e.getMessage());
        }
    }

    private static String resolvePortraitName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }

        String cached = RESOLVED_PORTRAIT_BY_ROLE.get(roleName);
        if (cached != null) {
            return NOT_FOUND.equals(cached) ? null : cached;
        }

        for (String candidate : buildCandidates(roleName)) {
            if (MOD_PORTRAITS.containsKey(candidate)) {
                RESOLVED_PORTRAIT_BY_ROLE.put(roleName, candidate);
                return candidate;
            }
        }

        String bestMatch = MobPortraitMatcher.findBestFuzzyPortrait(roleName, MOD_PORTRAITS.keySet(), TOKENS_BY_PORTRAIT);
        String result = bestMatch != null ? bestMatch : NOT_FOUND;
        RESOLVED_PORTRAIT_BY_ROLE.put(roleName, result);
        return bestMatch;
    }

    private static String findBestFuzzyPortrait(String roleName) {
        return MobPortraitMatcher.findBestFuzzyPortrait(roleName, MOD_PORTRAITS.keySet(), TOKENS_BY_PORTRAIT);
    }

    private static int scorePortraitMatch(List<String> roleTokens, List<String> portraitTokens, String portraitName) {
        return MobPortraitMatcher.scorePortraitMatch(roleTokens, portraitTokens, portraitName);
    }

    private static List<String> buildCandidates(String roleName) {
        return MobPortraitMatcher.buildCandidates(roleName);
    }

    private static List<String> normalizedTokens(String name) {
        return MobPortraitMatcher.normalizedTokens(name);
    }

    private static String normalizeRole(String roleName) {
        return MobPortraitMatcher.normalizeRole(roleName);
    }

    private static String fileStem(String path) {
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        if (fileName.endsWith(JSON_SUFFIX)) {
            return fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        }
        if (fileName.endsWith(PNG_SUFFIX)) {
            return fileName.substring(0, fileName.length() - PNG_SUFFIX.length());
        }
        return fileName;
    }

    private static Path resolveAssetsZipPath() {
        return HytaleInstallLocator.resolveAssetsZipPath();
    }

    private static List<Path> findSaveModArchives() {
        java.util.LinkedHashSet<Path> archives = new java.util.LinkedHashSet<>();
        for (Path savesRoot : findSaveRoots()) {
            try (var saveDirs = Files.list(savesRoot)) {
                saveDirs.filter(Files::isDirectory).forEach(saveDir -> {
                    Path modsDir = saveDir.resolve("mods");
                    if (!Files.isDirectory(modsDir)) {
                        return;
                    }
                    try (var modFiles = Files.list(modsDir)) {
                        modFiles.filter(Files::isRegularFile)
                                .filter(path -> {
                                    String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                                    return lower.endsWith(".jar") || lower.endsWith(".zip");
                                })
                                .forEach(archives::add);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return List.copyOf(archives);
    }

    private static List<Path> findSaveRoots() {
        return HytaleInstallLocator.findSaveRoots();
    }

    private record PortraitSource(Path archivePath, String entryName, String cacheKey) {
    }
}
