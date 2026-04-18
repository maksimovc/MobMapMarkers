package dev.thenexusgates.mobmapmarkers.filter;

import java.util.Map;
import java.util.TreeMap;

public final class MobMarkerFilterProfile {

    public int version = 1;
    public Map<String, MobMarkerFilterRule> mobRules = new TreeMap<>();

    public MobMarkerFilterProfile normalize() {
        Map<String, MobMarkerFilterRule> normalizedRules = new TreeMap<>();
        if (mobRules != null) {
            mobRules.forEach((key, value) -> {
                String normalizedKey = MobMarkerKeys.normalize(key);
                if (normalizedKey != null) {
                    normalizedRules.put(normalizedKey, value == null ? new MobMarkerFilterRule() : value);
                }
            });
        }
        mobRules = normalizedRules;
        if (version <= 0) {
            version = 1;
        }
        return this;
    }
}