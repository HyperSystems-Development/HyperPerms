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
 * Sets or clears a group's chat suffix, with optional priority.
 * <p>
 * Usage: /hp group setsuffix &lt;group&gt; [suffix] [priority]
 */
public class GroupSetSuffixCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final OptionalArg<String> suffixArg;
    private final OptionalArg<Integer> priorityArg;

    public GroupSetSuffixCommand(HyperPerms hyperPerms) {
        super("setsuffix", "Set a group's chat suffix");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.suffixArg = describeOptionalArg("suffix", "Suffix text (omit to clear)", ArgTypes.STRING);
        this.priorityArg = describeOptionalArg("priority", "Priority for multi-group resolution", ArgTypes.INTEGER);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        String suffix = stripQuotes(ctx.get(suffixArg));
        Integer priority = ctx.get(priorityArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        if (suffix == null || suffix.isEmpty()) {
            group.setSuffix(null);
            ctx.sender().sendMessage(Message.raw("Cleared suffix for group " + groupName));
        } else {
            group.setSuffix(suffix);
            ctx.sender().sendMessage(Message.raw("Set suffix of group " + groupName + " to \"" + suffix + "\""));
        }

        if (priority != null) {
            group.setSuffixPriority(priority);
            ctx.sender().sendMessage(Message.raw("Set suffix priority to " + priority));
        }

        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCacheInvalidator().invalidateGroup(groupName);

        return CompletableFuture.completedFuture(null);
    }
}
