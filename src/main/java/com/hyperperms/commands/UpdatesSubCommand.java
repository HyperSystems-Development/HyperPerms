package com.hyperperms.commands;

import com.hyperperms.HyperPerms;
import com.hyperperms.update.UpdateNotificationPreferences;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Subcommand for /hp updates [on|off].
 * <p>
 * Allows players to toggle update notifications on join.
 * <ul>
 *   <li>/hp updates - Show current notification preference</li>
 *   <li>/hp updates on - Enable update notifications</li>
 *   <li>/hp updates off - Disable update notifications</li>
 * </ul>
 */
public class UpdatesSubCommand extends AbstractCommand {

    private final HyperPerms hyperPerms;

    @SuppressWarnings("this-escape")
    public UpdatesSubCommand(@NotNull HyperPerms hyperPerms) {
        super("updates", "Toggle update notifications on join");
        this.hyperPerms = hyperPerms;

        // Add subcommands for on/off
        addSubCommand(new OnSubCommand(hyperPerms));
        addSubCommand(new OffSubCommand(hyperPerms));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Check permission
        if (!checkPermission(ctx)) {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(RED)
                    .insert(Message.raw("You don't have permission to use this command.").color(RED))
            );
            return CompletableFuture.completedFuture(null);
        }

        // Console cannot toggle notifications (null UUID means console)
        if (ctx.sender().getUuid() == null) {
            return handleConsole(ctx);
        }

        // No subcommand = show status
        return handleStatus(ctx);
    }

    /**
     * Handles console sender - shows status only.
     */
    private CompletableFuture<Void> handleConsole(CommandContext ctx) {
        ctx.sender().sendMessage(
            Message.raw("[HyperPerms] ").color(GOLD)
                .insert(Message.raw("Update notifications: ").color(GRAY))
                .insert(Message.raw("N/A for console").color(WHITE))
        );
        ctx.sender().sendMessage(
            Message.raw("[HyperPerms] ").color(GRAY)
                .insert(Message.raw("Use ").color(GRAY))
                .insert(Message.raw("/hp update").color(GREEN))
                .insert(Message.raw(" to check for updates.").color(GRAY))
        );
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles /hp updates - shows current preference.
     */
    private CompletableFuture<Void> handleStatus(CommandContext ctx) {
        UpdateNotificationPreferences prefs = hyperPerms.getNotificationPreferences();
        var uuid = ctx.sender().getUuid();

        boolean enabled = prefs != null && prefs.isEnabled(uuid);

        ctx.sender().sendMessage(
            Message.raw("[HyperPerms] ").color(GOLD)
                .insert(Message.raw("Update notifications: ").color(GRAY))
                .insert(enabled
                    ? Message.raw("Enabled").color(GREEN)
                    : Message.raw("Disabled").color(RED))
        );

        if (enabled) {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GRAY)
                    .insert(Message.raw("You will be notified about updates on join.").color(GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GRAY)
                    .insert(Message.raw("Run ").color(GRAY))
                    .insert(Message.raw("/hp updates off").color(WHITE))
                    .insert(Message.raw(" to disable.").color(GRAY))
            );
        } else {
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GRAY)
                    .insert(Message.raw("Run ").color(GRAY))
                    .insert(Message.raw("/hp updates on").color(WHITE))
                    .insert(Message.raw(" to enable.").color(GRAY))
            );
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Checks if the sender has permission to use this command.
     */
    private static boolean checkPermission(CommandContext ctx, HyperPerms hyperPerms) {
        UUID uuid = ctx.sender().getUuid();
        if (uuid == null) {
            return true;
        }

        if (hyperPerms.hasPermission(uuid, com.hyperperms.util.Permissions.ADMIN) ||
            hyperPerms.hasPermission(uuid, com.hyperperms.util.Permissions.UPDATES_ALL) ||
            hyperPerms.hasPermission(uuid, com.hyperperms.util.Permissions.UPDATES_TOGGLE)) {
            return true;
        }

        try {
            var permModule = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
            if (permModule != null) {
                return permModule.hasPermission(uuid, com.hyperperms.util.Permissions.ADMIN) ||
                       permModule.hasPermission(uuid, com.hyperperms.util.Permissions.UPDATES_TOGGLE) ||
                       permModule.hasPermission(uuid, "*");
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean checkPermission(CommandContext ctx) {
        return checkPermission(ctx, hyperPerms);
    }

    /**
     * Subcommand for /hp updates on
     */
    private static class OnSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        OnSubCommand(HyperPerms hyperPerms) {
            super("on", "Enable update notifications");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (!checkPermission(ctx, hyperPerms)) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("You don't have permission to use this command.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            if (ctx.sender().getUuid() == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("This command can only be used by players.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            UpdateNotificationPreferences prefs = hyperPerms.getNotificationPreferences();
            if (prefs == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("Update preferences not available.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            var uuid = ctx.sender().getUuid();
            prefs.setEnabled(uuid, true);

            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GREEN)
                    .insert(Message.raw("Update notifications ").color(GREEN))
                    .insert(Message.raw("enabled").color(GREEN).bold(true))
                    .insert(Message.raw(".").color(GREEN))
            );
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GRAY)
                    .insert(Message.raw("You will be notified about updates on join.").color(GRAY))
            );

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Subcommand for /hp updates off
     */
    private static class OffSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        OffSubCommand(HyperPerms hyperPerms) {
            super("off", "Disable update notifications");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (!checkPermission(ctx, hyperPerms)) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("You don't have permission to use this command.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            if (ctx.sender().getUuid() == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("This command can only be used by players.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            UpdateNotificationPreferences prefs = hyperPerms.getNotificationPreferences();
            if (prefs == null) {
                ctx.sender().sendMessage(
                    Message.raw("[HyperPerms] ").color(RED)
                        .insert(Message.raw("Update preferences not available.").color(RED))
                );
                return CompletableFuture.completedFuture(null);
            }

            var uuid = ctx.sender().getUuid();
            prefs.setEnabled(uuid, false);

            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GOLD)
                    .insert(Message.raw("Update notifications ").color(GOLD))
                    .insert(Message.raw("disabled").color(RED).bold(true))
                    .insert(Message.raw(".").color(GOLD))
            );
            ctx.sender().sendMessage(
                Message.raw("[HyperPerms] ").color(GRAY)
                    .insert(Message.raw("You can still use ").color(GRAY))
                    .insert(Message.raw("/hp update").color(WHITE))
                    .insert(Message.raw(" to check manually.").color(GRAY))
            );

            return CompletableFuture.completedFuture(null);
        }
    }
}
