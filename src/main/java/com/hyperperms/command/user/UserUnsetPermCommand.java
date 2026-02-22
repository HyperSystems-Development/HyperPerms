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
 * Removes a permission from a user.
 * Usage: /hp user unsetperm &lt;player&gt; &lt;permission&gt;
 */
public class UserUnsetPermCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> permArg;

    public UserUnsetPermCommand(HyperPerms hyperPerms) {
        super("unsetperm", "Remove a permission from a user");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String permission = ctx.get(permArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        var result = user.removeNode(permission);
        if (result == PermissionHolder.DataMutateResult.SUCCESS) {
            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());
            ctx.sender().sendMessage(Message.raw("Removed " + permission + " from user " + user.getFriendlyName()));
        } else {
            ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " does not have permission " + permission));
        }
        return CompletableFuture.completedFuture(null);
    }
}
