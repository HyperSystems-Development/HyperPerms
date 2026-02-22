package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * List registered permissions, optionally filtered by category.
 * <p>
 * Usage: /hp perms list [category]
 */
public class PermsListCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final OptionalArg<String> categoryArg;

    public PermsListCommand(HyperPerms hyperPerms) {
        super("list", "List registered permissions");
        this.hyperPerms = hyperPerms;
        this.categoryArg = describeOptionalArg("category", "Filter by category", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        var registry = hyperPerms.getPermissionRegistry();
        String category = ctx.get(categoryArg);

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
}
