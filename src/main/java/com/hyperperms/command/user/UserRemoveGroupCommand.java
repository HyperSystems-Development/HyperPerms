package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.PermissionHolder;
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
 * Removes a user from a group.
 * Usage: /hp user removegroup &lt;player&gt; &lt;group&gt;
 */
public class UserRemoveGroupCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> groupArg;

    public UserRemoveGroupCommand(HyperPerms hyperPerms) {
        super("removegroup", "Remove a user from a group");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String groupName = ctx.get(groupArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        var result = user.removeGroup(groupName);
        if (result == PermissionHolder.DataMutateResult.SUCCESS) {
            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCacheInvalidator().invalidate(user.getUuid());
            ctx.sender().sendMessage(Message.raw("Removed user " + user.getFriendlyName() + " from group " + groupName));
        } else {
            ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " is not in group " + groupName));
        }
        return CompletableFuture.completedFuture(null);
    }
}
