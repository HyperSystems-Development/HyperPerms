package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sets or clears the expiry on an existing group permission.
 * <p>
 * Usage: /hp group setexpiry &lt;group&gt; &lt;permission&gt; &lt;duration&gt;
 */
public class GroupSetExpiryCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> groupArg;
    private final RequiredArg<String> permArg;
    private final RequiredArg<String> durationArg;

    public GroupSetExpiryCommand(HyperPerms hyperPerms) {
        super("setexpiry", "Set or clear expiry on a group permission");
        this.hyperPerms = hyperPerms;
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
        this.durationArg = describeArg("duration", "Duration (e.g. 1d2h30m) or 'permanent'", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(groupArg);
        String permission = ctx.get(permArg).toLowerCase();
        String durationStr = ctx.get(durationArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // Find existing node
        Node existingNode = group.getNodes().stream()
                .filter(n -> n.getPermission().equals(permission))
                .findFirst()
                .orElse(null);

        if (existingNode == null) {
            ctx.sender().sendMessage(Message.raw("Permission " + permission + " not found on group " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // Parse duration
        Instant newExpiry = null;
        if (!durationStr.equalsIgnoreCase("permanent") && !durationStr.equalsIgnoreCase("perm")
                && !durationStr.equalsIgnoreCase("forever")) {
            Optional<Duration> duration = TimeUtil.parseDuration(durationStr);
            if (duration.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("Invalid duration: " + durationStr + ". Use formats like 1d, 2h30m, 1w, or 'permanent'"));
                return CompletableFuture.completedFuture(null);
            }
            newExpiry = TimeUtil.expiryFromDuration(duration.get());
        }

        // Replace with updated expiry
        Node newNode = existingNode.withExpiry(newExpiry);
        group.setNode(newNode);
        hyperPerms.getGroupManager().saveGroup(group);
        hyperPerms.getCacheInvalidator().invalidateGroup(groupName);

        String expiryDisplay = newExpiry != null ? TimeUtil.formatExpiry(newExpiry) : "permanent";
        ctx.sender().sendMessage(Message.raw("Set expiry on " + permission + " in group " + groupName + " to " + expiryDisplay));
        return CompletableFuture.completedFuture(null);
    }
}
