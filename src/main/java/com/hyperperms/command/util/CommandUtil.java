package com.hyperperms.command.util;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Centralized messaging utilities and color constants for HyperPerms commands.
 * <p>
 * Eliminates duplicate color definitions and message building boilerplate
 * that was previously scattered across 5+ command files.
 */
public final class CommandUtil {

    // ==================== Standard Colors ====================

    public static final Color GOLD = new Color(255, 170, 0);
    public static final Color GREEN = new Color(85, 255, 85);
    public static final Color RED = new Color(255, 85, 85);
    public static final Color YELLOW = new Color(255, 255, 85);
    public static final Color GRAY = Color.GRAY;
    public static final Color WHITE = Color.WHITE;
    public static final Color AQUA = new Color(85, 255, 255);
    public static final Color DARK_GRAY = new Color(100, 100, 100);
    public static final Color BLUE = new Color(85, 85, 255);

    private static final int HEADER_WIDTH = 42;

    private CommandUtil() {}

    // ==================== Prefix & Basic Messages ====================

    /**
     * Creates the HyperPerms prefix message.
     * Format: [HyperPerms]_
     *
     * @return the prefix message
     */
    @NotNull
    public static Message prefix() {
        return Message.join(
                Message.raw("[").color(GRAY),
                Message.raw("HyperPerms").color(GOLD),
                Message.raw("] ").color(GRAY)
        );
    }

    /**
     * Creates a prefixed message with the given text and color.
     *
     * @param text  the message text
     * @param color the text color
     * @return the formatted message
     */
    @NotNull
    public static Message msg(@NotNull String text, @NotNull Color color) {
        return Message.join(prefix(), Message.raw(text).color(color));
    }

    /**
     * Creates an error message (red text with prefix).
     *
     * @param text the error text
     * @return the formatted error message
     */
    @NotNull
    public static Message error(@NotNull String text) {
        return msg(text, RED);
    }

    /**
     * Creates a success message (green text with prefix).
     *
     * @param text the success text
     * @return the formatted success message
     */
    @NotNull
    public static Message success(@NotNull String text) {
        return msg(text, GREEN);
    }

    /**
     * Creates an info message (yellow text with prefix).
     *
     * @param text the info text
     * @return the formatted info message
     */
    @NotNull
    public static Message info(@NotNull String text) {
        return msg(text, YELLOW);
    }

    // ==================== Section Formatting ====================

    /**
     * Creates a styled header bar.
     * Example: --- Title -------------------------
     *
     * @param title the header title
     * @return the formatted header
     */
    @NotNull
    public static Message header(@NotNull String title) {
        int padding = HEADER_WIDTH - title.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);
        return Message.join(
                Message.raw("-".repeat(left) + " ").color(GRAY),
                Message.raw(title).color(GOLD),
                Message.raw(" " + "-".repeat(right) + "\n").color(GRAY)
        );
    }

    /**
     * Creates a footer separator bar.
     *
     * @return the formatted footer
     */
    @NotNull
    public static Message footer() {
        return Message.raw("-".repeat(HEADER_WIDTH)).color(GRAY);
    }

    /**
     * Creates a key-value line.
     * Example: "  Weight: 10\n"
     *
     * @param key   the label
     * @param value the value
     * @return the formatted key-value pair
     */
    @NotNull
    public static Message keyValue(@NotNull String key, @NotNull String value) {
        return Message.join(
                Message.raw("  " + key + ": ").color(GRAY),
                Message.raw(value + "\n").color(WHITE)
        );
    }

    /**
     * Creates a list item line.
     * Example: "  - item\n"
     *
     * @param text the item text
     * @return the formatted list item
     */
    @NotNull
    public static Message listItem(@NotNull String text) {
        return Message.join(
                Message.raw("  ").color(WHITE),
                Message.raw(text + "\n").color(GREEN)
        );
    }

    /**
     * Creates a list item with label and value.
     * Example: "    name - description\n"
     *
     * @param name        the item name
     * @param description the item description
     * @return the formatted list item
     */
    @NotNull
    public static Message listItem(@NotNull String name, @NotNull String description) {
        return Message.join(
                Message.raw("    " + name).color(GREEN),
                Message.raw(" - " + description + "\n").color(WHITE)
        );
    }

    // ==================== Permission Checking ====================

    /**
     * Checks if the command sender has the given permission.
     * <p>
     * Uses HyperPerms if available, falls back to the native PermissionsModule.
     *
     * @param ctx        the command context
     * @param hp         the HyperPerms instance
     * @param permission the permission to check
     * @return true if the sender has permission
     */
    public static boolean hasPermission(@NotNull CommandContext ctx, @NotNull HyperPerms hp,
                                        @NotNull String permission) {
        UUID senderUuid = ctx.sender().getUuid();
        if (senderUuid == null) {
            return true; // Console always has permission
        }

        // Use HyperPerms permission check
        var api = HyperPerms.getApi();
        if (api != null) {
            return api.hasPermission(senderUuid, permission);
        }

        // Fallback to native permission module
        return ctx.sender().hasPermission(permission);
    }

    /**
     * Checks if the sender has any of the given permissions.
     *
     * @param ctx         the command context
     * @param hp          the HyperPerms instance
     * @param permissions the permissions to check (any one is sufficient)
     * @return true if the sender has any of the permissions
     */
    public static boolean hasAnyPermission(@NotNull CommandContext ctx, @NotNull HyperPerms hp,
                                           @NotNull String... permissions) {
        for (String perm : permissions) {
            if (hasPermission(ctx, hp, perm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a "no permission" error message and returns true if the sender lacks permission.
     * Use in commands: {@code if (requirePermission(ctx, hp, Permissions.RELOAD)) return;}
     *
     * @param ctx        the command context
     * @param hp         the HyperPerms instance
     * @param permission the required permission
     * @return true if the sender LACKS permission (and was notified), false if they have it
     */
    public static boolean requirePermission(@NotNull CommandContext ctx, @NotNull HyperPerms hp,
                                            @NotNull String permission) {
        if (!hasPermission(ctx, hp, permission)) {
            ctx.sender().sendMessage(error("You don't have permission. (" + permission + ")"));
            return true;
        }
        return false;
    }

    // ==================== Async Error Handling ====================

    /**
     * Wraps an async future with standard error handling.
     * On success, calls the onSuccess consumer. On failure, sends an error message.
     *
     * @param future    the async operation
     * @param ctx       the command context for error reporting
     * @param onSuccess callback for successful result
     * @param <T>       the result type
     */
    public static <T> void handleAsync(@NotNull CompletableFuture<T> future,
                                       @NotNull CommandContext ctx,
                                       @NotNull Consumer<T> onSuccess) {
        future.thenAccept(onSuccess).exceptionally(throwable -> {
            Logger.severe("Async command operation failed", throwable);
            ctx.sender().sendMessage(error("An error occurred. Check console for details."));
            return null;
        });
    }

    /**
     * Wraps a void async future with standard error handling.
     *
     * @param future the async operation
     * @param ctx    the command context for error reporting
     */
    public static void handleAsync(@NotNull CompletableFuture<Void> future,
                                   @NotNull CommandContext ctx) {
        future.exceptionally(throwable -> {
            Logger.severe("Async command operation failed", throwable);
            ctx.sender().sendMessage(error("An error occurred. Check console for details."));
            return null;
        });
    }

    // ==================== Message Builders ====================

    /**
     * Joins multiple messages into one.
     * Convenience method to avoid Message.join(parts.toArray(new Message[0])) everywhere.
     *
     * @param parts the message parts
     * @return the joined message
     */
    @NotNull
    public static Message join(@NotNull List<Message> parts) {
        return Message.join(parts.toArray(new Message[0]));
    }
}
