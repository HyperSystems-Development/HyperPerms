package com.hyperperms.command.debug;

import com.hyperperms.command.HpSubCommand;
import com.hyperperms.config.ConfigManager;
import com.hyperperms.util.Logger;
import com.hyperperms.util.Logger.DebugCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Toggle debug categories on or off.
 * <p>
 * Usage: /hp debug toggle <category|all> [on|off]
 * <p>
 * Without on/off, toggles the current state. With "all", enables/disables all categories.
 * Changes are persisted to debug.json via ConfigManager.
 */
public class DebugToggleCommand extends HpSubCommand {
    private final RequiredArg<String> categoryArg;
    private final OptionalArg<String> stateArg;

    public DebugToggleCommand() {
        super("toggle", "Toggle debug categories on or off");
        this.categoryArg = describeArg("category", "Debug category name or 'all'", ArgTypes.STRING);
        this.stateArg = describeOptionalArg("state", "Explicit state: on or off (omit to toggle)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String categoryName = ctx.get(categoryArg).toUpperCase();
        String stateStr = ctx.get(stateArg);

        // Handle "all" keyword
        if (categoryName.equals("ALL")) {
            boolean newState;
            if (stateStr != null) {
                newState = stateStr.equalsIgnoreCase("on");
            } else {
                // Toggle: if any are enabled, disable all; otherwise enable all
                newState = !Logger.isAnyDebugEnabled();
            }

            if (newState) {
                Logger.enableAllDebug();
            } else {
                Logger.disableAllDebug();
            }

            // Persist to config
            ConfigManager.get().debug().syncFromLogger();

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("All debug categories ").color(GRAY));
            if (newState) {
                parts.add(Message.raw("ENABLED").color(GREEN));
            } else {
                parts.add(Message.raw("DISABLED").color(RED));
            }
            ctx.sender().sendMessage(join(parts));
            return CompletableFuture.completedFuture(null);
        }

        // Resolve category by name
        DebugCategory category;
        try {
            category = DebugCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            String validNames = Arrays.stream(DebugCategory.values())
                    .map(c -> c.name().toLowerCase())
                    .collect(Collectors.joining(", "));
            ctx.sender().sendMessage(Message.raw("✗ Unknown category: " + categoryName.toLowerCase()).color(RED));
            ctx.sender().sendMessage(Message.raw("  Valid categories: " + validNames).color(GRAY));
            ctx.sender().sendMessage(Message.raw("  Use 'all' to toggle all categories.").color(GRAY));
            return CompletableFuture.completedFuture(null);
        }

        // Determine new state
        boolean newState;
        if (stateStr != null) {
            newState = stateStr.equalsIgnoreCase("on");
        } else {
            newState = !Logger.isDebugEnabled(category);
        }

        // Apply and persist
        Logger.setDebugEnabled(category, newState);
        ConfigManager.get().debug().syncFromLogger();

        // Confirmation message
        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("Debug ").color(GRAY));
        parts.add(Message.raw(category.name().toLowerCase()).color(GOLD));
        parts.add(Message.raw(": ").color(GRAY));
        if (newState) {
            parts.add(Message.raw("ON").color(GREEN));
        } else {
            parts.add(Message.raw("OFF").color(RED));
        }
        ctx.sender().sendMessage(join(parts));

        // Show category description
        ctx.sender().sendMessage(Message.raw("  " + category.getDescription()).color(GRAY));

        return CompletableFuture.completedFuture(null);
    }
}
