package dev.thenexusgates.mobmapmarkers.catalog;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MobNameLocalization {

    private static final String DEFAULT_MOB_NAME = "Mob";
    private static final String DEFAULT_LANGUAGE = "en-US";
    private static final ConcurrentHashMap<String, String> RESOLVED_DISPLAY_NAMES = new ConcurrentHashMap<>();

    private MobNameLocalization() {
    }

    public static String resolveDisplayName(String translationKey, String fallbackDisplayName, String language) {
        String fallback = sanitizeFallbackName(fallbackDisplayName);
        String cacheKey = (translationKey == null ? "" : translationKey) + "|" + normalizeLanguage(language) + "|" + fallback;
        String cached = RESOLVED_DISPLAY_NAMES.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (translationKey == null || translationKey.isBlank()) {
            RESOLVED_DISPLAY_NAMES.putIfAbsent(cacheKey, fallback);
            return fallback;
        }

        I18nModule i18nModule = I18nModule.get();
        if (i18nModule == null) {
            RESOLVED_DISPLAY_NAMES.putIfAbsent(cacheKey, fallback);
            return fallback;
        }

        for (String candidateLanguage : candidateLanguages(language)) {
            String localized = sanitizeMessage(i18nModule.getMessage(candidateLanguage, translationKey));
            if (isResolved(localized, translationKey)) {
                RESOLVED_DISPLAY_NAMES.putIfAbsent(cacheKey, localized);
                return localized;
            }
        }

        RESOLVED_DISPLAY_NAMES.putIfAbsent(cacheKey, fallback);
        return fallback;
    }

    public static String buildAssetLocaleKey(String translationKey, String language, String localizedDisplayName) {
        if (translationKey != null && !translationKey.isBlank()) {
            return translationKey + "|" + normalizeLanguage(language);
        }
        return sanitizeFallbackName(localizedDisplayName);
    }

    public static String formatDistanceMeters(int distance, String language) {
        return distance + " " + localizedMeterUnit(language);
    }

    private static Set<String> candidateLanguages(String language) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeLanguage(language);
        candidates.add(normalized);

        String hyphen = normalized.replace('_', '-');
        String underscore = normalized.replace('-', '_');
        candidates.add(hyphen);
        candidates.add(underscore);

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("uk") || lower.startsWith("ua")) {
            candidates.add("uk");
            candidates.add("uk-UA");
            candidates.add("uk_UA");
        }
        if (lower.startsWith("en")) {
            candidates.add("en");
            candidates.add(DEFAULT_LANGUAGE);
            candidates.add("en_US");
        }

        candidates.add(DEFAULT_LANGUAGE);
        return candidates;
    }

    private static String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim();
    }

    private static String localizedMeterUnit(String language) {
        String normalized = normalizeLanguage(language).replace('_', '-').toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf('-');
        String primary = separatorIndex >= 0 ? normalized.substring(0, separatorIndex) : normalized;
        return switch (primary) {
            case "uk", "ru", "bg", "be", "kk", "mk", "sr" -> "м";
            case "ar", "fa", "ur" -> "م";
            default -> "m";
        };
    }

    private static String sanitizeFallbackName(String fallbackDisplayName) {
        return fallbackDisplayName == null || fallbackDisplayName.isBlank() ? DEFAULT_MOB_NAME : fallbackDisplayName;
    }

    private static boolean isResolved(String localized, String translationKey) {
        return localized != null && !localized.isBlank() && !localized.equals(translationKey);
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String trimmed = message.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}