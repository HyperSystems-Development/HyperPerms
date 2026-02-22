package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpContainerCommand;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Container command for managing group parent (inheritance) relationships.
 * <p>
 * Subcommands: add, remove
 * <p>
 * Usage: /hp group parent add|remove ...
 */
public class GroupParentCommand extends HpContainerCommand {

    public GroupParentCommand(HyperPerms hyperPerms) {
        super("parent", "Manage group parents (inheritance)");
        addSubCommand(new AddCommand(hyperPerms));
        addSubCommand(new RemoveCommand(hyperPerms));
    }

    // ==================== Add Subcommand ====================

    private static class AddCommand extends HpSubCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;
        private final OptionalArg<String> durationArg;

        AddCommand(HyperPerms hyperPerms) {
            super("add", "Add a parent group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
            this.durationArg = describeOptionalArg("duration", "Duration (e.g. 1d2h30m, permanent)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);
            String durationStr = ctx.get(durationArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            Group parent = hyperPerms.getGroupManager().getGroup(parentName);
            if (parent == null) {
                ctx.sender().sendMessage(Message.raw("Parent group not found: " + parentName));
                return CompletableFuture.completedFuture(null);
            }

            if (groupName.equalsIgnoreCase(parentName)) {
                ctx.sender().sendMessage(Message.raw("A group cannot inherit from itself"));
                return CompletableFuture.completedFuture(null);
            }

            // Parse optional duration
            Instant expiry = null;
            if (durationStr != null && !durationStr.isBlank()) {
                Optional<Duration> duration = TimeUtil.parseDuration(durationStr);
                if (duration.isEmpty() && !durationStr.equalsIgnoreCase("permanent")
                        && !durationStr.equalsIgnoreCase("perm") && !durationStr.equalsIgnoreCase("forever")) {
                    ctx.sender().sendMessage(Message.raw("Invalid duration: " + durationStr + ". Use formats like 1d, 2h30m, 1w"));
                    return CompletableFuture.completedFuture(null);
                }
                if (duration.isPresent()) {
                    expiry = TimeUtil.expiryFromDuration(duration.get());
                }
            }

            group.addParent(parentName, expiry);
            hyperPerms.getGroupManager().saveGroup(group);
            hyperPerms.getCache().invalidateAll();
            String expiryMsg = expiry != null ? " (" + TimeUtil.formatExpiry(expiry) + ")" : "";
            ctx.sender().sendMessage(Message.raw("Group " + groupName + " now inherits from " + parentName + expiryMsg));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Remove Subcommand ====================

    private static class RemoveCommand extends HpSubCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;

        RemoveCommand(HyperPerms hyperPerms) {
            super("remove", "Remove a parent group");
            this.hyperPerms = hyperPerms;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);

            Group group = hyperPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            var result = group.removeParent(parentName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                hyperPerms.getGroupManager().saveGroup(group);
                hyperPerms.getCache().invalidateAll();
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " no longer inherits from " + parentName));
            } else {
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not inherit from " + parentName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
