package com.hyperperms.platform;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.*;
import com.hyperperms.command.groups.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Root /hp command for HyperPerms.
 * <p>
 * All business logic lives in extracted command classes under
 * {@code com.hyperperms.command.*}. This class only handles
 * registration and the top-level help message.
 */
public class HyperPermsCommand extends AbstractCommand {

    @SuppressWarnings("this-escape")
    public HyperPermsCommand(HyperPerms hyperPerms) {
        super("hp", "HyperPerms management command");

        CommandScanner scanner = new CommandScanner(hyperPerms);

        // Scanned container groups
        GroupCommands groupCommands = new GroupCommands(hyperPerms);
        var groupContainer = scanner.scanGroup(groupCommands);
        groupContainer.addSubCommand(groupCommands.createParentCommand());
        addSubCommand(groupContainer);

        addSubCommand(scanner.scanGroup(new UserCommands(hyperPerms)));
        addSubCommand(scanner.scanGroup(new DebugCommands(hyperPerms)));
        addSubCommand(scanner.scanGroup(new PermsCommands(hyperPerms)));
        addSubCommand(scanner.scanGroup(new BackupCommands(hyperPerms)));

        // Scanned root-level commands
        for (var cmd : scanner.scanRootCommands(new RootCommands(hyperPerms))) {
            addSubCommand(cmd);
        }

        // Manual registrations (complex commands kept as-is)
        addSubCommand(new HelpCommand(this));
        addSubCommand(new ImportCommand(hyperPerms));
        addSubCommand(new ResetGroupsCommand(hyperPerms));
        addSubCommand(new com.hyperperms.commands.MigrateSubCommand(hyperPerms));

        // Web editor commands (conditional)
        if (hyperPerms.getWebEditorService() != null) {
            addSubCommand(new com.hyperperms.commands.EditorSubCommand(hyperPerms, hyperPerms.getWebEditorService()));
            addSubCommand(new com.hyperperms.commands.ApplySubCommand(hyperPerms, hyperPerms.getWebEditorService()));
        }

        // Update commands
        addSubCommand(new com.hyperperms.commands.UpdateSubCommand(hyperPerms));
        addSubCommand(new com.hyperperms.commands.UpdatesSubCommand(hyperPerms));

        // Template commands
        addSubCommand(new com.hyperperms.commands.TemplateSubCommand(hyperPerms));

        // Analytics commands
        addSubCommand(new com.hyperperms.commands.AnalyticsSubCommand(hyperPerms));

        // Aliases
        addAliases("hyperperms", "perms");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(getUsageString(ctx.sender()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Message getUsageString(com.hypixel.hytale.server.core.command.system.CommandSender sender) {
        java.util.List<Message> parts = new java.util.ArrayList<>();

        parts.add(header("hp"));
        parts.add(Message.raw("  HyperPerms management command\n\n").color(WHITE));
        parts.add(Message.raw("  Commands:\n").color(GOLD));

        for (var entry : getSubCommands().entrySet()) {
            if (entry.getKey().equals("help")) continue;
            parts.add(Message.raw("    " + entry.getKey()).color(GREEN));
            parts.add(Message.raw(" - " + entry.getValue().getDescription() + "\n").color(WHITE));
        }

        parts.add(Message.raw("\n  Use /hp <command> --help for details\n").color(GRAY));
        parts.add(footer());

        return join(parts);
    }
}
