package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.lang.reflect.Field;
import java.util.logging.Logger;

final class MobMapMarkerSupport {

    private static final Logger LOGGER = Logger.getLogger(MobMapMarkerSupport.class.getName());
    private static final Field CLIENT_HAS_WORLDMAP_VISIBLE_FIELD = resolveClientHasWorldMapVisibleField();

    private MobMapMarkerSupport() {
    }

    static boolean isWorldMapVisible(Player viewer) {
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
            LOGGER.warning("[MobMapMarkers] Failed to read world map visibility: " + e.getMessage());
            return false;
        }
    }

    static MapMarker createPlainMarker(String markerId, String markerLabel, String markerImage, Transform transform) {
        com.hypixel.hytale.protocol.Transform transformPacket = PositionUtil.toTransformPacket(transform);
        FormattedMessage formattedMessage = null;
        if (markerLabel != null && !markerLabel.isBlank()) {
            formattedMessage = new FormattedMessage();
            formattedMessage.rawText = markerLabel;
        }

        return new MapMarker(markerId, formattedMessage, markerImage, transformPacket, null, null);
    }

    private static Field resolveClientHasWorldMapVisibleField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LOGGER.warning("[MobMapMarkers] WorldMapTracker.clientHasWorldMapVisible is unavailable: " + e.getMessage());
            return null;
        }
    }
}
