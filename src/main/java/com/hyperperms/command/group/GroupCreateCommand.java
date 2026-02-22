package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

/**
 * Creates a new permission group.
 * <p>
 * Usage: /hp group create &lt;name&gt;
 */
public class GroupCreateCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> nameArg;

    public GroupCreateCommand(HyperPerms hyperPerms) {
        super("create", "Create a new group");
        this.hyperPerms = hyperPerms;
        this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(nameArg);
        if (hyperPerms.getGroupManager().getGroup(groupName) != null) {
            ctx.sender().sendMessage(Message.raw("Group already exists: " + groupName));
            return CompletableFuture.completedFuture(null);
        }
        hyperPerms.getGroupManager().createGroup(groupName);
        ctx.sender().sendMessage(Message.raw("Created group: " + groupName));
        return CompletableFuture.completedFuture(null);
    }
}
