package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpContainerCommand;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp group subcommands (flat commands).
 * The parent subcontainer is created separately via {@link #createParentCommand()}.
 */
@CommandGroup(name = "group", description = "Manage groups")
public class GroupCommands {

    private static final Color AMBER = new Color(255, 200, 0);
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000;

    private final HyperPerms plugin;

    public GroupCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== create ====================

    @Command(name = "create", description = "Create a new group")
    public CompletableFuture<Void> create(CommandContext ctx,
            @Arg(name = "name", description = "Group name") String name) {
        if (plugin.getGroupManager().getGroup(name) != null) {
            ctx.sender().sendMessage(Message.raw("Group already exists: " + name));
            return CompletableFuture.completedFuture(null);
        }
        plugin.getGroupManager().createGroup(name);
        ctx.sender().sendMessage(Message.raw("Created group: " + name));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== delete ====================

    @Command(name = "delete", description = "Delete a group")
    public CompletableFuture<Void> delete(CommandContext ctx,
            @Arg(name = "name", description = "Group name") String groupName) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        String confirmationKey = "group-delete:" + groupName.toLowerCase();

        // Check if this is a confirmation
        Long timestamp = pendingConfirmations.get(confirmationKey);
        if (timestamp != null && System.currentTimeMillis() - timestamp <= CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(confirmationKey);
            plugin.getGroupManager().deleteGroup(groupName);
            ctx.sender().sendMessage(Message.raw("Deleted group: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // First invocation - show warning and request confirmation
        pendingConfirmations.put(confirmationKey, System.currentTimeMillis());
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
        ctx.sender().sendMessage(Message.raw("You are about to DELETE group: " + groupName));
        ctx.sender().sendMessage(Message.raw("This will remove all permissions and settings for this group."));
        ctx.sender().sendMessage(Message.raw("Users in this group will lose inherited permissions."));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== list ====================

    @Command(name = "list", description = "List all groups")
    public CompletableFuture<Void> list(CommandContext ctx) {
        var groups = plugin.getGroupManager().getLoadedGroups();
        int width = 42;
        String label = "Groups (" + groups.size() + ")";
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        for (Group group : groups) {
            parts.add(Message.raw("  " + group.getName()).color(GREEN));
            parts.add(Message.raw(" (weight: ").color(GRAY));
            parts.add(Message.raw(String.valueOf(group.getWeight())).color(WHITE));
            parts.add(Message.raw(")\n").color(GRAY));
        }

        parts.add(Message.raw("-".repeat(width)).color(GRAY));
        ctx.sender().sendMessage(join(parts));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== info ====================

    @Command(name = "info", description = "View group info")
    public CompletableFuture<Void> info(CommandContext ctx,
            @Arg(name = "name", description = "Group name") String groupName) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        int width = 42;
        String label = "Group: " + group.getName();
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        // Display name
        parts.add(Message.raw("  Display Name: ").color(GOLD));
        parts.add(Message.raw((group.getDisplayName() != null ? group.getDisplayName() : group.getName()) + "\n").color(WHITE));

        // Weight
        parts.add(Message.raw("  Weight: ").color(GOLD));
        parts.add(Message.raw(group.getWeight() + "\n").color(WHITE));

        // Prefix
        parts.add(Message.raw("  Prefix: ").color(GOLD));
        parts.add(Message.raw((group.getPrefix() != null ? "\"" + group.getPrefix() + "\"" : "(none)") + "\n").color(WHITE));

        // Suffix
        parts.add(Message.raw("  Suffix: ").color(GOLD));
        parts.add(Message.raw((group.getSuffix() != null ? "\"" + group.getSuffix() + "\"" : "(none)") + "\n").color(WHITE));

        // Priorities
        parts.add(Message.raw("  Prefix Priority: ").color(GRAY));
        parts.add(Message.raw(group.getPrefixPriority() + "\n").color(WHITE));
        parts.add(Message.raw("  Suffix Priority: ").color(GRAY));
        parts.add(Message.raw(group.getSuffixPriority() + "\n").color(WHITE));

        // Parents
        var parents = group.getParentGroups();
        parts.add(Message.raw("  Parents: ").color(GOLD));
        parts.add(Message.raw((!parents.isEmpty() ? String.join(", ", parents) : "(none)") + "\n").color(GREEN));

        // Permissions
        long permCount = group.getNodes().stream().filter(n -> !n.isGroupNode()).count();
        parts.add(Message.raw("\n  Permissions (" + permCount + "):\n").color(GOLD));
        if (permCount == 0) {
            parts.add(Message.raw("    (none)\n").color(GRAY));
        } else {
            group.getNodes().stream()
                .filter(n -> !n.isGroupNode())
                .sorted(Comparator.comparing(Node::getBasePermission, String.CASE_INSENSITIVE_ORDER))
                .forEach(node -> {
                    boolean granted = node.getValue() && !node.isNegated();
                    String prefix = granted ? "+" : "-";
                    String displayPerm = node.getBasePermission();
                    Color permColor = granted ? GREEN : RED;
                    parts.add(Message.raw("    " + prefix + " " + displayPerm).color(permColor));
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

    @Command(name = "setperm", description = "Set a permission on a group")
    public CompletableFuture<Void> setperm(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @Arg(name = "permission", description = "Permission node") String permission,
            @OptionalArg(name = "value", description = "true or false (default: true)") String valueStr,
            @OptionalArg(name = "duration", description = "Duration (e.g. 1d2h30m, permanent)") String durationStr) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
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
        Node node = builder.build();
        group.setNode(node);
        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        String displayPerm = node.getBasePermission();
        boolean granted = node.getValue() && !node.isNegated();
        String action = granted ? "Granted" : "Denied";
        String expiryMsg = node.isTemporary() ? " (" + TimeUtil.formatExpiry(node.getExpiry()) + ")" : "";
        ctx.sender().sendMessage(Message.raw(action + " " + displayPerm + " on group " + groupName + expiryMsg));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== unsetperm ====================

    @Command(name = "unsetperm", description = "Remove a permission from a group")
    public CompletableFuture<Void> unsetperm(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @Arg(name = "permission", description = "Permission node") String permission) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        var result = group.removeNode(permission);
        if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
            plugin.getGroupManager().saveGroup(group);
            plugin.getCache().invalidateAll();
            ctx.sender().sendMessage(Message.raw("Removed " + permission + " from group " + groupName));
        } else {
            ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not have permission " + permission));
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setexpiry ====================

    @Command(name = "setexpiry", description = "Set or clear expiry on a group permission")
    public CompletableFuture<Void> setexpiry(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @Arg(name = "permission", description = "Permission node") String permissionStr,
            @Arg(name = "duration", description = "Duration (e.g. 1d2h30m) or 'permanent'") String durationStr) {
        String permission = permissionStr.toLowerCase();

        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        Node existingNode = group.getNodes().stream()
                .filter(n -> n.getPermission().equals(permission))
                .findFirst()
                .orElse(null);

        if (existingNode == null) {
            ctx.sender().sendMessage(Message.raw("Permission " + permission + " not found on group " + groupName));
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
        group.setNode(newNode);
        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        String expiryDisplay = newExpiry != null ? TimeUtil.formatExpiry(newExpiry) : "permanent";
        ctx.sender().sendMessage(Message.raw("Set expiry on " + permission + " in group " + groupName + " to " + expiryDisplay));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setweight ====================

    @Command(name = "setweight", description = "Set a group's weight/priority")
    public CompletableFuture<Void> setweight(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @Arg(name = "weight", description = "Weight value") String weightStr) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        int weight;
        try {
            weight = Integer.parseInt(weightStr);
        } catch (NumberFormatException e) {
            ctx.sender().sendMessage(Message.raw("Invalid weight: " + weightStr + ". Must be an integer."));
            return CompletableFuture.completedFuture(null);
        }

        group.setWeight(weight);
        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        ctx.sender().sendMessage(Message.raw("Set weight of group " + groupName + " to " + weight));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== setprefix ====================

    @Command(name = "setprefix", description = "Set a group's chat prefix")
    public CompletableFuture<Void> setprefix(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @OptionalArg(name = "prefix", description = "Prefix text (omit to clear)") String prefix,
            @OptionalArg(name = "priority", description = "Priority for multi-group resolution") String priorityStr) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        String stripped = stripQuotes(prefix);
        if (stripped == null || stripped.isEmpty()) {
            group.setPrefix(null);
            ctx.sender().sendMessage(Message.raw("Cleared prefix for group " + groupName));
        } else {
            group.setPrefix(stripped);
            ctx.sender().sendMessage(Message.raw("Set prefix of group " + groupName + " to \"" + stripped + "\""));
        }

        if (priorityStr != null) {
            try {
                int priority = Integer.parseInt(priorityStr);
                group.setPrefixPriority(priority);
                ctx.sender().sendMessage(Message.raw("Set prefix priority to " + priority));
            } catch (NumberFormatException e) {
                ctx.sender().sendMessage(Message.raw("Invalid priority: " + priorityStr + ". Must be an integer."));
            }
        }

        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        return CompletableFuture.completedFuture(null);
    }

    // ==================== setsuffix ====================

    @Command(name = "setsuffix", description = "Set a group's chat suffix")
    public CompletableFuture<Void> setsuffix(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @OptionalArg(name = "suffix", description = "Suffix text (omit to clear)") String suffix,
            @OptionalArg(name = "priority", description = "Priority for multi-group resolution") String priorityStr) {
        Group group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        String stripped = stripQuotes(suffix);
        if (stripped == null || stripped.isEmpty()) {
            group.setSuffix(null);
            ctx.sender().sendMessage(Message.raw("Cleared suffix for group " + groupName));
        } else {
            group.setSuffix(stripped);
            ctx.sender().sendMessage(Message.raw("Set suffix of group " + groupName + " to \"" + stripped + "\""));
        }

        if (priorityStr != null) {
            try {
                int priority = Integer.parseInt(priorityStr);
                group.setSuffixPriority(priority);
                ctx.sender().sendMessage(Message.raw("Set suffix priority to " + priority));
            } catch (NumberFormatException e) {
                ctx.sender().sendMessage(Message.raw("Invalid priority: " + priorityStr + ". Must be an integer."));
            }
        }

        plugin.getGroupManager().saveGroup(group);
        plugin.getCache().invalidateAll();

        return CompletableFuture.completedFuture(null);
    }

    // ==================== setdisplayname ====================

    @Command(name = "setdisplayname", description = "Set a group's display name")
    public CompletableFuture<Void> setdisplayname(CommandContext ctx,
            @Arg(name = "group", description = "Group name") String groupName,
            @OptionalArg(name = "displayname", description = "Display name (omit to clear)") String displayName) {
        Group group = plugin.getGroupManager().getGroup(groupName);
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

        plugin.getGroupManager().saveGroup(group);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== rename ====================

    @Command(name = "rename", description = "Rename a group")
    public CompletableFuture<Void> rename(CommandContext ctx,
            @Arg(name = "oldname", description = "Current group name") String oldName,
            @Arg(name = "newname", description = "New group name") String newName) {
        Group group = plugin.getGroupManager().getGroup(oldName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + oldName));
            return CompletableFuture.completedFuture(null);
        }

        if (plugin.getGroupManager().getGroup(newName) != null) {
            ctx.sender().sendMessage(Message.raw("A group with name " + newName + " already exists"));
            return CompletableFuture.completedFuture(null);
        }

        plugin.getGroupManager().deleteGroup(oldName);

        Group newGroup = new Group(newName);
        newGroup.setDisplayName(group.getDisplayName());
        newGroup.setWeight(group.getWeight());
        newGroup.setPrefix(group.getPrefix());
        newGroup.setSuffix(group.getSuffix());
        newGroup.setPrefixPriority(group.getPrefixPriority());
        newGroup.setSuffixPriority(group.getSuffixPriority());

        for (Node node : group.getNodes()) {
            newGroup.setNode(node);
        }

        for (String parent : group.getParentGroups()) {
            newGroup.addParent(parent);
        }

        plugin.getGroupManager().createGroup(newName);
        plugin.getGroupManager().saveGroup(newGroup);

        for (User user : plugin.getUserManager().getLoadedUsers()) {
            if (user.getInheritedGroups().contains(oldName.toLowerCase())) {
                user.removeGroup(oldName);
                user.addGroup(newName);
                plugin.getUserManager().saveUser(user);
            }
        }

        for (Group g : plugin.getGroupManager().getLoadedGroups()) {
            if (g.getParentGroups().contains(oldName.toLowerCase())) {
                g.removeParent(oldName);
                g.addParent(newName);
                plugin.getGroupManager().saveGroup(g);
            }
        }

        plugin.getCache().invalidateAll();
        ctx.sender().sendMessage(Message.raw("Renamed group " + oldName + " to " + newName));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== parent (nested container) ====================

    /**
     * Creates the parent subcontainer command ({@code /hp group parent add|remove}).
     * This is a nested container and cannot be expressed as a flat {@code @Command} method.
     */
    public HpContainerCommand createParentCommand() {
        return new ParentContainer(plugin);
    }

    private static class ParentContainer extends HpContainerCommand {
        ParentContainer(HyperPerms plugin) {
            super("parent", "Manage group parents (inheritance)");
            addSubCommand(new AddCommand(plugin));
            addSubCommand(new RemoveCommand(plugin));
        }
    }

    private static class AddCommand extends HpSubCommand {
        private final HyperPerms plugin;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;
        private final com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg<String> durationArg;

        AddCommand(HyperPerms plugin) {
            super("add", "Add a parent group");
            this.plugin = plugin;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
            this.durationArg = describeOptionalArg("duration", "Duration (e.g. 1d2h30m, permanent)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);
            String durationStr = ctx.get(durationArg);

            Group group = plugin.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            Group parent = plugin.getGroupManager().getGroup(parentName);
            if (parent == null) {
                ctx.sender().sendMessage(Message.raw("Parent group not found: " + parentName));
                return CompletableFuture.completedFuture(null);
            }

            if (groupName.equalsIgnoreCase(parentName)) {
                ctx.sender().sendMessage(Message.raw("A group cannot inherit from itself"));
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

            group.addParent(parentName, expiry);
            plugin.getGroupManager().saveGroup(group);
            plugin.getCache().invalidateAll();
            String expiryMsg = expiry != null ? " (" + TimeUtil.formatExpiry(expiry) + ")" : "";
            ctx.sender().sendMessage(Message.raw("Group " + groupName + " now inherits from " + parentName + expiryMsg));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class RemoveCommand extends HpSubCommand {
        private final HyperPerms plugin;
        private final RequiredArg<String> groupArg;
        private final RequiredArg<String> parentArg;

        RemoveCommand(HyperPerms plugin) {
            super("remove", "Remove a parent group");
            this.plugin = plugin;
            this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
            this.parentArg = describeArg("parent", "Parent group name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String groupName = ctx.get(groupArg);
            String parentName = ctx.get(parentArg);

            Group group = plugin.getGroupManager().getGroup(groupName);
            if (group == null) {
                ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
                return CompletableFuture.completedFuture(null);
            }

            var result = group.removeParent(parentName);
            if (result == com.hyperperms.api.PermissionHolder.DataMutateResult.SUCCESS) {
                plugin.getGroupManager().saveGroup(group);
                plugin.getCache().invalidateAll();
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " no longer inherits from " + parentName));
            } else {
                ctx.sender().sendMessage(Message.raw("Group " + groupName + " does not inherit from " + parentName));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Utilities ====================

    private static String stripQuotes(String value) {
        if (value != null && value.length() >= 2
                && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
