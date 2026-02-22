package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Node;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Copies all permissions, groups, prefix/suffix from one user to another.
 * Usage: /hp user clone &lt;source&gt; &lt;target&gt;
 */
public class UserCloneCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> sourceArg;
    private final RequiredArg<String> targetArg;

    public UserCloneCommand(HyperPerms hyperPerms) {
        super("clone", "Copy permissions from one user to another");
        this.hyperPerms = hyperPerms;
        this.sourceArg = describeArg("source", "Source player name or UUID", ArgTypes.STRING);
        this.targetArg = describeArg("target", "Target player name or UUID", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String sourceId = ctx.get(sourceArg);
        String targetId = ctx.get(targetArg);

        User source = PlayerResolver.resolve(hyperPerms, sourceId);
        if (source == null) {
            ctx.sender().sendMessage(Message.raw("Source user not found: " + sourceId));
            return CompletableFuture.completedFuture(null);
        }

        User target = PlayerResolver.resolve(hyperPerms, targetId);
        if (target == null) {
            ctx.sender().sendMessage(Message.raw("Target user not found: " + targetId));
            return CompletableFuture.completedFuture(null);
        }

        // Clear target data first
        target.getNodes().clear();
        target.getInheritedGroups().clear();

        // Copy nodes
        for (Node node : source.getNodes()) {
            target.setNode(node);
        }

        // Copy groups
        for (String group : source.getInheritedGroups()) {
            target.addGroup(group);
        }

        // Copy primary group
        target.setPrimaryGroup(source.getPrimaryGroup());

        // Copy custom prefix/suffix
        target.setCustomPrefix(source.getCustomPrefix());
        target.setCustomSuffix(source.getCustomSuffix());

        hyperPerms.getUserManager().saveUser(target);
        hyperPerms.getCacheInvalidator().invalidate(target.getUuid());
        var pluginObj = com.hyperperms.HyperPermsBootstrap.getPlugin();
        if (pluginObj instanceof com.hyperperms.platform.HyperPermsPlugin plugin) {
            plugin.syncPermissionsToHytale(target.getUuid(), target);
        }

        ctx.sender().sendMessage(Message.raw("Cloned permissions from " + source.getFriendlyName() + " to " + target.getFriendlyName()));
        return CompletableFuture.completedFuture(null);
    }
}
