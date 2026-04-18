package dev.thenexusgates.mobmapmarkers.security;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;
import dev.thenexusgates.mobmapmarkers.ui.MobMapUiText;

public final class MobMapPermissions {

    public static final String FILTER_MAP = "mobmapmarkers.filters.map";
    public static final String FILTER_MINIMAP = "mobmapmarkers.filters.minimap";
    public static final String FILTER_COMPASS = "mobmapmarkers.filters.compass";
    public static final String BULK_MAP = "mobmapmarkers.filters.bulk.map";
    public static final String BULK_MINIMAP = "mobmapmarkers.filters.bulk.minimap";
    public static final String BULK_COMPASS = "mobmapmarkers.filters.bulk.compass";

    private MobMapPermissions() {
    }

    public static boolean canOpenUi(PlayerRef playerRef) {
        return playerRef != null && MobMapMarkersPlugin.isMobMapCommandEnabled();
    }

    public static boolean canEditSurface(PlayerRef playerRef, MobMarkerSurface surface) {
        return has(playerRef, switch (surface) {
            case MAP -> FILTER_MAP;
            case MINIMAP -> FILTER_MINIMAP;
            case COMPASS -> FILTER_COMPASS;
        });
    }

    public static boolean canBulkSurface(PlayerRef playerRef, MobMarkerSurface surface) {
        return has(playerRef, switch (surface) {
            case MAP -> BULK_MAP;
            case MINIMAP -> BULK_MINIMAP;
            case COMPASS -> BULK_COMPASS;
        });
    }

    public static void sendUiDisabled(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }

        playerRef.sendMessage(Message.raw(MobMapUiText.key(playerRef, "permission.uiDisabled")));
    }

    public static void sendDenied(PlayerRef playerRef, MobMarkerSurface surface, boolean bulkAction) {
        if (playerRef == null || surface == null) {
            return;
        }

        String surfaceLabel = switch (surface) {
            case MAP -> MobMapUiText.key(playerRef, "permission.surface.map");
            case MINIMAP -> MobMapUiText.key(playerRef, "permission.surface.minimap");
            case COMPASS -> MobMapUiText.key(playerRef, "permission.surface.compass");
        };
        String message = bulkAction
                ? MobMapUiText.keyf(playerRef, "permission.denied.bulk", surfaceLabel)
                : MobMapUiText.keyf(playerRef, "permission.denied.single", surfaceLabel);
        playerRef.sendMessage(Message.raw(message));
    }

    private static boolean has(PlayerRef playerRef, String permission) {
        if (playerRef == null || permission == null || permission.isBlank()) {
            return false;
        }

        PermissionsModule permissionsModule = PermissionsModule.get();
        if (permissionsModule == null) {
            return false;
        }

        return permissionsModule.hasPermission(playerRef.getUuid(), permission);
    }
}