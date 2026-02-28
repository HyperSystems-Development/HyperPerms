package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Standalone commands registered directly under /hp (not within a group container).
 */
@CommandGroup(name = "root", description = "Root commands", root = true)
public class RootCommands {

    private final HyperPerms plugin;

    public RootCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== check ====================

    @Command(name = "check", description = "Check if a player has a permission")
    public CompletableFuture<Void> check(CommandContext ctx,
            @Arg(name = "player", description = "Player name or UUID") String identifier,
            @Arg(name = "permission", description = "Permission node to check") String permission) {
        User user = PlayerResolver.resolve(plugin, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("\u2717 User not found: " + identifier).color(RED));
            return CompletableFuture.completedFuture(null);
        }

        boolean hasPermission = plugin.hasPermission(user.getUuid(), permission);

        List<Message> parts = new ArrayList<>();
        if (hasPermission) {
            parts.add(Message.raw("\u2713 ").color(GREEN));
            parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
            parts.add(Message.raw(" has permission ").color(GRAY));
            parts.add(Message.raw(permission).color(GREEN));
        } else {
            parts.add(Message.raw("\u2717 ").color(RED));
            parts.add(Message.raw(user.getFriendlyName()).color(GOLD));
            parts.add(Message.raw(" does NOT have permission ").color(GRAY));
            parts.add(Message.raw(permission).color(RED));
        }
        ctx.sender().sendMessage(join(parts));

        return CompletableFuture.completedFuture(null);
    }

    // ==================== reload ====================

    @Command(name = "reload", description = "Reload HyperPerms configuration")
    public CompletableFuture<Void> reload(CommandContext ctx) {
        ctx.sender().sendMessage(Message.raw("Reloading HyperPerms...").color(GRAY));
        plugin.reload();
        ctx.sender().sendMessage(Message.raw("\u2713 HyperPerms reloaded successfully!").color(GREEN));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== export ====================

    @Command(name = "export", description = "Export all data to a file (use --filename <name> for custom name)")
    public CompletableFuture<Void> export_(CommandContext ctx,
            @OptionalArg(name = "filename", description = "Export file name prefix (default: auto-generated)") String filenamePrefix) {
        if (filenamePrefix == null || filenamePrefix.isEmpty()) {
            filenamePrefix = "export";
        }

        ctx.sender().sendMessage(Message.raw("Creating export...").color(GRAY));

        var backupManager = plugin.getBackupManager();
        if (backupManager == null) {
            ctx.sender().sendMessage(Message.raw("\u2717 Backup/export not available").color(RED));
            return CompletableFuture.completedFuture(null);
        }

        final String finalPrefix = filenamePrefix;
        return backupManager.createBackup(finalPrefix)
            .thenAccept(backupName -> {
                if (backupName != null) {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("\u2713 Data exported to: ").color(GREEN));
                    parts.add(Message.raw(backupName).color(WHITE));
                    ctx.sender().sendMessage(join(parts));
                } else {
                    ctx.sender().sendMessage(Message.raw("\u2717 Failed to export data").color(RED));
                }
            })
            .exceptionally(e -> {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("\u2717 Error exporting: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(join(parts));
                return null;
            });
    }
}
