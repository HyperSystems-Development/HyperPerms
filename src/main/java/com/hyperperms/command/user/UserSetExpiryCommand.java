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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Sets or clears the expiry on a user's permission node.
 * Usage: /hp user setexpiry &lt;player&gt; &lt;permission&gt; &lt;duration&gt;
 */
public class UserSetExpiryCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> permArg;
    private final RequiredArg<String> durationArg;

    public UserSetExpiryCommand(HyperPerms hyperPerms) {
        super("setexpiry", "Set or clear expiry on a user permission");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
        this.durationArg = describeArg("duration", "Duration (e.g. 1d2h30m) or 'permanent'", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String permission = ctx.get(permArg).toLowerCase();
        String durationStr = ctx.get(durationArg);

        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            return CompletableFuture.completedFuture(null);
        }

        // Find existing node
        Node existingNode = user.getNodes().stream()
                .filter(n -> n.getPermission().equals(permission))
                .findFirst()
                .orElse(null);

        if (existingNode == null) {
            ctx.sender().sendMessage(Message.raw("Permission " + permission + " not found on user " + user.getFriendlyName()));
            return CompletableFuture.completedFuture(null);
        }

        // Parse duration
        Instant newExpiry = null;
        if (!durationStr.equalsIgnoreCase("permanent") && !durationStr.equalsIgnoreCase("perm")
                && !durationStr.equalsIgnoreCase("forever")) {
            Optional<Duration> duration = TimeUtil.parseDuration(durationStr);
            if (duration.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("Invalid duration: " + durationStr + ". Use formats like 1d, 2h30m, 1w, or 'permanent'"));
                return CompletableFuture.completedFuture(null);
            }
            newExpiry = TimeUtil.expiryFromDuration(duration.get());
        }

        // Replace with updated expiry
        Node newNode = existingNode.withExpiry(newExpiry);
        user.setNode(newNode);
        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCache().invalidate(user.getUuid());

        String expiryDisplay = newExpiry != null ? TimeUtil.formatExpiry(newExpiry) : "permanent";
        ctx.sender().sendMessage(Message.raw("Set expiry on " + permission + " for user " + user.getFriendlyName() + " to " + expiryDisplay));
        return CompletableFuture.completedFuture(null);
    }
}
