package com.hyperperms.command.debug;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Show all current contexts for a user.
 * <p>
 * Usage: /hp debug contexts <user>
 */
public class DebugContextsCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> userArg;

    public DebugContextsCommand(HyperPerms hyperPerms) {
        super("contexts", "Show all current contexts for a user");
        this.hyperPerms = hyperPerms;
        this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(userArg);
        User user = PlayerResolver.resolve(hyperPerms, identifier);

        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        var contexts = hyperPerms.getContexts(user.getUuid());

        ctx.sender().sendMessage(Message.raw("=== Contexts for " + user.getFriendlyName() + " ==="));

        if (contexts.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (no contexts)"));
        } else {
            for (var context : contexts.toSet()) {
                ctx.sender().sendMessage(Message.raw("  " + context.key() + " = " + context.value()));
            }
        }

        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("Registered calculators: " + hyperPerms.getContextManager().getCalculatorCount()));

        return CompletableFuture.completedFuture(null);
    }
}
