package com.hyperperms.command.debug;

import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Toggle verbose permission check logging.
 * <p>
 * Usage: /hp debug perms
 * <p>
 * When enabled, all permission checks are logged to console with detailed info,
 * which helps debug issues between plugins like HyperHomes.
 */
public class DebugPermsCommand extends AbstractCommand {

    public DebugPermsCommand() {
        super("perms", "Toggle verbose permission check logging");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        boolean currentState = Logger.isPermissionDebugEnabled();
        Logger.setPermissionDebugEnabled(!currentState);

        if (!currentState) {
            ctx.sender().sendMessage(Message.raw("Permission debug logging ENABLED").color(GREEN));
            ctx.sender().sendMessage(Message.raw("All permission checks will now be logged to console with detailed info.").color(GRAY));
            ctx.sender().sendMessage(Message.raw("This helps debug issues between plugins like HyperHomes.").color(GRAY));
            ctx.sender().sendMessage(Message.raw("Run /hp debug perms again to disable.").color(GRAY));
        } else {
            ctx.sender().sendMessage(Message.raw("Permission debug logging DISABLED").color(RED));
        }
        return CompletableFuture.completedFuture(null);
    }
}
