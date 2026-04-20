package dev.thenexusgates.mobmapmarkers.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MobPortraitMatcher {

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
            "Dungeon", "Temple", "Test", "Tamed", "Friendly", "Passive", "Companion", "Summoned");
    private static final Set<String> TRAILING_SUFFIXES = Set.of(
            "Wander", "Wandering", "Patrol", "Static", "Alerted", "Sleeping", "Tutorial");
    private static final Set<String> MATCH_IGNORED_TOKENS = Set.of(
            "Black", "White", "Brown", "Red", "Blue", "Green", "Yellow", "Orange",
            "Gray", "Grey", "Pink", "Purple", "Gold", "Silver", "Light", "Dark", "Calico",
            "Armored", "Armoured", "Berserk", "Berserker", "Chief", "Elite", "Heavy",
            "Male", "Female", "Adult", "Child", "Baby", "Young", "Old", "Small", "Large", "Wild", "Tamed",
            "Giant", "Alpha", "Beta", "Test", "Unified");
    private static final Set<String> STRUCTURAL_TOKENS = Set.of(
            "Test", "Temple", "Component", "Instruction", "Template", "Beacon", "Notify", "Receive",
            "Sensor", "Action", "Sequence", "Computable", "Condition", "Filter", "Tag", "Guided",
            "Shooter", "Dummy", "Static", "Override", "Desired", "Altitude", "Weight", "Ray",
            "Seek", "Takeoff", "TakeOff", "Land", "Block", "Pos", "Path", "List", "Switch",
            "Effect", "Entity", "Combat", "Charge", "Culling", "Friendly", "Damage", "Death",
            "Particles", "Benchmark", "Search", "Warning", "Warn", "Shot", "Greeting", "Converge",
            "Return", "Home", "Dropped", "Meat", "Offensive", "Reputation", "Sleep", "Spar",
            "Can", "Low", "Hp", "Day", "Attack", "Melee", "Ranged", "Charged", "Bow", "Flee");
    private static final Set<String> EXCLUDED_ROLE_TOKENS = Set.of("Component", "Test", "Template");
        private static final Set<String> TRAVERSAL_EXCLUDED_ROLE_TOKENS = Set.of("Component", "Test");

    private MobPortraitMatcher() {
    }

    public static String normalizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }

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

    public static boolean isComponentRole(String roleName) {
        String normalized = normalizeRole(roleName);
        return normalized.equals("Component") || normalized.startsWith("Component_");
    }

    public static boolean isExcludedRole(String roleName) {
        return hasExcludedToken(roleName, EXCLUDED_ROLE_TOKENS);
    }

    public static boolean isTraversalExcludedRole(String roleName) {
        return hasExcludedToken(roleName, TRAVERSAL_EXCLUDED_ROLE_TOKENS);
    }

    private static boolean hasExcludedToken(String roleName, Set<String> excludedTokens) {
        String normalized = normalizeRole(roleName);
        if (normalized.isBlank()) {
            return false;
        }

        for (String token : normalized.split("_")) {
            if (excludedTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> buildCandidates(String roleName) {
        String normalized = normalizeRole(roleName);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (normalized.isBlank()) {
            return List.of();
        }

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

        addStructuralStrippedVariants(normalized, candidates);
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

    public static String findMatchingPortrait(String roleName, Iterable<String> portraitNames) {
        if (roleName == null || roleName.isBlank() || portraitNames == null) {
            return null;
        }

        LinkedHashSet<String> available = collectAvailableNames(portraitNames);
        if (available.isEmpty()) {
            return null;
        }

        String strictMatch = findBestCandidateMatch(roleName, available);
        if (strictMatch != null) {
            return strictMatch;
        }

        return findUnambiguousFamilyPortrait(roleName, available);
    }

    public static String findBestCandidateMatch(String roleName, Iterable<String> names) {
        if (roleName == null || roleName.isBlank() || names == null) {
            return null;
        }

        LinkedHashSet<String> available = collectAvailableNames(names);
        if (available.isEmpty()) {
            return null;
        }

        for (String candidate : buildCandidates(roleName)) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    public static List<String> normalizedTokens(String name) {
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

    public static int scorePortraitMatch(List<String> roleTokens, List<String> portraitTokens, String portraitName) {
        int overlap = 0;
        HashMap<String, Integer> portraitCounts = new HashMap<>();
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

    public static String findBestFuzzyPortrait(String roleName, Iterable<String> portraitNames,
                                               Map<String, List<String>> tokensByPortrait) {
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

    private static LinkedHashSet<String> collectAvailableNames(Iterable<String> names) {
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (String portraitName : names) {
            if (portraitName != null && !portraitName.isBlank()) {
                available.add(portraitName);
            }
        }
        return available;
    }

    private static void addAnimalFallbacks(String normalized, LinkedHashSet<String> candidates) {
        List<String> parts = Arrays.asList(normalized.split("_"));
        if (parts.contains("Kitten")) {
            candidates.add("Kitten");
            candidates.add("Cat");
        }
        if (parts.contains("Cat") || parts.contains("Cats")) {
            candidates.add("Cat");
        }
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

    private static void addStructuralStrippedVariants(String normalized, LinkedHashSet<String> candidates) {
        List<String> parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        if (parts.isEmpty()) {
            return;
        }

        while (!parts.isEmpty() && LEADING_PREFIXES.contains(parts.get(0))) {
            parts.remove(0);
        }

        if (!parts.isEmpty()) {
            candidates.add(String.join("_", parts));
        }

        List<String> structuralStripped = new ArrayList<>();
        for (String part : parts) {
            if (!STRUCTURAL_TOKENS.contains(part)) {
                structuralStripped.add(part);
            }
        }

        if (!structuralStripped.isEmpty() && structuralStripped.size() < parts.size()) {
            String canonical = String.join("_", structuralStripped);
            candidates.add(canonical);

            if (structuralStripped.size() > 1) {
                for (int start = 1; start < structuralStripped.size(); start++) {
                    candidates.add(String.join("_", structuralStripped.subList(start, structuralStripped.size())));
                }
                for (int end = structuralStripped.size() - 1; end > 0; end--) {
                    candidates.add(String.join("_", structuralStripped.subList(0, end)));
                }
            }
        }
    }

    private static String findUnambiguousFamilyPortrait(String roleName, LinkedHashSet<String> portraitNames) {
        List<String> roleTokens = normalizedTokens(normalizeRole(roleName));
        if (roleTokens.isEmpty()) {
            return null;
        }

        String familyToken = roleTokens.get(0);
        String familyPrefix = familyToken.substring(0, 1).toUpperCase(Locale.ROOT)
                + familyToken.substring(1).toLowerCase(Locale.ROOT);
        List<String> familyMatches = new ArrayList<>();
        for (String portraitName : portraitNames) {
            if (portraitName.equals(familyPrefix) || portraitName.startsWith(familyPrefix + "_")) {
                familyMatches.add(portraitName);
            }
        }

        return familyMatches.size() == 1 ? familyMatches.get(0) : null;
    }
}