package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
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
 * Shows detailed information about a user's groups and permissions.
 * Usage: /hp user info &lt;player&gt;
 */
public class UserInfoCommand extends HpSubCommand {

    private static final Color AMBER = new Color(255, 200, 0);

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;

    public UserInfoCommand(HyperPerms hyperPerms) {
        super("info", "Show user's groups and permissions");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        User user = PlayerResolver.resolve(hyperPerms, identifier);

        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Note: User must be online or have existing data"));
            return CompletableFuture.completedFuture(null);
        }

        int width = 42;
        String label = "User: " + user.getFriendlyName();
        int padding = width - label.length() - 2;
        int left = 3;
        int right = Math.max(3, padding - left);

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("-".repeat(left) + " ").color(GRAY));
        parts.add(Message.raw(label).color(GOLD));
        parts.add(Message.raw(" " + "-".repeat(right) + "\n").color(GRAY));

        // UUID
        parts.add(Message.raw("  UUID: ").color(GRAY));
        parts.add(Message.raw(user.getUuid().toString() + "\n").color(WHITE));

        // Primary group
        parts.add(Message.raw("  Primary Group: ").color(GOLD));
        parts.add(Message.raw(user.getPrimaryGroup() + "\n").color(GREEN));

        // Custom prefix
        parts.add(Message.raw("  Custom Prefix: ").color(GOLD));
        parts.add(Message.raw((user.getCustomPrefix() != null ? "\"" + user.getCustomPrefix() + "\"" : "(none)") + "\n").color(WHITE));

        // Custom suffix
        parts.add(Message.raw("  Custom Suffix: ").color(GOLD));
        parts.add(Message.raw((user.getCustomSuffix() != null ? "\"" + user.getCustomSuffix() + "\"" : "(none)") + "\n").color(WHITE));

        // Groups (with expiry info for temporary group nodes)
        parts.add(Message.raw("  Groups: ").color(GOLD));
        var groupNodes = user.getNodes().stream()
                .filter(Node::isGroupNode)
                .filter(n -> !n.isExpired())
                .sorted(Comparator.comparing(n -> n.getGroupName() != null ? n.getGroupName() : "", String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (groupNodes.isEmpty() && (user.getPrimaryGroup() == null || user.getPrimaryGroup().equals("default"))) {
            parts.add(Message.raw("(none)\n").color(GRAY));
        } else {
            boolean first = true;
            // Show primary group first
            if (user.getPrimaryGroup() != null && !user.getPrimaryGroup().isEmpty()) {
                parts.add(Message.raw(user.getPrimaryGroup()).color(GREEN));
                first = false;
            }
            for (Node gNode : groupNodes) {
                String gName = gNode.getGroupName();
                if (gName == null || gName.equalsIgnoreCase(user.getPrimaryGroup())) continue;
                if (!first) parts.add(Message.raw(", ").color(GRAY));
                parts.add(Message.raw(gName).color(GREEN));
                if (gNode.isTemporary()) {
                    parts.add(Message.raw(" (" + TimeUtil.formatExpiry(gNode.getExpiry()) + ")").color(AMBER));
                }
                first = false;
            }
            parts.add(Message.raw("\n").color(WHITE));
        }

        // Direct permissions
        long permCount = user.getNodes().stream().filter(n -> !n.isGroupNode()).count();
        parts.add(Message.raw("\n  Direct Permissions (" + permCount + "):\n").color(GOLD));
        if (permCount == 0) {
            parts.add(Message.raw("    (none)\n").color(GRAY));
        } else {
            user.getNodes().stream()
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
