package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Adds a user to a group, with optional duration.
 * Usage: /hp user addgroup &lt;player&gt; &lt;group&gt; [duration]
 */
public class UserAddGroupCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> groupArg;
    private final OptionalArg<String> durationArg;

    public UserAddGroupCommand(HyperPerms hyperPerms) {
        super("addgroup", "Add a user to a group");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.groupArg = describeArg("group", "Group name", ArgTypes.STRING);
        this.durationArg = describeOptionalArg("duration", "Duration (e.g. 1d2h30m, permanent)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String groupName = ctx.get(groupArg);
        String durationStr = ctx.get(durationArg);

        Group group = hyperPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Group not found: " + groupName));
            return CompletableFuture.completedFuture(null);
        }

        // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
        User user = PlayerResolver.resolveOrCreate(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        // Parse optional duration
        Instant expiry = null;
        if (durationStr != null && !durationStr.isBlank()) {
            Optional<Duration> duration = TimeUtil.parseDuration(durationStr);
            if (duration.isEmpty() && !durationStr.equalsIgnoreCase("permanent")
                    && !durationStr.equalsIgnoreCase("perm") && !durationStr.equalsIgnoreCase("forever")) {
                ctx.sender().sendMessage(Message.raw("Invalid duration: " + durationStr + ". Use formats like 1d, 2h30m, 1w"));
                return CompletableFuture.completedFuture(null);
            }
            if (duration.isPresent()) {
                expiry = TimeUtil.expiryFromDuration(duration.get());
            }
        }

        user.addGroup(groupName, expiry);
        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCacheInvalidator().invalidate(user.getUuid());
        var pluginObj = com.hyperperms.HyperPermsBootstrap.getPlugin();
        if (pluginObj instanceof com.hyperperms.platform.HyperPermsPlugin plugin) {
            plugin.syncPermissionsToHytale(user.getUuid(), user);
        }
        String expiryMsg = expiry != null ? " (" + TimeUtil.formatExpiry(expiry) + ")" : "";
        ctx.sender().sendMessage(Message.raw("Added user " + user.getFriendlyName() + " to group " + groupName + expiryMsg));
        return CompletableFuture.completedFuture(null);
    }
}
