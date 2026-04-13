package dev.thenexusgates.mobmapmarkers;

import java.util.Map;
import java.util.TreeMap;

final class MobMarkerFilterProfile {

    int version = 1;
    Map<String, MobMarkerFilterRule> mobRules = new TreeMap<>();

    MobMarkerFilterProfile normalize() {
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