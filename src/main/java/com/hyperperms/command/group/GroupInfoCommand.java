package com.hyperperms.command.group;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.Node;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Displays detailed information about a group: display name, weight, prefix/suffix,
 * priorities, parents, and all permissions with expiry info.
 * <p>
 * Usage: /hp group info &lt;name&gt;
 */
public class GroupInfoCommand extends HpSubCommand {
    private static final Color AMBER = new Color(255, 200, 0);

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> nameArg;

    public GroupInfoCommand(HyperPerms hyperPerms) {
        super("info", "View group info");
        this.hyperPerms = hyperPerms;
        this.nameArg = describeArg("name", "Group name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String groupName = ctx.get(nameArg);
        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        int width = 42;
        String label = "Group: " + group.getName();
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        // Display name
        parts.add(Message.raw("  Display Name: ").color(GOLD));
        parts.add(Message.raw((group.getDisplayName() != null ? group.getDisplayName() : group.getName()) + "\n").color(WHITE));

        // Weight
        parts.add(Message.raw("  Weight: ").color(GOLD));
        parts.add(Message.raw(group.getWeight() + "\n").color(WHITE));

        // Prefix
        parts.add(Message.raw("  Prefix: ").color(GOLD));
        parts.add(Message.raw((group.getPrefix() != null ? "\"" + group.getPrefix() + "\"" : "(none)") + "\n").color(WHITE));

        // Suffix
        parts.add(Message.raw("  Suffix: ").color(GOLD));
        parts.add(Message.raw((group.getSuffix() != null ? "\"" + group.getSuffix() + "\"" : "(none)") + "\n").color(WHITE));

        // Priorities
        parts.add(Message.raw("  Prefix Priority: ").color(GRAY));
        parts.add(Message.raw(group.getPrefixPriority() + "\n").color(WHITE));
        parts.add(Message.raw("  Suffix Priority: ").color(GRAY));
        parts.add(Message.raw(group.getSuffixPriority() + "\n").color(WHITE));

        // Parents
        var parents = group.getParentGroups();
        parts.add(Message.raw("  Parents: ").color(GOLD));
        parts.add(Message.raw((!parents.isEmpty() ? String.join(", ", parents) : "(none)") + "\n").color(GREEN));

        // Permissions
        long permCount = group.getNodes().stream().filter(n -> !n.isGroupNode()).count();
        parts.add(Message.raw("\n  Permissions (" + permCount + "):\n").color(GOLD));
        if (permCount == 0) {
            parts.add(Message.raw("    (none)\n").color(GRAY));
        } else {
            group.getNodes().stream()
                .filter(n -> !n.isGroupNode())
                .sorted(Comparator.comparing(Node::getBasePermission, String.CASE_INSENSITIVE_ORDER))
                .forEach(node -> {
                    boolean granted = node.getValue() && !node.isNegated();
                    String prefix = granted ? "+" : "-";
                    String displayPerm = node.getBasePermission();
                    Color permColor = granted ? GREEN : RED;
                    parts.add(Message.raw("    " + prefix + " " + displayPerm).color(permColor));
                    if (node.isTemporary()) {
                        if (node.isExpired()) {
                            parts.add(Message.raw(" (EXPIRED)").color(GRAY));
                        } else {
                            parts.add(Message.raw(" (" + TimeUtil.formatExpiry(node.getExpiry()) + ")").color(AMBER));
                        }
                    }
                    parts.add(Message.raw("\n").color(WHITE));
                });
        }

        parts.add(Message.raw("-".repeat(width)).color(GRAY));
        ctx.sender().sendMessage(join(parts));
        return CompletableFuture.completedFuture(null);
    }
}
