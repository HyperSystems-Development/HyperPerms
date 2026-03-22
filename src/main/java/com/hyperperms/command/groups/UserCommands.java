package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.api.PermissionHolder;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.api.context.Context;
import com.hyperperms.api.context.ContextSet;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp user subcommands.
 */
@CommandGroup(name = "user", description = "Manage users")
public class UserCommands {

    private static final Color AMBER = new Color(255, 200, 0);
    private static final Color CYAN = new Color(85, 255, 255);
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000;

    private final HyperPerms plugin;

    public UserCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== info ====================

    @Command(name = "info", description = "Show user's groups and permissions")
    public CompletableFuture<Void> info(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Note: User must be online or have existing data"));
            return CompletableFuture.completedFuture(null);
        }

        int width = 42;
        String label = "User: " + user.getFriendlyName();
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        // UUID
        parts.add(Message.raw("  UUID: ").color(GRAY));
        parts.add(Message.raw(user.getUuid().toString() + "\n").color(WHITE));

        // Primary group
        parts.add(Message.raw("  Primary Group: ").color(GOLD));
        parts.add(Message.raw(user.getPrimaryGroup() + "\n").color(GREEN));

        // Custom prefix
        parts.add(Message.raw("  Custom Prefix: ").color(GOLD));
        parts.add(Message.raw((user.getCustomPrefix() != null ? "\"" + user.getCustomPrefix() + "\"" : "(none)") + "\n").color(WHITE));

        // Custom suffix
        parts.add(Message.raw("  Custom Suffix: ").color(GOLD));
        parts.add(Message.raw((user.getCustomSuffix() != null ? "\"" + user.getCustomSuffix() + "\"" : "(none)") + "\n").color(WHITE));

        // Groups (with expiry info for temporary group nodes)
        parts.add(Message.raw("  Groups: ").color(GOLD));
        var groupNodes = user.getNodes().stream()
                .filter(Node::isGroupNode)
                .filter(n -> !n.isExpired())
                .sorted(Comparator.comparing(n -> n.getGroupName() != null ? n.getGroupName() : "", String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (groupNodes.isEmpty() && (user.getPrimaryGroup() == null || user.getPrimaryGroup().equals("default"))) {
            parts.add(Message.raw("(none)\n").color(GRAY));
        } else {
            boolean first = true;
            if (user.getPrimaryGroup() != null && !user.getPrimaryGroup().isEmpty()) {
                parts.add(Message.raw(user.getPrimaryGroup()).color(GREEN));
                first = false;
            }
            for (Node gNode : groupNodes) {
                String gName = gNode.getGroupName();
                if (gName == null || gName.equalsIgnoreCase(user.getPrimaryGroup())) continue;
                if (!first) parts.add(Message.raw(", ").color(GRAY));
                parts.add(Message.raw(gName).color(GREEN));
                if (gNode.isTemporary()) {
                    parts.add(Message.raw(" (" + TimeUtil.formatExpiry(gNode.getExpiry()) + ")").color(AMBER));
                }
                first = false;
            }
            parts.add(Message.raw("\n").color(WHITE));
        }

        // Direct permissions
        long permCount = user.getNodes().stream().filter(n -> !n.isGroupNode()).count();
        parts.add(Message.raw("\n  Direct Permissions (" + permCount + "):\n").color(GOLD));
        if (permCount == 0) {
            parts.add(Message.raw("    (none)\n").color(GRAY));
        } else {
            user.getNodes().stream()
                .filter(n -> !n.isGroupNode())
                .sorted(Comparator.comparing(Node::getBasePermission, String.CASE_INSENSITIVE_ORDER))
                .forEach(node -> {
                    boolean granted = node.getValue() && !node.isNegated();
                    String prefix = granted ? "+" : "-";
                    String displayPerm = node.getBasePermission();
                    Color permColor = granted ? GREEN : RED;
                    parts.add(Message.raw("    " + prefix + " " + displayPerm).color(permColor));
                    if (!node.getContexts().isEmpty()) {
                        parts.add(Message.raw(" " + formatContexts(node.getContexts())).color(CYAN));
                    }
                    if (node.isTemporary()) {
                        if (node.isExpired()) {
                            parts.add(Message.raw(" (EXPIRED)").color(GRAY));
                        } else {
                            parts.add(Message.raw(" (" + TimeUtil.formatExpiry(node.getExpiry()) + ")").color(AMBER));
                        }
                    }
                    parts.add(Message.raw("\n").color(WHITE));
                });
        }

        parts.add(Message.raw("-".repeat(width)).color(GRAY));
        ctx.sender().sendMessage(join(parts));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setperm ====================

    @Command(name = "setperm", description = "Set a permission on a user")
    public CompletableFuture<Void> setperm(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "permission", description = "Permission node") String permission,
            @OptionalArg(name = "value", description = "true or false (default: true)") String valueStr,
            @OptionalArg(name = "duration", description = "Duration (e.g. 1d2h30m, permanent)") String durationStr,
            @OptionalArg(name = "world", description = "World name (restricts permission to that world)") String world) {
        User user = PlayerResolver.resolveOrCreate(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        boolean value = valueStr == null || !valueStr.equalsIgnoreCase("false");
        if (permission.startsWith("-")) {
            value = true;
        }

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

        var builder = Node.builder(permission).value(value);
        if (expiry != null) {
            builder.expiry(expiry);
        }
        if (world != null && !world.isBlank()) {
            builder.world(world);
        }
        Node node = builder.build();
        user.setNode(node);
        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        String displayPerm = node.getBasePermission();
        boolean granted = node.getValue() && !node.isNegated();
        String action = granted ? "Granted" : "Denied";
        String expiryMsg = node.isTemporary() ? " (" + TimeUtil.formatExpiry(node.getExpiry()) + ")" : "";
        String worldMsg = world != null && !world.isBlank() ? " [world=" + world + "]" : "";
        ctx.sender().sendMessage(Message.raw(action + " " + displayPerm + " on user " + user.getFriendlyName() + expiryMsg + worldMsg));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== unsetperm ====================

    @Command(name = "unsetperm", description = "Remove a permission from a user")
    public CompletableFuture<Void> unsetperm(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "permission", description = "Permission node") String permission,
            @OptionalArg(name = "world", description = "World name (remove only the world-specific entry)") String world) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        if (world != null && !world.isBlank()) {
            // Remove only the world-specific node
            var node = findNodeWithWorld(user.getNodes(), permission, world);
            if (node != null) {
                user.removeNode(node);
                plugin.getUserManager().saveUser(user).join();
                plugin.getCacheInvalidator().invalidate(user.getUuid());
                ctx.sender().sendMessage(Message.raw("Removed " + permission + " [world=" + world + "] from user " + user.getFriendlyName()));
            } else {
                ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " does not have permission " + permission + " in world " + world));
            }
        } else {
            var result = user.removeNode(permission);
            if (result == PermissionHolder.DataMutateResult.SUCCESS) {
                plugin.getUserManager().saveUser(user).join();
                plugin.getCacheInvalidator().invalidate(user.getUuid());
                ctx.sender().sendMessage(Message.raw("Removed " + permission + " from user " + user.getFriendlyName()));
            } else {
                ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " does not have permission " + permission));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setexpiry ====================

    @Command(name = "setexpiry", description = "Set or clear expiry on a user permission")
    public CompletableFuture<Void> setexpiry(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "permission", description = "Permission node") String permissionStr,
            @Arg(name = "duration", description = "Duration (e.g. 1d2h30m) or 'permanent'") String durationStr) {
        String permission = permissionStr.toLowerCase();

        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        Node existingNode = user.getNodes().stream()
                .filter(n -> n.getPermission().equals(permission))
                .findFirst()
                .orElse(null);

        if (existingNode == null) {
            ctx.sender().sendMessage(Message.raw("Permission " + permission + " not found on user " + user.getFriendlyName()));
            return CompletableFuture.completedFuture(null);
        }

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

        Node newNode = existingNode.withExpiry(newExpiry);
        user.setNode(newNode);
        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        String expiryDisplay = newExpiry != null ? TimeUtil.formatExpiry(newExpiry) : "permanent";
        ctx.sender().sendMessage(Message.raw("Set expiry on " + permission + " for user " + user.getFriendlyName() + " to " + expiryDisplay));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== addgroup ====================

    @Command(name = "addgroup", description = "Add a user to a group")
    public CompletableFuture<Void> addgroup(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "group", description = "Group name") String groupName,
            @OptionalArg(name = "duration", description = "Duration (e.g. 1d2h30m, permanent)") String durationStr) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        User user = PlayerResolver.resolveOrCreate(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

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

        user.addGroup(groupName, expiry);
        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());
        String expiryMsg = expiry != null ? " (" + TimeUtil.formatExpiry(expiry) + ")" : "";
        ctx.sender().sendMessage(Message.raw("Added user " + user.getFriendlyName() + " to group " + groupName + expiryMsg));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== removegroup ====================

    @Command(name = "removegroup", description = "Remove a user from a group")
    public CompletableFuture<Void> removegroup(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "group", description = "Group name") String groupName) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        var result = user.removeGroup(groupName);
        if (result == PermissionHolder.DataMutateResult.SUCCESS) {
            plugin.getUserManager().saveUser(user).join();
            plugin.getCacheInvalidator().invalidate(user.getUuid());
            ctx.sender().sendMessage(Message.raw("Removed user " + user.getFriendlyName() + " from group " + groupName));
        } else {
            ctx.sender().sendMessage(Message.raw("User " + user.getFriendlyName() + " is not in group " + groupName));
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setprimarygroup ====================

    @Command(name = "setprimarygroup", description = "Set a user's primary/display group")
    public CompletableFuture<Void> setprimarygroup(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "group", description = "Group name") String groupName) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        User user = PlayerResolver.resolveOrCreate(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        if (!user.getInheritedGroups().contains(groupName.toLowerCase())) {
            user.addGroup(groupName);
        }

        user.setPrimaryGroup(groupName.toLowerCase());
        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        ctx.sender().sendMessage(Message.raw("Set primary group of " + user.getFriendlyName() + " to " + groupName));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== promote ====================

    @Command(name = "promote", description = "Promote a user along a track")
    public CompletableFuture<Void> promote(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "track", description = "Track name") String trackName) {
        Track track = plugin.getTrackManager().getTrack(trackName);
        if (track == null) {
            ctx.sender().sendMessage(Message.raw("Track not found: " + trackName));
            return CompletableFuture.completedFuture(null);
        }

        if (track.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Track '" + trackName + "' has no groups defined."));
            return CompletableFuture.completedFuture(null);
        }

        User user = PlayerResolver.resolveOrCreate(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        String currentGroupOnTrack = null;
        for (String gName : user.getInheritedGroups()) {
            if (track.containsGroup(gName)) {
                if (currentGroupOnTrack == null || track.indexOf(gName) > track.indexOf(currentGroupOnTrack)) {
                    currentGroupOnTrack = gName;
                }
            }
        }

        String targetGroup;
        String actionMessage;

        if (currentGroupOnTrack == null) {
            targetGroup = track.getFirstGroup();
            actionMessage = "Added to track at";
        } else {
            String nextGroup = track.getNextGroup(currentGroupOnTrack);
            if (nextGroup == null) {
                ctx.sender().sendMessage(
                    Message.raw(user.getFriendlyName() + " is already at the top of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
                );
                return CompletableFuture.completedFuture(null);
            }
            targetGroup = nextGroup;
            actionMessage = "Promoted from " + currentGroupOnTrack + " to";
        }

        Group targetGroupObj = plugin.getGroupManager().getGroup(targetGroup);
        if (targetGroupObj == null) {
            ctx.sender().sendMessage(Message.raw("Target group '" + targetGroup + "' does not exist. Please create the group first."));
            return CompletableFuture.completedFuture(null);
        }

        if (currentGroupOnTrack != null) {
            user.removeGroup(currentGroupOnTrack);
        }

        user.addGroup(targetGroup);
        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        ctx.sender().sendMessage(
            Message.raw(actionMessage + " ").color(GRAY)
                .insert(Message.raw(targetGroup).color(GREEN))
                .insert(Message.raw(" for ").color(GRAY))
                .insert(Message.raw(user.getFriendlyName()).color(GREEN))
                .insert(Message.raw(" on track ").color(GRAY))
                .insert(Message.raw(trackName).color(GREEN))
        );

        return CompletableFuture.completedFuture(null);
    }

    // ==================== demote ====================

    @Command(name = "demote", description = "Demote a user along a track")
    public CompletableFuture<Void> demote(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "track", description = "Track name") String trackName) {
        Track track = plugin.getTrackManager().getTrack(trackName);
        if (track == null) {
            ctx.sender().sendMessage(Message.raw("Track not found: " + trackName));
            return CompletableFuture.completedFuture(null);
        }

        if (track.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Track '" + trackName + "' has no groups defined."));
            return CompletableFuture.completedFuture(null);
        }

        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        String currentGroupOnTrack = null;
        for (String gName : user.getInheritedGroups()) {
            if (track.containsGroup(gName)) {
                if (currentGroupOnTrack == null || track.indexOf(gName) > track.indexOf(currentGroupOnTrack)) {
                    currentGroupOnTrack = gName;
                }
            }
        }

        if (currentGroupOnTrack == null) {
            ctx.sender().sendMessage(
                Message.raw(user.getFriendlyName() + " is not on track '" + trackName + "'.").color(RED)
            );
            return CompletableFuture.completedFuture(null);
        }

        String previousGroup = track.getPreviousGroup(currentGroupOnTrack);
        if (previousGroup == null) {
            ctx.sender().sendMessage(
                Message.raw(user.getFriendlyName() + " is already at the bottom of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
            );
            return CompletableFuture.completedFuture(null);
        }

        Group targetGroupObj = plugin.getGroupManager().getGroup(previousGroup);
        if (targetGroupObj == null) {
            ctx.sender().sendMessage(Message.raw("Target group '" + previousGroup + "' does not exist. Please create the group first."));
            return CompletableFuture.completedFuture(null);
        }

        user.removeGroup(currentGroupOnTrack);
        user.addGroup(previousGroup);

        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        ctx.sender().sendMessage(
            Message.raw("Demoted from " + currentGroupOnTrack + " to ").color(GRAY)
                .insert(Message.raw(previousGroup).color(RED))
                .insert(Message.raw(" for ").color(GRAY))
                .insert(Message.raw(user.getFriendlyName()).color(RED))
                .insert(Message.raw(" on track ").color(GRAY))
                .insert(Message.raw(trackName).color(RED))
        );

        return CompletableFuture.completedFuture(null);
    }

    // ==================== setprefix ====================

    @Command(name = "setprefix", description = "Set a user's custom prefix")
    public CompletableFuture<Void> setprefix(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @OptionalArg(name = "prefix", description = "Prefix text (omit to clear)") String prefix) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        String stripped = stripQuotes(prefix);
        if (stripped == null || stripped.isEmpty()) {
            user.setCustomPrefix(null);
            ctx.sender().sendMessage(Message.raw("Cleared custom prefix for " + user.getFriendlyName()));
        } else {
            user.setCustomPrefix(stripped);
            ctx.sender().sendMessage(Message.raw("Set custom prefix of " + user.getFriendlyName() + " to \"" + stripped + "\""));
        }

        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        return CompletableFuture.completedFuture(null);
    }

    // ==================== setsuffix ====================

    @Command(name = "setsuffix", description = "Set a user's custom suffix")
    public CompletableFuture<Void> setsuffix(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @OptionalArg(name = "suffix", description = "Suffix text (omit to clear)") String suffix) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        String stripped = stripQuotes(suffix);
        if (stripped == null || stripped.isEmpty()) {
            user.setCustomSuffix(null);
            ctx.sender().sendMessage(Message.raw("Cleared custom suffix for " + user.getFriendlyName()));
        } else {
            user.setCustomSuffix(stripped);
            ctx.sender().sendMessage(Message.raw("Set custom suffix of " + user.getFriendlyName() + " to \"" + stripped + "\""));
        }

        plugin.getUserManager().saveUser(user).join();
        plugin.getCacheInvalidator().invalidate(user.getUuid());

        return CompletableFuture.completedFuture(null);
    }

    // ==================== clear ====================

    @Command(name = "clear", description = "Clear all data for a user")
    public CompletableFuture<Void> clear(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        String confirmationKey = "user-clear:" + user.getUuid();

        // Check if this is a confirmation
        Long timestamp = pendingConfirmations.get(confirmationKey);
        if (timestamp != null && System.currentTimeMillis() - timestamp <= CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(confirmationKey);

            user.clearNodes();
            user.setPrimaryGroup("default");
            user.setCustomPrefix(null);
            user.setCustomSuffix(null);

            plugin.getUserManager().saveUser(user).join();
            plugin.getCacheInvalidator().invalidate(user.getUuid());

            ctx.sender().sendMessage(Message.raw("Cleared all data for " + user.getFriendlyName()));
            return CompletableFuture.completedFuture(null);
        }

        // First invocation - show warning
        pendingConfirmations.put(confirmationKey, System.currentTimeMillis());
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(Color.RED));
        ctx.sender().sendMessage(Message.raw("You are about to CLEAR ALL DATA for user: " + user.getFriendlyName()));
        ctx.sender().sendMessage(Message.raw("This will remove:"));
        ctx.sender().sendMessage(Message.raw("  - All permissions"));
        ctx.sender().sendMessage(Message.raw("  - All group memberships (reset to 'default')"));
        ctx.sender().sendMessage(Message.raw("  - Custom prefix/suffix"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== clone ====================

    @Command(name = "clone", description = "Copy permissions from one user to another")
    public CompletableFuture<Void> clone_(CommandContext ctx,
            @Arg(name = "source", description = "Source player name or UUID") String sourceId,
            @Arg(name = "target", description = "Target player name or UUID") String targetId) {
        User source = PlayerResolver.resolve(plugin, sourceId);
        if (source == null) {
            ctx.sender().sendMessage(Message.raw("Source user not found: " + sourceId));
            return CompletableFuture.completedFuture(null);
        }

        User target = PlayerResolver.resolve(plugin, targetId);
        if (target == null) {
            ctx.sender().sendMessage(Message.raw("Target user not found: " + targetId));
            return CompletableFuture.completedFuture(null);
        }

        target.getNodes().clear();
        target.getInheritedGroups().clear();

        for (Node node : source.getNodes()) {
            target.setNode(node);
        }

        for (String group : source.getInheritedGroups()) {
            target.addGroup(group);
        }

        target.setPrimaryGroup(source.getPrimaryGroup());
        target.setCustomPrefix(source.getCustomPrefix());
        target.setCustomSuffix(source.getCustomSuffix());

        plugin.getUserManager().saveUser(target);
        plugin.getCacheInvalidator().invalidate(target.getUuid());

        ctx.sender().sendMessage(Message.raw("Cloned permissions from " + source.getFriendlyName() + " to " + target.getFriendlyName()));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Utilities ====================

    private static String stripQuotes(String value) {
        if (value != null && value.length() >= 2
                && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Formats a context set for display, e.g. "[world=Survival]".
     */
    private static String formatContexts(ContextSet contexts) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (Context c : contexts) {
            joiner.add(c.key() + "=" + c.value());
        }
        return joiner.toString();
    }

    /**
     * Finds a node matching a permission and world context.
     */
    private static Node findNodeWithWorld(Set<Node> nodes, String permission, String world) {
        String lowerPerm = permission.toLowerCase();
        String lowerWorld = world.toLowerCase();
        return nodes.stream()
            .filter(n -> n.getPermission().equals(lowerPerm))
            .filter(n -> {
                String nodeWorld = n.getContexts().getValue(Context.WORLD_KEY);
                return nodeWorld != null && nodeWorld.equalsIgnoreCase(lowerWorld);
            })
            .findFirst()
            .orElse(null);
    }
}
