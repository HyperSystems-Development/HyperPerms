package com.hyperperms.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Base class for HyperPerms container commands that hold subcommands.
 * <p>
 * Extracted from the inner {@code HpContainerCommand} class in HyperPermsCommand.java.
 * Automatically adds a "help" subcommand and provides styled help formatting.
 */
public abstract class HpContainerCommand extends AbstractCommand {

    @SuppressWarnings("this-escape")
    protected HpContainerCommand(String name, String description) {
        super(name, description);
        addSubCommand(new AbstractCommand("help", "Show help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ctx.sender().sendMessage(HpContainerCommand.this.getUsageString(ctx.sender()));
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    public Message getUsageString(CommandSender sender) {
        List<Message> parts = new ArrayList<>();
        String name = getFullyQualifiedName();

        // Header
        parts.add(header(name));

        // Description
        parts.add(Message.raw("  " + getDescription() + "\n\n").color(WHITE));

        // Subcommand list
        parts.add(Message.raw("  Subcommands:\n").color(GOLD));
        for (var entry : getSubCommands().entrySet()) {
            if (entry.getKey().equals("help")) continue;
            parts.add(Message.raw("    " + entry.getKey()).color(GREEN));
            parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
        }

        // Hint
        parts.add(Message.raw("\n  Use /" + name + " <subcommand> --help for details\n").color(GRAY));

        // Footer
        parts.add(footer());

        return join(parts);
    }

    @Override
    public Message getUsageShort(CommandSender sender, boolean showAll) {
        List<Message> parts = new ArrayList<>();

        parts.add(Message.raw("  Usage: ").color(GOLD));
        parts.add(Message.raw("/").color(GRAY));
        parts.add(Message.raw(getFullyQualifiedName()).color(GOLD));
        String subNames = " <" + getSubCommands().keySet().stream()
                .filter(k -> !k.equals("help"))
                .collect(Collectors.joining("|")) + ">";
        parts.add(Message.raw(subNames).color(RED));

        if (showAll) {
            parts.add(Message.raw("\n  Available:\n").color(RED));
            for (var entry : getSubCommands().entrySet()) {
                if (entry.getKey().equals("help")) continue;
                parts.add(Message.raw("    " + entry.getKey()).color(GREEN));
                parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
            }
        }

        return join(parts);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(getUsageString(ctx.sender()));
        return CompletableFuture.completedFuture(null);
    }
}
