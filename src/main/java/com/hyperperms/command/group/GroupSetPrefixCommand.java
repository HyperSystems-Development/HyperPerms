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
 * Sets or clears a group's chat prefix, with optional priority.
 * <p>
 * Usage: /hp group setprefix &lt;group&gt; [prefix] [priority]
 */
public class GroupSetPrefixCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final OptionalArg<String> prefixArg;
    private final OptionalArg<Integer> priorityArg;

    public GroupSetPrefixCommand(HyperPerms hyperPerms) {
        super("setprefix", "Set a group's chat prefix");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.prefixArg = describeOptionalArg("prefix", "Prefix text (omit to clear)", ArgTypes.STRING);
        this.priorityArg = describeOptionalArg("priority", "Priority for multi-group resolution", ArgTypes.INTEGER);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        String prefix = stripQuotes(ctx.get(prefixArg));
        Integer priority = ctx.get(priorityArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        if (prefix == null || prefix.isEmpty()) {
            group.setPrefix(null);
            ctx.sender().sendMessage(Message.raw("Cleared prefix for group " + groupName));
        } else {
            group.setPrefix(prefix);
            ctx.sender().sendMessage(Message.raw("Set prefix of group " + groupName + " to \"" + prefix + "\""));
        }

        if (priority != null) {
            group.setPrefixPriority(priority);
            ctx.sender().sendMessage(Message.raw("Set prefix priority to " + priority));
        }

        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCache().invalidateAll();

        return CompletableFuture.completedFuture(null);
    }
}
