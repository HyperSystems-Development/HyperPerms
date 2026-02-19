package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

/**
 * Sets or clears a group's display name.
 * <p>
 * Usage: /hp group setdisplayname &lt;group&gt; [displayname]
 */
public class GroupSetDisplayNameCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final OptionalArg<String> displayNameArg;

    public GroupSetDisplayNameCommand(HyperPerms hyperPerms) {
        super("setdisplayname", "Set a group's display name");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.displayNameArg = describeOptionalArg("displayname", "Display name (omit to clear)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        String displayName = ctx.get(displayNameArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        if (displayName == null || displayName.isEmpty()) {
            group.setDisplayName(null);
            ctx.sender().sendMessage(Message.raw("Cleared display name for group " + groupName));
        } else {
            group.setDisplayName(displayName);
            ctx.sender().sendMessage(Message.raw("Set display name of group " + groupName + " to \"" + displayName + "\""));
        }

        hyperPerms.getGroupManager().saveGroup(group);
        return CompletableFuture.completedFuture(null);
    }
}
