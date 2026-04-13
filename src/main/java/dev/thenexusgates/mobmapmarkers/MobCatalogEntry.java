package dev.thenexusgates.mobmapmarkers;

final class MobCatalogEntry {

    private final String mobKey;
    private final String roleName;
    private final String nameTranslationKey;
    private final String fallbackDisplayName;
    private final boolean facingRight;

    MobCatalogEntry(String mobKey, String roleName, String nameTranslationKey, String fallbackDisplayName,
                    boolean facingRight) {
        this.mobKey = mobKey;
        this.roleName = roleName;
        this.nameTranslationKey = nameTranslationKey;
        this.fallbackDisplayName = fallbackDisplayName;
        this.facingRight = facingRight;
    }

    static MobCatalogEntry fallback(String rawMobKey) {
        String normalizedKey = MobMarkerKeys.normalize(rawMobKey);
        String roleName = rawMobKey == null || rawMobKey.isBlank() ? "mob" : rawMobKey;
        return new MobCatalogEntry(
                normalizedKey == null ? "mob" : normalizedKey,
                roleName,
                null,
                MobMarkerNames.formatRoleName(roleName),
                false);
    }

    String mobKey() {
        return mobKey;
    }

    String roleName() {
        return roleName;
    }

    String nameTranslationKey() {
        return nameTranslationKey;
    }

    String fallbackDisplayName() {
        return fallbackDisplayName;
    }

    boolean facingRight() {
        return facingRight;
    }
}