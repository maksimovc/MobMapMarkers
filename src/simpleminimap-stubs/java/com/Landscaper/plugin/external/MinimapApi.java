package com.Landscaper.plugin.external;

import com.Landscaper.plugin.MinimapPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class MinimapApi {

    @SuppressWarnings("unused")
    private MinimapPlugin plugin;

    public static MinimapApi getOrNull() {
        return null;
    }

    public boolean isMinimapEnabled(PlayerRef playerRef) {
        return false;
    }
}
