package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Node;
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
 * Sets a permission on a user, with optional value and duration.
 * Usage: /hp user setperm &lt;player&gt; &lt;permission&gt; [value] [duration]
 */
public class UserSetPermCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> permArg;
    private final OptionalArg<String> valueArg;
    private final OptionalArg<String> durationArg;

    public UserSetPermCommand(HyperPerms hyperPerms) {
        super("setperm", "Set a permission on a user");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.permArg = describeArg("permission", "Permission node", ArgTypes.STRING);
        this.valueArg = describeOptionalArg("value", "true or false (default: true)", ArgTypes.STRING);
        this.durationArg = describeOptionalArg("duration", "Duration (e.g. 1d2h30m, permanent)", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String permission = ctx.get(permArg);
        String valueStr = ctx.get(valueArg);
        String durationStr = ctx.get(durationArg);

        // Use resolveOrCreateUser to support offline players (e.g., from Tebex)
        User user = PlayerResolver.resolveOrCreate(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        boolean value = valueStr == null || !valueStr.equalsIgnoreCase("false");

        // Normalize negation: -prefix always uses value=true
        // The - prefix already encodes "deny", value=false would double-negate
        if (permission.startsWith("-")) {
            value = true;
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

        var builder = Node.builder(permission).value(value);
        if (expiry != null) {
            builder.expiry(expiry);
        }
        Node node = builder.build();
        user.setNode(node);
        hyperPerms.getUserManager().saveUser(user).join();

        // Invalidate cache for this user
        hyperPerms.getCacheInvalidator().invalidate(user.getUuid());

        String displayPerm = node.getBasePermission();
        boolean granted = node.getValue() && !node.isNegated();
        String action = granted ? "Granted" : "Denied";
        String expiryMsg = node.isTemporary() ? " (" + TimeUtil.formatExpiry(node.getExpiry()) + ")" : "";
        ctx.sender().sendMessage(Message.raw(action + " " + displayPerm + " on user " + user.getFriendlyName() + expiryMsg));
        return CompletableFuture.completedFuture(null);
    }
}
