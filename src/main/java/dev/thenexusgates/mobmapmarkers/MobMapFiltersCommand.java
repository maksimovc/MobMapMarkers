package dev.thenexusgates.mobmapmarkers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class MobMapFiltersCommand extends AbstractPlayerCommand {

    private final MobMapMarkersPlugin plugin;

    MobMapFiltersCommand(MobMapMarkersPlugin plugin) {
        super("mobmap", "Open the mob marker filter page");
        this.plugin = plugin;
        requirePermission(MobMapPermissions.USE);
        setAllowsExtraArguments(true);
        addAliases("mobmarkers");
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> entityRef,
                           PlayerRef playerRef,
                           World world) {
        if (!MobMapPermissions.canOpenUi(playerRef)) {
            MobMapPermissions.sendDenied(playerRef);
            return;
        }

        String input = context.getInputString();
        String[] args = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        int start = 0;
        if (args.length > 0 && ("mobmap".equalsIgnoreCase(args[0]) || "mobmarkers".equalsIgnoreCase(args[0]))) {
            start = 1;
        }

        String subcommand = args.length > start ? args[start].trim().toLowerCase() : "";
        if (!subcommand.isEmpty() && !"filters".equals(subcommand)) {
            playerRef.sendMessage(Message.raw(MobMapUiText.choose(
                    playerRef,
                    "Usage: /mobmap or /mobmap filters",
                    "Використання: /mobmap або /mobmap filters")));
            return;
        }

        plugin.openFilters(store, entityRef, playerRef);
    }
}