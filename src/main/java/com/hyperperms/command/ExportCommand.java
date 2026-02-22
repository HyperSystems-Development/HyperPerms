package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Export all data to a file.
 * <p>
 * Usage: /hp export [--filename=name]
 */
public class ExportCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final OptionalArg<String> filenameArg;

    public ExportCommand(HyperPerms hyperPerms) {
        super("export", "Export all data to a file (use --filename <name> for custom name)");
        this.hyperPerms = hyperPerms;
        this.filenameArg = describeOptionalArg("filename", "Export file name prefix (default: auto-generated)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String filenamePrefix = ctx.get(filenameArg);
        if (filenamePrefix == null || filenamePrefix.isEmpty()) {
            filenamePrefix = "export";
        }

        ctx.sender().sendMessage(Message.raw("Creating export...").color(GRAY));

        // Export is essentially a backup with a custom name
        var backupManager = hyperPerms.getBackupManager();
        if (backupManager == null) {
            ctx.sender().sendMessage(Message.raw("✗ Backup/export not available").color(RED));
            return CompletableFuture.completedFuture(null);
        }

        final String finalPrefix = filenamePrefix;
        return backupManager.createBackup(finalPrefix)
            .thenAccept(backupName -> {
                if (backupName != null) {
                    List<Message> parts = new ArrayList<>();
                    parts.add(Message.raw("✓ Data exported to: ").color(GREEN));
                    parts.add(Message.raw(backupName).color(WHITE));
                    ctx.sender().sendMessage(join(parts));
                } else {
                    ctx.sender().sendMessage(Message.raw("✗ Failed to export data").color(RED));
                }
            })
            .exceptionally(e -> {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("✗ Error exporting: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(join(parts));
                return null;
            });
    }
}
