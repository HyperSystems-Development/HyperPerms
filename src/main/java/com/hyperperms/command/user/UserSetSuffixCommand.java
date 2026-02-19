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
 * Sets or clears a user's custom suffix.
 * Usage: /hp user setsuffix &lt;player&gt; [suffix]
 */
public class UserSetSuffixCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final OptionalArg<String> suffixArg;

    public UserSetSuffixCommand(HyperPerms hyperPerms) {
        super("setsuffix", "Set a user's custom suffix");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.suffixArg = describeOptionalArg("suffix", "Suffix text (omit to clear)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String suffix = stripQuotes(ctx.get(suffixArg));

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        if (suffix == null || suffix.isEmpty()) {
            user.setCustomSuffix(null);
            ctx.sender().sendMessage(Message.raw("Cleared custom suffix for " + user.getFriendlyName()));
        } else {
            user.setCustomSuffix(suffix);
            ctx.sender().sendMessage(Message.raw("Set custom suffix of " + user.getFriendlyName() + " to \"" + suffix + "\""));
        }

        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCache().invalidate(user.getUuid());

        return CompletableFuture.completedFuture(null);
    }
}
