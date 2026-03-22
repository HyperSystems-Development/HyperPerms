package com.hyperperms.command;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Container command for import subcommands.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /hp import defaults} - Create default group hierarchy</li>
 *   <li>{@code /hp import file <filename>} - Import data from a backup file</li>
 * </ul>
 */
public class ImportCommand extends HpContainerCommand {

    @SuppressWarnings("this-escape")
    public ImportCommand(HyperPerms hyperPerms) {
        super("import", "Import data from file or create defaults");
        addSubCommand(new ImportDefaultsSubCommand(hyperPerms));
        addSubCommand(new ImportFileSubCommand(hyperPerms));
    }

    // ==================== Import Defaults ====================

    private static class ImportDefaultsSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ImportDefaultsSubCommand(HyperPerms hyperPerms) {
            super("defaults", "Create default group hierarchy");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(Message.raw("Creating default groups..."));

            var groupManager = hyperPerms.getGroupManager();

            // Check if groups already exist
            int existingCount = 0;
            String[] defaultGroups = {"default", "member", "builder", "moderator", "admin", "owner"};
            for (String name : defaultGroups) {
                if (groupManager.getGroup(name) != null) {
                    existingCount++;
                }
            }

            if (existingCount > 0) {
                ctx.sender().sendMessage(Message.raw("WARNING: " + existingCount + " default groups already exist."));
                ctx.sender().sendMessage(Message.raw("Existing groups will be updated, not replaced."));
            }

            List<CompletableFuture<Void>> saveFutures = new ArrayList<>();

            // Create or update default group
            Group defaultGroup = getOrCreateGroup("default");
            defaultGroup.setWeight(0);
            defaultGroup.setDisplayName("Default");
            defaultGroup.setNode(Node.builder("hyperperms.command.check.self").value(true).build());
            saveFutures.add(groupManager.saveGroup(defaultGroup));

            // Create member group
            Group memberGroup = getOrCreateGroup("member");
            memberGroup.setWeight(10);
            memberGroup.setDisplayName("Member");
            memberGroup.addParent("default");
            saveFutures.add(groupManager.saveGroup(memberGroup));

            // Create builder group
            Group builderGroup = getOrCreateGroup("builder");
            builderGroup.setWeight(20);
            builderGroup.setDisplayName("Builder");
            builderGroup.setPrefix("&2[Builder] ");
            builderGroup.addParent("member");
            builderGroup.setNode(Node.builder("hytale.editor.builderTools").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.brush.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.selection.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.prefab.use").value(true).build());
            builderGroup.setNode(Node.builder("hytale.editor.history").value(true).build());
            builderGroup.setNode(Node.builder("hytale.camera.flycam").value(true).build());
            saveFutures.add(groupManager.saveGroup(builderGroup));

            // Create moderator group
            Group modGroup = getOrCreateGroup("moderator");
            modGroup.setWeight(50);
            modGroup.setDisplayName("Moderator");
            modGroup.setPrefix("&9[Mod] ");
            modGroup.addParent("builder");
            modGroup.setNode(Node.builder("hyperperms.command.user.info").value(true).build());
            modGroup.setNode(Node.builder("hyperperms.command.check.others").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.kick").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.ban").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.unban").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.tp.self").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.tp.others").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.inventory.see").value(true).build());
            modGroup.setNode(Node.builder("hytale.command.who").value(true).build());
            saveFutures.add(groupManager.saveGroup(modGroup));

            // Create admin group
            Group adminGroup = getOrCreateGroup("admin");
            adminGroup.setWeight(90);
            adminGroup.setDisplayName("Admin");
            adminGroup.setPrefix("&c[Admin] ");
            adminGroup.addParent("moderator");
            adminGroup.setNode(Node.builder("hyperperms.command.*").value(true).build());
            adminGroup.setNode(Node.builder("hytale.command.*").value(true).build());
            adminGroup.setNode(Node.builder("hytale.editor.*").value(true).build());
            saveFutures.add(groupManager.saveGroup(adminGroup));

            // Create owner group
            Group ownerGroup = getOrCreateGroup("owner");
            ownerGroup.setWeight(100);
            ownerGroup.setDisplayName("Owner");
            ownerGroup.setPrefix("&4[Owner] ");
            ownerGroup.setNode(Node.builder("*").value(true).build());
            saveFutures.add(groupManager.saveGroup(ownerGroup));

            // Wait for all saves to complete before reporting success
            return CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new))
                .thenAccept(v -> {
                    hyperPerms.getCacheInvalidator().invalidateAll();

                    ctx.sender().sendMessage(Message.raw(""));
                    ctx.sender().sendMessage(Message.raw("Default groups created:"));
                    ctx.sender().sendMessage(Message.raw("  default (weight 0) - Base permissions"));
                    ctx.sender().sendMessage(Message.raw("  member (weight 10) - Trusted players"));
                    ctx.sender().sendMessage(Message.raw("  builder (weight 20) - Building/editor tools"));
                    ctx.sender().sendMessage(Message.raw("  moderator (weight 50) - Player management"));
                    ctx.sender().sendMessage(Message.raw("  admin (weight 90) - Full command access"));
                    ctx.sender().sendMessage(Message.raw("  owner (weight 100) - Full server access (*)"));
                    ctx.sender().sendMessage(Message.raw(""));
                    ctx.sender().sendMessage(Message.raw("Use /hp user <player> addgroup <group> to assign players"));
                })
                .exceptionally(e -> {
                    ctx.sender().sendMessage(Message.raw("Failed to save default groups: " + e.getMessage()));
                    return null;
                });
        }

        private Group getOrCreateGroup(String name) {
            var groupManager = hyperPerms.getGroupManager();
            Group group = groupManager.getGroup(name);
            if (group == null) {
                groupManager.createGroup(name);
                group = groupManager.getGroup(name);
            }
            return group;
        }
    }

    // ==================== Import File ====================

    private static class ImportFileSubCommand extends HpSubCommand {
        private final HyperPerms hyperPerms;
        private final RequiredArg<String> filenameArg;

        ImportFileSubCommand(HyperPerms hyperPerms) {
            super("file", "Import data from a backup file");
            this.hyperPerms = hyperPerms;
            this.filenameArg = describeArg("filename", "Import file name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String filename = ctx.get(filenameArg);

            var backupManager = hyperPerms.getBackupManager();
            if (backupManager == null) {
                ctx.sender().sendMessage(Message.raw("Backup/import not available"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("Importing data from: " + filename));
            ctx.sender().sendMessage(Message.raw("WARNING: This will merge with or overwrite current data!"));

            return backupManager.restoreBackup(filename)
                .thenAccept(success -> {
                    if (success) {
                        ctx.sender().sendMessage(Message.raw("Data imported successfully!"));
                        ctx.sender().sendMessage(Message.raw("Please run /hp reload to apply changes"));
                    } else {
                        ctx.sender().sendMessage(Message.raw("Failed to import data. Check if file exists."));
                    }
                })
                .exceptionally(e -> {
                    ctx.sender().sendMessage(Message.raw("Error importing: " + e.getMessage()));
                    return null;
                });
        }
    }
}
