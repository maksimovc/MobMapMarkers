package dev.thenexusgates.mobmapmarkers.util;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

public final class MobFacingResolver {

    private MobFacingResolver() {
    }

    public static boolean resolveFacingRight(Vector3d position, Vector3f rotation,
                                             Vector3d previousPosition, boolean previousFacingRight) {
        if (position != null && previousPosition != null) {
            double dx = position.x - previousPosition.x;
            double dz = position.z - previousPosition.z;
            if (Math.abs(dx) >= 0.08D && Math.abs(dx) >= Math.abs(dz)) {
                return dx >= 0D;
            }
        }

        if (rotation != null) {
            double yawRadians = Math.toRadians(rotation.getYaw());
            double horizontalFacing = Math.sin(yawRadians);
            if (Math.abs(horizontalFacing) >= 0.2D) {
                return horizontalFacing >= 0D;
            }
        }

        return previousFacingRight;
    }
}
