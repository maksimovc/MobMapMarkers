package dev.thenexusgates.mobmapmarkers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MobPortraitMatcher {

    private static final int MIN_FUZZY_MATCH_SCORE = 6;

    private static final Map<String, List<String>> ROLE_ALIASES = Map.ofEntries(
            Map.entry("Mosshorn_Plain", List.of("Mosshorn")),
            Map.entry("Bunny", List.of("Bunny", "Rabbit")),
            Map.entry("Rabbit", List.of("Rabbit", "Bunny")),
            Map.entry("Cat_Black", List.of("Cat")),
            Map.entry("Cat_Orange", List.of("Cat")),
            Map.entry("Cat_White", List.of("Cat")),
            Map.entry("Cat_Gray", List.of("Cat")),
            Map.entry("Cat_Grey", List.of("Cat")),
            Map.entry("Fox_Red", List.of("Fox")),
            Map.entry("Fox_White", List.of("Fox")),
            Map.entry("Sheep_Woolly", List.of("Sheep")),
            Map.entry("Pig_Boar", List.of("Pig_Wild", "Pig")),
            Map.entry("Pig_Wild_Boar", List.of("Pig_Wild", "Pig"))
    );

    private static final Set<String> LEADING_PREFIXES = Set.of(
            "Temple", "Tamed", "Friendly", "Passive", "Companion", "Summoned");
    private static final Set<String> TRAILING_SUFFIXES = Set.of(
            "Wander", "Wandering", "Patrol", "Static", "Alerted", "Sleeping");
    private static final Set<String> MATCH_IGNORED_TOKENS = Set.of(
            "Black", "White", "Brown", "Red", "Blue", "Green", "Yellow", "Orange",
            "Gray", "Grey", "Pink", "Purple", "Gold", "Silver", "Light", "Dark",
            "Armored", "Armoured", "Berserk", "Berserker", "Chief", "Elite", "Heavy",
            "Male", "Female", "Adult", "Child", "Baby", "Young", "Old", "Small", "Large",
            "Giant", "Alpha", "Beta", "Test", "Unified");

    private MobPortraitMatcher() {
    }

    static String normalizeRole(String roleName) {
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

    static List<String> buildCandidates(String roleName) {
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

        addAnimalFallbacks(normalized, candidates);
        addDescriptorStrippedVariants(normalized, candidates);

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

    static List<String> normalizedTokens(String name) {
        List<String> tokens = new ArrayList<>();
        for (String token : name.split("_")) {
            if (token == null || token.isBlank()) {
                continue;
            }

            if (LEADING_PREFIXES.contains(token) || TRAILING_SUFFIXES.contains(token) || MATCH_IGNORED_TOKENS.contains(token)) {
                continue;
            }

            tokens.add(token.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tokens);
    }

    static int scorePortraitMatch(List<String> roleTokens, List<String> portraitTokens, String portraitName) {
        int overlap = 0;
        java.util.HashMap<String, Integer> portraitCounts = new java.util.HashMap<>();
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
        score -= (roleTokens.size() - overlap) * 3;
        score -= (portraitTokens.size() - overlap) * 2;

        String normalizedRole = normalizeRole(String.join("_", roleTokens));
        if (portraitName.equals(normalizedRole)) {
            score += 50;
        } else if (portraitName.endsWith(normalizedRole) || normalizedRole.endsWith(portraitName)) {
            score += 20;
        }

        return score;
    }

    static String findBestFuzzyPortrait(String roleName, Iterable<String> portraitNames, Map<String, List<String>> tokensByPortrait) {
        List<String> roleTokens = normalizedTokens(normalizeRole(roleName));
        if (roleTokens.isEmpty()) {
            return null;
        }

        int bestScore = Integer.MIN_VALUE;
        String bestPortrait = null;
        for (String portraitName : portraitNames) {
            List<String> portraitTokens = tokensByPortrait.get(portraitName);
            if (portraitTokens == null || portraitTokens.isEmpty()) {
                continue;
            }

            int score = scorePortraitMatch(roleTokens, portraitTokens, portraitName);
            if (score > bestScore) {
                bestScore = score;
                bestPortrait = portraitName;
            }
        }

        return bestScore >= MIN_FUZZY_MATCH_SCORE ? bestPortrait : null;
    }

    private static void addAnimalFallbacks(String normalized, LinkedHashSet<String> candidates) {
        if (normalized.startsWith("Wolf_")) {
            candidates.add("Wolf_Black");
            candidates.add("Wolf_White");
        }
        if (normalized.startsWith("Pig_") && normalized.contains("Boar")) {
            candidates.add("Pig_Wild");
            candidates.add("Pig_Wild_Piglet");
            candidates.add("Pig");
        }
        if (normalized.startsWith("Sheep_")) {
            candidates.add("Sheep");
        }
        if (normalized.startsWith("Goat_")) {
            candidates.add("Goat");
        }
        if (normalized.startsWith("Cow_")) {
            candidates.add("Cow");
        }
        if (normalized.startsWith("Horse_")) {
            candidates.add("Horse");
        }
        if (normalized.startsWith("Spider_")) {
            candidates.add("Spider");
        }
        if (normalized.startsWith("Fox_")) {
            candidates.add("Fox");
        }
        if (normalized.startsWith("Cat_")) {
            candidates.add("Cat");
        }
    }

    private static void addDescriptorStrippedVariants(String normalized, LinkedHashSet<String> candidates) {
        List<String> parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        List<String> stripped = new ArrayList<>();
        for (String part : parts) {
            if (!MATCH_IGNORED_TOKENS.contains(part)) {
                stripped.add(part);
            }
        }
        if (!stripped.isEmpty() && stripped.size() < parts.size()) {
            candidates.add(String.join("_", stripped));
        }
    }
}