package dev.thenexusgates.mobmapmarkers.marker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.thenexusgates.mobmapmarkers.catalog.MobNameLocalization;

import java.lang.reflect.Field;

public final class MobMapMarkerSupport {

    public static final String MARKER_PREFIX = "MobMapMarker-";
    private static final Field CLIENT_HAS_WORLDMAP_VISIBLE_FIELD = resolveClientHasWorldMapVisibleField();

    private MobMapMarkerSupport() {
    }

    public static boolean isWorldMapVisible(Player viewer) {
        if (viewer == null) {
            return false;
        }

        Field field = CLIENT_HAS_WORLDMAP_VISIBLE_FIELD;
        if (field == null) {
            return false;
        }

        try {
            WorldMapTracker tracker = viewer.getWorldMapTracker();
            if (tracker == null) {
                return false;
            }

            return field.getBoolean(tracker);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static MapMarker createPlainMarker(String markerId, FormattedMessage markerLabel, String markerImage,
                                              Transform transform) {
        com.hypixel.hytale.protocol.Transform transformPacket = PositionUtil.toTransformPacket(transform);
        return new MapMarker(markerId, markerLabel, markerImage, transformPacket, null, null);
    }

    public static boolean isManagedMarkerId(String markerId) {
        return markerId != null && markerId.startsWith(MARKER_PREFIX);
    }

    public static FormattedMessage createMarkerLabel(String displayName, String language, boolean showMobNames,
                                                     boolean showDistance, double distanceSquared, boolean includeDistance) {
        if (!showMobNames && !showDistance) {
            return null;
        }

        String safeName = displayName != null && !displayName.isBlank() ? displayName : "Mob";
        String rawText;
        if (!includeDistance || !showDistance) {
            rawText = showMobNames ? safeName : null;
        } else {
            int distance = (int) Math.sqrt(distanceSquared);
            String localizedDistance = MobNameLocalization.formatDistanceMeters(distance, language);
            rawText = showMobNames ? safeName + " (" + localizedDistance + ")" : localizedDistance;
        }

        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        FormattedMessage formattedMessage = new FormattedMessage();
        formattedMessage.rawText = rawText;
        return formattedMessage;
    }

    private static Field resolveClientHasWorldMapVisibleField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
