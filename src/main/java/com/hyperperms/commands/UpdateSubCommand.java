package com.hyperperms.commands;

import com.hyperperms.HyperPerms;
import com.hyperperms.update.UpdateChecker;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

public class UpdateSubCommand extends AbstractCommand {

    private final HyperPerms hyperPerms;

    @SuppressWarnings("this-escape")
    public UpdateSubCommand(@NotNull HyperPerms hyperPerms) {
        super("update", "Check for and download plugin updates");
        this.hyperPerms = hyperPerms;
        addSubCommand(new ConfirmSubCommand(hyperPerms));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Check permission
        if (!hasPermission(ctx)) {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(RED)
                    .insert(Message.raw("You don't have permission to use this command.").color(RED))
            );
            return CompletableFuture.completedFuture(null);
        }

        // Check if update checker is available
        UpdateChecker checker = hyperPerms.getUpdateChecker();
        if (checker == null) {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(RED)
                    .insert(Message.raw("Update checking is disabled in config.").color(RED))
            );
            return CompletableFuture.completedFuture(null);
        }

        return handleCheck(ctx, checker);
    }

    /**
     * Handles /hp update - checks for updates and displays status.
     */
    private CompletableFuture<Void> handleCheck(CommandContext ctx, UpdateChecker checker) {
        ctx.sender().sendMessage(
            Message.raw("[HyperPerms] ").color(GOLD)
                .insert(Message.raw("Checking for updates...").color(GRAY))
        );

        // Force refresh the update check
        checker.checkForUpdates(true).thenAccept(info -> {
            if (info != null) {
                // Update available
                String currentVersion = checker.getCurrentVersion();
                String newVersion = info.version();

                ctx.sender().sendMessage(Message.raw(""));
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(GOLD)
                        .insert(Message.raw("A new version is available!").color(GOLD).bold(true))
                );
                ctx.sender().sendMessage(
                    Message.raw("Current: ").color(GRAY)
                        .insert(Message.raw("v" + currentVersion).color(WHITE))
                        .insert(Message.raw(" → ").color(GRAY))
                        .insert(Message.raw("Latest: ").color(GRAY))
                        .insert(Message.raw("v" + newVersion).color(GREEN))
                );

                // Show changelog summary if available
                if (info.changelog() != null && !info.changelog().isEmpty()) {
                    String summary = truncateChangelog(info.changelog(), 150);
                    ctx.sender().sendMessage(
                        Message.raw("Changelog: ").color(GRAY)
                            .insert(Message.raw(summary).color(WHITE))
                    );
                }

                ctx.sender().sendMessage(Message.raw(""));
                ctx.sender().sendMessage(
                    Message.raw("Run ").color(GRAY)
                        .insert(Message.raw("/hp update confirm").color(GREEN))
                        .insert(Message.raw(" to download and install the update.").color(GRAY))
                );

            } else {
                // Up to date
                String currentVersion = checker.getCurrentVersion();
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(GREEN)
                        .insert(Message.raw("Plugin is up-to-date ").color(GREEN))
                        .insert(Message.raw("(v" + currentVersion + ")").color(WHITE))
                );
            }
        }).exceptionally(e -> {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(RED)
                    .insert(Message.raw("Failed to check for updates: " + e.getMessage()).color(RED))
            );
            Logger.warn("[Update] Failed to check for updates: %s", e.getMessage());
            return null;
        });

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Checks if the sender has permission to use this command.
     * Checks for wildcard, admin permission, specific permission, and Hytale native permissions.
     */
    private static boolean hasPermission(CommandContext ctx) {
        // Console always has permission (null UUID means console)
        UUID uuid = ctx.sender().getUuid();
        if (uuid == null) {
            return true;
        }

        HyperPerms hp = HyperPerms.getInstance();
        if (hp == null) {
            return false;
        }

        // Check HyperPerms permissions (wildcard first, then specific)
        if (hp.hasPermission(uuid, com.hyperperms.util.Permissions.ADMIN) ||
            hp.hasPermission(uuid, com.hyperperms.util.Permissions.UPDATE)) {
            return true;
        }

        // Fallback to Hytale's native permission system (for operators)
        try {
            var permModule = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
            if (permModule != null) {
                return permModule.hasPermission(uuid, com.hyperperms.util.Permissions.ADMIN) ||
                       permModule.hasPermission(uuid, com.hyperperms.util.Permissions.UPDATE) ||
                       permModule.hasPermission(uuid, "*");
            }
        } catch (Exception ignored) {
        }
        
        return false;
    }

    /**
     * Truncates changelog to a maximum length for display.
     */
    private static String truncateChangelog(String changelog, int maxLength) {
        // Remove markdown formatting and newlines
        String cleaned = changelog
            .replaceAll("#+\\s*", "")  // Remove headers
            .replaceAll("\\*+", "")     // Remove bold/italic
            .replaceAll("\\n+", " ")    // Replace newlines with spaces
            .replaceAll("\\s+", " ")    // Collapse whitespace
            .trim();

        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - 3) + "...";
    }

    /**
     * Nested subcommand for /hp update confirm.
     * Downloads and installs the latest update.
     */
    private static class ConfirmSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ConfirmSubCommand(HyperPerms hyperPerms) {
            super("confirm", "Download and install the latest update");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            // Check permission - use outer class's static method
            if (!UpdateSubCommand.hasPermission(ctx)) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("You don't have permission to use this command.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            // Check if update checker is available
            UpdateChecker checker = hyperPerms.getUpdateChecker();
            if (checker == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("Update checking is disabled in config.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            return handleConfirm(ctx, checker);
        }

        /**
         * Handles /hp update confirm - downloads the update.
         */
        private CompletableFuture<Void> handleConfirm(CommandContext ctx, UpdateChecker checker) {
            UpdateChecker.UpdateInfo info = checker.getCachedUpdate();

            if (info == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(GRAY)
                        .insert(Message.raw("No update available. Run ").color(GRAY))
                        .insert(Message.raw("/hp update").color(GREEN))
                        .insert(Message.raw(" to check for updates.").color(GRAY))
                );
                return CompletableFuture.completedFuture(null);
            }

            if (info.downloadUrl() == null || info.downloadUrl().isEmpty()) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("No download URL available for this release.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            String newVersion = info.version();

            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GOLD)
                    .insert(Message.raw("Downloading update v" + newVersion + "...").color(GOLD))
            );

            checker.downloadUpdate(info).thenAccept(path -> {
                if (path != null) {
                    ctx.sender().sendMessage(
                        Message.raw("[HyperPerms] ").color(GREEN)
                            .insert(Message.raw("Update downloaded successfully!").color(GREEN).bold(true))
                    );
                    ctx.sender().sendMessage(
                        Message.raw("[HyperPerms] ").color(GRAY)
                            .insert(Message.raw("Restart the server to apply the update.").color(GOLD))
                    );
                    Logger.info("[Update] Downloaded update v%s to %s", newVersion, path.getFileName());
                } else {
                    ctx.sender().sendMessage(
                        Message.raw("[HyperPerms] ").color(RED)
                            .insert(Message.raw("Failed to download update. Check console for details.").color(RED))
                    );
                }
            }).exceptionally(e -> {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("Failed to download update: " + e.getMessage()).color(RED))
                );
                Logger.warn("[Update] Failed to download update: %s", e.getMessage());
                return null;
            });

            return CompletableFuture.completedFuture(null);
        }
    }
}
