package dev.thenexusgates.mobmapmarkers;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HytaleNpcPortraitResolverTest {

    @Test
    void normalizeRoleCanonicalizesSeparatorsAndCasing() throws Exception {
        assertEquals("Temple_Bunny_Wandering", invokeNormalizeRole("temple-bunny_wandering"));
    }

    @Test
    void buildCandidatesIncludesAliasesAndTrimmedVariants() throws Exception {
        List<String> bunnyCandidates = invokeBuildCandidates("Bunny");
        assertTrue(bunnyCandidates.contains("Bunny"));
        assertTrue(bunnyCandidates.contains("Rabbit"));

        List<String> templeCandidates = invokeBuildCandidates("Temple_Mosshorn_Plain_Wander");
        assertTrue(templeCandidates.contains("Temple_Mosshorn_Plain_Wander"));
        assertTrue(templeCandidates.contains("Mosshorn_Plain_Wander"));
        assertTrue(templeCandidates.contains("Temple_Mosshorn_Plain"));
        assertTrue(templeCandidates.contains("Temple_Mosshorn"));

        List<String> critterTokens = MobPortraitMatcher.normalizedTokens("Passive_Critter_Wander_Red");
        assertEquals(List.of("critter"), critterTokens);
    }

    @Test
    void fuzzyMatcherAcceptsDescriptorHeavyRoles() {
        String portrait = MobPortraitMatcher.findBestFuzzyPortrait(
                "Passive_Critter_Wander_Red",
                Set.of("Critter"),
                Map.of("Critter", MobPortraitMatcher.normalizedTokens("Critter")));
        assertNotNull(portrait);
        assertEquals("Critter", portrait);
    }

    private static String invokeNormalizeRole(String roleName) throws Exception {
        Method method = HytaleNpcPortraitResolver.class.getDeclaredMethod("normalizeRole", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, roleName);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeBuildCandidates(String roleName) throws Exception {
        Method method = HytaleNpcPortraitResolver.class.getDeclaredMethod("buildCandidates", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, roleName);
    }
}
