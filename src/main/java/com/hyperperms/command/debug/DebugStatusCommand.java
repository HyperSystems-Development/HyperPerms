package com.hyperperms.command.debug;

import com.hyperperms.util.Logger;
import com.hyperperms.util.Logger.DebugCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Show the status of all debug categories.
 * <p>
 * Usage: /hp debug status
 * <p>
 * Displays each category with a color-coded ON/OFF indicator and its description.
 */
public class DebugStatusCommand extends AbstractCommand {

    public DebugStatusCommand() {
        super("status", "Show all debug category states");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        DebugCategory[] categories = DebugCategory.values();
        int enabledCount = 0;
        for (DebugCategory cat : categories) {
            if (Logger.isDebugEnabled(cat)) {
                enabledCount++;
            }
        }

        // Header
        List<Message> headerParts = new ArrayList<>();
        headerParts.add(header("Debug Categories"));
        ctx.sender().sendMessage(join(headerParts));

        // Summary line
        List<Message> summary = new ArrayList<>();
        summary.add(Message.raw("  ").color(WHITE));
        summary.add(Message.raw(String.valueOf(enabledCount)).color(enabledCount > 0 ? GREEN : GRAY));
        summary.add(Message.raw(" of ").color(GRAY));
        summary.add(Message.raw(String.valueOf(categories.length)).color(WHITE));
        summary.add(Message.raw(" categories enabled\n").color(GRAY));
        ctx.sender().sendMessage(join(summary));

        // Each category
        for (DebugCategory category : categories) {
            boolean enabled = Logger.isDebugEnabled(category);

            List<Message> line = new ArrayList<>();
            if (enabled) {
                line.add(Message.raw("  ✓ ").color(GREEN));
                line.add(Message.raw(category.name().toLowerCase()).color(GREEN));
                line.add(Message.raw(" ON").color(GREEN));
            } else {
                line.add(Message.raw("  ✗ ").color(RED));
                line.add(Message.raw(category.name().toLowerCase()).color(GRAY));
                line.add(Message.raw(" OFF").color(RED));
            }
            line.add(Message.raw(" - " + category.getDescription()).color(DARK_GRAY));
            ctx.sender().sendMessage(join(line));
        }

        // Footer hint
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("  Use /hp debug toggle <category|all> [on|off]").color(GRAY));

        List<Message> footerParts = new ArrayList<>();
        footerParts.add(footer());
        ctx.sender().sendMessage(join(footerParts));

        return CompletableFuture.completedFuture(null);
    }
}
