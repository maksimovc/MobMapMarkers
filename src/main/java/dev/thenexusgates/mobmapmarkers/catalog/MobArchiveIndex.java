package dev.thenexusgates.mobmapmarkers.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerKeys;
import dev.thenexusgates.mobmapmarkers.util.HytaleInstallLocator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MobArchiveIndex {

    private static final String ROLE_PREFIX = "Server/NPC/Roles/";
    private static final String JSON_SUFFIX = ".json";
    private static final String PNG_SUFFIX = ".png";

    private static final Map<String, MobCatalogEntry> CATALOG_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, RoleDefinition> ROLE_BY_ENTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ROLE_ENTRIES_BY_SELECTOR = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> REFERENCED_ROLE_ENTRIES_BY_ENTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> DESCENDANT_ROLE_ENTRIES_BY_ENTRY = new ConcurrentHashMap<>();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);

    private MobArchiveIndex() {
    }

    public static void prewarm() {
        ensureIndexed();
    }

    public static void clearCaches() {
        CATALOG_BY_KEY.clear();
        ROLE_BY_ENTRY.clear();
        ROLE_ENTRIES_BY_SELECTOR.clear();
        REFERENCED_ROLE_ENTRIES_BY_ENTRY.clear();
        DESCENDANT_ROLE_ENTRIES_BY_ENTRY.clear();
        INDEXED.set(false);
        HytaleInstallLocator.clearCaches();
    }

    public static List<MobCatalogEntry> getCatalogEntries() {
        ensureIndexed();
        return List.copyOf(CATALOG_BY_KEY.values());
    }

    public static List<String> resolveRoleEntryNames(String roleSelector) {
        ensureIndexed();
        if (roleSelector == null || roleSelector.isBlank() || MobPortraitMatcher.isTraversalExcludedRole(roleSelector)) {
            return List.of();
        }

        return resolveEntriesForSelector(roleSelector, ROLE_ENTRIES_BY_SELECTOR);
    }

    public static List<String> getReferencedRoleEntryNames(String entryName) {
        ensureIndexed();
        return listOrEmpty(REFERENCED_ROLE_ENTRIES_BY_ENTRY.get(normalizeEntryName(entryName)));
    }

    public static List<String> getDescendantRoleEntryNames(String entryName) {
        ensureIndexed();
        return listOrEmpty(DESCENDANT_ROLE_ENTRIES_BY_ENTRY.get(normalizeEntryName(entryName)));
    }

    public static List<String> getRoleAppearanceCandidatesByEntry(String entryName) {
        ensureIndexed();
        RoleDefinition definition = ROLE_BY_ENTRY.get(normalizeEntryName(entryName));
        return definition == null ? List.of() : definition.appearanceCandidates();
    }

    public static List<String> getRolePortraitCandidatesByEntry(String entryName) {
        ensureIndexed();
        RoleDefinition definition = ROLE_BY_ENTRY.get(normalizeEntryName(entryName));
        return definition == null ? List.of() : definition.portraitCandidates();
    }

    public static String getRoleNameByEntry(String entryName) {
        ensureIndexed();
        RoleDefinition definition = ROLE_BY_ENTRY.get(normalizeEntryName(entryName));
        return definition == null ? null : definition.roleName();
    }

    public static List<String> getRoleReferenceCandidates(String roleName) {
        ensureIndexed();
        if (roleName == null || roleName.isBlank() || MobPortraitMatcher.isTraversalExcludedRole(roleName)) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String entryName : resolveRoleEntryNames(roleName)) {
            for (String referencedEntry : getReferencedRoleEntryNames(entryName)) {
                String referencedRole = getRoleNameByEntry(referencedEntry);
                if (referencedRole != null && !referencedRole.isBlank()) {
                    candidates.add(referencedRole);
                }
            }
        }
        return List.copyOf(candidates);
    }

    public static List<String> getRoleAppearanceCandidates(String roleName) {
        ensureIndexed();
        if (roleName == null || roleName.isBlank() || MobPortraitMatcher.isTraversalExcludedRole(roleName)) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String entryName : resolveRoleEntryNames(roleName)) {
            candidates.addAll(getRoleAppearanceCandidatesByEntry(entryName));
        }
        return List.copyOf(candidates);
    }

    public static List<String> getRolePortraitCandidates(String roleName) {
        ensureIndexed();
        if (roleName == null || roleName.isBlank() || MobPortraitMatcher.isTraversalExcludedRole(roleName)) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String entryName : resolveRoleEntryNames(roleName)) {
            candidates.addAll(getRolePortraitCandidatesByEntry(entryName));
        }
        return List.copyOf(candidates);
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        Path officialAssetsZip = resolveAssetsZipPath();
        if (officialAssetsZip != null) {
            indexRolesFromArchive(officialAssetsZip, false);
        }

        for (Path archivePath : findInstalledModArchives()) {
            indexRolesFromArchive(archivePath, true);
        }

        linkRoleGraph();
    }

    private static void indexRolesFromArchive(Path archivePath, boolean fromMods) {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(ROLE_PREFIX) && entry.getName().endsWith(JSON_SUFFIX))
                    .forEach(entry -> addRoleEntry(zipFile, entry, fromMods));
        } catch (IOException ignored) {
        }
    }

    private static void addRoleEntry(ZipFile zipFile, ZipEntry entry, boolean fromMods) {
        String entryName = normalizeEntryName(entry.getName());
        String roleName = fileStem(entryName);
        String roleKey = normalizeRoleKey(roleName);
        if (entryName == null || roleKey == null) {
            return;
        }

        String json;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }

        RoleMetadata metadata = RoleMetadata.EMPTY;
        JsonObject root = parseJsonObject(json);
        if (root != null) {
            metadata = extractRoleMetadata(root);
        }

        RoleDefinition definition = new RoleDefinition(
                entryName,
                roleName,
                metadata.referenceSelectors(),
                metadata.appearanceCandidates(),
                metadata.portraitCandidates(),
                metadata.nameTranslationKey(),
                fromMods);

        ROLE_BY_ENTRY.compute(entryName, (ignored, existing) -> existing == null || fromMods ? definition : existing);
        for (String selector : buildRoleSelectorKeys(entryName)) {
            registerSelector(ROLE_ENTRIES_BY_SELECTOR, selector, entryName, fromMods);
        }

        if (MobPortraitMatcher.isExcludedRole(roleName)) {
            return;
        }

        String mobKey = MobMarkerKeys.normalize(roleName);
        if (mobKey == null) {
            return;
        }

        String fallbackDisplayName = MobMarkerNames.formatRoleName(roleName);
        MobCatalogEntry next = new MobCatalogEntry(
                mobKey,
                roleName,
                metadata.nameTranslationKey() == null || metadata.nameTranslationKey().isBlank()
                        ? "server.npcRoles." + roleName + ".name"
                        : metadata.nameTranslationKey(),
                fallbackDisplayName);

        CATALOG_BY_KEY.merge(mobKey, next, (existing, candidate) -> fromMods ? candidate : existing);
    }

    private static void linkRoleGraph() {
        for (RoleDefinition definition : ROLE_BY_ENTRY.values()) {
            LinkedHashSet<String> referencedEntries = new LinkedHashSet<>();
            for (String selector : definition.referenceSelectors()) {
                for (String referencedEntry : resolveEntriesForSelector(selector, ROLE_ENTRIES_BY_SELECTOR)) {
                    if (!definition.entryName().equals(referencedEntry)) {
                        referencedEntries.add(referencedEntry);
                    }
                }
            }

            List<String> references = List.copyOf(referencedEntries);
            REFERENCED_ROLE_ENTRIES_BY_ENTRY.put(definition.entryName(), references);
            for (String referencedEntry : references) {
                registerSelector(
                        DESCENDANT_ROLE_ENTRIES_BY_ENTRY,
                        referencedEntry,
                        definition.entryName(),
                        definition.fromMods());
            }
        }
    }

    private static RoleMetadata extractRoleMetadata(JsonObject root) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        LinkedHashSet<String> appearances = new LinkedHashSet<>();
        LinkedHashSet<String> portraits = new LinkedHashSet<>();

        JsonObject modify = readObject(root, "Modify");
        JsonObject parameters = readObject(root, "Parameters");

        addReferenceSelector(references, readString(root, "Reference"));
        addReferenceSelector(references, readString(modify, "Reference"));

        addAppearanceCandidate(appearances, readString(root, "Appearance"));
        addAppearanceCandidate(appearances, readString(modify, "Appearance"));
        addAppearanceCandidate(appearances, readParameterValue(parameters, "Appearance"));

        addPortraitCandidate(portraits, readString(root, "MemoriesNameOverride"));
        addPortraitCandidate(portraits, readString(modify, "MemoriesNameOverride"));
        addPortraitCandidate(portraits, readParameterValue(parameters, "MemoriesNameOverride"));

        return new RoleMetadata(
                List.copyOf(references),
                List.copyOf(appearances),
                List.copyOf(portraits),
                firstNonBlank(
                        readString(root, "NameTranslationKey"),
                        readString(modify, "NameTranslationKey"),
                        readParameterValue(parameters, "NameTranslationKey")));
    }

    private static void addReferenceSelector(LinkedHashSet<String> references, String rawValue) {
        String candidate = normalizeCandidateValue(rawValue);
        if (candidate != null && !MobPortraitMatcher.isTraversalExcludedRole(candidate)) {
            references.add(candidate);
        }
    }

    private static void addAppearanceCandidate(LinkedHashSet<String> appearances, String rawValue) {
        String candidate = normalizeCandidateValue(rawValue);
        if (candidate != null && !MobPortraitMatcher.isTraversalExcludedRole(candidate)) {
            appearances.add(candidate);
        }
    }

    private static void addPortraitCandidate(LinkedHashSet<String> portraits, String rawValue) {
        String candidate = normalizeCandidateValue(rawValue);
        if (candidate != null && !MobPortraitMatcher.isTraversalExcludedRole(candidate)) {
            portraits.add(candidate);
        }
    }

    private static List<String> resolveEntriesForSelector(String rawSelector, Map<String, List<String>> entriesBySelector) {
        LinkedHashSet<String> resolvedEntries = new LinkedHashSet<>();
        for (String selector : buildRoleSelectorKeys(rawSelector)) {
            resolvedEntries.addAll(listOrEmpty(entriesBySelector.get(selector)));
        }
        return List.copyOf(resolvedEntries);
    }

    private static LinkedHashSet<String> buildRoleSelectorKeys(String rawValue) {
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        if (rawValue == null || rawValue.isBlank()) {
            return selectors;
        }

        String pathSelector = normalizePathSelector(rawValue, ROLE_PREFIX);
        if (pathSelector != null) {
            selectors.add(pathSelector);
        }

        String roleKey = normalizeRoleKey(rawValue);
        if (roleKey != null) {
            selectors.add(roleKey);
        }
        return selectors;
    }

    private static String normalizeCandidateValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "server.npcRoles.", 0, "server.npcRoles.".length())) {
            int start = "server.npcRoles.".length();
            int end = trimmed.indexOf('.', start);
            if (end > start) {
                return MobPortraitMatcher.normalizeRole(trimmed.substring(start, end));
            }
        }

        String normalized = trimmed.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - JSON_SUFFIX.length());
        } else if (normalized.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - PNG_SUFFIX.length());
        }

        if (normalized.contains("/")) {
            return normalized;
        }

        String roleLike = MobPortraitMatcher.normalizeRole(normalized);
        if (roleLike.isBlank() || roleLike.equals("Reference") || roleLike.equals("Appearance")) {
            return null;
        }
        return roleLike;
    }

    private static String normalizePathSelector(String value, String prefix) {
        String normalized = normalizeEntryName(normalizeCandidateValue(value));
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            normalized = normalized.substring(prefix.length());
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - JSON_SUFFIX.length());
        }

        return normalized.contains("/") ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private static JsonObject readObject(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }

        try {
            return object.getAsJsonObject(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String readParameterValue(JsonObject parameters, String key) {
        JsonObject parameter = readObject(parameters, key);
        return readString(parameter, "Value");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeRoleKey(String roleName) {
        String normalized = MobPortraitMatcher.normalizeRole(fileStem(roleName));
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeEntryName(String entryName) {
        return entryName == null ? null : entryName.replace('\\', '/');
    }

    private static String fileStem(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (fileName.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            return fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX)) {
            return fileName.substring(0, fileName.length() - PNG_SUFFIX.length());
        }
        return fileName;
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

    private static Path resolveAssetsZipPath() {
        return HytaleInstallLocator.resolveAssetsZipPath();
    }

    private static List<Path> findInstalledModArchives() {
        return HytaleInstallLocator.findInstalledModArchives(MobMapMarkersPlugin.class);
    }

    private record RoleDefinition(
            String entryName,
            String roleName,
            List<String> referenceSelectors,
            List<String> appearanceCandidates,
            List<String> portraitCandidates,
            String nameTranslationKey,
            boolean fromMods) {
    }

    private record RoleMetadata(
            List<String> referenceSelectors,
            List<String> appearanceCandidates,
            List<String> portraitCandidates,
            String nameTranslationKey) {

        private static final RoleMetadata EMPTY = new RoleMetadata(List.of(), List.of(), List.of(), null);
    }
}
