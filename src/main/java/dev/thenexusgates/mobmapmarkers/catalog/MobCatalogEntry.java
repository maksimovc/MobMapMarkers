package dev.thenexusgates.mobmapmarkers.catalog;

import dev.thenexusgates.mobmapmarkers.filter.MobMarkerKeys;

public final class MobCatalogEntry {

    private final String mobKey;
    private final String roleName;
    private final String nameTranslationKey;
    private final String fallbackDisplayName;
    private final boolean facingRight;

    public MobCatalogEntry(String mobKey, String roleName, String nameTranslationKey, String fallbackDisplayName,
                           boolean facingRight) {
        this.mobKey = mobKey;
        this.roleName = roleName;
        this.nameTranslationKey = nameTranslationKey;
        this.fallbackDisplayName = fallbackDisplayName;
        this.facingRight = facingRight;
    }

    public MobCatalogEntry(String mobKey, String roleName, String nameTranslationKey, String fallbackDisplayName) {
        this(mobKey, roleName, nameTranslationKey, fallbackDisplayName, false);
    }

    public static MobCatalogEntry fallback(String rawMobKey) {
        String normalizedKey = MobMarkerKeys.normalize(rawMobKey);
        String roleName = rawMobKey == null || rawMobKey.isBlank() ? "mob" : rawMobKey;
        return new MobCatalogEntry(
                normalizedKey == null ? "mob" : normalizedKey,
                roleName,
                null,
                MobMarkerNames.formatRoleName(roleName),
                false);
    }

    public String mobKey() {
        return mobKey;
    }

    public String roleName() {
        return roleName;
    }

    public String nameTranslationKey() {
        return nameTranslationKey;
    }

    public String fallbackDisplayName() {
        return fallbackDisplayName;
    }

    public boolean facingRight() {
        return facingRight;
    }
}