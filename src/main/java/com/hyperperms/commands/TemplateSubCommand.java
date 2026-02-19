package com.hyperperms.commands;

import com.hyperperms.HyperPerms;
import com.hyperperms.template.*;
import com.hyperperms.util.Logger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Container command for template management: /hp template
 */
public class TemplateSubCommand extends AbstractCommand {

    private final HyperPerms hyperPerms;
    private final TemplateLoader templateLoader;
    private final TemplateApplier templateApplier;
    private final TemplateExporter templateExporter;

    @SuppressWarnings("this-escape")
    public TemplateSubCommand(HyperPerms hyperPerms) {
        super("template", "Manage permission templates");
        this.hyperPerms = hyperPerms;

        // Initialize template system
        Path templatesDir = hyperPerms.getDataDirectory().resolve(
                hyperPerms.getConfig().getTemplatesCustomDirectory());
        this.templateLoader = new TemplateLoader(templatesDir);
        this.templateApplier = new TemplateApplier(hyperPerms);
        this.templateExporter = new TemplateExporter(hyperPerms, templatesDir);

        // Load templates
        templateLoader.loadAll();

        // Add subcommands
        addSubCommand(new ListSubCommand());
        addSubCommand(new PreviewSubCommand());
        addSubCommand(new ApplySubCommand());
        addSubCommand(new ExportSubCommand());
        addSubCommand(new ReloadSubCommand());

        // Help command
        addSubCommand(new AbstractCommand("help", "Show template help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ctx.sender().sendMessage(buildHelp());
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(buildHelp());
        return CompletableFuture.completedFuture(null);
    }

    private Message buildHelp() {
        return Message.raw("--- Template Commands ---\n").color(GRAY)
                .insert(Message.raw("  /hp template list").color(GOLD))
                .insert(Message.raw(" - Show available templates\n").color(WHITE))
                .insert(Message.raw("  /hp template preview <name>").color(GOLD))
                .insert(Message.raw(" - Preview a template\n").color(WHITE))
                .insert(Message.raw("  /hp template apply <name> [--mode]").color(GOLD))
                .insert(Message.raw(" - Apply a template\n").color(WHITE))
                .insert(Message.raw("  /hp template export <name>").color(GOLD))
                .insert(Message.raw(" - Export current setup\n").color(WHITE))
                .insert(Message.raw("  /hp template reload").color(GOLD))
                .insert(Message.raw(" - Reload templates\n").color(WHITE))
                .insert(Message.raw("----------------------------").color(GRAY));
    }

    /**
     * /hp template list
     */
    private class ListSubCommand extends AbstractCommand {
        ListSubCommand() {
            super("list", "List all available templates");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            Collection<PermissionTemplate> templates = templateLoader.getAllTemplates();

            if (templates.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("No templates found.").color(GRAY));
                return CompletableFuture.completedFuture(null);
            }

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("--- Available Templates ---\n").color(GRAY));

            // Bundled templates
            List<PermissionTemplate> bundled = templates.stream()
                    .filter(PermissionTemplate::isBundled)
                    .sorted(Comparator.comparing(PermissionTemplate::getName))
                    .toList();

            if (!bundled.isEmpty()) {
                parts.add(Message.raw("  Bundled:\n").color(AQUA));
                for (PermissionTemplate t : bundled) {
                    parts.add(Message.raw("    " + t.getName()).color(GREEN));
                    parts.add(Message.raw(" - " + t.getDisplayName()).color(WHITE));
                    parts.add(Message.raw(" (" + t.getGroupCount() + " groups)\n").color(GRAY));
                }
            }

            // Custom templates
            List<PermissionTemplate> custom = templates.stream()
                    .filter(t -> !t.isBundled())
                    .sorted(Comparator.comparing(PermissionTemplate::getName))
                    .toList();

            if (!custom.isEmpty()) {
                parts.add(Message.raw("  Custom:\n").color(YELLOW));
                for (PermissionTemplate t : custom) {
                    parts.add(Message.raw("    " + t.getName()).color(GREEN));
                    parts.add(Message.raw(" - " + t.getDisplayName()).color(WHITE));
                    parts.add(Message.raw(" (" + t.getGroupCount() + " groups)\n").color(GRAY));
                }
            }

            parts.add(Message.raw("---------------------------\n").color(GRAY));
            parts.add(Message.raw("Use /hp template preview <name> for details").color(GRAY));

            ctx.sender().sendMessage(join(parts));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /hp template preview <name>
     */
    private class PreviewSubCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        PreviewSubCommand() {
            super("preview", "Preview a template");
            this.nameArg = withRequiredArg("name", "Template name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String name = nameArg.get(ctx);
            PermissionTemplate template = templateLoader.getTemplate(name);

            if (template == null) {
                ctx.sender().sendMessage(Message.raw("Template not found: " + name).color(RED));
                return CompletableFuture.completedFuture(null);
            }

            // Get apply preview
            TemplateApplier.ApplyPreview preview = templateApplier.preview(template, TemplateApplier.ApplyMode.MERGE);

            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw("--- Template: " + template.getDisplayName() + " ---\n").color(GOLD));
            parts.add(Message.raw("  Name: ").color(GRAY).insert(Message.raw(template.getName() + "\n").color(WHITE)));
            parts.add(Message.raw("  Description: ").color(GRAY).insert(Message.raw(template.getDescription() + "\n").color(WHITE)));
            parts.add(Message.raw("  Version: ").color(GRAY).insert(Message.raw(template.getVersion() + "\n").color(WHITE)));
            parts.add(Message.raw("  Author: ").color(GRAY).insert(Message.raw(template.getAuthor() + "\n").color(WHITE)));
            parts.add(Message.raw("  Type: ").color(GRAY).insert(Message.raw((template.isBundled() ? "Bundled" : "Custom") + "\n").color(AQUA)));
            parts.add(Message.raw("\n").color(WHITE));

            // Groups
            parts.add(Message.raw("  Groups (" + template.getGroupCount() + "):\n").color(GREEN));
            for (String groupName : template.getGroupNamesSortedByWeight()) {
                TemplateGroup group = template.getGroup(groupName);
                if (group != null) {
                    String info = String.format("    %s (weight: %d, perms: %d)",
                            groupName, group.getWeight(), group.getPermissionCount());
                    parts.add(Message.raw(info + "\n").color(WHITE));
                }
            }

            // Tracks
            if (!template.getTracks().isEmpty()) {
                parts.add(Message.raw("\n  Tracks:\n").color(AQUA));
                for (TemplateTrack track : template.getTracks().values()) {
                    parts.add(Message.raw("    " + track.name() + ": ").color(WHITE));
                    parts.add(Message.raw(String.join(" → ", track.groups()) + "\n").color(GRAY));
                }
            }

            // Apply preview
            parts.add(Message.raw("\n  If applied (merge mode):\n").color(YELLOW));
            if (!preview.groupsToCreate().isEmpty()) {
                parts.add(Message.raw("    New groups: ").color(GREEN)
                        .insert(Message.raw(String.join(", ", preview.groupsToCreate()) + "\n").color(WHITE)));
            }
            if (!preview.groupsToUpdate().isEmpty()) {
                parts.add(Message.raw("    Updated groups: ").color(YELLOW)
                        .insert(Message.raw(String.join(", ", preview.groupsToUpdate()) + "\n").color(WHITE)));
            }

            // Metadata
            if (template.getMetadata().hasRecommendedPlugins()) {
                parts.add(Message.raw("\n  Recommended plugins: ").color(GRAY)
                        .insert(Message.raw(String.join(", ", template.getMetadata().recommendedPlugins()) + "\n").color(AQUA)));
            }

            parts.add(Message.raw("----------------------------------------").color(GRAY));

            ctx.sender().sendMessage(join(parts));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /hp template apply <name> [--mode=merge|replace]
     */
    private class ApplySubCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;
        private final OptionalArg<String> modeArg;

        ApplySubCommand() {
            super("apply", "Apply a template to the server");
            this.nameArg = withRequiredArg("name", "Template name", ArgTypes.STRING);
            this.modeArg = withOptionalArg("mode", "Apply mode (merge or replace)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String name = nameArg.get(ctx);
            String modeStr = modeArg.get(ctx);

            PermissionTemplate template = templateLoader.getTemplate(name);
            if (template == null) {
                ctx.sender().sendMessage(Message.raw("Template not found: " + name).color(RED));
                return CompletableFuture.completedFuture(null);
            }

            // Parse mode
            TemplateApplier.ApplyMode mode = TemplateApplier.ApplyMode.MERGE;
            if (modeStr != null) {
                String modeLower = modeStr.toLowerCase().replace("--", "").replace("mode=", "");
                if (modeLower.equals("replace")) {
                    mode = TemplateApplier.ApplyMode.REPLACE;
                } else if (!modeLower.equals("merge")) {
                    ctx.sender().sendMessage(Message.raw("Invalid mode. Use 'merge' or 'replace'.").color(RED));
                    return CompletableFuture.completedFuture(null);
                }
            }

            final TemplateApplier.ApplyMode finalMode = mode;
            ctx.sender().sendMessage(Message.raw("Applying template '" + name + "' in " + mode.name().toLowerCase() + " mode...").color(YELLOW));

            return templateApplier.apply(template, finalMode)
                    .thenAccept(result -> {
                        if (result.success()) {
                            List<Message> parts = new ArrayList<>();
                            parts.add(Message.raw("Template applied successfully!\n").color(GREEN));
                            parts.add(Message.raw("  Groups created: ").color(GRAY)
                                    .insert(Message.raw(result.groupsCreated() + "\n").color(WHITE)));
                            parts.add(Message.raw("  Groups updated: ").color(GRAY)
                                    .insert(Message.raw(result.groupsUpdated() + "\n").color(WHITE)));
                            if (result.groupsRemoved() > 0) {
                                parts.add(Message.raw("  Groups removed: ").color(GRAY)
                                        .insert(Message.raw(result.groupsRemoved() + "\n").color(WHITE)));
                            }
                            if (result.tracksCreated() > 0 || result.tracksUpdated() > 0) {
                                parts.add(Message.raw("  Tracks: ").color(GRAY)
                                        .insert(Message.raw(result.tracksCreated() + " created, " + result.tracksUpdated() + " updated\n").color(WHITE)));
                            }
                            if (result.backupName() != null) {
                                parts.add(Message.raw("  Backup: ").color(GRAY)
                                        .insert(Message.raw(result.backupName() + "\n").color(AQUA)));
                            }
                            ctx.sender().sendMessage(join(parts));
                        } else {
                            ctx.sender().sendMessage(Message.raw("Failed to apply template: " + result.message()).color(RED));
                        }
                    })
                    .exceptionally(e -> {
                        ctx.sender().sendMessage(Message.raw("Error applying template: " + e.getMessage()).color(RED));
                        Logger.warn("[Template] Error applying template: %s", e.getMessage());
                        return null;
                    });
        }
    }

    /**
     * /hp template export <name>
     */
    private class ExportSubCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        ExportSubCommand() {
            super("export", "Export current setup as a template");
            this.nameArg = withRequiredArg("name", "Template name to create", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            String name = nameArg.get(ctx);

            ctx.sender().sendMessage(Message.raw("Exporting current setup as '" + name + "'...").color(YELLOW));

            return templateExporter.export(name, null, null, null)
                    .thenAccept(result -> {
                        if (result.success()) {
                            ctx.sender().sendMessage(Message.raw("Template exported successfully!").color(GREEN));
                            if (result.filePath() != null) {
                                ctx.sender().sendMessage(Message.raw("File: " + result.filePath().getFileName()).color(GRAY));
                            }
                            ctx.sender().sendMessage(Message.raw("Use /hp template reload to load it.").color(GRAY));
                        } else {
                            ctx.sender().sendMessage(Message.raw("Failed to export: " + result.message()).color(RED));
                        }
                    })
                    .exceptionally(e -> {
                        ctx.sender().sendMessage(Message.raw("Error exporting template: " + e.getMessage()).color(RED));
                        return null;
                    });
        }
    }

    /**
     * /hp template reload
     */
    private class ReloadSubCommand extends AbstractCommand {
        ReloadSubCommand() {
            super("reload", "Reload all templates");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            templateLoader.reload();
            ctx.sender().sendMessage(Message.raw("Templates reloaded. " + templateLoader.getAllTemplates().size() + " templates loaded.").color(GREEN));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Gets the template loader instance.
     */
    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }
}
