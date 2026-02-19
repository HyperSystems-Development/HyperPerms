package com.hyperperms.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Reset all groups to default from default-groups.json.
 * <p>
 * Usage: /hp resetgroups
 * <p>
 * Requires confirmation: /hp resetgroups confirm
 */
public class ResetGroupsCommand extends AbstractCommand {
    private final HyperPerms hyperPerms;

    @SuppressWarnings("this-escape")
    public ResetGroupsCommand(HyperPerms hyperPerms) {
        super("resetgroups", "Reset all groups to default");
        this.hyperPerms = hyperPerms;
        addSubCommand(new ResetGroupsConfirmSubCommand(hyperPerms));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== WARNING ===").color(RED));
        ctx.sender().sendMessage(Message.raw("This will DELETE all existing groups and replace them with defaults."));
        ctx.sender().sendMessage(Message.raw("User group memberships will be preserved, but custom group settings will be lost."));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("To confirm, run: /hp resetgroups confirm"));
        ctx.sender().sendMessage(Message.raw(""));
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Confirmation ====================

    private static class ResetGroupsConfirmSubCommand extends AbstractCommand {
        private final HyperPerms hyperPerms;

        ResetGroupsConfirmSubCommand(HyperPerms hyperPerms) {
            super("confirm", "Confirm resetting groups to default");
            this.hyperPerms = hyperPerms;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sender().sendMessage(Message.raw("Resetting groups to defaults...").color(GRAY));

            try (var inputStream = getClass().getClassLoader().getResourceAsStream("default-groups.json")) {
                if (inputStream == null) {
                    ctx.sender().sendMessage(Message.raw("✗ Error: default-groups.json not found in plugin resources").color(RED));
                    return CompletableFuture.completedFuture(null);
                }

                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject groups = root.getAsJsonObject("groups");

                if (groups == null) {
                    ctx.sender().sendMessage(Message.raw("✗ Error: No 'groups' object found in default-groups.json").color(RED));
                    return CompletableFuture.completedFuture(null);
                }

                int updated = 0;
                for (var entry : groups.entrySet()) {
                    String groupName = entry.getKey();
                    JsonObject groupData = entry.getValue().getAsJsonObject();

                    // Get or create the group
                    Group group = hyperPerms.getGroupManager().getGroup(groupName);
                    if (group == null) {
                        group = hyperPerms.getGroupManager().createGroup(groupName);
                    } else {
                        // Clear existing permissions and parents (both stored as nodes)
                        group.clearNodes();
                    }

                    // Set weight
                    if (groupData.has("weight")) {
                        group.setWeight(groupData.get("weight").getAsInt());
                    }

                    // Set prefix
                    if (groupData.has("prefix")) {
                        group.setPrefix(groupData.get("prefix").getAsString());
                    }

                    // Set suffix
                    if (groupData.has("suffix")) {
                        group.setSuffix(groupData.get("suffix").getAsString());
                    }

                    // Add permissions
                    if (groupData.has("permissions")) {
                        for (var perm : groupData.getAsJsonArray("permissions")) {
                            group.addNode(Node.builder(perm.getAsString()).build());
                        }
                    }

                    // Add parent groups
                    if (groupData.has("parents")) {
                        for (var parent : groupData.getAsJsonArray("parents")) {
                            group.addParent(parent.getAsString());
                        }
                    }

                    // Save the group
                    hyperPerms.getGroupManager().saveGroup(group).join();
                    updated++;
                }

                // Invalidate all caches
                hyperPerms.getCacheInvalidator().invalidateAll();

                ctx.sender().sendMessage(Message.raw(""));

                List<Message> success = new ArrayList<>();
                success.add(Message.raw("✓ Success! Reset ").color(GREEN));
                success.add(Message.raw(String.valueOf(updated)).color(WHITE));
                success.add(Message.raw(" groups to defaults.").color(GREEN));
                ctx.sender().sendMessage(join(success));

                ctx.sender().sendMessage(Message.raw("All permission caches have been invalidated.").color(GRAY));
                ctx.sender().sendMessage(Message.raw(""));

            } catch (Exception e) {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("✗ Error resetting groups: ").color(RED));
                parts.add(Message.raw(e.getMessage()).color(GRAY));
                ctx.sender().sendMessage(join(parts));
            }

            return CompletableFuture.completedFuture(null);
        }
    }
}
