package com.hyperperms.command.groups;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.*;
import com.hyperperms.command.annotation.Command;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * All /hp backup subcommands.
 */
@CommandGroup(name = "backup", description = "Manage backups")
public class BackupCommands {

    private final HyperPerms plugin;

    /** Inline confirmation tracking for restore (key = "backup-restore:" + backupName). */
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000;

    public BackupCommands(@NotNull HyperPerms plugin) {
        this.plugin = plugin;
    }

    // ==================== create ====================

    @Command(name = "create", description = "Create a manual backup (use --name <prefix> for custom name)")
    public CompletableFuture<Void> create(CommandContext ctx,
            @OptionalArg(name = "name", description = "Backup name prefix (default: manual)") String namePrefix) {
        var backupManager = plugin.getBackupManager();
        if (backupManager == null) {
            ctx.sender().sendMessage(Message.raw("\u2717 Backup manager not available").color(RED));
            return CompletableFuture.completedFuture(null);
        }

        if (namePrefix == null || namePrefix.isEmpty()) {
            namePrefix = "manual";
        }

        ctx.sender().sendMessage(Message.raw("Creating backup...").color(GRAY));

        final String finalPrefix = namePrefix;
        return backupManager.createBackup(finalPrefix)
            .thenAccept(backupName -> {
                if (backupName != null) {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("\u2713 Backup created: ").color(GREEN));
                    parts.add(Message.raw(backupName).color(WHITE));
                    ctx.sender().sendMessage(join(parts));
                } else {
                    ctx.sender().sendMessage(Message.raw("\u2717 Failed to create backup").color(RED));
                }
            })
            .exceptionally(e -> {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("\u2717 Error creating backup: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(join(parts));
                return null;
            });
    }

    // ==================== list ====================

    @Command(name = "list", description = "List available backups")
    public CompletableFuture<Void> list(CommandContext ctx) {
        var backupManager = plugin.getBackupManager();
        if (backupManager == null) {
            ctx.sender().sendMessage(Message.raw("\u2717 Backup manager not available").color(RED));
            return CompletableFuture.completedFuture(null);
        }

        return backupManager.listBackups()
            .thenAccept(backups -> {
                if (backups.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("No backups found").color(GRAY));
                    return;
                }

                ctx.sender().sendMessage(Message.raw(""));

                List<Message> header = new ArrayList<>();
                header.add(Message.raw("--- Backups (").color(GRAY));
                header.add(Message.raw(String.valueOf(backups.size())).color(GOLD));
                header.add(Message.raw(") ---").color(GRAY));
                ctx.sender().sendMessage(join(header));

                for (String backup : backups) {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("  \u2022 ").color(GREEN));
                    parts.add(Message.raw(backup).color(WHITE));
                    ctx.sender().sendMessage(join(parts));
                }
                ctx.sender().sendMessage(Message.raw(""));
            })
            .exceptionally(e -> {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("\u2717 Error listing backups: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(join(parts));
                return null;
            });
    }

    // ==================== restore ====================

    @Command(name = "restore", description = "Restore from a backup")
    public CompletableFuture<Void> restore(CommandContext ctx,
            @Arg(name = "backup", description = "Backup file name") String backupName) {
        var backupManager = plugin.getBackupManager();
        if (backupManager == null) {
            ctx.sender().sendMessage(Message.raw("\u2717 Backup manager not available").color(RED));
            return CompletableFuture.completedFuture(null);
        }

        String confirmationKey = "backup-restore:" + backupName;

        // Check if this is a confirmation
        Long timestamp = pendingConfirmations.get(confirmationKey);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) <= CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(confirmationKey);

            List<Message> restoring = new ArrayList<>();
            restoring.add(Message.raw("Restoring from backup: ").color(GRAY));
            restoring.add(Message.raw(backupName).color(GOLD));
            ctx.sender().sendMessage(join(restoring));

            return backupManager.restoreBackup(backupName)
                .thenAccept(success -> {
                    if (success) {
                        ctx.sender().sendMessage(Message.raw("\u2713 Backup restored successfully!").color(GREEN));

                        List<Message> reload = new ArrayList<>();
                        reload.add(Message.raw("Please run ").color(GRAY));
                        reload.add(Message.raw("/hp reload").color(GOLD));
                        reload.add(Message.raw(" to apply changes").color(GRAY));
                        ctx.sender().sendMessage(join(reload));
                    } else {
                        ctx.sender().sendMessage(Message.raw("\u2717 Failed to restore backup").color(RED));
                    }
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("\u2717 Error restoring backup: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(join(parts));
                    return null;
                });
        }

        // Expired or no pending — clean up and show warning
        if (timestamp != null) {
            pendingConfirmations.remove(confirmationKey);
        }

        // First invocation - show warning and request confirmation
        pendingConfirmations.put(confirmationKey, System.currentTimeMillis());
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(RED));
        ctx.sender().sendMessage(Message.raw("You are about to RESTORE from backup: " + backupName));
        ctx.sender().sendMessage(Message.raw("This will OVERWRITE all current data including:"));
        ctx.sender().sendMessage(Message.raw("  - All users and their permissions"));
        ctx.sender().sendMessage(Message.raw("  - All groups and their settings"));
        ctx.sender().sendMessage(Message.raw("  - All tracks"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("Current data will be LOST unless you have a backup."));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }
}
