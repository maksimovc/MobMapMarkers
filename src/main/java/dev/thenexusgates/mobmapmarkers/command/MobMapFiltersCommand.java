package dev.thenexusgates.mobmapmarkers.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.security.MobMapPermissions;
import dev.thenexusgates.mobmapmarkers.ui.MobMapUiText;

public final class MobMapFiltersCommand extends AbstractPlayerCommand {

    private final MobMapMarkersPlugin plugin;

    public MobMapFiltersCommand(MobMapMarkersPlugin plugin) {
        super("mobmap", "Open the mob marker filter page");
        this.plugin = plugin;
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> entityRef,
                           PlayerRef playerRef,
                           World world) {
        if (!MobMapPermissions.canOpenUi(playerRef)) {
            MobMapPermissions.sendUiDisabled(playerRef);
            return;
        }

        String input = context.getInputString();
        String[] args = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        int argumentCount = args.length;
        if (argumentCount > 0 && "mobmap".equalsIgnoreCase(args[0])) {
            argumentCount--;
        }
        if (argumentCount > 0) {
            playerRef.sendMessage(Message.raw(MobMapUiText.key(playerRef, "command.usage")));
            return;
        }

        plugin.openFilters(store, entityRef, playerRef);
    }
}