package dev.thenexusgates.mobmapmarkers.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.util.HytaleInstallLocator;
import dev.thenexusgates.mobmapmarkers.util.WeightedLruCache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class HytaleMobIconResolver {

    private static final String MEMORY_PORTRAITS_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String MODEL_PREFIX = "Server/Models/";
    private static final String COMMON_PREFIX = "Common/";
    private static final String ICONS_PREFIX = "Icons/";
    private static final String JSON_SUFFIX = ".json";
    private static final String PNG_SUFFIX = ".png";
    private static final String NOT_FOUND = "!";
    private static final int MAX_CACHED_ICONS = 512;
    private static final long ICON_CACHE_BYTES = 64L * 1024 * 1024;

    private static final Map<String, RawModelDefinition> MODEL_BY_ENTRY = new ConcurrentHashMap<>();
    private static final Map<String, ResolvedModelDefinition> RESOLVED_MODEL_BY_ENTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> MODEL_ENTRIES_BY_SELECTOR = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> MODEL_ENTRIES_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, Path> PNG_ARCHIVE_BY_ENTRY = new ConcurrentHashMap<>();
    private static final Map<String, String> MEMORY_PORTRAIT_ENTRY_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_PORTRAIT_ENTRY_BY_CANDIDATE = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_MODEL_ENTRY_BY_CANDIDATE = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_ICON_ENTRY_BY_ROLE = new ConcurrentHashMap<>();
    private static final WeightedLruCache<String, byte[]> ICON_BYTES = new WeightedLruCache<>(
            MAX_CACHED_ICONS,
            ICON_CACHE_BYTES,
            bytes -> bytes.length);
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);

    private HytaleMobIconResolver() {
    }

    public static void prewarm() {
        ensureIndexed();
    }

    public static void clearCaches() {
        MODEL_BY_ENTRY.clear();
        RESOLVED_MODEL_BY_ENTRY.clear();
        MODEL_ENTRIES_BY_SELECTOR.clear();
        MODEL_ENTRIES_BY_KEY.clear();
        PNG_ARCHIVE_BY_ENTRY.clear();
        MEMORY_PORTRAIT_ENTRY_BY_NAME.clear();
        RESOLVED_PORTRAIT_ENTRY_BY_CANDIDATE.clear();
        RESOLVED_MODEL_ENTRY_BY_CANDIDATE.clear();
        RESOLVED_ICON_ENTRY_BY_ROLE.clear();
        ICON_BYTES.clear();
        INDEXED.set(false);
        HytaleInstallLocator.clearCaches();
    }

    public static String resolveIconEntryName(String roleName) {
        ensureIndexed();
        if (roleName == null || roleName.isBlank() || MobPortraitMatcher.isExcludedRole(roleName)) {
            return null;
        }

        String cacheKey = normalizeSelectorCacheKey(roleName);
        if (cacheKey == null || cacheKey.isBlank()) {
            return null;
        }

        String cached = RESOLVED_ICON_ENTRY_BY_ROLE.get(cacheKey);
        if (cached != null) {
            return NOT_FOUND.equals(cached) ? null : cached;
        }

        String resolvedEntryName = resolveIconEntryNameInternal(roleName);
        RESOLVED_ICON_ENTRY_BY_ROLE.put(cacheKey, resolvedEntryName != null ? resolvedEntryName : NOT_FOUND);
        return resolvedEntryName;
    }

    public static byte[] loadIconPngByEntryName(String entryName) {
        ensureIndexed();
        if (entryName == null || entryName.isBlank()) {
            return null;
        }

        String archiveEntryName = toArchiveEntryName(entryName);
        if (archiveEntryName == null || archiveEntryName.isBlank()) {
            return null;
        }

        byte[] cached = ICON_BYTES.get(archiveEntryName);
        if (cached != null) {
            return cached.clone();
        }

        Path archivePath = PNG_ARCHIVE_BY_ENTRY.get(archiveEntryName);
        return loadArchiveEntryBytes(archivePath, archiveEntryName, ICON_BYTES, archiveEntryName);
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        Path officialAssetsZip = HytaleInstallLocator.resolveAssetsZipPath();
        if (officialAssetsZip != null) {
            indexArchive(officialAssetsZip, false);
        }

        for (Path archivePath : findInstalledModArchives()) {
            indexArchive(archivePath, true);
        }

        resolveAllModelDefinitions();
    }

    private static void indexArchive(Path archivePath, boolean fromMods) {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .forEach(entry -> {
                        String entryName = entry.getName();
                        if (entryName.startsWith(MODEL_PREFIX) && entryName.endsWith(JSON_SUFFIX)) {
                            addModelEntry(zipFile, archivePath, entry, fromMods);
                            return;
                        }

                        if (isMemoryPortraitEntry(entryName)) {
                            registerMemoryPortraitEntry(archivePath, entryName, fromMods);
                        }
                    });
        } catch (IOException e) {
        }
    }

    private static void addModelEntry(ZipFile zipFile, Path archivePath, ZipEntry entry, boolean fromMods) {
        String entryName = normalizeEntryName(entry.getName());
        String modelName = fileStem(entryName);
        String modelKey = normalizeRoleKey(modelName);
        if (entryName == null || entryName.isBlank() || modelKey == null) {
            return;
        }

        String json;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        JsonObject root = parseJsonObject(json);
        if (root == null) {
            return;
        }

        String parentSelector = readString(root, "Parent");
        String iconEntryName = toModelIconEntryName(readString(root, "Icon"));
        if (iconEntryName != null) {
            registerPngEntrySource(zipFile, archivePath, iconEntryName, fromMods);
        }

        RawModelDefinition definition = new RawModelDefinition(entryName, modelName, parentSelector, iconEntryName);
        MODEL_BY_ENTRY.compute(entryName, (ignored, existing) -> existing == null || fromMods ? definition : existing);
        for (String selector : buildModelSelectorKeys(entryName)) {
            registerSelector(MODEL_ENTRIES_BY_SELECTOR, selector, entryName, fromMods);
        }
        registerSelector(MODEL_ENTRIES_BY_KEY, modelKey, entryName, fromMods);
    }

    private static boolean isMemoryPortraitEntry(String entryName) {
        return entryName != null
                && entryName.startsWith(MEMORY_PORTRAITS_PREFIX)
                && entryName.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX);
    }

    private static void registerMemoryPortraitEntry(Path archivePath, String entryName, boolean fromMods) {
        String portraitName = fileStem(entryName);
        String normalizedPortraitName = MobPortraitMatcher.normalizeRole(portraitName);
        if (normalizedPortraitName.isBlank()) {
            return;
        }

        PNG_ARCHIVE_BY_ENTRY.compute(entryName, (ignored, existing) -> existing == null || fromMods ? archivePath : existing);
        MEMORY_PORTRAIT_ENTRY_BY_NAME.compute(
                normalizedPortraitName,
                (ignored, existing) -> existing == null || fromMods ? entryName : existing);
    }

    private static void registerPngEntrySource(ZipFile zipFile, Path archivePath, String entryName, boolean fromMods) {
        if (entryName == null || entryName.isBlank()) {
            return;
        }

        ZipEntry iconEntry = zipFile.getEntry(entryName);
        if (iconEntry == null || iconEntry.isDirectory()) {
            return;
        }

        PNG_ARCHIVE_BY_ENTRY.compute(entryName, (ignored, existing) -> existing == null || fromMods ? archivePath : existing);
    }

    private static void resolveAllModelDefinitions() {
        for (String entryName : MODEL_BY_ENTRY.keySet()) {
            resolveModelDefinition(entryName, new LinkedHashSet<>());
        }
    }

    private static String resolveIconEntryNameInternal(String roleName) {
        ArrayList<String> primaryEntries = new ArrayList<>(MobArchiveIndex.resolveRoleEntryNames(roleName));
        LinkedHashSet<String> descendantEntries = new LinkedHashSet<>();
        LinkedHashSet<String> visitedEntries = new LinkedHashSet<>();

        for (int index = 0; index < primaryEntries.size(); index++) {
            String entryName = normalizeEntryName(primaryEntries.get(index));
            if (entryName == null || entryName.isBlank() || !visitedEntries.add(entryName)) {
                continue;
            }

            String resolved = resolveRoleEntryIcon(entryName);
            if (resolved != null) {
                return resolved;
            }

            primaryEntries.addAll(MobArchiveIndex.getReferencedRoleEntryNames(entryName));
            descendantEntries.addAll(MobArchiveIndex.getDescendantRoleEntryNames(entryName));
        }

        ArrayList<String> fallbackEntries = new ArrayList<>(descendantEntries);
        for (int index = 0; index < fallbackEntries.size(); index++) {
            String entryName = normalizeEntryName(fallbackEntries.get(index));
            if (entryName == null || entryName.isBlank() || !visitedEntries.add(entryName)) {
                continue;
            }

            String resolved = resolveRoleEntryIcon(entryName);
            if (resolved != null) {
                return resolved;
            }

            fallbackEntries.addAll(MobArchiveIndex.getReferencedRoleEntryNames(entryName));
            fallbackEntries.addAll(MobArchiveIndex.getDescendantRoleEntryNames(entryName));
        }

        return resolveIconFromCandidates(List.of(roleName), List.of(roleName));
    }

    private static String resolveRoleEntryIcon(String entryName) {
        String roleName = MobArchiveIndex.getRoleNameByEntry(entryName);
        LinkedHashSet<String> portraitCandidates = new LinkedHashSet<>();
        LinkedHashSet<String> modelCandidates = new LinkedHashSet<>();

        addCandidates(portraitCandidates, MobArchiveIndex.getRolePortraitCandidatesByEntry(entryName));
        addCandidate(portraitCandidates, roleName);
        addCandidates(portraitCandidates, MobArchiveIndex.getRoleAppearanceCandidatesByEntry(entryName));

        addCandidates(modelCandidates, MobArchiveIndex.getRoleAppearanceCandidatesByEntry(entryName));
        addCandidate(modelCandidates, roleName);
        addCandidates(modelCandidates, MobArchiveIndex.getRolePortraitCandidatesByEntry(entryName));

        return resolveIconFromCandidates(portraitCandidates, modelCandidates);
    }

    private static String resolveIconFromCandidates(Iterable<String> portraitCandidates,
                                                    Iterable<String> modelCandidates) {
        for (String candidate : portraitCandidates) {
            String portraitEntryName = resolvePortraitEntryName(candidate);
            if (portraitEntryName != null) {
                return portraitEntryName;
            }
        }

        for (String candidate : modelCandidates) {
            String modelEntryName = resolveModelAssetEntryName(candidate);
            if (modelEntryName != null && !modelEntryName.isBlank()) {
                return modelEntryName;
            }
        }

        return null;
    }

    private static ResolvedModelDefinition resolveModelDefinition(String entryName, LinkedHashSet<String> stack) {
        String normalizedEntryName = normalizeEntryName(entryName);
        if (normalizedEntryName == null || normalizedEntryName.isBlank()) {
            return null;
        }

        ResolvedModelDefinition cached = RESOLVED_MODEL_BY_ENTRY.get(normalizedEntryName);
        if (cached != null) {
            return cached;
        }

        RawModelDefinition raw = MODEL_BY_ENTRY.get(normalizedEntryName);
        if (raw == null) {
            return null;
        }

        if (!stack.add(normalizedEntryName)) {
            return new ResolvedModelDefinition(resolveDirectModelAssetEntryName(raw.modelName(), raw.iconEntryName()));
        }

        String assetEntryName = resolveDirectModelAssetEntryName(raw.modelName(), raw.iconEntryName());
        if ((assetEntryName == null || assetEntryName.isBlank())
                && raw.parentSelector() != null
                && !raw.parentSelector().isBlank()) {
            for (String parentEntryName : resolveModelEntryNames(raw.parentSelector())) {
                ResolvedModelDefinition parent = resolveModelDefinition(parentEntryName, stack);
                if (parent != null && parent.assetEntryName() != null && !parent.assetEntryName().isBlank()) {
                    assetEntryName = parent.assetEntryName();
                    break;
                }
            }
        }

        ResolvedModelDefinition resolved = new ResolvedModelDefinition(assetEntryName);
        RESOLVED_MODEL_BY_ENTRY.put(normalizedEntryName, resolved);
        stack.remove(normalizedEntryName);
        return resolved;
    }

    private static String resolveDirectModelAssetEntryName(String modelName, String iconEntryName) {
        if (iconEntryName != null && !iconEntryName.isBlank() && PNG_ARCHIVE_BY_ENTRY.containsKey(iconEntryName)) {
            return iconEntryName;
        }

        return resolvePortraitEntryName(modelName);
    }

    private static String resolveModelAssetEntryName(String candidate) {
        String modelEntryName = resolveModelEntryName(candidate);
        if (modelEntryName == null) {
            return null;
        }

        ResolvedModelDefinition definition = RESOLVED_MODEL_BY_ENTRY.get(modelEntryName);
        return definition == null ? null : definition.assetEntryName();
    }

    private static List<String> resolveModelEntryNames(String candidate) {
        LinkedHashSet<String> resolvedEntries = new LinkedHashSet<>(
                resolveEntriesForSelector(candidate, MODEL_ENTRIES_BY_SELECTOR));
        if (!resolvedEntries.isEmpty()) {
            return List.copyOf(resolvedEntries);
        }

        String modelKey = normalizeRoleKey(candidate);
        if (modelKey == null) {
            return List.of();
        }

        for (String modelCandidate : buildModelNameCandidates(modelKey)) {
            resolvedEntries.addAll(resolveEntriesForSelector(modelCandidate, MODEL_ENTRIES_BY_KEY));
            if (!resolvedEntries.isEmpty()) {
                return List.copyOf(resolvedEntries);
            }
        }

        for (String modelCandidate : buildModelNameCandidates(modelKey)) {
            String matchedKey = MobPortraitMatcher.findBestCandidateMatch(modelCandidate, MODEL_ENTRIES_BY_KEY.keySet());
            if (matchedKey != null) {
                resolvedEntries.addAll(listOrEmpty(MODEL_ENTRIES_BY_KEY.get(matchedKey)));
                if (!resolvedEntries.isEmpty()) {
                    return List.copyOf(resolvedEntries);
                }
            }
        }

        return List.of();
    }

    private static void addCandidates(LinkedHashSet<String> candidates, Iterable<String> values) {
        if (values == null) {
            return;
        }

        for (String value : values) {
            addCandidate(candidates, value);
        }
    }

    private static void addCandidate(LinkedHashSet<String> candidates, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }

        String normalizedCandidate = normalizeSelectorCacheKey(candidate);
        if (normalizedCandidate != null && !normalizedCandidate.isBlank()) {
            candidates.add(candidate);
        }
    }

    private static String resolvePortraitEntryName(String candidate) {
        String portraitKey = normalizePortraitKey(candidate);
        if (portraitKey == null || portraitKey.isBlank()) {
            return null;
        }

        String cached = RESOLVED_PORTRAIT_ENTRY_BY_CANDIDATE.get(portraitKey);
        if (cached != null) {
            return NOT_FOUND.equals(cached) ? null : cached;
        }

        String entryName = MEMORY_PORTRAIT_ENTRY_BY_NAME.get(portraitKey);
        if (entryName == null && portraitKey.startsWith("Model_") && portraitKey.length() > "Model_".length()) {
            entryName = MEMORY_PORTRAIT_ENTRY_BY_NAME.get(portraitKey.substring("Model_".length()));
        }
        if (entryName == null) {
            for (String portraitCandidate : MobPortraitMatcher.buildCandidates(portraitKey)) {
                entryName = MEMORY_PORTRAIT_ENTRY_BY_NAME.get(portraitCandidate);
                if (entryName != null) {
                    break;
                }
            }
        }
        if (entryName == null) {
            String matchedPortraitName = MobPortraitMatcher.findMatchingPortrait(portraitKey, MEMORY_PORTRAIT_ENTRY_BY_NAME.keySet());
            if (matchedPortraitName != null) {
                entryName = MEMORY_PORTRAIT_ENTRY_BY_NAME.get(matchedPortraitName);
            }
        }

        RESOLVED_PORTRAIT_ENTRY_BY_CANDIDATE.put(portraitKey, entryName != null ? entryName : NOT_FOUND);
        return entryName;
    }

    private static String resolveModelEntryName(String candidate) {
        String cacheKey = normalizeSelectorCacheKey(candidate);
        if (cacheKey == null || cacheKey.isBlank()) {
            return null;
        }

        String cached = RESOLVED_MODEL_ENTRY_BY_CANDIDATE.get(cacheKey);
        if (cached != null) {
            return NOT_FOUND.equals(cached) ? null : cached;
        }

        String resolved = firstEntry(resolveEntriesForSelector(candidate, MODEL_ENTRIES_BY_SELECTOR));
        if (resolved == null) {
            String modelKey = normalizeRoleKey(candidate);
            if (modelKey != null) {
                for (String modelCandidate : buildModelNameCandidates(modelKey)) {
                    resolved = firstEntry(resolveEntriesForSelector(modelCandidate, MODEL_ENTRIES_BY_KEY));
                    if (resolved != null) {
                        break;
                    }
                }

                if (resolved == null) {
                    for (String modelCandidate : buildModelNameCandidates(modelKey)) {
                        String matchedKey = MobPortraitMatcher.findBestCandidateMatch(modelCandidate, MODEL_ENTRIES_BY_KEY.keySet());
                        if (matchedKey != null) {
                            resolved = firstEntry(listOrEmpty(MODEL_ENTRIES_BY_KEY.get(matchedKey)));
                            if (resolved != null) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        RESOLVED_MODEL_ENTRY_BY_CANDIDATE.put(cacheKey, resolved != null ? resolved : NOT_FOUND);
        return resolved;
    }

    private static List<String> buildModelNameCandidates(String normalizedCandidate) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>(MobPortraitMatcher.buildCandidates(normalizedCandidate));
        candidates.add(normalizedCandidate);

        ArrayList<String> snapshot = new ArrayList<>(candidates);
        for (String candidate : snapshot) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            if (!candidate.startsWith("Template_")) {
                candidates.add("Template_" + candidate);
            } else if (candidate.length() > "Template_".length()) {
                candidates.add(candidate.substring("Template_".length()));
            }

            if (!candidate.endsWith("_Model")) {
                candidates.add(candidate + "_Model");
            } else if (candidate.length() > "_Model".length()) {
                candidates.add(candidate.substring(0, candidate.length() - "_Model".length()));
            }
        }

        return List.copyOf(candidates);
    }

    private static List<String> resolveEntriesForSelector(String rawSelector, Map<String, List<String>> entriesBySelector) {
        LinkedHashSet<String> resolvedEntries = new LinkedHashSet<>();
        for (String selector : buildModelSelectorKeys(rawSelector)) {
            resolvedEntries.addAll(listOrEmpty(entriesBySelector.get(selector)));
        }
        return List.copyOf(resolvedEntries);
    }

    private static List<String> buildModelSelectorKeys(String rawValue) {
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        String pathSelector = normalizePathSelector(rawValue, MODEL_PREFIX);
        if (pathSelector != null) {
            selectors.add(pathSelector);
            int slash = pathSelector.indexOf('/');
            while (slash >= 0 && slash < pathSelector.length() - 1) {
                pathSelector = pathSelector.substring(slash + 1);
                selectors.add(pathSelector);
                slash = pathSelector.indexOf('/');
            }
        }

        String modelKey = normalizeRoleKey(rawValue);
        if (modelKey != null) {
            selectors.add(modelKey);
        }

        return List.copyOf(selectors);
    }

    private static String normalizePortraitKey(String candidate) {
        String portraitName = MobPortraitMatcher.normalizeRole(fileStem(candidate));
        return portraitName.isBlank() ? null : portraitName;
    }

    private static String normalizeSelectorCacheKey(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String pathSelector = normalizePathSelector(candidate, "");
        if (pathSelector != null) {
            return pathSelector;
        }

        return normalizeRoleKey(candidate);
    }

    private static String normalizeRoleKey(String candidate) {
        String normalized = MobPortraitMatcher.normalizeRole(fileStem(candidate));
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizePathSelector(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalizeEntryName(value.trim());
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (prefix != null && !prefix.isBlank() && normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            normalized = normalized.substring(prefix.length());
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - JSON_SUFFIX.length());
        } else if (normalized.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - PNG_SUFFIX.length());
        }

        return normalized.contains("/") ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private static String normalizeEntryName(String entryName) {
        return entryName == null ? null : entryName.replace('\\', '/');
    }

    private static void registerSelector(Map<String, List<String>> index,
                                         String selector,
                                         String entryName,
                                         boolean fromMods) {
        if (selector == null || selector.isBlank() || entryName == null || entryName.isBlank()) {
            return;
        }

        index.compute(selector, (ignored, existing) -> {
            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            if (fromMods) {
                ordered.add(entryName);
            }
            if (existing != null) {
                ordered.addAll(existing);
            }
            if (!fromMods) {
                ordered.add(entryName);
            }
            return List.copyOf(ordered);
        });
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values != null ? values : List.of();
    }

    private static String firstEntry(List<String> entries) {
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static byte[] loadArchiveEntryBytes(Path archivePath, String entryName,
                                                WeightedLruCache<String, byte[]> cache, String cacheKey) {
        if (archivePath == null || entryName == null || entryName.isBlank()) {
            return null;
        }

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                return null;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] bytes = inputStream.readAllBytes();
                cache.put(cacheKey, bytes);
                return bytes.clone();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String toModelIconEntryName(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }

        String normalized = iconPath.replace('\\', '/');
        if (normalized.startsWith(COMMON_PREFIX)) {
            return normalized.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX) ? normalized : null;
        }
        if (normalized.startsWith(ICONS_PREFIX)) {
            return COMMON_PREFIX + normalized;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.startsWith(ICONS_PREFIX) && normalized.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)
                ? COMMON_PREFIX + normalized
                : null;
    }

    private static String toArchiveEntryName(String entryNameOrAssetPath) {
        if (entryNameOrAssetPath == null || entryNameOrAssetPath.isBlank()) {
            return null;
        }

        String normalized = entryNameOrAssetPath.replace('\\', '/');
        if (PNG_ARCHIVE_BY_ENTRY.containsKey(normalized)) {
            return normalized;
        }
        if (normalized.startsWith(COMMON_PREFIX)) {
            return normalized;
        }
        return COMMON_PREFIX + normalized;
    }

    private static JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }

        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String fileStem(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        if (fileName.endsWith(JSON_SUFFIX)) {
            return fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
            return fileName.substring(0, fileName.length() - PNG_SUFFIX.length());
        }
        return fileName;
    }

    private static List<Path> findInstalledModArchives() {
        return HytaleInstallLocator.findInstalledModArchives(MobMapMarkersPlugin.class);
    }

    private record RawModelDefinition(String entryName, String modelName, String parentSelector, String iconEntryName) {
    }

    private record ResolvedModelDefinition(String assetEntryName) {
    }
}