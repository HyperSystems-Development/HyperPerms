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
 * Removes a permission node from a group.
 * <p>
 * Usage: /hp group unsetperm &lt;group&gt; &lt;permission&gt;
 */
public class GroupUnsetPermCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final RequiredArg<String> permArg;

    public GroupUnsetPermCommand(HyperPerms hyperPerms) {
        super("unsetperm", "Remove a permission from a group");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        String permission = ctx.get(permArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        var result = group.removeNode(permission);
        if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
            hyperPerms.getGroupManager().saveGroup(group);
            hyperPerms.getCacheInvalidator().invalidateGroup(groupName);
            ctx.sender().sendMessage(Message.raw("Removed " + permission + " from group " + groupName));
        } else {
            ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not have permission " + permission));
        }
        return CompletableFuture.completedFuture(null);
    }
}
