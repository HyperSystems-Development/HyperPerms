package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Reload HyperPerms configuration.
 * <p>
 * Usage: /hp reload
 */
public class ReloadCommand extends AbstractCommand {
    private final HyperPerms hyperPerms;

    public ReloadCommand(HyperPerms hyperPerms) {
        super("reload", "Reload HyperPerms configuration");
        this.hyperPerms = hyperPerms;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(Message.raw("Reloading HyperPerms...").color(GRAY));
        hyperPerms.reload();
        ctx.sender().sendMessage(Message.raw("✓ HyperPerms reloaded successfully!").color(GREEN));
        return CompletableFuture.completedFuture(null);
    }
}
