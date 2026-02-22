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
 * Debug permission resolution step-by-step.
 * <p>
 * Temporarily enables verbose mode to capture the resolution trace,
 * then restores the previous state.
 * <p>
 * Usage: /hp debug resolve <user> <permission>
 */
public class DebugResolveCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> userArg;
    private final RequiredArg<String> permArg;

    public DebugResolveCommand(HyperPerms hyperPerms) {
        super("resolve", "Debug permission resolution step-by-step");
        this.hyperPerms = hyperPerms;
        this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission to resolve", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(userArg);
        String permission = ctx.get(permArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Resolving: " + permission + " for " + user.getFriendlyName() + " ==="));

        // Get current contexts
        var contexts = hyperPerms.getContexts(user.getUuid());
        ctx.sender().sendMessage(Message.raw("Current contexts: " + contexts));

        // Enable verbose mode temporarily to capture the trace
        boolean wasVerbose = hyperPerms.isVerboseMode();
        hyperPerms.setVerboseMode(true);

        boolean result = hyperPerms.hasPermission(user.getUuid(), permission, contexts);

        hyperPerms.setVerboseMode(wasVerbose);

        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("Final result: " + (result ? "ALLOWED" : "DENIED")));
        ctx.sender().sendMessage(Message.raw("(Check console for detailed trace if verbose logging is enabled)"));

        return CompletableFuture.completedFuture(null);
    }
}
