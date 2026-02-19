package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Clears all data for a user (permissions, groups, prefix/suffix).
 * Requires double-invocation confirmation within 60 seconds.
 * Usage: /hp user clear &lt;player&gt;
 */
public class UserClearCommand extends HpSubCommand {

    // Confirmation tracking for this destructive operation
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000; // 60 seconds

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;

    public UserClearCommand(HyperPerms hyperPerms) {
        super("clear", "Clear all data for a user");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        String confirmationKey = "user-clear:" + user.getUuid();

        // Check if this is a confirmation
        if (hasPendingConfirmation(confirmationKey)) {
            clearPendingConfirmation(confirmationKey);

            // Clear all nodes (permissions and group memberships)
            user.clearNodes();
            user.setPrimaryGroup("default");

            // Clear custom prefix/suffix
            user.setCustomPrefix(null);
            user.setCustomSuffix(null);

            hyperPerms.getUserManager().saveUser(user).join();
            hyperPerms.getCache().invalidate(user.getUuid());

            ctx.sender().sendMessage(Message.raw("Cleared all data for " + user.getFriendlyName()));
            return CompletableFuture.completedFuture(null);
        }

        // First invocation - show warning and request confirmation
        setPendingConfirmation(confirmationKey);
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(Color.RED));
        ctx.sender().sendMessage(Message.raw("You are about to CLEAR ALL DATA for user: " + user.getFriendlyName()));
        ctx.sender().sendMessage(Message.raw("This will remove:"));
        ctx.sender().sendMessage(Message.raw("  - All permissions"));
        ctx.sender().sendMessage(Message.raw("  - All group memberships (reset to 'default')"));
        ctx.sender().sendMessage(Message.raw("  - Custom prefix/suffix"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Confirmation Tracking ====================

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
}
