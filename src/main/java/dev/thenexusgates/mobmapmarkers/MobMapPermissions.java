package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class MobMapPermissions {

    static final String USE = "mobmapmarkers.use";
    static final String ADMIN = "mobmapmarkers.admin";
    static final String FILTER_MAP = "mobmapmarkers.filters.map";
    static final String FILTER_MINIMAP = "mobmapmarkers.filters.minimap";
    static final String FILTER_COMPASS = "mobmapmarkers.filters.compass";
    static final String BULK_MAP = "mobmapmarkers.filters.bulk.map";
    static final String BULK_MINIMAP = "mobmapmarkers.filters.bulk.minimap";
    static final String BULK_COMPASS = "mobmapmarkers.filters.bulk.compass";

    private MobMapPermissions() {
    }

    static boolean canOpenUi(PlayerRef playerRef) {
        return has(playerRef, USE);
    }

    static boolean canEditSurface(PlayerRef playerRef, MobMarkerSurface surface) {
        return has(playerRef, switch (surface) {
            case MAP -> FILTER_MAP;
            case MINIMAP -> FILTER_MINIMAP;
            case COMPASS -> FILTER_COMPASS;
        });
    }

    static boolean canBulkSurface(PlayerRef playerRef, MobMarkerSurface surface) {
        return has(playerRef, switch (surface) {
            case MAP -> BULK_MAP;
            case MINIMAP -> BULK_MINIMAP;
            case COMPASS -> BULK_COMPASS;
        });
    }

    static void sendDenied(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }

        playerRef.sendMessage(Message.raw(MobMapUiText.choose(
                playerRef,
                "You do not have permission to use /mobmap.",
                "У вас немає дозволу на використання /mobmap.")));
    }

    static void sendDenied(PlayerRef playerRef, MobMarkerSurface surface, boolean bulkAction) {
        if (playerRef == null || surface == null) {
            return;
        }

        String surfaceLabel = switch (surface) {
            case MAP -> MobMapUiText.choose(playerRef, "map", "мапою");
            case MINIMAP -> MobMapUiText.choose(playerRef, "minimap", "мінімапою");
            case COMPASS -> MobMapUiText.choose(playerRef, "compass", "компасом");
        };
        String message = bulkAction
                ? MobMapUiText.format(playerRef,
                "You do not have permission to bulk-manage %s visibility.",
                "У вас немає дозволу масово керувати видимістю для %s.",
                surfaceLabel)
                : MobMapUiText.format(playerRef,
                "You do not have permission to change %s visibility.",
                "У вас немає дозволу змінювати видимість для %s.",
                surfaceLabel);
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

        return permissionsModule.hasPermission(playerRef.getUuid(), ADMIN)
                || permissionsModule.hasPermission(playerRef.getUuid(), permission);
    }
}