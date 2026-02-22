package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Deletes a permission group with a two-step confirmation.
 * <p>
 * First invocation shows a warning; running the same command again within 60 seconds
 * confirms the deletion. Confirmation tracking is kept here as static state since
 * this is the only group command that uses it.
 * <p>
 * Usage: /hp group delete &lt;name&gt;
 */
public class GroupDeleteCommand extends HpSubCommand {

    // ==================== Confirmation Tracking ====================

    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 60_000; // 60 seconds

    /**
     * Checks if a pending confirmation exists and is still valid.
     *
     * @param key the confirmation key
     * @return true if confirmation is pending and not expired
     */
    public static boolean hasPendingConfirmation(String key) {
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

    /**
     * Records a new pending confirmation.
     *
     * @param key the confirmation key
     */
    public static void setPendingConfirmation(String key) {
        pendingConfirmations.put(key, System.currentTimeMillis());
    }

    /**
     * Clears a pending confirmation after execution.
     *
     * @param key the confirmation key
     */
    public static void clearPendingConfirmation(String key) {
        pendingConfirmations.remove(key);
    }

    // ==================== Command ====================

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> nameArg;

    public GroupDeleteCommand(HyperPerms hyperPerms) {
        super("delete", "Delete a group");
        this.hyperPerms = hyperPerms;
        this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(nameArg);
        Group group = hyperPerms.getGroupManager().getGroup(groupName);

        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        String confirmationKey = "group-delete:" + groupName.toLowerCase();

        // Check if this is a confirmation
        if (hasPendingConfirmation(confirmationKey)) {
            clearPendingConfirmation(confirmationKey);
            hyperPerms.getGroupManager().deleteGroup(groupName);
            ctx.sender().sendMessage(Message.raw("Deleted group: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // First invocation - show warning and request confirmation
        setPendingConfirmation(confirmationKey);
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(java.awt.Color.RED));
        ctx.sender().sendMessage(Message.raw("You are about to DELETE group: " + groupName));
        ctx.sender().sendMessage(Message.raw("This will remove all permissions and settings for this group."));
        ctx.sender().sendMessage(Message.raw("Users in this group will lose inherited permissions."));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run the same command again within 60 seconds."));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }
}
