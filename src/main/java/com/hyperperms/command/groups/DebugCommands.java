package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.config.ConfigManager;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.util.Logger;
import com.hyperperms.util.Logger.DebugCategory;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp debug subcommands.
 */
@CommandGroup(name = "debug", description = "Debug and diagnostic tools")
public class DebugCommands {

    private final HyperPerms plugin;

    public DebugCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== perms ====================

    @Command(name = "perms", description = "Toggle verbose permission check logging")
    public CompletableFuture<Void> perms(CommandContext ctx) {
        boolean currentState = Logger.isPermissionDebugEnabled();
        Logger.setPermissionDebugEnabled(!currentState);

        if (!currentState) {
            ctx.sender().sendMessage(Message.raw("Permission debug logging ENABLED").color(GREEN));
            ctx.sender().sendMessage(Message.raw("All permission checks will now be logged to console with detailed info.").color(GRAY));
            ctx.sender().sendMessage(Message.raw("This helps debug issues between plugins like HyperHomes.").color(GRAY));
            ctx.sender().sendMessage(Message.raw("Run /hp debug perms again to disable.").color(GRAY));
        } else {
            ctx.sender().sendMessage(Message.raw("Permission debug logging DISABLED").color(RED));
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== tree ====================

    @Command(name = "tree", description = "Show inheritance tree for a user")
    public CompletableFuture<Void> tree(CommandContext ctx,
            @Arg(name = "user", description = "Player name or UUID") String identifier) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Inheritance Tree for " + user.getFriendlyName() + " ==="));

        // Direct permissions
        ctx.sender().sendMessage(Message.raw("Direct Permissions:"));
        var directPerms = user.getNodes();
        if (directPerms.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (none)"));
        } else {
            for (var node : directPerms) {
                boolean granted = node.getValue() && !node.isNegated();
                String prefix = granted ? "  + " : "  - ";
                ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
            }
        }

        // Groups
        ctx.sender().sendMessage(Message.raw("Groups:"));
        var groups = user.getInheritedGroups();
        if (groups.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (none)"));
        } else {
            for (String groupName : groups) {
                printGroupTree(ctx, groupName, "  ", new HashSet<>());
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private void printGroupTree(CommandContext ctx, String groupName,
                                String indent, Set<String> visited) {
        if (visited.contains(groupName)) {
            ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (circular ref)"));
            return;
        }
        visited.add(groupName);

        var group = plugin.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (not found)"));
            return;
        }

        ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (weight=" + group.getWeight() + ")"));

        for (var node : group.getNodes()) {
            boolean granted = node.getValue() && !node.isNegated();
            String prefix = granted ? indent + "  + " : indent + "  - ";
            ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
        }

        for (String parent : group.getParentGroups()) {
            printGroupTree(ctx, parent, indent + "  ", visited);
        }
    }

    private String formatContext(Node node) {
        var contexts = node.getContexts();
        if (contexts == null || contexts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" [");
        boolean first = true;
        for (var ctx : contexts.toSet()) {
            if (!first) sb.append(", ");
            sb.append(ctx.key()).append("=").append(ctx.value());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== resolve ====================

    @Command(name = "resolve", description = "Debug permission resolution step-by-step")
    public CompletableFuture<Void> resolve(CommandContext ctx,
            @Arg(name = "user", description = "Player name or UUID") String identifier,
            @Arg(name = "permission", description = "Permission to resolve") String permission) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Resolving: " + permission + " for " + user.getFriendlyName() + " ==="));

        var contexts = plugin.getContexts(user.getUuid());
        ctx.sender().sendMessage(Message.raw("Current contexts: " + contexts));

        boolean wasVerbose = plugin.isVerboseMode();
        plugin.setVerboseMode(true);

        boolean result = plugin.hasPermission(user.getUuid(), permission, contexts);

        plugin.setVerboseMode(wasVerbose);

        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("Final result: " + (result ? "ALLOWED" : "DENIED")));
        ctx.sender().sendMessage(Message.raw("(Check console for detailed trace if verbose logging is enabled)"));

        return CompletableFuture.completedFuture(null);
    }

    // ==================== contexts ====================

    @Command(name = "contexts", description = "Show all current contexts for a user")
    public CompletableFuture<Void> contexts(CommandContext ctx,
            @Arg(name = "user", description = "Player name or UUID") String identifier) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        var userContexts = plugin.getContexts(user.getUuid());

        ctx.sender().sendMessage(Message.raw("=== Contexts for " + user.getFriendlyName() + " ==="));

        if (userContexts.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (no contexts)"));
        } else {
            for (var context : userContexts.toSet()) {
                ctx.sender().sendMessage(Message.raw("  " + context.key() + " = " + context.value()));
            }
        }

        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("Registered calculators: " + plugin.getContextManager().getCalculatorCount()));

        return CompletableFuture.completedFuture(null);
    }

    // ==================== toggle ====================

    @Command(name = "toggle", description = "Toggle debug categories on or off")
    public CompletableFuture<Void> toggle(CommandContext ctx,
            @Arg(name = "category", description = "Debug category name or 'all'") String categoryName,
            @OptionalArg(name = "state", description = "Explicit state: on or off (omit to toggle)") String stateStr) {
        categoryName = categoryName.toUpperCase();

        // Handle "all" keyword
        if (categoryName.equals("ALL")) {
            boolean newState;
            if (stateStr != null) {
                newState = stateStr.equalsIgnoreCase("on");
            } else {
                newState = !Logger.isAnyDebugEnabled();
            }

            if (newState) {
                Logger.enableAllDebug();
            } else {
                Logger.disableAllDebug();
            }

            ConfigManager.get().debug().syncFromLogger();

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("All debug categories ").color(GRAY));
            if (newState) {
                parts.add(Message.raw("ENABLED").color(GREEN));
            } else {
                parts.add(Message.raw("DISABLED").color(RED));
            }
            ctx.sender().sendMessage(join(parts));
            return CompletableFuture.completedFuture(null);
        }

        // Resolve category by name
        DebugCategory category;
        try {
            category = DebugCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            String validNames = Arrays.stream(DebugCategory.values())
                    .map(c -> c.name().toLowerCase())
                    .collect(Collectors.joining(", "));
            ctx.sender().sendMessage(Message.raw("\u2717 Unknown category: " + categoryName.toLowerCase()).color(RED));
            ctx.sender().sendMessage(Message.raw("  Valid categories: " + validNames).color(GRAY));
            ctx.sender().sendMessage(Message.raw("  Use 'all' to toggle all categories.").color(GRAY));
            return CompletableFuture.completedFuture(null);
        }

        boolean newState;
        if (stateStr != null) {
            newState = stateStr.equalsIgnoreCase("on");
        } else {
            newState = !Logger.isDebugEnabled(category);
        }

        Logger.setDebugEnabled(category, newState);
        ConfigManager.get().debug().syncFromLogger();

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("Debug ").color(GRAY));
        parts.add(Message.raw(category.name().toLowerCase()).color(GOLD));
        parts.add(Message.raw(": ").color(GRAY));
        if (newState) {
            parts.add(Message.raw("ON").color(GREEN));
        } else {
            parts.add(Message.raw("OFF").color(RED));
        }
        ctx.sender().sendMessage(join(parts));

        ctx.sender().sendMessage(Message.raw("  " + category.getDescription()).color(GRAY));

        return CompletableFuture.completedFuture(null);
    }

    // ==================== status ====================

    @Command(name = "status", description = "Show all debug category states")
    public CompletableFuture<Void> status(CommandContext ctx) {
        DebugCategory[] categories = DebugCategory.values();
        int enabledCount = 0;
        for (DebugCategory cat : categories) {
            if (Logger.isDebugEnabled(cat)) {
                enabledCount++;
            }
        }

        // Header
        List<Message> headerParts = new ArrayList<>();
        headerParts.add(header("Debug Categories"));
        ctx.sender().sendMessage(join(headerParts));

        // Summary line
        List<Message> summary = new ArrayList<>();
        summary.add(Message.raw("  ").color(WHITE));
        summary.add(Message.raw(String.valueOf(enabledCount)).color(enabledCount > 0 ? GREEN : GRAY));
        summary.add(Message.raw(" of ").color(GRAY));
        summary.add(Message.raw(String.valueOf(categories.length)).color(WHITE));
        summary.add(Message.raw(" categories enabled\n").color(GRAY));
        ctx.sender().sendMessage(join(summary));

        // Each category
        for (DebugCategory category : categories) {
            boolean enabled = Logger.isDebugEnabled(category);

            List<Message> line = new ArrayList<>();
            if (enabled) {
                line.add(Message.raw("  \u2713 ").color(GREEN));
                line.add(Message.raw(category.name().toLowerCase()).color(GREEN));
                line.add(Message.raw(" ON").color(GREEN));
            } else {
                line.add(Message.raw("  \u2717 ").color(RED));
                line.add(Message.raw(category.name().toLowerCase()).color(GRAY));
                line.add(Message.raw(" OFF").color(RED));
            }
            line.add(Message.raw(" - " + category.getDescription()).color(DARK_GRAY));
            ctx.sender().sendMessage(join(line));
        }

        // Footer hint
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("  Use /hp debug toggle <category|all> [on|off]").color(GRAY));

        List<Message> footerParts = new ArrayList<>();
        footerParts.add(footer());
        ctx.sender().sendMessage(join(footerParts));

        return CompletableFuture.completedFuture(null);
    }
}
