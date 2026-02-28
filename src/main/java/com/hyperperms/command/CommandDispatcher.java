package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.annotation.Confirm;
import com.hyperperms.command.annotation.Permission;
import com.hyperperms.command.util.CommandUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles permission checks and confirmation tracking for annotated command methods.
 */
public final class CommandDispatcher {

    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();

    private CommandDispatcher() {}

    /**
     * Check if the sender has the required permission.
     * Returns true if allowed, false if denied (sends error message).
     */
    public static boolean checkPermission(@NotNull CommandContext ctx, @NotNull HyperPerms hp,
                                           @Nullable Permission permission) {
        if (permission == null) return true;
        return !CommandUtil.requirePermission(ctx, hp, permission.value());
    }

    /**
     * Handle confirmation flow. Returns true if the command should execute
     * (second invocation within timeout). First invocation shows warning and returns false.
     */
    public static boolean handleConfirmation(@NotNull CommandContext ctx, @NotNull String commandKey,
                                              @NotNull Confirm confirm) {
        long now = System.currentTimeMillis();
        long timeoutMillis = confirm.timeoutSeconds() * 1000L;

        Long timestamp = pendingConfirmations.get(commandKey);
        if (timestamp != null && (now - timestamp) <= timeoutMillis) {
            pendingConfirmations.remove(commandKey);
            return true;
        }

        pendingConfirmations.put(commandKey, now);
        ctx.sender().sendMessage(CommandUtil.msg(confirm.message(), CommandUtil.YELLOW));
        ctx.sender().sendMessage(CommandUtil.msg(
                "Run the command again within " + confirm.timeoutSeconds() + " seconds to confirm.",
                CommandUtil.GRAY));
        return false;
    }
}
