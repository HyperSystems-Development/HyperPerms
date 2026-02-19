package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Sets a user's primary/display group.
 * Usage: /hp user setprimarygroup &lt;player&gt; &lt;group&gt;
 */
public class UserSetPrimaryGroupCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> groupArg;

    public UserSetPrimaryGroupCommand(HyperPerms hyperPerms) {
        super("setprimarygroup", "Set a user's primary/display group");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String groupName = ctx.get(groupArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
        User user = PlayerResolver.resolveOrCreate(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        // Ensure user is in the group
        if (!user.getInheritedGroups().contains(groupName.toLowerCase())) {
            // Add them to the group automatically
            user.addGroup(groupName);
        }

        user.setPrimaryGroup(groupName.toLowerCase());
        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCache().invalidate(user.getUuid());

        ctx.sender().sendMessage(Message.raw("Set primary group of " + user.getFriendlyName() + " to " + groupName));
        return CompletableFuture.completedFuture(null);
    }
}
