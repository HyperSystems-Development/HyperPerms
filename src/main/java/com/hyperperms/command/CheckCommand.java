package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Check if a player has a specific permission.
 * <p>
 * Usage: /hp check <player> <permission>
 */
public class CheckCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> permArg;

    public CheckCommand(HyperPerms hyperPerms) {
        super("check", "Check if a player has a permission");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node to check", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String permission = ctx.get(permArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("✗ User not found: " + identifier).color(RED));
            return CompletableFuture.completedFuture(null);
        }

        boolean hasPermission = hyperPerms.hasPermission(user.getUuid(), permission);

        List<Message> parts = new ArrayList<>();
        if (hasPermission) {
            parts.add(Message.raw("✓ ").color(GREEN));
            parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
            parts.add(Message.raw(" has permission ").color(GRAY));
            parts.add(Message.raw(permission).color(GREEN));
        } else {
            parts.add(Message.raw("✗ ").color(RED));
            parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
            parts.add(Message.raw(" does NOT have permission ").color(GRAY));
            parts.add(Message.raw(permission).color(RED));
        }
        ctx.sender().sendMessage(join(parts));

        return CompletableFuture.completedFuture(null);
    }
}
