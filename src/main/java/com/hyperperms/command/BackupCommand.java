package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Container command for backup management.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /hp backup create [--name=prefix]} - Create a manual backup</li>
 *   <li>{@code /hp backup list} - List available backups</li>
 *   <li>{@code /hp backup restore <backup>} - Restore from a backup (with confirmation)</li>
 * </ul>
 */
public class BackupCommand extends HpContainerCommand {

    @SuppressWarnings("this-escape")
    public BackupCommand(HyperPerms hyperPerms) {
        super("backup", "Manage backups");
        addSubCommand(new BackupCreateSubCommand(hyperPerms));
        addSubCommand(new BackupListSubCommand(hyperPerms));
        addSubCommand(new BackupRestoreSubCommand(hyperPerms));
    }

    // ==================== Confirmation Tracking ====================

    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000;

    private static boolean hasPendingConfirmation(String key) {
        Long timestamp = pendingConfirmations.get(key);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(key);
            return false;
        }
        return true;
    }

    private static void setPendingConfirmation(String key) {
        pendingConfirmations.put(key, System.currentTimeMillis());
    }

    private static void clearPendingConfirmation(String key) {
        pendingConfirmations.remove(key);
    }

    // ==================== Backup Create ====================

    private static class BackupCreateSubCommand extends HpSubCommand {
        private final HyperPerms hyperPerms;
        private final OptionalArg<String> nameArg;

        BackupCreateSubCommand(HyperPerms hyperPerms) {
            super("create", "Create a manual backup (use --name <prefix> for custom name)");
            this.hyperPerms = hyperPerms;
            this.nameArg = describeOptionalArg("name", "Backup name prefix (default: manual)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            String namePrefix = ctx.get(nameArg);
            if (namePrefix == null || namePrefix.isEmpty()) {
                namePrefix = "manual";
            }

            ctx.sender().sendMessage(Message.raw("Creating backup...").color(GRAY));

            final String finalPrefix = namePrefix;
            return backupManager.createBackup(finalPrefix)
                .thenAccept(backupName -> {
                    if (backupName != null) {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("✓ Backup created: ").color(GREEN));
                        parts.add(Message.raw(backupName).color(WHITE));
                        ctx.sender().sendMessage(join(parts));
                    } else {
                        ctx.sender().sendMessage(Message.raw("✗ Failed to create backup").color(RED));
                    }
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✗ Error creating backup: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(join(parts));
                    return null;
                });
        }
    }

    // ==================== Backup List ====================

    private static class BackupListSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        BackupListSubCommand(HyperPerms hyperPerms) {
            super("list", "List available backups");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
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
                        parts.add(Message.raw("  • ").color(GREEN));
                        parts.add(Message.raw(backup).color(WHITE));
                        ctx.sender().sendMessage(join(parts));
                    }
                    ctx.sender().sendMessage(Message.raw(""));
                })
                .exceptionally(e -> {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✗ Error listing backups: ").color(RED));
                    parts.add(Message.raw(e.getMessage()).color(GRAY));
                    ctx.sender().sendMessage(join(parts));
                    return null;
                });
        }
    }

    // ==================== Backup Restore ====================

    private static class BackupRestoreSubCommand extends HpSubCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> backupArg;

        BackupRestoreSubCommand(HyperPerms hyperPerms) {
            super("restore", "Restore from a backup");
            this.hyperPerms = hyperPerms;
            this.backupArg = describeArg("backup", "Backup file name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String backupName = ctx.get(backupArg);

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("✗ Backup manager not available").color(RED));
                return CompletableFuture.completedFuture(null);
            }

            String confirmationKey = "backup-restore:" + backupName;

            // Check if this is a confirmation
            if (hasPendingConfirmation(confirmationKey)) {
                clearPendingConfirmation(confirmationKey);

                List<Message> restoring = new ArrayList<>();
                restoring.add(Message.raw("Restoring from backup: ").color(GRAY));
                restoring.add(Message.raw(backupName).color(GOLD));
                ctx.sender().sendMessage(join(restoring));

                return backupManager.restoreBackup(backupName)
                    .thenAccept(success -> {
                        if (success) {
                            ctx.sender().sendMessage(Message.raw("✓ Backup restored successfully!").color(GREEN));

                            List<Message> reload = new ArrayList<>();
                            reload.add(Message.raw("Please run ").color(GRAY));
                            reload.add(Message.raw("/hp reload").color(GOLD));
                            reload.add(Message.raw(" to apply changes").color(GRAY));
                            ctx.sender().sendMessage(join(reload));
                        } else {
                            ctx.sender().sendMessage(Message.raw("✗ Failed to restore backup").color(RED));
                        }
                    })
                    .exceptionally(e -> {
                        List<Message> parts = new ArrayList<>();
                        parts.add(Message.raw("✗ Error restoring backup: ").color(RED));
                        parts.add(Message.raw(e.getMessage()).color(GRAY));
                        ctx.sender().sendMessage(join(parts));
                        return null;
                    });
            }

            // First invocation - show warning and request confirmation
            setPendingConfirmation(confirmationKey);
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
}
