package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Sets or clears a user's custom prefix.
 * Usage: /hp user setprefix &lt;player&gt; [prefix]
 */
public class UserSetPrefixCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final OptionalArg<String> prefixArg;

    public UserSetPrefixCommand(HyperPerms hyperPerms) {
        super("setprefix", "Set a user's custom prefix");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.prefixArg = describeOptionalArg("prefix", "Prefix text (omit to clear)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String prefix = stripQuotes(ctx.get(prefixArg));

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        if (prefix == null || prefix.isEmpty()) {
            user.setCustomPrefix(null);
            ctx.sender().sendMessage(Message.raw("Cleared custom prefix for " + user.getFriendlyName()));
        } else {
            user.setCustomPrefix(prefix);
            ctx.sender().sendMessage(Message.raw("Set custom prefix of " + user.getFriendlyName() + " to \"" + prefix + "\""));
        }

        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCache().invalidate(user.getUuid());

        return CompletableFuture.completedFuture(null);
    }
}
