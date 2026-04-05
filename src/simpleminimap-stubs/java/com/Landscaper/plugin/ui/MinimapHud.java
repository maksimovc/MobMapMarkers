package com.Landscaper.plugin.ui;

import com.Landscaper.plugin.MinimapPlugin;
import com.Landscaper.plugin.config.MinimapConfig;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public class MinimapHud {

    private boolean wailaEnabled;

    public MinimapHud(PlayerRef playerRef, MinimapPlugin plugin, MinimapConfig minimapConfig) {
    }

    protected void build(UICommandBuilder commands) {
    }

    public void refreshWithConfig(MinimapConfig minimapConfig) {
    }

    public void forceUpdate(PlayerRef playerRef, World world) {
    }

    public void tick(PlayerRef playerRef, World world) {
    }

    public boolean isWailaEnabled() {
        return wailaEnabled;
    }

    public void setWailaEnabled(boolean enabled) {
        this.wailaEnabled = enabled;
    }

    protected void update(boolean reliable, UICommandBuilder commands) {
    }
}
