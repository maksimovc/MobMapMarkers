package com.Landscaper.plugin;

import com.Landscaper.plugin.config.MinimapConfig;
import com.Landscaper.plugin.external.MultipleHudHelper;
import com.Landscaper.plugin.ui.MinimapHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

public class MinimapPlugin {

    @SuppressWarnings("unused")
    private final Map<PlayerRef, MinimapHud> huds = new HashMap<>();
    @SuppressWarnings("unused")
    private final Map<PlayerRef, ScheduledFuture<?>> tickTasks = new HashMap<>();
    @SuppressWarnings("unused")
    private MultipleHudHelper multipleHudHelper;

    public MinimapConfig getPlayerConfig(PlayerRef playerRef) {
        return new MinimapConfig();
    }

    @SuppressWarnings("unused")
    private void initializePlayerHud(PlayerRef playerRef, MinimapHud hud, MinimapConfig config) {
    }
}
