package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.model.Group;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Lists all loaded groups with their weights.
 * <p>
 * Usage: /hp group list
 */
public class GroupListCommand extends AbstractCommand {
    private final HyperPerms hyperPerms;

    public GroupListCommand(HyperPerms hyperPerms) {
        super("list", "List all groups");
        this.hyperPerms = hyperPerms;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        var groups = hyperPerms.getGroupManager().getLoadedGroups();
        int width = 42;
        String label = "Groups (" + groups.size() + ")";
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        for (Group group : groups) {
            parts.add(Message.raw("  " + group.getName()).color(GREEN));
            parts.add(Message.raw(" (weight: ").color(GRAY));
            parts.add(Message.raw(String.valueOf(group.getWeight())).color(WHITE));
            parts.add(Message.raw(")\n").color(GRAY));
        }

        parts.add(Message.raw("-".repeat(width)).color(GRAY));
        ctx.sender().sendMessage(join(parts));
        return CompletableFuture.completedFuture(null);
    }
}
