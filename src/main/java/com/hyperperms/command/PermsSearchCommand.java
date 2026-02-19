package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Search for permissions by name or description.
 * <p>
 * Usage: /hp perms search <query>
 */
public class PermsSearchCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> queryArg;

    public PermsSearchCommand(HyperPerms hyperPerms) {
        super("search", "Search for permissions by name or description");
        this.hyperPerms = hyperPerms;
        this.queryArg = describeArg("query", "Search query", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String query = ctx.get(queryArg);
        var registry = hyperPerms.getPermissionRegistry();

        var results = registry.search(query);

        if (results.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("No permissions found matching: " + query));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Search results for '" + query + "' (" + results.size() + " found) ==="));

        int shown = 0;
        for (var perm : results) {
            if (shown >= 20) {
                ctx.sender().sendMessage(Message.raw("... and " + (results.size() - 20) + " more"));
                break;
            }
            ctx.sender().sendMessage(Message.raw("  " + perm.getPermission() + " [" + perm.getCategory() + "]"));
            ctx.sender().sendMessage(Message.raw("    " + perm.getDescription()));
            shown++;
        }

        return CompletableFuture.completedFuture(null);
    }
}
