package com.hyperperms.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Displays the main HyperPerms help message.
 * <p>
 * Usage: /hp help
 * <p>
 * This command is registered as a subcommand of the root {@code /hp} command
 * and delegates to the parent's subcommand map for listing.
 */
public class HelpCommand extends AbstractCommand {

    private final AbstractCommand parent;

    /**
     * Creates the help command.
     *
     * @param parent the parent command whose subcommands to list
     */
    public HelpCommand(AbstractCommand parent) {
        super("help", "Show HyperPerms help");
        this.parent = parent;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        List<Message> parts = new ArrayList<>();

        // Header
        parts.add(header("HyperPerms"));

        // Description
        parts.add(Message.raw("  " + parent.getDescription() + "\n\n").color(WHITE));

        // Subcommand list
        parts.add(Message.raw("  Commands:\n").color(GOLD));
        for (var entry : parent.getSubCommands().entrySet()) {
            String name = entry.getKey();
            if (name.equals("help")) continue;
            parts.add(Message.raw("    " + name).color(GREEN));
            parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
        }

        // Hint
        parts.add(Message.raw("\n  Use /hp <command> --help for details\n").color(GRAY));

        // Footer
        parts.add(footer());

        ctx.sender().sendMessage(join(parts));
        return CompletableFuture.completedFuture(null);
    }
}
