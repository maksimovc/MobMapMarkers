package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobFacingResolverTest {

    @Test
    void resolvesFacingFromHorizontalMovement() {
        assertTrue(MobFacingResolver.resolveFacingRight(
                new Vector3d(10, 0, 0),
                null,
                new Vector3d(9, 0, 0),
                false));

        assertFalse(MobFacingResolver.resolveFacingRight(
                new Vector3d(8.5, 0, 0),
                null,
                new Vector3d(9, 0, 0),
                true));
    }

    @Test
    void fallsBackToYawWhenMovementIsNotUseful() {
        Vector3f rotation = new Vector3f();
        rotation.setYaw(90F);
        assertTrue(MobFacingResolver.resolveFacingRight(null, rotation, null, false));

        rotation.setYaw(270F);
        assertFalse(MobFacingResolver.resolveFacingRight(null, rotation, null, true));
    }

    @Test
    void fallsBackToPreviousFacingWhenNoSignalExists() {
        assertTrue(MobFacingResolver.resolveFacingRight(null, null, null, true));
        assertFalse(MobFacingResolver.resolveFacingRight(null, null, null, false));
    }
}
