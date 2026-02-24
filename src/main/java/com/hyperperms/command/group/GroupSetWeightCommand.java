package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

/**
 * Sets the weight (priority) of a group.
 * <p>
 * Usage: /hp group setweight &lt;group&gt; &lt;weight&gt;
 */
public class GroupSetWeightCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final RequiredArg<Integer> weightArg;

    public GroupSetWeightCommand(HyperPerms hyperPerms) {
        super("setweight", "Set a group's weight/priority");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.weightArg = describeArg("weight", "Weight value", ArgTypes.INTEGER);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        int weight = ctx.get(weightArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        group.setWeight(weight);
        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCacheInvalidator().invalidateGroup(groupName);

        ctx.sender().sendMessage(Message.raw("Set weight of group " + groupName + " to " + weight));
        return CompletableFuture.completedFuture(null);
    }
}
