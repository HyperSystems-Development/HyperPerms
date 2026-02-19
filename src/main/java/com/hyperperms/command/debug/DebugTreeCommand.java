package com.hyperperms.command.debug;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Show inheritance tree for a user, displaying direct permissions and group hierarchy.
 * <p>
 * Usage: /hp debug tree <user>
 */
public class DebugTreeCommand extends HpSubCommand {
    private final HyperPerms hyperPerms;
    private final RequiredArg<String> userArg;

    public DebugTreeCommand(HyperPerms hyperPerms) {
        super("tree", "Show inheritance tree for a user");
        this.hyperPerms = hyperPerms;
        this.userArg = describeArg("user", "Player name or UUID", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(userArg);
        User user = PlayerResolver.resolve(hyperPerms, identifier);

        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("=== Inheritance Tree for " + user.getFriendlyName() + " ==="));

        // Direct permissions
        ctx.sender().sendMessage(Message.raw("Direct Permissions:"));
        var directPerms = user.getNodes();
        if (directPerms.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (none)"));
        } else {
            for (var node : directPerms) {
                boolean granted = node.getValue() && !node.isNegated();
                String prefix = granted ? "  + " : "  - ";
                ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
            }
        }

        // Groups
        ctx.sender().sendMessage(Message.raw("Groups:"));
        var groups = user.getInheritedGroups();
        if (groups.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("  (none)"));
        } else {
            for (String groupName : groups) {
                printGroupTree(ctx, groupName, "  ", new HashSet<>());
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private void printGroupTree(CommandContext ctx, String groupName,
                                String indent, Set<String> visited) {
        if (visited.contains(groupName)) {
            ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (circular ref)"));
            return;
        }
        visited.add(groupName);

        var group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (not found)"));
            return;
        }

        ctx.sender().sendMessage(Message.raw(indent + "[" + groupName + "] (weight=" + group.getWeight() + ")"));

        // Show permissions
        for (var node : group.getNodes()) {
            boolean granted = node.getValue() && !node.isNegated();
            String prefix = granted ? indent + "  + " : indent + "  - ";
            ctx.sender().sendMessage(Message.raw(prefix + node.getBasePermission() + formatContext(node)));
        }

        // Show parent groups
        for (String parent : group.getParentGroups()) {
            printGroupTree(ctx, parent, indent + "  ", visited);
        }
    }

    private String formatContext(Node node) {
        var contexts = node.getContexts();
        if (contexts == null || contexts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" [");
        boolean first = true;
        for (var ctx : contexts.toSet()) {
            if (!first) sb.append(", ");
            sb.append(ctx.key()).append("=").append(ctx.value());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
