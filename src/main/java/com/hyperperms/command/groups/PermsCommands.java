package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp perms subcommands.
 */
@CommandGroup(name = "perms", description = "Permission listing and search")
public class PermsCommands {

    private final HyperPerms plugin;

    public PermsCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== list ====================

    @Command(name = "list", description = "List registered permissions")
    public CompletableFuture<Void> list(CommandContext ctx,
            @OptionalArg(name = "category", description = "Filter by category") String category) {
        var registry = plugin.getPermissionRegistry();

        if (category != null) {
            // List by category
            var perms = registry.getByCategory(category);
            if (perms.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("No permissions found in category: " + category));
                ctx.sender().sendMessage(Message.raw("Available categories: " + String.join(", ", registry.getCategories())));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("=== Permissions in category '" + category + "' (" + perms.size() + ") ==="));
            for (var perm : perms) {
                ctx.sender().sendMessage(Message.raw("  " + perm.getPermission()));
                ctx.sender().sendMessage(Message.raw("    " + perm.getDescription()));
            }
        } else {
            // List categories
            var categories = registry.getCategories();
            ctx.sender().sendMessage(Message.raw("=== Permission Categories (" + registry.size() + " total permissions) ==="));
            for (String cat : categories) {
                int count = registry.getByCategory(cat).size();
                ctx.sender().sendMessage(Message.raw("  " + cat + " (" + count + " permissions)"));
            }
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Use /hp perms list <category> to view permissions in a category"));
        }

        return CompletableFuture.completedFuture(null);
    }

    // ==================== search ====================

    @Command(name = "search", description = "Search for permissions by name or description")
    public CompletableFuture<Void> search(CommandContext ctx,
            @Arg(name = "query", description = "Search query") String query) {
        var registry = plugin.getPermissionRegistry();

        var results = registry.search(query);

        if (results.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("No permissions found matching: " + query));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Search results for '" + query + "' (" + results.size() + " found) ==="));

        int shown = 0;
        for (var perm : results) {
            if (shown >= 20) {
                ctx.sender().sendMessage(Message.raw("... and " + (results.size() - 20) + " more"));
                break;
            }
            ctx.sender().sendMessage(Message.raw("  " + perm.getPermission() + " [" + perm.getCategory() + "]"));
            ctx.sender().sendMessage(Message.raw("    " + perm.getDescription()));
            shown++;
        }

        return CompletableFuture.completedFuture(null);
    }
}
